// Rapier JNI bridge for Impulse.
// This file keeps the native exports together so the Java to Rust mapping stays easy to follow.

use jni::objects::{JClass, JFloatArray, JIntArray, JLongArray};
use jni::sys::{jboolean, jfloat, jfloatArray, jint, jlong};
use jni::JNIEnv;
use rapier3d::prelude::*;
use std::collections::HashMap;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::sync::{Arc, Mutex, OnceLock};
use std::time::{Duration, Instant};

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
    snapshot_body_ids: Vec<jlong>,
    snapshot_body_values: Vec<jfloat>,
    step_phase_stats: NativeStepPhaseStats,
}

#[derive(Clone, Copy, Default)]
struct NativeStepPhaseStats {
    step_nanos: u64,
    broad_phase_nanos: u64,
    narrow_phase_nanos: u64,
    solver_nanos: u64,
    ccd_nanos: u64,
    snapshot_nanos: u64,
}

impl NativeStepPhaseStats {
    fn reset(&mut self) {
        *self = Self::default();
    }

    fn add_snapshot_time(&mut self, elapsed: Duration) {
        self.snapshot_nanos = saturating_add_u64(self.snapshot_nanos, duration_nanos(elapsed));
    }

    fn values(&self) -> [jlong; 6] {
        [
            saturating_jlong(self.step_nanos),
            saturating_jlong(self.broad_phase_nanos),
            saturating_jlong(self.narrow_phase_nanos),
            saturating_jlong(self.solver_nanos),
            saturating_jlong(self.ccd_nanos),
            saturating_jlong(self.snapshot_nanos),
        ]
    }
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
            snapshot_body_ids: Vec::new(),
            snapshot_body_values: Vec::new(),
            step_phase_stats: NativeStepPhaseStats::default(),
        }
    }

    fn step(&mut self, dt: f32) {
        let dt = finite_or(dt, 0.0);
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
        self.record_pipeline_phase_stats();
    }

    fn reset_step_phase_stats(&mut self) {
        self.step_phase_stats.reset();
    }

    fn record_pipeline_phase_stats(&mut self) {
        let counters = &self.pipeline.counters;
        self.step_phase_stats.step_nanos = saturating_add_u64(
            self.step_phase_stats.step_nanos,
            duration_nanos(counters.step_time.time()),
        );
        self.step_phase_stats.broad_phase_nanos = saturating_add_u64(
            self.step_phase_stats.broad_phase_nanos,
            saturating_add_u64(
                duration_nanos(counters.cd.broad_phase_time.time()),
                duration_nanos(counters.cd.final_broad_phase_time.time()),
            ),
        );
        self.step_phase_stats.narrow_phase_nanos = saturating_add_u64(
            self.step_phase_stats.narrow_phase_nanos,
            duration_nanos(counters.cd.narrow_phase_time.time()),
        );
        self.step_phase_stats.solver_nanos = saturating_add_u64(
            self.step_phase_stats.solver_nanos,
            duration_nanos(counters.stages.solver_time.time()),
        );
        self.step_phase_stats.ccd_nanos = saturating_add_u64(
            self.step_phase_stats.ccd_nanos,
            duration_nanos(counters.ccd.toi_computation_time.time()),
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
        self.integration_parameters
            .num_internal_stabilization_iterations = stabilization_iterations;
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

    fn refresh_query_aabb(&mut self, collider_handle: ColliderHandle) {
        if let Some(collider) = self.colliders.get(collider_handle) {
            let aabb =
                collider.compute_broad_phase_aabb(&self.integration_parameters, &self.bodies);
            self.broad_phase
                .set_aabb(&self.integration_parameters, collider_handle, aabb);
        }
    }
}

struct RegisteredSpace {
    space: Mutex<Option<NativeSpace>>,
}

struct SpaceRegistry {
    next_slot: u32,
    next_generation: u32,
    spaces: HashMap<u64, Arc<RegisteredSpace>>,
}

impl SpaceRegistry {
    fn new() -> Self {
        Self {
            next_slot: 1,
            next_generation: 1,
            spaces: HashMap::new(),
        }
    }

    fn insert(&mut self, space: NativeSpace) -> jlong {
        let slot = self.next_slot;
        let generation = self.next_generation;
        self.next_slot = self.next_slot.wrapping_add(1).max(1);
        if self.next_slot == 1 {
            self.next_generation = self.next_generation.wrapping_add(1).max(1);
        }

        let handle = pack_space_handle(slot, generation);
        self.spaces.insert(
            handle,
            Arc::new(RegisteredSpace {
                space: Mutex::new(Some(space)),
            }),
        );
        handle as jlong
    }

    fn get(&self, handle: jlong) -> Option<Arc<RegisteredSpace>> {
        unpack_space_handle(handle).and_then(|packed| self.spaces.get(&packed).cloned())
    }

    fn remove(&mut self, handle: jlong) -> Option<Arc<RegisteredSpace>> {
        unpack_space_handle(handle).and_then(|packed| self.spaces.remove(&packed))
    }
}

static SPACE_REGISTRY: OnceLock<Mutex<SpaceRegistry>> = OnceLock::new();

fn space_registry() -> &'static Mutex<SpaceRegistry> {
    SPACE_REGISTRY.get_or_init(|| Mutex::new(SpaceRegistry::new()))
}

fn pack_space_handle(slot: u32, generation: u32) -> u64 {
    ((generation as u64) << 32) | slot as u64
}

fn unpack_space_handle(handle: jlong) -> Option<u64> {
    if handle <= 0 {
        return None;
    }
    let packed = handle as u64;
    let slot = packed as u32;
    let generation = (packed >> 32) as u32;
    if slot == 0 || generation == 0 {
        None
    } else {
        Some(packed)
    }
}

fn lock_registry() -> std::sync::MutexGuard<'static, SpaceRegistry> {
    match space_registry().lock() {
        Ok(registry) => registry,
        Err(poisoned) => poisoned.into_inner(),
    }
}

fn register_space(space: NativeSpace) -> jlong {
    lock_registry().insert(space)
}

fn destroy_registered_space(handle: jlong) -> bool {
    let Some(record) = lock_registry().remove(handle) else {
        return false;
    };
    let mut space = match record.space.lock() {
        Ok(space) => space,
        Err(poisoned) => poisoned.into_inner(),
    };
    space.take().is_some()
}

fn space_record(handle: jlong) -> Option<Arc<RegisteredSpace>> {
    lock_registry().get(handle)
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

// JNI native-space access policy:
// - Use `with_space_checked` when Java-side state must stay in sync with native state.
// - Use `with_space` only for best-effort reads, optional diagnostics, cleanup, or add paths
//   where the Java caller converts the default return value into a failure.
// - Attached-object mutations should throw instead of silently returning defaults.
fn catch_jni_default<T, F>(default: T, f: F) -> T
where
    F: FnOnce() -> T,
{
    match catch_unwind(AssertUnwindSafe(f)) {
        Ok(value) => value,
        Err(_) => default,
    }
}

#[derive(Clone, Copy)]
enum NativeSpaceFailure {
    StaleHandle,
    DestroyedSpace,
    Panic,
}

fn with_space_checked<T, F>(handle: jlong, f: F) -> Result<T, NativeSpaceFailure>
where
    F: FnOnce(&mut NativeSpace) -> T,
{
    match catch_unwind(AssertUnwindSafe(|| {
        let Some(record) = space_record(handle) else {
            return Err(NativeSpaceFailure::StaleHandle);
        };
        let mut guard = match record.space.lock() {
            Ok(space) => space,
            Err(poisoned) => poisoned.into_inner(),
        };
        let Some(space) = guard.as_mut() else {
            return Err(NativeSpaceFailure::DestroyedSpace);
        };
        Ok(f(space))
    })) {
        Ok(result) => result,
        Err(_) => Err(NativeSpaceFailure::Panic),
    }
}

fn throw_native_space_failure(env: &mut JNIEnv<'_>, operation: &str, failure: NativeSpaceFailure) {
    let reason = match failure {
        NativeSpaceFailure::StaleHandle => "stale native space handle",
        NativeSpaceFailure::DestroyedSpace => "destroyed native space",
        NativeSpaceFailure::Panic => "caught Rust panic",
    };
    let _ = env.throw_new(
        "java/lang/IllegalStateException",
        format!("Rapier native {operation} failed: {reason}"),
    );
}

fn with_space<T, F>(handle: jlong, default: T, f: F) -> T
where
    T: Clone,
    F: FnOnce(&mut NativeSpace) -> T,
{
    let default_for_panic = default.clone();
    catch_jni_default(default_for_panic, || {
        let Some(record) = space_record(handle) else {
            return default;
        };
        let mut guard = match record.space.lock() {
            Ok(space) => space,
            Err(poisoned) => poisoned.into_inner(),
        };
        let Some(space) = guard.as_mut() else {
            return default;
        };
        f(space)
    })
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
    let rotation = Rotation::from_xyzw(
        finite_or(x, 0.0),
        finite_or(y, 0.0),
        finite_or(z, 0.0),
        finite_or(w, 1.0),
    );
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

fn finite_or(value: f32, default: f32) -> f32 {
    if value.is_finite() {
        value
    } else {
        default
    }
}

fn finite_vector_or_zero(x: f32, y: f32, z: f32) -> Vector {
    Vector::new(finite_or(x, 0.0), finite_or(y, 0.0), finite_or(z, 0.0))
}

fn finite_nonnegative(value: f32) -> f32 {
    finite_at_least(value, 0.0)
}

fn duration_nanos(duration: Duration) -> u64 {
    duration.as_nanos().min(u64::MAX as u128) as u64
}

fn saturating_add_u64(left: u64, right: u64) -> u64 {
    left.saturating_add(right)
}

fn saturating_jlong(value: u64) -> jlong {
    value.min(jlong::MAX as u64) as jlong
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
        SHAPE_SPHERE => ColliderBuilder::ball(finite_at_least(radius, 0.001)),
        SHAPE_CAPSULE => match axis {
            0 => ColliderBuilder::capsule_x(
                finite_at_least(half_height, 0.001),
                finite_at_least(radius, 0.001),
            ),
            2 => ColliderBuilder::capsule_z(
                finite_at_least(half_height, 0.001),
                finite_at_least(radius, 0.001),
            ),
            _ => ColliderBuilder::capsule_y(
                finite_at_least(half_height, 0.001),
                finite_at_least(radius, 0.001),
            ),
        },
        SHAPE_CYLINDER => {
            let builder = ColliderBuilder::cylinder(
                finite_at_least(half_height, 0.001),
                finite_at_least(radius, 0.001),
            );
            if axis == 1 {
                builder
            } else {
                builder.position(Pose::from_parts(Vector::ZERO, axis_rotation(axis)))
            }
        }
        SHAPE_CONE => {
            let builder = ColliderBuilder::cone(
                finite_at_least(half_height, 0.001),
                finite_at_least(radius, 0.001),
            );
            if axis == 1 {
                builder
            } else {
                builder.position(Pose::from_parts(Vector::ZERO, axis_rotation(axis)))
            }
        }
        SHAPE_PLANE => ColliderBuilder::cuboid(10000.0, 0.05, 10000.0)
            .translation(Vector::new(0.0, -0.05, 0.0)),
        _ => ColliderBuilder::cuboid(
            finite_at_least(half_x, 0.001),
            finite_at_least(half_y, 0.001),
            finite_at_least(half_z, 0.001),
        ),
    }
}

fn normalized_or_y(x: f32, y: f32, z: f32) -> Vector {
    let mut axis = finite_vector_or_zero(x, y, z);
    if axis.length_squared() == 0.0 || !axis.is_finite() {
        axis = Vector::Y;
    }
    axis.normalize()
}

// Space lifecycle and world configuration.

mod body_exports;
mod joint_exports;
mod query_exports;
mod space_exports;
mod voxel_exports;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn registry_does_not_resolve_destroyed_handles() {
        let handle = register_space(NativeSpace::new());
        assert!(handle > 0);
        assert!(space_record(handle).is_some());

        assert!(destroy_registered_space(handle));
        assert!(space_record(handle).is_none());
        assert_eq!(with_space(handle, 7, |_| 1), 7);
        assert!(!destroy_registered_space(handle));
    }

    #[test]
    fn registry_advances_generation_when_slots_wrap() {
        let mut registry = SpaceRegistry::new();
        registry.next_slot = u32::MAX;
        registry.next_generation = 7;

        let before_wrap = registry.insert(NativeSpace::new());
        let after_wrap = registry.insert(NativeSpace::new());

        assert_eq!(
            unpack_space_handle(before_wrap),
            Some(pack_space_handle(u32::MAX, 7))
        );
        assert_eq!(
            unpack_space_handle(after_wrap),
            Some(pack_space_handle(1, 8))
        );
        assert_ne!(before_wrap, after_wrap);
        assert!(registry.remove(before_wrap).is_some());
        assert!(registry.remove(after_wrap).is_some());
    }
}
