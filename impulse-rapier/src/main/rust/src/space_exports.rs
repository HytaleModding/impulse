use super::*;

const RUNTIME_STATS_INTS: usize = 10;
const STEP_PHASE_STATS_LONGS: usize = 6;
const CONTACT_EVENT_FLOATS: usize = 16;
const MAX_CONTACT_EVENTS: usize = 16_384;
const MAX_CONTACT_EVENT_FLOATS: usize = CONTACT_EVENT_FLOATS * MAX_CONTACT_EVENTS;
const CONTACT_PHASE_STARTED: jfloat = 0.0;
const CONTACT_PHASE_ENDED: jfloat = 2.0;
const CONTACT_PHASE_FORCE: jfloat = 4.0;
const TERRAIN_COLLISION_GROUP: u32 = 0b0000_0001;
const DYNAMIC_BODY_COLLISION_GROUP: u32 = 0b0000_0010;

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_createSpaceNative(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    catch_jni_default(0, || register_space(NativeSpace::new()))
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_destroySpaceNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
) {
    catch_jni_default((), || {
        let _ = destroy_registered_space(space_handle);
    });
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
    with_space(space_handle, (), |space| {
        space.gravity = finite_vector_or_zero(x, y, z);
    });
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_getGravityNative(
    env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    out: JFloatArray,
) {
    let mut values = [0.0, -9.81, 0.0];
    values = with_space(space_handle, values, |space| {
        [space.gravity.x, space.gravity.y, space.gravity.z]
    });
    let _ = env.set_float_array_region(&out, 0, &values);
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_stepNative(
    mut env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    dt: jfloat,
) -> jboolean {
    match with_space_checked(space_handle, |space| space.step(dt)) {
        Ok(()) => 1,
        Err(failure) => {
            throw_native_space_failure(&mut env, "step", failure);
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_stepContactEventsNative(
    mut env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    dt: jfloat,
) -> jfloatArray {
    match with_space_checked(space_handle, |space| space.step_contact_events(dt)) {
        Ok(values) => float_array_or_null(&env, &values),
        Err(failure) => {
            throw_native_space_failure(&mut env, "step contact events", failure);
            std::ptr::null_mut()
        }
    }
}

impl NativeSpace {
    fn step_contact_events(&mut self, dt: f32) -> Vec<jfloat> {
        let dt = finite_or(dt, 0.0);
        if dt <= 0.0 {
            return Vec::new();
        }

        let values = {
            let NativeSpace {
                pipeline,
                gravity,
                integration_parameters,
                islands,
                broad_phase,
                narrow_phase,
                bodies,
                colliders,
                impulse_joints,
                multibody_joints,
                ccd_solver,
                collider_to_body_id,
                ..
            } = self;
            integration_parameters.dt = dt;
            let event_collector = NativeContactEventCollector::new(collider_to_body_id);
            pipeline.step(
                *gravity,
                integration_parameters,
                islands,
                broad_phase,
                narrow_phase,
                bodies,
                colliders,
                impulse_joints,
                multibody_joints,
                ccd_solver,
                &(),
                &event_collector,
            );
            event_collector.into_values()
        };

        self.record_pipeline_phase_stats();
        values
    }
}

struct NativeContactEventCollector<'a> {
    collider_to_body_id: &'a HashMap<ColliderHandle, i64>,
    values: Mutex<Vec<jfloat>>,
}

impl<'a> NativeContactEventCollector<'a> {
    fn new(collider_to_body_id: &'a HashMap<ColliderHandle, i64>) -> Self {
        Self {
            collider_to_body_id,
            values: Mutex::new(Vec::new()),
        }
    }

    fn into_values(self) -> Vec<jfloat> {
        self.values
            .into_inner()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
    }

    fn push_contact_event(
        &self,
        phase: jfloat,
        collider_a: ColliderHandle,
        collider_b: ColliderHandle,
        contact_pair: Option<&ContactPair>,
        impulse: jfloat,
    ) {
        let Some(body_a_id) = self.collider_to_body_id.get(&collider_a) else {
            return;
        };
        let Some(body_b_id) = self.collider_to_body_id.get(&collider_b) else {
            return;
        };

        let (upper_a_bits, lower_a_bits) =
            crate::query_exports::i32_to_raw_bit_f32_pair(*body_a_id);
        let (upper_b_bits, lower_b_bits) =
            crate::query_exports::i32_to_raw_bit_f32_pair(*body_b_id);
        let contact = contact_pair
            .and_then(first_solver_contact)
            .unwrap_or_default();
        let event_impulse = if phase == CONTACT_PHASE_FORCE {
            impulse
        } else {
            contact.impulse
        };
        let record = [
            phase,
            upper_a_bits,
            lower_a_bits,
            upper_b_bits,
            lower_b_bits,
            contact.point.x,
            contact.point.y,
            contact.point.z,
            contact.point.x,
            contact.point.y,
            contact.point.z,
            contact.normal.x,
            contact.normal.y,
            contact.normal.z,
            contact.distance,
            event_impulse,
        ];

        let mut values = self
            .values
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        if record.len() > MAX_CONTACT_EVENT_FLOATS.saturating_sub(values.len())
            || values.try_reserve(record.len()).is_err()
        {
            return;
        }
        values.extend_from_slice(&record);
    }
}

impl EventHandler for NativeContactEventCollector<'_> {
    fn handle_collision_event(
        &self,
        _bodies: &RigidBodySet,
        _colliders: &ColliderSet,
        event: CollisionEvent,
        contact_pair: Option<&ContactPair>,
    ) {
        match event {
            CollisionEvent::Started(collider_a, collider_b, _) => {
                self.push_contact_event(
                    CONTACT_PHASE_STARTED,
                    collider_a,
                    collider_b,
                    contact_pair,
                    0.0,
                );
            }
            CollisionEvent::Stopped(collider_a, collider_b, _) => {
                self.push_contact_event(
                    CONTACT_PHASE_ENDED,
                    collider_a,
                    collider_b,
                    contact_pair,
                    0.0,
                );
            }
        }
    }

    fn handle_contact_force_event(
        &self,
        _dt: Real,
        _bodies: &RigidBodySet,
        _colliders: &ColliderSet,
        contact_pair: &ContactPair,
        total_force_magnitude: Real,
    ) {
        self.push_contact_event(
            CONTACT_PHASE_FORCE,
            contact_pair.collider1,
            contact_pair.collider2,
            Some(contact_pair),
            total_force_magnitude,
        );
    }
}

#[derive(Clone, Copy, Default)]
struct NativeContactRecord {
    point: Vector,
    normal: Vector,
    distance: jfloat,
    impulse: jfloat,
}

fn first_solver_contact(contact_pair: &ContactPair) -> Option<NativeContactRecord> {
    for manifold in &contact_pair.manifolds {
        let normal = manifold.data.normal;
        if let Some(contact) = manifold.data.solver_contacts.first() {
            return Some(NativeContactRecord {
                point: contact.point,
                normal,
                distance: contact.dist,
                impulse: contact.warmstart_impulse,
            });
        }
    }
    None
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_getRuntimeStatsNative(
    env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
) -> jni::sys::jintArray {
    let values = catch_jni_default([0; RUNTIME_STATS_INTS], || {
        runtime_stats_values(space_handle)
    });
    int_array_or_null(&env, &values)
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_resetStepPhaseStatsNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
) {
    with_space(space_handle, (), |space| {
        space.reset_step_phase_stats();
    });
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_getStepPhaseStatsNative(
    env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
) -> jni::sys::jlongArray {
    let values = catch_jni_default([0; STEP_PHASE_STATS_LONGS], || {
        with_space(space_handle, [0; STEP_PHASE_STATS_LONGS], |space| {
            space.step_phase_stats.values()
        })
    });
    long_array_or_null(&env, &values)
}

fn runtime_stats_values(space_handle: jlong) -> [jint; RUNTIME_STATS_INTS] {
    with_space(space_handle, [0; RUNTIME_STATS_INTS], |space| {
        let mut contact_pairs = 0;
        let mut contact_manifolds = 0;
        let mut contact_points = 0;
        let mut dynamic_dynamic_contact_pairs = 0;
        let mut terrain_contact_pairs = 0;
        for pair in space.narrow_phase.contact_pairs() {
            contact_pairs += 1;
            contact_manifolds += pair.manifolds.len();
            for manifold in &pair.manifolds {
                contact_points += manifold.data.solver_contacts.len();
            }
            let (group_a, group_b) =
                contact_pair_memberships(space, pair.collider1, pair.collider2);
            if is_dynamic_group(group_a) && is_dynamic_group(group_b) {
                dynamic_dynamic_contact_pairs += 1;
            }
            if is_terrain_dynamic_pair(group_a, group_b) {
                terrain_contact_pairs += 1;
            }
        }

        [
            saturating_jint(space.bodies.len()),
            saturating_jint(space.colliders.len()),
            saturating_jint(space.islands.active_bodies().count()),
            saturating_jint(contact_pairs),
            saturating_jint(contact_manifolds),
            saturating_jint(contact_points),
            saturating_jint(dynamic_dynamic_contact_pairs),
            saturating_jint(terrain_contact_pairs),
            saturating_jint(active_island_count(space)),
            saturating_jint(space.impulse_joints.len() + space.multibody_joints.iter().count()),
        ]
    })
}

fn active_island_count(space: &NativeSpace) -> usize {
    space
        .islands
        .active_bodies()
        .filter(|handle| {
            space
                .bodies
                .get(*handle)
                .is_some_and(|body| body.effective_active_set_offset() == 0)
        })
        .count()
}

fn contact_pair_memberships(
    space: &NativeSpace,
    collider_a: ColliderHandle,
    collider_b: ColliderHandle,
) -> (u32, u32) {
    (
        collider_membership(space, collider_a),
        collider_membership(space, collider_b),
    )
}

fn collider_membership(space: &NativeSpace, collider: ColliderHandle) -> u32 {
    space
        .colliders
        .get(collider)
        .map(|collider| collider.collision_groups().memberships.bits())
        .unwrap_or(0)
}

fn is_dynamic_group(group: u32) -> bool {
    group & DYNAMIC_BODY_COLLISION_GROUP != 0
}

fn is_terrain_group(group: u32) -> bool {
    group & TERRAIN_COLLISION_GROUP != 0
}

fn is_terrain_dynamic_pair(group_a: u32, group_b: u32) -> bool {
    (is_terrain_group(group_a) && is_dynamic_group(group_b))
        || (is_dynamic_group(group_a) && is_terrain_group(group_b))
}

fn saturating_jint(value: usize) -> jint {
    value.min(jint::MAX as usize) as jint
}

fn int_array_or_null(env: &JNIEnv<'_>, values: &[jint]) -> jni::sys::jintArray {
    if values.len() > i32::MAX as usize {
        return std::ptr::null_mut();
    }

    let Ok(out) = env.new_int_array(values.len() as i32) else {
        return std::ptr::null_mut();
    };

    if env.set_int_array_region(&out, 0, values).is_err() {
        return std::ptr::null_mut();
    }

    out.into_raw()
}

fn long_array_or_null(env: &JNIEnv<'_>, values: &[jlong]) -> jni::sys::jlongArray {
    if values.len() > i32::MAX as usize {
        return std::ptr::null_mut();
    }

    let Ok(out) = env.new_long_array(values.len() as i32) else {
        return std::ptr::null_mut();
    };

    if env.set_long_array_region(&out, 0, values).is_err() {
        return std::ptr::null_mut();
    }

    out.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setSolverTuningNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    solver_iterations: jint,
    internal_pgs_iterations: jint,
    stabilization_iterations: jint,
    min_island_size: jint,
) {
    with_space(space_handle, (), |space| {
        space.set_solver_tuning(
            positive_usize(solver_iterations),
            positive_usize(internal_pgs_iterations),
            non_negative_usize(stabilization_iterations),
            positive_usize(min_island_size),
        );
    });
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setDynamicSleepTuningNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    linear_threshold: jfloat,
    angular_threshold: jfloat,
    time_until_sleep: jfloat,
) {
    with_space(space_handle, (), |space| {
        space.set_dynamic_sleep_tuning(linear_threshold, angular_threshold, time_until_sleep);
    });
}

fn positive_usize(value: jint) -> usize {
    if value < 1 {
        1
    } else {
        value as usize
    }
}

fn non_negative_usize(value: jint) -> usize {
    if value < 0 {
        0
    } else {
        value as usize
    }
}
