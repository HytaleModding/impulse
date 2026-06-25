#pragma once

#include "abi/impulse_jolt_native.h"

#include <Jolt/Jolt.h>
#include <Jolt/Core/JobSystemThreadPool.h>
#include <Jolt/Core/TempAllocator.h>
#include <Jolt/Physics/PhysicsSystem.h>
#include <Jolt/Physics/Body/BodyID.h>
#include <Jolt/Physics/Collision/BroadPhase/BroadPhaseLayerInterfaceMask.h>
#include <Jolt/Physics/Collision/BroadPhase/ObjectVsBroadPhaseLayerFilterMask.h>
#include <Jolt/Physics/Collision/ContactListener.h>
#include <Jolt/Physics/Collision/ObjectLayerPairFilterMask.h>
#include <Jolt/Physics/Constraints/Constraint.h>

#include <cstdint>
#include <mutex>
#include <unordered_map>
#include <vector>

namespace ImpulseJolt {

struct Space;

struct ContactRecord {
    JPH::SubShapeIDPair m_Key;
    std::uint64_t m_BodyAHandle = 0;
    std::uint64_t m_BodyBHandle = 0;
    float m_PointAX = 0.0F;
    float m_PointAY = 0.0F;
    float m_PointAZ = 0.0F;
    float m_PointBX = 0.0F;
    float m_PointBY = 0.0F;
    float m_PointBZ = 0.0F;
    float m_NormalBX = 0.0F;
    float m_NormalBY = 0.0F;
    float m_NormalBZ = 0.0F;
    float m_Distance = 0.0F;
    float m_Impulse = 0.0F;
};

class ImpulseContactListener final : public JPH::ContactListener {
public:
    explicit ImpulseContactListener(Space* owner);

    void OnContactAdded(const JPH::Body& body1,
        const JPH::Body& body2,
        const JPH::ContactManifold& manifold,
        JPH::ContactSettings& settings) override;

    void OnContactPersisted(const JPH::Body& body1,
        const JPH::Body& body2,
        const JPH::ContactManifold& manifold,
        JPH::ContactSettings& settings) override;

    void OnContactRemoved(const JPH::SubShapeIDPair& subShapePair) override;

private:
    Space* m_Owner;
};

struct BodyState {
    JPH::BodyID m_BodyId;
    int m_ShapeType = 0;
    int m_BodyType = 0;
    bool m_Sensor = false;
    float m_Mass = 0.0F;
    float m_Friction = 0.0F;
    float m_Restitution = 0.0F;
    float m_LinearDamping = 0.0F;
    float m_AngularDamping = 0.0F;
    int m_CollisionGroup = 0;
    int m_CollisionMask = 0;
    bool m_ContinuousCollision = false;
    float m_CenterOfMassOffsetY = 0.0F;
    bool m_HasBoxHalfExtents = false;
    float m_HalfExtentX = 0.0F;
    float m_HalfExtentY = 0.0F;
    float m_HalfExtentZ = 0.0F;
    float m_Radius = 0.0F;
    float m_HalfHeight = 0.0F;
    int m_Axis = AxisY;
};

struct JointState {
    JPH::Ref<JPH::Constraint> m_Constraint;
    std::uint64_t m_BodyAHandle = 0;
    std::uint64_t m_BodyBHandle = 0;
    int m_JointType = 0;
};

struct Space {
    JPH::BroadPhaseLayerInterfaceMask m_BroadPhaseLayerInterface;
    JPH::ObjectVsBroadPhaseLayerFilterMask m_ObjectVsBroadphaseLayerFilter;
    JPH::ObjectLayerPairFilterMask m_ObjectLayerFilter;
    JPH::PhysicsSystem m_PhysicsSystem;
    ImpulseContactListener m_ContactListener;
    JPH::TempAllocatorImpl m_TempAllocator;
    JPH::JobSystemThreadPool m_JobSystem;
    std::unordered_map<std::uint64_t, BodyState> m_Bodies;
    std::unordered_map<std::uint32_t, std::uint64_t> m_BodyHandlesByJoltId;
    std::unordered_map<std::uint64_t, JointState> m_Joints;
    std::mutex m_ContactMutex;
    std::vector<ContactRecord> m_Contacts;

    Space();
    ~Space();

    void ReplaceContactRecords(const JPH::Body& body1,
        const JPH::Body& body2,
        const JPH::ContactManifold& manifold);

    void EraseContactRecords(const JPH::SubShapeIDPair& key);

    void EraseContactRecordsForBodyHandle(std::uint64_t bodyHandle);

    void EraseConstraintsForBodyHandle(std::uint64_t bodyHandle);

    bool RemoveJointHandle(std::uint64_t jointHandle);
};

} // namespace ImpulseJolt
