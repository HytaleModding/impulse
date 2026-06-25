#include "internal/impulse_jolt_registry.h"
#include "internal/impulse_jolt_space.h"

#include <Jolt/Physics/Body/BodyInterface.h>

#include <algorithm>
#include <thread>

namespace ImpulseJolt {

ImpulseContactListener::ImpulseContactListener(Space* owner)
    : m_Owner(owner) {
}

Space::Space()
    : m_BroadPhaseLayerInterface(1),
      m_ObjectVsBroadphaseLayerFilter(m_BroadPhaseLayerInterface),
      m_ContactListener(this),
      m_TempAllocator(10 * 1024 * 1024),
      m_JobSystem(JPH::cMaxPhysicsJobs,
          JPH::cMaxPhysicsBarriers,
          std::max(1U, std::thread::hardware_concurrency())) {
    m_BroadPhaseLayerInterface.ConfigureLayer(
        JPH::BroadPhaseLayer(0),
        JPH::ObjectLayerPairFilterMask::cMask,
        0);
    m_PhysicsSystem.Init(MaxBodies,
        0,
        MaxBodyPairs,
        MaxContactConstraints,
        m_BroadPhaseLayerInterface,
        m_ObjectVsBroadphaseLayerFilter,
        m_ObjectLayerFilter);
    m_PhysicsSystem.SetContactListener(&m_ContactListener);
    m_PhysicsSystem.SetGravity(JPH::Vec3(0.0F, -9.81F, 0.0F));
}

Space::~Space() {
    for (auto& entry : m_Joints) {
        JointState& joint = entry.second;
        if (joint.m_Constraint != nullptr) {
            m_PhysicsSystem.RemoveConstraint(joint.m_Constraint);
        }
    }
    m_Joints.clear();
    JPH::BodyInterface& bodyInterface = m_PhysicsSystem.GetBodyInterface();
    for (auto& entry : m_Bodies) {
        BodyState& body = entry.second;
        if (!body.m_BodyId.IsInvalid()) {
            if (bodyInterface.IsAdded(body.m_BodyId)) {
                bodyInterface.RemoveBody(body.m_BodyId);
            }
            bodyInterface.DestroyBody(body.m_BodyId);
        }
    }
}

void Space::ReplaceContactRecords(const JPH::Body& body1,
    const JPH::Body& body2,
    const JPH::ContactManifold& manifold) {
    std::uint64_t bodyAHandle = NativeHandleForBodyId(*this, body1.GetID());
    std::uint64_t bodyBHandle = NativeHandleForBodyId(*this, body2.GetID());
    if (bodyAHandle == 0 || bodyBHandle == 0) {
        return;
    }

    JPH::SubShapeIDPair key(body1.GetID(),
        manifold.mSubShapeID1,
        body2.GetID(),
        manifold.mSubShapeID2);
    std::lock_guard<std::mutex> lock(m_ContactMutex);
    m_Contacts.erase(std::remove_if(m_Contacts.begin(),
                       m_Contacts.end(),
                       [&key](const ContactRecord& record) {
                           return record.m_Key == key;
                       }),
        m_Contacts.end());

    for (JPH::uint index = 0; index < manifold.mRelativeContactPointsOn1.size(); index++) {
        JPH::RVec3 pointA = manifold.GetWorldSpaceContactPointOn1(index);
        JPH::RVec3 pointB = manifold.GetWorldSpaceContactPointOn2(index);
        ContactRecord record;
        record.m_Key = key;
        record.m_BodyAHandle = bodyAHandle;
        record.m_BodyBHandle = bodyBHandle;
        record.m_PointAX = static_cast<float>(pointA.GetX());
        record.m_PointAY = static_cast<float>(pointA.GetY());
        record.m_PointAZ = static_cast<float>(pointA.GetZ());
        record.m_PointBX = static_cast<float>(pointB.GetX());
        record.m_PointBY = static_cast<float>(pointB.GetY());
        record.m_PointBZ = static_cast<float>(pointB.GetZ());
        record.m_NormalBX = manifold.mWorldSpaceNormal.GetX();
        record.m_NormalBY = manifold.mWorldSpaceNormal.GetY();
        record.m_NormalBZ = manifold.mWorldSpaceNormal.GetZ();
        record.m_Distance = -manifold.mPenetrationDepth;
        record.m_Impulse = 0.0F;
        m_Contacts.push_back(record);
    }
}

void Space::EraseContactRecords(const JPH::SubShapeIDPair& key) {
    std::lock_guard<std::mutex> lock(m_ContactMutex);
    m_Contacts.erase(std::remove_if(m_Contacts.begin(),
                       m_Contacts.end(),
                       [&key](const ContactRecord& record) {
                           return record.m_Key == key;
                       }),
        m_Contacts.end());
}

void Space::EraseContactRecordsForBodyHandle(std::uint64_t bodyHandle) {
    std::lock_guard<std::mutex> lock(m_ContactMutex);
    m_Contacts.erase(std::remove_if(m_Contacts.begin(),
                       m_Contacts.end(),
                       [bodyHandle](const ContactRecord& record) {
                           return record.m_BodyAHandle == bodyHandle
                               || record.m_BodyBHandle == bodyHandle;
                       }),
        m_Contacts.end());
}

void Space::EraseConstraintsForBodyHandle(std::uint64_t bodyHandle) {
    for (auto iterator = m_Joints.begin(); iterator != m_Joints.end();) {
        JointState& joint = iterator->second;
        if (joint.m_BodyAHandle != bodyHandle && joint.m_BodyBHandle != bodyHandle) {
            ++iterator;
            continue;
        }
        if (joint.m_Constraint != nullptr) {
            m_PhysicsSystem.RemoveConstraint(joint.m_Constraint);
        }
        iterator = m_Joints.erase(iterator);
    }
}

bool Space::RemoveJointHandle(std::uint64_t jointHandle) {
    auto iterator = m_Joints.find(jointHandle);
    if (iterator == m_Joints.end()) {
        return false;
    }
    if (iterator->second.m_Constraint != nullptr) {
        m_PhysicsSystem.RemoveConstraint(iterator->second.m_Constraint);
    }
    m_Joints.erase(iterator);
    return true;
}

void ImpulseContactListener::OnContactAdded(const JPH::Body& body1,
    const JPH::Body& body2,
    const JPH::ContactManifold& manifold,
    JPH::ContactSettings& settings) {
    (void) settings;
    if (m_Owner != nullptr) {
        m_Owner->ReplaceContactRecords(body1, body2, manifold);
    }
}

void ImpulseContactListener::OnContactPersisted(const JPH::Body& body1,
    const JPH::Body& body2,
    const JPH::ContactManifold& manifold,
    JPH::ContactSettings& settings) {
    (void) settings;
    if (m_Owner != nullptr) {
        m_Owner->ReplaceContactRecords(body1, body2, manifold);
    }
}

void ImpulseContactListener::OnContactRemoved(const JPH::SubShapeIDPair& subShapePair) {
    if (m_Owner != nullptr) {
        m_Owner->EraseContactRecords(subShapePair);
    }
}

} // namespace ImpulseJolt
