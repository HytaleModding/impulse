// Rapier JNI bridge for Impulse.
// This file keeps the native exports together so the Java to Rust mapping stays easy to follow.

use jni::objects::{JClass, JFloatArray, JIntArray, JLongArray};
use jni::sys::{jboolean, jfloat, jfloatArray, jint, jlong};
use jni::JNIEnv;
use rapier3d::prelude::*;
use std::collections::{HashMap, HashSet};
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::sync::{Mutex, OnceLock};

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

const IMPULSE_DYNAMIC_SLEEP_LINEAR_THRESHOLD: f32 = 0.85;
const IMPULSE_DYNAMIC_SLEEP_ANGULAR_THRESHOLD: f32 = 0.9;
const IMPULSE_DYNAMIC_TIME_UNTIL_SLEEP: f32 = 0.75;

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
    dynamic_sleep_linear_threshold: f32,
    dynamic_sleep_angular_threshold: f32,
    dynamic_sleep_time_until_sleep: f32,
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
            dynamic_sleep_linear_threshold: IMPULSE_DYNAMIC_SLEEP_LINEAR_THRESHOLD,
            dynamic_sleep_angular_threshold: IMPULSE_DYNAMIC_SLEEP_ANGULAR_THRESHOLD,
            dynamic_sleep_time_until_sleep: IMPULSE_DYNAMIC_TIME_UNTIL_SLEEP,
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

    fn set_solver_tuning(
        &mut self,
        solver_iterations: usize,
        internal_pgs_iterations: usize,
        stabilization_iterations: usize,
        min_island_size: usize,
    ) {
        self.integration_parameters.num_solver_iterations = solver_iterations.max(1);
        self.integration_parameters.num_internal_pgs_iterations = internal_pgs_iterations.max(1);
        self.integration_parameters.num_internal_stabilization_iterations = stabilization_iterations;
        self.integration_parameters.min_island_size = min_island_size.max(1);
    }

    fn set_dynamic_sleep_tuning(
        &mut self,
        linear_threshold: f32,
        angular_threshold: f32,
        time_until_sleep: f32,
    ) {
        self.dynamic_sleep_linear_threshold = finite_at_least(linear_threshold, 0.0);
        self.dynamic_sleep_angular_threshold = finite_at_least(angular_threshold, 0.0);
        self.dynamic_sleep_time_until_sleep = finite_at_least(time_until_sleep, 0.0);

        for (_, entry) in self.handles.iter() {
            if let Some(body) = self.bodies.get_mut(entry.body) {
                if body.is_dynamic() {
                    apply_dynamic_sleep_tuning(
                        body,
                        self.dynamic_sleep_linear_threshold,
                        self.dynamic_sleep_angular_threshold,
                        self.dynamic_sleep_time_until_sleep,
                    );
                }
            }
        }
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

static ACTIVE_SPACES: OnceLock<Mutex<HashSet<usize>>> = OnceLock::new();

fn active_spaces() -> &'static Mutex<HashSet<usize>> {
    ACTIVE_SPACES.get_or_init(|| Mutex::new(HashSet::new()))
}

fn register_space(space: *mut NativeSpace) -> bool {
    active_spaces()
        .lock()
        .map(|mut spaces| spaces.insert(space as usize))
        .unwrap_or(false)
}

fn unregister_space(handle: jlong) -> bool {
    if handle == 0 || !is_aligned_space_handle(handle) {
        return false;
    }
    active_spaces()
        .lock()
        .map(|mut spaces| spaces.remove(&(handle as usize)))
        .unwrap_or(false)
}

fn is_active_space_handle(handle: jlong) -> bool {
    handle != 0
        && is_aligned_space_handle(handle)
        && active_spaces()
            .lock()
            .map(|spaces| spaces.contains(&(handle as usize)))
            .unwrap_or(false)
}

fn is_aligned_space_handle(handle: jlong) -> bool {
    (handle as usize) % std::mem::align_of::<NativeSpace>() == 0
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

fn catch_jni_default<T, F>(default: T, f: F) -> T
where
    F: FnOnce() -> T,
{
    match catch_unwind(AssertUnwindSafe(f)) {
        Ok(value) => value,
        Err(_) => default,
    }
}

fn float_array_or_null(env: &JNIEnv<'_>, values: &[jfloat]) -> jfloatArray {
    if values.len() > i32::MAX as usize {
        return std::ptr::null_mut();
    }

    let Ok(out) = env.new_float_array(values.len() as i32) else {
        return std::ptr::null_mut();
    };

    if env.set_float_array_region(&out, 0, values).is_err() {
        return std::ptr::null_mut();
    }

    out.into_raw()
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

fn finite_at_least(value: f32, min: f32) -> f32 {
    if !value.is_finite() || value < min {
        min
    } else {
        value
    }
}

fn apply_dynamic_sleep_tuning(
    body: &mut RigidBody,
    linear_threshold: f32,
    angular_threshold: f32,
    time_until_sleep: f32,
) {
    let activation = body.activation_mut();
    activation.normalized_linear_threshold = linear_threshold;
    activation.angular_threshold = angular_threshold;
    activation.time_until_sleep = time_until_sleep;
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
    if !is_active_space_handle(handle) {
        None
    } else {
        (handle as *mut NativeSpace).as_mut()
    }
}

// Space lifecycle and world configuration.

mod space_exports {
    use super::*;

    include!("space_exports.rs");
}

mod body_exports {
    use super::*;

    include!("body_exports.rs");
}

mod voxel_exports {
    use super::*;

    include!("voxel_exports.rs");
}

mod query_exports {
    use super::*;

    include!("query_exports.rs");
}

mod joint_exports {
    use super::*;

    include!("joint_exports.rs");
}
