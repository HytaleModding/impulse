#include <Jolt/Jolt.h>

#include <Jolt/RegisterTypes.h>
#include <Jolt/Core/Factory.h>
#include <Jolt/Core/JobSystemThreadPool.h>
#include <Jolt/Core/Memory.h>
#include <Jolt/Core/TempAllocator.h>
#include <Jolt/Geometry/Plane.h>
#include <Jolt/Math/Quat.h>
#include <Jolt/Math/Vec3.h>
#include <Jolt/Physics/EActivation.h>
#include <Jolt/Physics/PhysicsSystem.h>
#include <Jolt/Physics/Body/BodyCreationSettings.h>
#include <Jolt/Physics/Body/BodyInterface.h>
#include <Jolt/Physics/Body/BodyLock.h>
#include <Jolt/Physics/Body/MotionProperties.h>
#include <Jolt/Physics/Body/MotionQuality.h>
#include <Jolt/Physics/Body/MotionType.h>
#include <Jolt/Physics/Collision/BroadPhase/BroadPhaseLayerInterfaceMask.h>
#include <Jolt/Physics/Collision/CastResult.h>
#include <Jolt/Physics/Collision/CollisionCollectorImpl.h>
#include <Jolt/Physics/Collision/ContactListener.h>
#include <Jolt/Physics/Collision/NarrowPhaseQuery.h>
#include <Jolt/Physics/Collision/BroadPhase/ObjectVsBroadPhaseLayerFilterMask.h>
#include <Jolt/Physics/Collision/ObjectLayerPairFilterMask.h>
#include <Jolt/Physics/Collision/RayCast.h>
#include <Jolt/Physics/Collision/Shape/BoxShape.h>
#include <Jolt/Physics/Collision/Shape/CapsuleShape.h>
#include <Jolt/Physics/Collision/Shape/CylinderShape.h>
#include <Jolt/Physics/Collision/Shape/PlaneShape.h>
#include <Jolt/Physics/Collision/Shape/RotatedTranslatedShape.h>
#include <Jolt/Physics/Collision/Shape/Shape.h>
#include <Jolt/Physics/Collision/Shape/SphereShape.h>
#include <Jolt/Physics/Collision/Shape/TaperedCylinderShape.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <memory>
#include <mutex>
#include <thread>
#include <unordered_map>
#include <vector>

#if defined(_WIN32)
#define IMPULSE_JOLT_EXPORT __declspec(dllexport)
#else
#define IMPULSE_JOLT_EXPORT __attribute__((visibility("default")))
#endif

namespace {

constexpr int SHAPE_BOX = 1;
constexpr int SHAPE_SPHERE = 2;
constexpr int SHAPE_CAPSULE = 3;
constexpr int SHAPE_CYLINDER = 4;
constexpr int SHAPE_CONE = 5;
constexpr int SHAPE_PLANE = 6;

constexpr int BODY_STATIC = 1;
constexpr int BODY_DYNAMIC = 2;
constexpr int BODY_KINEMATIC = 3;

constexpr int AXIS_X = 1;
constexpr int AXIS_Y = 2;
constexpr int AXIS_Z = 3;

constexpr float MIN_SHAPE_SIZE = 0.001F;
constexpr std::uint32_t DEFAULT_COLLISION_GROUP = 1;
constexpr int RAY_HIT_FLOAT_COUNT = 8;
constexpr int CONTACT_BODY_HANDLE_COUNT = 2;
constexpr int CONTACT_FLOAT_COUNT = 11;

struct Space;

struct ContactRecord {
    JPH::SubShapeIDPair key;
    std::uint64_t body_a_handle = 0;
    std::uint64_t body_b_handle = 0;
    float point_ax = 0.0F;
    float point_ay = 0.0F;
    float point_az = 0.0F;
    float point_bx = 0.0F;
    float point_by = 0.0F;
    float point_bz = 0.0F;
    float normal_bx = 0.0F;
    float normal_by = 0.0F;
    float normal_bz = 0.0F;
    float distance = 0.0F;
    float impulse = 0.0F;
};

class ImpulseContactListener final : public JPH::ContactListener {
public:
    explicit ImpulseContactListener(Space* owner)
        : owner(owner) {
    }

    void OnContactAdded(const JPH::Body& body1,
        const JPH::Body& body2,
        const JPH::ContactManifold& manifold,
        JPH::ContactSettings& settings) override;

    void OnContactPersisted(const JPH::Body& body1,
        const JPH::Body& body2,
        const JPH::ContactManifold& manifold,
        JPH::ContactSettings& settings) override;

    void OnContactRemoved(const JPH::SubShapeIDPair& sub_shape_pair) override;

private:
    Space* owner;
};

struct BodyState {
    JPH::BodyID body_id;
    int shape_type = 0;
    int body_type = 0;
    bool sensor = false;
    float mass = 0.0F;
    float friction = 0.0F;
    float restitution = 0.0F;
    float linear_damping = 0.0F;
    float angular_damping = 0.0F;
    int collision_group = 0;
    int collision_mask = 0;
    bool continuous_collision = false;
    float center_of_mass_offset_y = 0.0F;
    bool has_box_half_extents = false;
    float half_extent_x = 0.0F;
    float half_extent_y = 0.0F;
    float half_extent_z = 0.0F;
    float radius = 0.0F;
    float half_height = 0.0F;
    int axis = AXIS_Y;
};

struct Space {
    JPH::BroadPhaseLayerInterfaceMask broad_phase_layer_interface;
    JPH::ObjectVsBroadPhaseLayerFilterMask object_vs_broadphase_layer_filter;
    JPH::ObjectLayerPairFilterMask object_layer_filter;
    JPH::PhysicsSystem physics_system;
    ImpulseContactListener contact_listener;
    JPH::TempAllocatorImpl temp_allocator;
    JPH::JobSystemThreadPool job_system;
    std::unordered_map<std::uint64_t, BodyState> bodies;
    std::unordered_map<std::uint32_t, std::uint64_t> body_handles_by_jolt_id;
    std::mutex contact_mutex;
    std::vector<ContactRecord> contacts;

    Space()
        : broad_phase_layer_interface(1),
          object_vs_broadphase_layer_filter(broad_phase_layer_interface),
          contact_listener(this),
          temp_allocator(10 * 1024 * 1024),
          job_system(JPH::cMaxPhysicsJobs,
              JPH::cMaxPhysicsBarriers,
              std::max(1U, std::thread::hardware_concurrency())) {
        broad_phase_layer_interface.ConfigureLayer(
            JPH::BroadPhaseLayer(0),
            JPH::ObjectLayerPairFilterMask::cMask,
            0);
        physics_system.Init(65536,
            0,
            65536,
            10240,
            broad_phase_layer_interface,
            object_vs_broadphase_layer_filter,
            object_layer_filter);
        physics_system.SetContactListener(&contact_listener);
        physics_system.SetGravity(JPH::Vec3(0.0F, -9.81F, 0.0F));
    }

    ~Space() {
        JPH::BodyInterface& body_interface = physics_system.GetBodyInterface();
        for (auto& [_, body] : bodies) {
            if (!body.body_id.IsInvalid()) {
                if (body_interface.IsAdded(body.body_id)) {
                    body_interface.RemoveBody(body.body_id);
                }
                body_interface.DestroyBody(body.body_id);
            }
        }
    }

    void replace_contact_records(const JPH::Body& body1,
        const JPH::Body& body2,
        const JPH::ContactManifold& manifold);

    void erase_contact_records(const JPH::SubShapeIDPair& key);

    void erase_contact_records_for_body_handle(std::uint64_t body_handle);
};

std::once_flag jolt_init_once;
std::mutex registry_mutex;
std::uint64_t next_space_handle = 1;
std::uint64_t next_body_handle = 1001;
std::unordered_map<std::uint64_t, std::unique_ptr<Space>> spaces;

void ensure_jolt_initialized() {
    std::call_once(jolt_init_once, [] {
        JPH::RegisterDefaultAllocator();
        if (JPH::Factory::sInstance == nullptr) {
            JPH::Factory::sInstance = new JPH::Factory();
        }
        JPH::RegisterTypes();
    });
}

float positive(float value) {
    return std::max(value, MIN_SHAPE_SIZE);
}

Space* find_space(std::uint64_t handle) {
    auto iterator = spaces.find(handle);
    return iterator == spaces.end() ? nullptr : iterator->second.get();
}

BodyState* find_body(Space& space, std::uint64_t handle) {
    auto iterator = space.bodies.find(handle);
    return iterator == space.bodies.end() ? nullptr : &iterator->second;
}

std::uint64_t native_handle_for_body_id(const Space& space, const JPH::BodyID& body_id) {
    auto iterator = space.body_handles_by_jolt_id.find(body_id.GetIndexAndSequenceNumber());
    return iterator == space.body_handles_by_jolt_id.end() ? 0 : iterator->second;
}

JPH::EMotionType motion_type(int body_type) {
    switch (body_type) {
        case BODY_STATIC:
            return JPH::EMotionType::Static;
        case BODY_KINEMATIC:
            return JPH::EMotionType::Kinematic;
        default:
            return JPH::EMotionType::Dynamic;
    }
}

JPH::ObjectLayer object_layer(int collision_group, int collision_mask) {
    constexpr std::uint32_t mask_bits = JPH::ObjectLayerPairFilterMask::cMask;
    std::uint32_t group = static_cast<std::uint32_t>(collision_group) & mask_bits;
    std::uint32_t mask = static_cast<std::uint32_t>(collision_mask) & mask_bits;
    if (group == 0) {
        group = DEFAULT_COLLISION_GROUP;
    }
    if (mask == 0) {
        mask = mask_bits;
    }
    return JPH::ObjectLayerPairFilterMask::sGetObjectLayer(group, mask);
}

float center_of_mass_offset_y(int shape_type,
    float half_extent_y,
    float radius,
    float half_height,
    int axis) {
    switch (shape_type) {
        case SHAPE_BOX:
            return half_extent_y;
        case SHAPE_SPHERE:
            return radius;
        case SHAPE_CAPSULE:
            return axis == AXIS_Y ? radius + half_height : radius;
        case SHAPE_CYLINDER:
            return axis == AXIS_Y ? half_height : radius;
        case SHAPE_CONE:
            return axis == AXIS_Y ? half_height : radius;
        default:
            return 0.0F;
    }
}

JPH::Quat axis_rotation(int axis) {
    switch (axis) {
        case AXIS_X:
            return JPH::Quat::sRotation(JPH::Vec3::sAxisZ(), -0.5F * JPH::JPH_PI);
        case AXIS_Z:
            return JPH::Quat::sRotation(JPH::Vec3::sAxisX(), 0.5F * JPH::JPH_PI);
        default:
            return JPH::Quat::sIdentity();
    }
}

JPH::ShapeRefC rotated_for_axis(JPH::ShapeRefC shape, int axis) {
    if (axis == AXIS_Y || shape == nullptr) {
        return shape;
    }
    return new JPH::RotatedTranslatedShape(JPH::Vec3::sZero(), axis_rotation(axis), shape);
}

JPH::ShapeRefC create_shape(int shape_type,
    float half_extent_x,
    float half_extent_y,
    float half_extent_z,
    float radius,
    float half_height,
    int axis,
    float ground_y) {
    switch (shape_type) {
        case SHAPE_BOX:
            return new JPH::BoxShape(JPH::Vec3(positive(half_extent_x),
                positive(half_extent_y),
                positive(half_extent_z)));
        case SHAPE_SPHERE:
            return new JPH::SphereShape(positive(radius));
        case SHAPE_CAPSULE:
            return rotated_for_axis(
                new JPH::CapsuleShape(positive(half_height), positive(radius)),
                axis);
        case SHAPE_CYLINDER:
            return rotated_for_axis(
                new JPH::CylinderShape(positive(half_height), positive(radius)),
                axis);
        case SHAPE_CONE: {
            JPH::TaperedCylinderShapeSettings settings(positive(half_height),
                0.0F,
                positive(radius));
            JPH::Shape::ShapeResult result = settings.Create();
            if (result.HasError()) {
                return nullptr;
            }
            return rotated_for_axis(result.Get(), axis);
        }
        case SHAPE_PLANE:
            return new JPH::PlaneShape(
                JPH::Plane::sFromPointAndNormal(JPH::Vec3(0.0F, ground_y, 0.0F),
                    JPH::Vec3::sAxisY()));
        default:
            return nullptr;
    }
}

void write_snapshot(Space& space, const BodyState& body, float* floats, int* ints) {
    JPH::BodyInterface& body_interface = space.physics_system.GetBodyInterface();
    JPH::RVec3 position = JPH::RVec3::sZero();
    JPH::Quat rotation = JPH::Quat::sIdentity();
    JPH::Vec3 linear_velocity = JPH::Vec3::sZero();
    JPH::Vec3 angular_velocity = JPH::Vec3::sZero();
    float linear_damping = body.linear_damping;
    float angular_damping = body.angular_damping;

    body_interface.GetPositionAndRotation(body.body_id, position, rotation);
    body_interface.GetLinearAndAngularVelocity(body.body_id, linear_velocity, angular_velocity);
    JPH::BodyLockRead lock(space.physics_system.GetBodyLockInterface(), body.body_id);
    if (lock.Succeeded()) {
        const JPH::MotionProperties* motion_properties =
            lock.GetBody().GetMotionPropertiesUnchecked();
        if (motion_properties != nullptr) {
            linear_damping = motion_properties->GetLinearDamping();
            angular_damping = motion_properties->GetAngularDamping();
        }
    }

    floats[0] = static_cast<float>(position.GetX());
    floats[1] = static_cast<float>(position.GetY());
    floats[2] = static_cast<float>(position.GetZ());
    floats[3] = rotation.GetX();
    floats[4] = rotation.GetY();
    floats[5] = rotation.GetZ();
    floats[6] = rotation.GetW();
    floats[7] = linear_velocity.GetX();
    floats[8] = linear_velocity.GetY();
    floats[9] = linear_velocity.GetZ();
    floats[10] = angular_velocity.GetX();
    floats[11] = angular_velocity.GetY();
    floats[12] = angular_velocity.GetZ();
    floats[13] = body.mass;
    floats[14] = body_interface.GetFriction(body.body_id);
    floats[15] = body_interface.GetRestitution(body.body_id);
    floats[16] = linear_damping;
    floats[17] = angular_damping;
    floats[18] = body.center_of_mass_offset_y;
    floats[19] = body.half_extent_x;
    floats[20] = body.half_extent_y;
    floats[21] = body.half_extent_z;
    floats[22] = body.radius;
    floats[23] = body.half_height;

    ints[0] = body.shape_type;
    ints[1] = body.body_type;
    ints[2] = body_interface.IsActive(body.body_id) ? 0 : 1;
    ints[3] = body_interface.IsSensor(body.body_id) ? 1 : 0;
    ints[4] = body.collision_group;
    ints[5] = body.collision_mask;
    ints[6] = body_interface.GetMotionQuality(body.body_id)
            == JPH::EMotionQuality::LinearCast
        ? 1
        : 0;
    ints[7] = body.has_box_half_extents ? 1 : 0;
    ints[8] = body.axis;
}

void Space::replace_contact_records(const JPH::Body& body1,
    const JPH::Body& body2,
    const JPH::ContactManifold& manifold) {
    std::uint64_t body_a_handle = native_handle_for_body_id(*this, body1.GetID());
    std::uint64_t body_b_handle = native_handle_for_body_id(*this, body2.GetID());
    if (body_a_handle == 0 || body_b_handle == 0) {
        return;
    }

    JPH::SubShapeIDPair key(body1.GetID(),
        manifold.mSubShapeID1,
        body2.GetID(),
        manifold.mSubShapeID2);
    std::lock_guard<std::mutex> lock(contact_mutex);
    contacts.erase(std::remove_if(contacts.begin(),
                       contacts.end(),
                       [&key](const ContactRecord& record) {
                           return record.key == key;
                       }),
        contacts.end());

    for (JPH::uint index = 0; index < manifold.mRelativeContactPointsOn1.size(); index++) {
        JPH::RVec3 point_a = manifold.GetWorldSpaceContactPointOn1(index);
        JPH::RVec3 point_b = manifold.GetWorldSpaceContactPointOn2(index);
        ContactRecord record;
        record.key = key;
        record.body_a_handle = body_a_handle;
        record.body_b_handle = body_b_handle;
        record.point_ax = static_cast<float>(point_a.GetX());
        record.point_ay = static_cast<float>(point_a.GetY());
        record.point_az = static_cast<float>(point_a.GetZ());
        record.point_bx = static_cast<float>(point_b.GetX());
        record.point_by = static_cast<float>(point_b.GetY());
        record.point_bz = static_cast<float>(point_b.GetZ());
        record.normal_bx = manifold.mWorldSpaceNormal.GetX();
        record.normal_by = manifold.mWorldSpaceNormal.GetY();
        record.normal_bz = manifold.mWorldSpaceNormal.GetZ();
        record.distance = -manifold.mPenetrationDepth;
        record.impulse = 0.0F;
        contacts.push_back(record);
    }
}

void Space::erase_contact_records(const JPH::SubShapeIDPair& key) {
    std::lock_guard<std::mutex> lock(contact_mutex);
    contacts.erase(std::remove_if(contacts.begin(),
                       contacts.end(),
                       [&key](const ContactRecord& record) {
                           return record.key == key;
                       }),
        contacts.end());
}

void Space::erase_contact_records_for_body_handle(std::uint64_t body_handle) {
    std::lock_guard<std::mutex> lock(contact_mutex);
    contacts.erase(std::remove_if(contacts.begin(),
                       contacts.end(),
                       [body_handle](const ContactRecord& record) {
                           return record.body_a_handle == body_handle
                               || record.body_b_handle == body_handle;
                       }),
        contacts.end());
}

void ImpulseContactListener::OnContactAdded(const JPH::Body& body1,
    const JPH::Body& body2,
    const JPH::ContactManifold& manifold,
    JPH::ContactSettings& settings) {
    (void) settings;
    if (owner != nullptr) {
        owner->replace_contact_records(body1, body2, manifold);
    }
}

void ImpulseContactListener::OnContactPersisted(const JPH::Body& body1,
    const JPH::Body& body2,
    const JPH::ContactManifold& manifold,
    JPH::ContactSettings& settings) {
    (void) settings;
    if (owner != nullptr) {
        owner->replace_contact_records(body1, body2, manifold);
    }
}

void ImpulseContactListener::OnContactRemoved(const JPH::SubShapeIDPair& sub_shape_pair) {
    if (owner != nullptr) {
        owner->erase_contact_records(sub_shape_pair);
    }
}

bool write_ray_hit(Space& space,
    const JPH::RRayCast& ray,
    const JPH::RayCastResult& hit,
    int index,
    std::int64_t* body_handles,
    float* hits) {
    std::uint64_t body_handle = native_handle_for_body_id(space, hit.mBodyID);
    if (body_handle == 0) {
        return false;
    }

    JPH::RVec3 point = ray.GetPointOnRay(hit.mFraction);
    JPH::Vec3 normal = JPH::Vec3::sZero();
    JPH::BodyLockRead lock(space.physics_system.GetBodyLockInterface(), hit.mBodyID);
    if (lock.Succeeded()) {
        normal = lock.GetBody().GetWorldSpaceSurfaceNormal(hit.mSubShapeID2, point);
    }

    body_handles[index] = static_cast<std::int64_t>(body_handle);
    int offset = index * RAY_HIT_FLOAT_COUNT;
    hits[offset] = static_cast<float>(point.GetX());
    hits[offset + 1] = static_cast<float>(point.GetY());
    hits[offset + 2] = static_cast<float>(point.GetZ());
    hits[offset + 3] = normal.GetX();
    hits[offset + 4] = normal.GetY();
    hits[offset + 5] = normal.GetZ();
    hits[offset + 6] = hit.mFraction;
    hits[offset + 7] = ray.mDirection.Length() * hit.mFraction;
    return true;
}

} // namespace

extern "C" {

IMPULSE_JOLT_EXPORT std::int64_t impulse_jolt_create_space() {
    ensure_jolt_initialized();
    std::lock_guard<std::mutex> lock(registry_mutex);
    const std::uint64_t handle = next_space_handle++;
    spaces.emplace(handle, std::make_unique<Space>());
    return static_cast<std::int64_t>(handle);
}

IMPULSE_JOLT_EXPORT int impulse_jolt_destroy_space(std::int64_t space_handle) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    return spaces.erase(static_cast<std::uint64_t>(space_handle)) > 0 ? 1 : 0;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_step(std::int64_t space_handle, float dt) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    if (space == nullptr || !std::isfinite(dt) || dt <= 0.0F) {
        return 0;
    }
    const int collision_steps = std::max(1, static_cast<int>(std::ceil(dt * 60.0F)));
    space->physics_system.Update(dt,
        collision_steps,
        &space->temp_allocator,
        &space->job_system);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_set_gravity(std::int64_t space_handle, float x, float y, float z) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    if (space == nullptr) {
        return 0;
    }
    space->physics_system.SetGravity(JPH::Vec3(x, y, z));
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_get_gravity(std::int64_t space_handle, float* out) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    if (space == nullptr || out == nullptr) {
        return 0;
    }
    JPH::Vec3 gravity = space->physics_system.GetGravity();
    out[0] = gravity.GetX();
    out[1] = gravity.GetY();
    out[2] = gravity.GetZ();
    return 1;
}

IMPULSE_JOLT_EXPORT std::int64_t impulse_jolt_create_body(std::int64_t space_handle,
    int shape_type,
    float half_extent_x,
    float half_extent_y,
    float half_extent_z,
    float radius,
    float half_height,
    int axis,
    float ground_y,
    float mass,
    int body_type,
    float position_x,
    float position_y,
    float position_z,
    float rotation_x,
    float rotation_y,
    float rotation_z,
    float rotation_w) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    if (space == nullptr) {
        return 0;
    }

    JPH::ShapeRefC shape = create_shape(shape_type,
        half_extent_x,
        half_extent_y,
        half_extent_z,
        radius,
        half_height,
        axis,
        ground_y);
    if (shape == nullptr) {
        return 0;
    }

    BodyState body;
    body.shape_type = shape_type;
    body.body_type = body_type;
    body.mass = mass;
    body.center_of_mass_offset_y = center_of_mass_offset_y(shape_type,
        half_extent_y,
        radius,
        half_height,
        axis);
    body.has_box_half_extents = shape_type == SHAPE_BOX
        && half_extent_x > 0.0F
        && half_extent_y > 0.0F
        && half_extent_z > 0.0F;
    body.half_extent_x = half_extent_x;
    body.half_extent_y = half_extent_y;
    body.half_extent_z = half_extent_z;
    body.radius = radius;
    body.half_height = half_height;
    body.axis = axis;

    JPH::BodyCreationSettings settings(shape,
        JPH::RVec3(position_x, position_y, position_z),
        JPH::Quat(rotation_x, rotation_y, rotation_z, rotation_w),
        motion_type(body_type),
        object_layer(body.collision_group, body.collision_mask));
    settings.mFriction = body.friction;
    settings.mRestitution = body.restitution;
    settings.mLinearDamping = body.linear_damping;
    settings.mAngularDamping = body.angular_damping;
    settings.mAllowDynamicOrKinematic = true;
    settings.mIsSensor = body.sensor;
    if (mass > 0.0F && body_type != BODY_STATIC) {
        settings.mOverrideMassProperties = JPH::EOverrideMassProperties::CalculateInertia;
        settings.mMassPropertiesOverride.mMass = mass;
    }

    JPH::BodyID body_id =
        space->physics_system.GetBodyInterface().CreateAndAddBody(settings,
            body_type == BODY_STATIC
                ? JPH::EActivation::DontActivate
                : JPH::EActivation::Activate);
    if (body_id.IsInvalid()) {
        return 0;
    }
    body.body_id = body_id;

    const std::uint64_t body_handle = next_body_handle++;
    space->bodies.emplace(body_handle, body);
    space->body_handles_by_jolt_id.emplace(body_id.GetIndexAndSequenceNumber(), body_handle);
    return static_cast<std::int64_t>(body_handle);
}

IMPULSE_JOLT_EXPORT int impulse_jolt_remove_body(std::int64_t space_handle, std::int64_t body_handle) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    if (space == nullptr) {
        return 0;
    }
    auto iterator = space->bodies.find(static_cast<std::uint64_t>(body_handle));
    if (iterator == space->bodies.end()) {
        return 1;
    }
    JPH::BodyInterface& body_interface = space->physics_system.GetBodyInterface();
    if (body_interface.IsAdded(iterator->second.body_id)) {
        body_interface.RemoveBody(iterator->second.body_id);
    }
    body_interface.DestroyBody(iterator->second.body_id);
    space->body_handles_by_jolt_id.erase(iterator->second.body_id.GetIndexAndSequenceNumber());
    space->erase_contact_records_for_body_handle(static_cast<std::uint64_t>(body_handle));
    space->bodies.erase(iterator);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_contains_body(std::int64_t space_handle, std::int64_t body_handle) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    return space != nullptr
        && find_body(*space, static_cast<std::uint64_t>(body_handle)) != nullptr
        ? 1
        : 0;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_body_snapshot(std::int64_t space_handle,
    std::int64_t body_handle,
    float* floats,
    int* ints) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    if (space == nullptr || floats == nullptr || ints == nullptr) {
        return 0;
    }
    BodyState* body = find_body(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    write_snapshot(*space, *body, floats, ints);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_set_body_transform(std::int64_t space_handle,
    std::int64_t body_handle,
    float position_x,
    float position_y,
    float position_z,
    float rotation_x,
    float rotation_y,
    float rotation_z,
    float rotation_w) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : find_body(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    space->physics_system.GetBodyInterface().SetPositionAndRotation(body->body_id,
        JPH::RVec3(position_x, position_y, position_z),
        JPH::Quat(rotation_x, rotation_y, rotation_z, rotation_w),
        JPH::EActivation::Activate);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_set_body_position(std::int64_t space_handle,
    std::int64_t body_handle,
    float x,
    float y,
    float z) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : find_body(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    space->physics_system.GetBodyInterface().SetPosition(body->body_id,
        JPH::RVec3(x, y, z),
        JPH::EActivation::Activate);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_set_body_velocity(std::int64_t space_handle,
    std::int64_t body_handle,
    float linear_x,
    float linear_y,
    float linear_z,
    float angular_x,
    float angular_y,
    float angular_z) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : find_body(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    space->physics_system.GetBodyInterface().SetLinearAndAngularVelocity(body->body_id,
        JPH::Vec3(linear_x, linear_y, linear_z),
        JPH::Vec3(angular_x, angular_y, angular_z));
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_set_body_type(std::int64_t space_handle, std::int64_t body_handle, int body_type) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : find_body(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    body->body_type = body_type;
    space->physics_system.GetBodyInterface().SetMotionType(body->body_id,
        motion_type(body_type),
        body_type == BODY_STATIC ? JPH::EActivation::DontActivate : JPH::EActivation::Activate);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_set_body_damping(std::int64_t space_handle,
    std::int64_t body_handle,
    float linear,
    float angular) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : find_body(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    body->linear_damping = std::max(0.0F, linear);
    body->angular_damping = std::max(0.0F, angular);
    JPH::BodyLockWrite body_lock(space->physics_system.GetBodyLockInterface(), body->body_id);
    if (body_lock.Succeeded()) {
        JPH::MotionProperties* motion_properties =
            body_lock.GetBody().GetMotionPropertiesUnchecked();
        if (motion_properties != nullptr) {
            motion_properties->SetLinearDamping(body->linear_damping);
            motion_properties->SetAngularDamping(body->angular_damping);
        }
    }
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_set_body_friction(std::int64_t space_handle,
    std::int64_t body_handle,
    float friction) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : find_body(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    body->friction = friction;
    space->physics_system.GetBodyInterface().SetFriction(body->body_id, friction);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_set_body_restitution(std::int64_t space_handle,
    std::int64_t body_handle,
    float restitution) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : find_body(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    body->restitution = restitution;
    space->physics_system.GetBodyInterface().SetRestitution(body->body_id, restitution);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_set_body_collision_filter(std::int64_t space_handle,
    std::int64_t body_handle,
    int group,
    int mask) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : find_body(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    body->collision_group = group;
    body->collision_mask = mask;
    space->physics_system.GetBodyInterface().SetObjectLayer(body->body_id,
        object_layer(group, mask));
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_set_body_sensor(std::int64_t space_handle, std::int64_t body_handle, int sensor) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : find_body(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    body->sensor = sensor != 0;
    space->physics_system.GetBodyInterface().SetIsSensor(body->body_id, body->sensor);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_set_body_continuous_collision(std::int64_t space_handle,
    std::int64_t body_handle,
    int enabled) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : find_body(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    body->continuous_collision = enabled != 0;
    space->physics_system.GetBodyInterface().SetMotionQuality(body->body_id,
        body->continuous_collision
            ? JPH::EMotionQuality::LinearCast
            : JPH::EMotionQuality::Discrete);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_is_body_continuous_collision_enabled(std::int64_t space_handle,
    std::int64_t body_handle) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : find_body(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    return space->physics_system.GetBodyInterface().GetMotionQuality(body->body_id)
            == JPH::EMotionQuality::LinearCast
        ? 1
        : 0;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_activate_body(std::int64_t space_handle, std::int64_t body_handle) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : find_body(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    space->physics_system.GetBodyInterface().ActivateBody(body->body_id);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_sleep_body(std::int64_t space_handle, std::int64_t body_handle) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : find_body(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    space->physics_system.GetBodyInterface().DeactivateBody(body->body_id);
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_apply_body_impulse(std::int64_t space_handle,
    std::int64_t body_handle,
    float x,
    float y,
    float z,
    int has_offset,
    float offset_x,
    float offset_y,
    float offset_z,
    int torque) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : find_body(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    JPH::BodyInterface& body_interface = space->physics_system.GetBodyInterface();
    JPH::Vec3 value(x, y, z);
    if (torque != 0) {
        body_interface.AddAngularImpulse(body->body_id, value);
    } else if (has_offset != 0) {
        body_interface.AddImpulse(body->body_id,
            value,
            JPH::RVec3(offset_x, offset_y, offset_z));
    } else {
        body_interface.AddImpulse(body->body_id, value);
    }
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_apply_body_force(std::int64_t space_handle,
    std::int64_t body_handle,
    float x,
    float y,
    float z,
    int has_offset,
    float offset_x,
    float offset_y,
    float offset_z,
    int torque) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    BodyState* body = space == nullptr
        ? nullptr
        : find_body(*space, static_cast<std::uint64_t>(body_handle));
    if (body == nullptr) {
        return 0;
    }
    JPH::BodyInterface& body_interface = space->physics_system.GetBodyInterface();
    JPH::Vec3 value(x, y, z);
    if (torque != 0) {
        body_interface.AddTorque(body->body_id, value, JPH::EActivation::Activate);
    } else if (has_offset != 0) {
        body_interface.AddForce(body->body_id,
            value,
            JPH::RVec3(offset_x, offset_y, offset_z),
            JPH::EActivation::Activate);
    } else {
        body_interface.AddForce(body->body_id, value, JPH::EActivation::Activate);
    }
    return 1;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_raycast_closest(std::int64_t space_handle,
    float from_x,
    float from_y,
    float from_z,
    float to_x,
    float to_y,
    float to_z,
    std::int64_t* body_handles,
    float* hits) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    if (space == nullptr || body_handles == nullptr || hits == nullptr) {
        return 0;
    }

    JPH::Vec3 direction(to_x - from_x, to_y - from_y, to_z - from_z);
    if (direction.LengthSq() <= 0.0F) {
        return 0;
    }
    JPH::RRayCast ray(JPH::RVec3(from_x, from_y, from_z), direction);
    JPH::RayCastResult hit;
    if (!space->physics_system.GetNarrowPhaseQuery().CastRay(ray, hit)) {
        return 0;
    }
    return write_ray_hit(*space, ray, hit, 0, body_handles, hits) ? 1 : 0;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_raycast_all(std::int64_t space_handle,
    float from_x,
    float from_y,
    float from_z,
    float to_x,
    float to_y,
    float to_z,
    int max_hits,
    std::int64_t* body_handles,
    float* hits) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    if (space == nullptr || max_hits <= 0 || body_handles == nullptr || hits == nullptr) {
        return 0;
    }

    JPH::Vec3 direction(to_x - from_x, to_y - from_y, to_z - from_z);
    if (direction.LengthSq() <= 0.0F) {
        return 0;
    }

    JPH::RRayCast ray(JPH::RVec3(from_x, from_y, from_z), direction);
    JPH::RayCastSettings settings;
    JPH::ClosestHitPerBodyCollisionCollector<JPH::CastRayCollector> collector;
    space->physics_system.GetNarrowPhaseQuery().CastRay(ray, settings, collector);
    collector.Sort();

    int emitted = 0;
    for (const JPH::RayCastResult& hit : collector.mHits) {
        if (emitted >= max_hits) {
            break;
        }
        if (write_ray_hit(*space, ray, hit, emitted, body_handles, hits)) {
            emitted++;
        }
    }
    return emitted;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_contacts(std::int64_t space_handle,
    int max_contacts,
    std::int64_t* body_handles,
    float* contacts) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    if (space == nullptr || max_contacts <= 0 || body_handles == nullptr || contacts == nullptr) {
        return 0;
    }

    std::lock_guard<std::mutex> contact_lock(space->contact_mutex);
    int emitted = 0;
    for (const ContactRecord& contact : space->contacts) {
        if (emitted >= max_contacts) {
            break;
        }
        int body_offset = emitted * CONTACT_BODY_HANDLE_COUNT;
        body_handles[body_offset] = static_cast<std::int64_t>(contact.body_a_handle);
        body_handles[body_offset + 1] = static_cast<std::int64_t>(contact.body_b_handle);

        int contact_offset = emitted * CONTACT_FLOAT_COUNT;
        contacts[contact_offset] = contact.point_ax;
        contacts[contact_offset + 1] = contact.point_ay;
        contacts[contact_offset + 2] = contact.point_az;
        contacts[contact_offset + 3] = contact.point_bx;
        contacts[contact_offset + 4] = contact.point_by;
        contacts[contact_offset + 5] = contact.point_bz;
        contacts[contact_offset + 6] = contact.normal_bx;
        contacts[contact_offset + 7] = contact.normal_by;
        contacts[contact_offset + 8] = contact.normal_bz;
        contacts[contact_offset + 9] = contact.distance;
        contacts[contact_offset + 10] = contact.impulse;
        emitted++;
    }
    return emitted;
}

IMPULSE_JOLT_EXPORT int impulse_jolt_contact_count(std::int64_t space_handle) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    if (space == nullptr) {
        return 0;
    }
    std::lock_guard<std::mutex> contact_lock(space->contact_mutex);
    return static_cast<int>(space->contacts.size());
}

IMPULSE_JOLT_EXPORT int impulse_jolt_body_count(std::int64_t space_handle) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    Space* space = find_space(static_cast<std::uint64_t>(space_handle));
    return space == nullptr ? 0 : static_cast<int>(space->bodies.size());
}

IMPULSE_JOLT_EXPORT int impulse_jolt_joint_count(std::int64_t space_handle) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    return find_space(static_cast<std::uint64_t>(space_handle)) == nullptr ? 0 : 0;
}

} // extern "C"
