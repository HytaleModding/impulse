// Rapier JNI bridge for Impulse.
// This file keeps the native exports together so the Java to Rust mapping stays easy to follow.

use jni::objects::{JClass, JFloatArray, JIntArray};
use jni::sys::{jboolean, jfloat, jfloatArray, jint, jlong};
use jni::JNIEnv;
use rapier3d::prelude::*;
use std::collections::HashMap;

// Integer ids shared with the Java side enums.
const BODY_TYPE_STATIC: i32 = 0;
const BODY_TYPE_DYNAMIC: i32 = 1;
const BODY_TYPE_KINEMATIC: i32 = 2;

const SHAPE_SPHERE: i32 = 1;
const SHAPE_CAPSULE: i32 = 2;
const SHAPE_CYLINDER: i32 = 3;
const SHAPE_CONE: i32 = 4;
const SHAPE_PLANE: i32 = 5;

const JOINT_FIXED: i32 = 0;
const JOINT_POINT: i32 = 1;
const JOINT_HINGE: i32 = 2;
const JOINT_SLIDER: i32 = 3;
const JOINT_SPRING: i32 = 4;

// One native simulation space plus the opaque ids used by JNI.
#[derive(Clone, Copy)]
struct BodyEntry {
    body: RigidBodyHandle,
    collider: ColliderHandle,
}

#[derive(Clone, Copy)]
struct JointEntry {
    joint: ImpulseJointHandle,
    joint_type: i32,
    enabled: bool,
}

struct NativeSpace {
    pipeline: PhysicsPipeline,
    gravity: Vector,
    integration_parameters: IntegrationParameters,
    islands: IslandManager,
    broad_phase: BroadPhaseBvh,
    narrow_phase: NarrowPhase,
    bodies: RigidBodySet,
    colliders: ColliderSet,
    impulse_joints: ImpulseJointSet,
    multibody_joints: MultibodyJointSet,
    ccd_solver: CCDSolver,
    handles: HashMap<i64, BodyEntry>,
    collider_to_body_id: HashMap<ColliderHandle, i64>,
    joints: HashMap<i64, JointEntry>,
    next_body_id: i64,
    next_joint_id: i64,
}

impl NativeSpace {
    fn new() -> Self {
        Self {
            pipeline: PhysicsPipeline::new(),
            gravity: Vector::new(0.0, -9.81, 0.0),
            integration_parameters: IntegrationParameters::default(),
            islands: IslandManager::new(),
            broad_phase: BroadPhaseBvh::new(),
            narrow_phase: NarrowPhase::new(),
            bodies: RigidBodySet::new(),
            colliders: ColliderSet::new(),
            impulse_joints: ImpulseJointSet::new(),
            multibody_joints: MultibodyJointSet::new(),
            ccd_solver: CCDSolver::new(),
            handles: HashMap::new(),
            collider_to_body_id: HashMap::new(),
            joints: HashMap::new(),
            next_body_id: 1,
            next_joint_id: 1,
        }
    }

    fn step(&mut self, dt: f32) {
        if dt <= 0.0 {
            return;
        }
        self.integration_parameters.dt = dt;
        self.pipeline.step(
            self.gravity,
            &self.integration_parameters,
            &mut self.islands,
            &mut self.broad_phase,
            &mut self.narrow_phase,
            &mut self.bodies,
            &mut self.colliders,
            &mut self.impulse_joints,
            &mut self.multibody_joints,
            &mut self.ccd_solver,
            &(),
            &(),
        );
    }

    fn insert_entry(&mut self, body: RigidBodyHandle, collider: ColliderHandle) -> i64 {
        let id = self.next_body_id;
        self.next_body_id += 1;
        self.handles.insert(id, BodyEntry { body, collider });
        self.collider_to_body_id.insert(collider, id);
        id
    }

    fn entry(&self, id: i64) -> Option<BodyEntry> {
        self.handles.get(&id).copied()
    }

    fn insert_joint(&mut self, joint: ImpulseJointHandle, joint_type: i32) -> i64 {
        let id = self.next_joint_id;
        self.next_joint_id += 1;
        self.joints.insert(
            id,
            JointEntry {
                joint,
                joint_type,
                enabled: true,
            },
        );
        id
    }

    fn joint(&self, id: i64) -> Option<JointEntry> {
        self.joints.get(&id).copied()
    }
}

fn bool_from_jboolean(value: jboolean) -> bool {
    value != 0
}

fn jboolean_from_bool(value: bool) -> jboolean {
    if value {
        1
    } else {
        0
    }
}

fn rotation_from_xyzw(x: f32, y: f32, z: f32, w: f32) -> Rotation {
    let rotation = Rotation::from_xyzw(x, y, z, w);
    if rotation.length_squared() == 0.0 || !rotation.is_finite() {
        Rotation::IDENTITY
    } else {
        rotation.normalize()
    }
}

fn axis_rotation(axis: i32) -> Rotation {
    match axis {
        0 => Rotation::from_rotation_z(std::f32::consts::FRAC_PI_2),
        2 => Rotation::from_rotation_x(std::f32::consts::FRAC_PI_2),
        _ => Rotation::IDENTITY,
    }
}

fn body_type_from_int(body_type: i32) -> RigidBodyType {
    match body_type {
        BODY_TYPE_STATIC => RigidBodyType::Fixed,
        BODY_TYPE_KINEMATIC => RigidBodyType::KinematicPositionBased,
        _ => RigidBodyType::Dynamic,
    }
}

fn body_type_to_int(body: &RigidBody) -> i32 {
    if body.is_fixed() {
        BODY_TYPE_STATIC
    } else if body.is_kinematic() {
        BODY_TYPE_KINEMATIC
    } else {
        BODY_TYPE_DYNAMIC
    }
}

fn interaction_groups(group: i32, mask: i32) -> InteractionGroups {
    InteractionGroups::new(
        Group::from_bits_truncate(group as u32),
        Group::from_bits_truncate(mask as u32),
        InteractionTestMode::And,
    )
}

fn build_collider(
    shape_type: i32,
    half_x: f32,
    half_y: f32,
    half_z: f32,
    radius: f32,
    half_height: f32,
    axis: i32,
    _ground_y: f32,
) -> ColliderBuilder {
    // Shapes are built in the backend local orientation.
    match shape_type {
        SHAPE_SPHERE => ColliderBuilder::ball(radius.max(0.001)),
        SHAPE_CAPSULE => match axis {
            0 => ColliderBuilder::capsule_x(half_height.max(0.001), radius.max(0.001)),
            2 => ColliderBuilder::capsule_z(half_height.max(0.001), radius.max(0.001)),
            _ => ColliderBuilder::capsule_y(half_height.max(0.001), radius.max(0.001)),
        },
        SHAPE_CYLINDER => {
            let builder = ColliderBuilder::cylinder(half_height.max(0.001), radius.max(0.001));
            if axis == 1 {
                builder
            } else {
                builder.position(Pose::from_parts(Vector::ZERO, axis_rotation(axis)))
            }
        }
        SHAPE_CONE => {
            let builder = ColliderBuilder::cone(half_height.max(0.001), radius.max(0.001));
            if axis == 1 {
                builder
            } else {
                builder.position(Pose::from_parts(Vector::ZERO, axis_rotation(axis)))
            }
        }
        SHAPE_PLANE => ColliderBuilder::cuboid(10000.0, 0.05, 10000.0)
            .translation(Vector::new(0.0, -0.05, 0.0)),
        _ => ColliderBuilder::cuboid(half_x.max(0.001), half_y.max(0.001), half_z.max(0.001)),
    }
}

fn normalized_or_y(x: f32, y: f32, z: f32) -> Vector {
    let mut axis = Vector::new(x, y, z);
    if axis.length_squared() == 0.0 {
        axis = Vector::Y;
    }
    axis.normalize()
}

// Look up a native space from the opaque handle passed through JNI.
unsafe fn space_mut(handle: jlong) -> Option<&'static mut NativeSpace> {
    if handle == 0 {
        None
    } else {
        (handle as *mut NativeSpace).as_mut()
    }
}

// Space lifecycle and world configuration.
#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_createSpaceNative(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    Box::into_raw(Box::new(NativeSpace::new())) as jlong
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_destroySpaceNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
) {
    if space_handle == 0 {
        return;
    }
    unsafe {
        drop(Box::from_raw(space_handle as *mut NativeSpace));
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setGravityNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    x: jfloat,
    y: jfloat,
    z: jfloat,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            space.gravity = Vector::new(x, y, z);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_getGravityNative(
    env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    out: JFloatArray,
) {
    let mut values = [0.0, -9.81, 0.0];
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            values = [space.gravity.x, space.gravity.y, space.gravity.z];
        }
    }
    let _ = env.set_float_array_region(&out, 0, &values);
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_stepNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    dt: jfloat,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            space.step(dt);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_addBodyNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    shape_type: jint,
    half_x: jfloat,
    half_y: jfloat,
    half_z: jfloat,
    radius: jfloat,
    half_height: jfloat,
    axis: jint,
    body_type: jint,
    mass: jfloat,
    pos_x: jfloat,
    pos_y: jfloat,
    pos_z: jfloat,
    rot_x: jfloat,
    rot_y: jfloat,
    rot_z: jfloat,
    rot_w: jfloat,
    lin_vel_x: jfloat,
    lin_vel_y: jfloat,
    lin_vel_z: jfloat,
    ang_vel_x: jfloat,
    ang_vel_y: jfloat,
    ang_vel_z: jfloat,
    friction: jfloat,
    restitution: jfloat,
    linear_damping: jfloat,
    angular_damping: jfloat,
    sensor: jboolean,
    collision_group: jint,
    collision_mask: jint,
    ccd_enabled: jboolean,
) -> jlong {
    unsafe {
        let Some(space) = space_mut(space_handle) else {
            return 0;
        };

        let body_builder = RigidBodyBuilder::new(body_type_from_int(body_type))
            .pose(Pose::from_parts(
                Vector::new(pos_x, pos_y, pos_z),
                rotation_from_xyzw(rot_x, rot_y, rot_z, rot_w),
            ))
            .linvel(Vector::new(lin_vel_x, lin_vel_y, lin_vel_z))
            .angvel(Vector::new(ang_vel_x, ang_vel_y, ang_vel_z))
            .linear_damping(linear_damping)
            .angular_damping(angular_damping)
            .ccd_enabled(bool_from_jboolean(ccd_enabled));
        let body_handle = space.bodies.insert(body_builder.build());

        let mut collider = build_collider(
            shape_type,
            half_x,
            half_y,
            half_z,
            radius,
            half_height,
            axis,
            pos_y,
        )
        .friction(friction)
        .friction_combine_rule(CoefficientCombineRule::Multiply)
        .restitution_combine_rule(CoefficientCombineRule::Multiply)
        .restitution(restitution)
        .sensor(bool_from_jboolean(sensor))
        .collision_groups(interaction_groups(collision_group, collision_mask));

        if body_type == BODY_TYPE_DYNAMIC && mass > 0.0 {
            collider = collider.mass(mass);
        } else {
            collider = collider.density(0.0);
        }

        let collider_handle =
            space
                .colliders
                .insert_with_parent(collider, body_handle, &mut space.bodies);

        space.insert_entry(body_handle, collider_handle) as jlong
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_removeBodyNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
) {
    unsafe {
        let Some(space) = space_mut(space_handle) else {
            return;
        };
        let Some(entry) = space.handles.remove(&body_id) else {
            return;
        };
        space.collider_to_body_id.remove(&entry.collider);
        space.bodies.remove(
            entry.body,
            &mut space.islands,
            &mut space.colliders,
            &mut space.impulse_joints,
            &mut space.multibody_joints,
            true,
        );
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_getBodyPositionNative(
    env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    out: JFloatArray,
) {
    let mut values = [0.0, 0.0, 0.0];
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get(entry.body) {
                    let pos = body.translation();
                    values = [pos.x, pos.y, pos.z];
                }
            }
        }
    }
    let _ = env.set_float_array_region(&out, 0, &values);
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodyPositionNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    x: jfloat,
    y: jfloat,
    z: jfloat,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get_mut(entry.body) {
                    body.set_translation(Vector::new(x, y, z), true);
                }
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_getBodyRotationNative(
    env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    out: JFloatArray,
) {
    let mut values = [0.0, 0.0, 0.0, 1.0];
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get(entry.body) {
                    let rot = body.rotation().to_array();
                    values = [rot[0], rot[1], rot[2], rot[3]];
                }
            }
        }
    }
    let _ = env.set_float_array_region(&out, 0, &values);
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodyRotationNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    x: jfloat,
    y: jfloat,
    z: jfloat,
    w: jfloat,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get_mut(entry.body) {
                    body.set_rotation(rotation_from_xyzw(x, y, z, w), true);
                }
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_getBodyLinearVelocityNative(
    env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    out: JFloatArray,
) {
    let mut values = [0.0, 0.0, 0.0];
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get(entry.body) {
                    let vel = body.linvel();
                    values = [vel.x, vel.y, vel.z];
                }
            }
        }
    }
    let _ = env.set_float_array_region(&out, 0, &values);
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodyLinearVelocityNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    x: jfloat,
    y: jfloat,
    z: jfloat,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get_mut(entry.body) {
                    body.set_linvel(Vector::new(x, y, z), true);
                }
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_getBodyAngularVelocityNative(
    env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    out: JFloatArray,
) {
    let mut values = [0.0, 0.0, 0.0];
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get(entry.body) {
                    let vel = body.angvel();
                    values = [vel.x, vel.y, vel.z];
                }
            }
        }
    }
    let _ = env.set_float_array_region(&out, 0, &values);
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodyAngularVelocityNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    x: jfloat,
    y: jfloat,
    z: jfloat,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get_mut(entry.body) {
                    body.set_angvel(Vector::new(x, y, z), true);
                }
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodyTypeNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    body_type: jint,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get_mut(entry.body) {
                    body.set_body_type(body_type_from_int(body_type), true);
                }
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_getBodyTypeNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
) -> jint {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get(entry.body) {
                    return body_type_to_int(body);
                }
            }
        }
    }
    BODY_TYPE_STATIC
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodyMassNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    mass: jfloat,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(collider) = space.colliders.get_mut(entry.collider) {
                    if mass > 0.0 {
                        collider.set_mass(mass);
                    } else {
                        collider.set_density(0.0);
                    }
                }
                if let Some(body) = space.bodies.get_mut(entry.body) {
                    if mass <= 0.0 {
                        body.set_body_type(RigidBodyType::Fixed, true);
                    }
                }
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_getBodyMassNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
) -> jfloat {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get(entry.body) {
                    return body.mass();
                }
            }
        }
    }
    0.0
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodyDampingNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    linear_damping: jfloat,
    angular_damping: jfloat,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get_mut(entry.body) {
                    body.set_linear_damping(linear_damping);
                    body.set_angular_damping(angular_damping);
                }
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_getBodyDampingNative(
    env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    out: JFloatArray,
) {
    let mut values = [0.0, 0.0];
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get(entry.body) {
                    values = [body.linear_damping(), body.angular_damping()];
                }
            }
        }
    }
    let _ = env.set_float_array_region(&out, 0, &values);
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_isBodySleepingNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
) -> jboolean {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get(entry.body) {
                    return jboolean_from_bool(body.is_sleeping());
                }
            }
        }
    }
    0
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_sleepBodyNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get_mut(entry.body) {
                    body.sleep();
                }
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_activateBodyNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get_mut(entry.body) {
                    body.wake_up(true);
                }
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_applyBodyCentralForceNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    x: jfloat,
    y: jfloat,
    z: jfloat,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get_mut(entry.body) {
                    body.add_force(Vector::new(x, y, z), true);
                }
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_applyBodyForceNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    force_x: jfloat,
    force_y: jfloat,
    force_z: jfloat,
    offset_x: jfloat,
    offset_y: jfloat,
    offset_z: jfloat,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get_mut(entry.body) {
                    let point = body.translation() + Vector::new(offset_x, offset_y, offset_z);
                    body.add_force_at_point(Vector::new(force_x, force_y, force_z), point, true);
                }
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_applyBodyCentralImpulseNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    x: jfloat,
    y: jfloat,
    z: jfloat,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get_mut(entry.body) {
                    body.apply_impulse(Vector::new(x, y, z), true);
                }
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_applyBodyImpulseNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    impulse_x: jfloat,
    impulse_y: jfloat,
    impulse_z: jfloat,
    offset_x: jfloat,
    offset_y: jfloat,
    offset_z: jfloat,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get_mut(entry.body) {
                    let point = body.translation() + Vector::new(offset_x, offset_y, offset_z);
                    body.apply_impulse_at_point(
                        Vector::new(impulse_x, impulse_y, impulse_z),
                        point,
                        true,
                    );
                }
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_applyBodyTorqueNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    x: jfloat,
    y: jfloat,
    z: jfloat,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get_mut(entry.body) {
                    body.add_torque(Vector::new(x, y, z), true);
                }
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_applyBodyTorqueImpulseNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    x: jfloat,
    y: jfloat,
    z: jfloat,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get_mut(entry.body) {
                    body.apply_torque_impulse(Vector::new(x, y, z), true);
                }
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_clearBodyForcesNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get_mut(entry.body) {
                    body.reset_forces(true);
                    body.reset_torques(true);
                }
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodyFrictionNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    friction: jfloat,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(collider) = space.colliders.get_mut(entry.collider) {
                    collider.set_friction(friction);
                }
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodyRestitutionNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    restitution: jfloat,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(collider) = space.colliders.get_mut(entry.collider) {
                    collider.set_restitution(restitution);
                }
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodySensorNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    sensor: jboolean,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(collider) = space.colliders.get_mut(entry.collider) {
                    collider.set_sensor(bool_from_jboolean(sensor));
                }
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_isBodySensorNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
) -> jboolean {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(collider) = space.colliders.get(entry.collider) {
                    return jboolean_from_bool(collider.is_sensor());
                }
            }
        }
    }
    0
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodyCollisionFilterNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    group: jint,
    mask: jint,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(collider) = space.colliders.get_mut(entry.collider) {
                    collider.set_collision_groups(interaction_groups(group, mask));
                }
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_getBodyCollisionFilterNative(
    env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    out: JIntArray,
) {
    let mut values = [1, 1];
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(collider) = space.colliders.get(entry.collider) {
                    let groups = collider.collision_groups();
                    values = [
                        groups.memberships.bits() as i32,
                        groups.filter.bits() as i32,
                    ];
                }
            }
        }
    }
    let _ = env.set_int_array_region(&out, 0, &values);
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodyCcdNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    enabled: jboolean,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get_mut(entry.body) {
                    body.enable_ccd(bool_from_jboolean(enabled));
                }
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_isBodyCcdNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
) -> jboolean {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.entry(body_id) {
                if let Some(body) = space.bodies.get(entry.body) {
                    return jboolean_from_bool(body.is_ccd_enabled());
                }
            }
        }
    }
    0
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_raycastAllNative(
    env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    from_x: jfloat,
    from_y: jfloat,
    from_z: jfloat,
    to_x: jfloat,
    to_y: jfloat,
    to_z: jfloat,
) -> jfloatArray {
    let mut values: Vec<jfloat> = Vec::new();
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            let from = Vector::new(from_x, from_y, from_z);
            let to = Vector::new(to_x, to_y, to_z);
            let delta = to - from;
            let distance = delta.length();
            if distance > 0.0 {
                let ray = Ray::new(from, delta / distance);
                let query = space.broad_phase.as_query_pipeline(
                    space.narrow_phase.query_dispatcher(),
                    &space.bodies,
                    &space.colliders,
                    QueryFilter::default(),
                );
                for (collider_handle, _collider, hit) in query.intersect_ray(ray, distance, true) {
                    if let Some(body_id) = space.collider_to_body_id.get(&collider_handle) {
                        let point = from + ray.dir * hit.time_of_impact;
                        values.extend_from_slice(&[
                            *body_id as jfloat,
                            point.x,
                            point.y,
                            point.z,
                            hit.normal.x,
                            hit.normal.y,
                            hit.normal.z,
                            hit.time_of_impact / distance,
                            hit.time_of_impact,
                        ]);
                    }
                }
            }
        }
    }
    let out = env.new_float_array(values.len() as i32).unwrap();
    let _ = env.set_float_array_region(&out, 0, &values);
    out.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_getContactsNative(
    env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
) -> jfloatArray {
    let mut values: Vec<jfloat> = Vec::new();
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            for pair in space.narrow_phase.contact_pairs() {
                let Some(body_a_id) = space.collider_to_body_id.get(&pair.collider1) else {
                    continue;
                };
                let Some(body_b_id) = space.collider_to_body_id.get(&pair.collider2) else {
                    continue;
                };
                for manifold in &pair.manifolds {
                    let normal = manifold.data.normal;
                    for contact in &manifold.data.solver_contacts {
                        let point = contact.point;
                        values.extend_from_slice(&[
                            *body_a_id as jfloat,
                            *body_b_id as jfloat,
                            point.x,
                            point.y,
                            point.z,
                            point.x,
                            point.y,
                            point.z,
                            normal.x,
                            normal.y,
                            normal.z,
                            contact.dist,
                            contact.warmstart_impulse,
                        ]);
                    }
                }
            }
        }
    }
    let out = env.new_float_array(values.len() as i32).unwrap();
    let _ = env.set_float_array_region(&out, 0, &values);
    out.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_addJointNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    joint_type: jint,
    body_a_id: jlong,
    body_b_id: jlong,
    anchor_a_x: jfloat,
    anchor_a_y: jfloat,
    anchor_a_z: jfloat,
    anchor_b_x: jfloat,
    anchor_b_y: jfloat,
    anchor_b_z: jfloat,
    axis_x: jfloat,
    axis_y: jfloat,
    axis_z: jfloat,
    rest_length: jfloat,
    stiffness: jfloat,
    damping: jfloat,
) -> jlong {
    unsafe {
        let Some(space) = space_mut(space_handle) else {
            return 0;
        };
        let Some(body_a) = space.entry(body_a_id) else {
            return 0;
        };
        let Some(body_b) = space.entry(body_b_id) else {
            return 0;
        };

        let anchor_a = Vector::new(anchor_a_x, anchor_a_y, anchor_a_z);
        let anchor_b = Vector::new(anchor_b_x, anchor_b_y, anchor_b_z);
        let axis = normalized_or_y(axis_x, axis_y, axis_z);
        let handle = match joint_type {
            JOINT_FIXED => {
                let joint = FixedJointBuilder::new()
                    .local_anchor1(anchor_a)
                    .local_anchor2(anchor_b)
                    .build();
                space
                    .impulse_joints
                    .insert(body_a.body, body_b.body, joint, true)
            }
            JOINT_POINT => {
                let joint = SphericalJointBuilder::new()
                    .local_anchor1(anchor_a)
                    .local_anchor2(anchor_b)
                    .build();
                space
                    .impulse_joints
                    .insert(body_a.body, body_b.body, joint, true)
            }
            JOINT_HINGE => {
                let joint = RevoluteJointBuilder::new(axis)
                    .local_anchor1(anchor_a)
                    .local_anchor2(anchor_b)
                    .build();
                space
                    .impulse_joints
                    .insert(body_a.body, body_b.body, joint, true)
            }
            JOINT_SLIDER => {
                let joint = PrismaticJointBuilder::new(axis)
                    .local_anchor1(anchor_a)
                    .local_anchor2(anchor_b)
                    .build();
                space
                    .impulse_joints
                    .insert(body_a.body, body_b.body, joint, true)
            }
            JOINT_SPRING => {
                let joint = SpringJointBuilder::new(rest_length, stiffness, damping)
                    .local_anchor1(anchor_a)
                    .local_anchor2(anchor_b)
                    .build();
                space
                    .impulse_joints
                    .insert(body_a.body, body_b.body, joint, true)
            }
            _ => return 0,
        };
        space.insert_joint(handle, joint_type) as jlong
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_removeJointNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    joint_id: jlong,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.joints.remove(&joint_id) {
                space.impulse_joints.remove(entry.joint, true);
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setJointEnabledNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    joint_id: jlong,
    enabled: jboolean,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(mut entry) = space.joint(joint_id) {
                entry.enabled = bool_from_jboolean(enabled);
                if let Some(joint) = space.impulse_joints.get_mut(entry.joint, true) {
                    joint.data.set_enabled(entry.enabled);
                }
                space.joints.insert(joint_id, entry);
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_isJointEnabledNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    joint_id: jlong,
) -> jboolean {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.joint(joint_id) {
                return jboolean_from_bool(entry.enabled);
            }
        }
    }
    0
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setJointLimitsNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    joint_id: jlong,
    lower_limit: jfloat,
    upper_limit: jfloat,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.joint(joint_id) {
                if let Some(joint) = space.impulse_joints.get_mut(entry.joint, true) {
                    match entry.joint_type {
                        JOINT_HINGE => {
                            joint
                                .data
                                .set_limits(JointAxis::AngX, [lower_limit, upper_limit]);
                        }
                        JOINT_SLIDER => {
                            joint
                                .data
                                .set_limits(JointAxis::LinX, [lower_limit, upper_limit]);
                        }
                        _ => {}
                    }
                }
            }
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setJointMotorNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    joint_id: jlong,
    enabled: jboolean,
    target_velocity: jfloat,
    max_force: jfloat,
) {
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            if let Some(entry) = space.joint(joint_id) {
                if let Some(joint) = space.impulse_joints.get_mut(entry.joint, true) {
                    let target_velocity = if bool_from_jboolean(enabled) {
                        target_velocity
                    } else {
                        0.0
                    };
                    let max_force = if bool_from_jboolean(enabled) {
                        max_force
                    } else {
                        0.0
                    };
                    match entry.joint_type {
                        JOINT_HINGE => {
                            joint
                                .data
                                .set_motor_velocity(JointAxis::AngX, target_velocity, 1.0);
                            joint.data.set_motor_max_force(JointAxis::AngX, max_force);
                        }
                        JOINT_SLIDER => {
                            joint
                                .data
                                .set_motor_velocity(JointAxis::LinX, target_velocity, 1.0);
                            joint.data.set_motor_max_force(JointAxis::LinX, max_force);
                        }
                        _ => {}
                    }
                }
            }
        }
    }
}
