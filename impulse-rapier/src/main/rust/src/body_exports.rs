use super::*;

const BODY_SNAPSHOT_FLOATS: usize = 16;

enum SnapshotFailure {
    BufferAllocation,
    BodyIdRead,
    StaleBodyHandle(jlong),
    StaleRigidBody(jlong),
    StaleCollider(jlong),
    OutputWrite,
}

fn throw_snapshot_failure(env: &mut JNIEnv<'_>, failure: SnapshotFailure) {
    let message = match failure {
        SnapshotFailure::BufferAllocation => {
            "Rapier native snapshot failed: unable to allocate snapshot buffer".to_string()
        }
        SnapshotFailure::BodyIdRead => {
            "Rapier native snapshot failed: unable to read body handle array".to_string()
        }
        SnapshotFailure::StaleBodyHandle(body_id) => {
            format!("Rapier native snapshot failed: stale body handle {body_id}")
        }
        SnapshotFailure::StaleRigidBody(body_id) => {
            format!("Rapier native snapshot failed: stale rigid body handle for body {body_id}")
        }
        SnapshotFailure::StaleCollider(body_id) => {
            format!("Rapier native snapshot failed: stale collider handle for body {body_id}")
        }
        SnapshotFailure::OutputWrite => {
            "Rapier native snapshot failed: unable to write snapshot output array".to_string()
        }
    };
    let _ = env.throw_new("java/lang/IllegalStateException", message);
}

fn resize_reused_buffer<T: Clone>(buffer: &mut Vec<T>, len: usize, fill: T) -> bool {
    if buffer.len() < len && buffer.try_reserve(len - buffer.len()).is_err() {
        return false;
    }
    buffer.resize(len, fill);
    true
}

#[derive(Clone, Copy)]
enum BodyMutationFailure {
    StaleBodyHandle(jlong),
    StaleRigidBody(jlong),
    StaleCollider(jlong),
}

fn throw_body_mutation_failure(
    env: &mut JNIEnv<'_>,
    operation: &str,
    failure: BodyMutationFailure,
) {
    let message = match failure {
        BodyMutationFailure::StaleBodyHandle(body_id) => {
            format!("Rapier native {operation} failed: stale body handle {body_id}")
        }
        BodyMutationFailure::StaleRigidBody(body_id) => {
            format!("Rapier native {operation} failed: stale rigid body handle for body {body_id}")
        }
        BodyMutationFailure::StaleCollider(body_id) => {
            format!("Rapier native {operation} failed: stale collider handle for body {body_id}")
        }
    };
    let _ = env.throw_new("java/lang/IllegalStateException", message);
}

fn checked_body_entry(
    space: &NativeSpace,
    body_id: jlong,
) -> Result<BodyEntry, BodyMutationFailure> {
    space
        .entry(body_id)
        .ok_or(BodyMutationFailure::StaleBodyHandle(body_id))
}

fn checked_body_exists(
    space: &NativeSpace,
    entry: BodyEntry,
    body_id: jlong,
) -> Result<(), BodyMutationFailure> {
    if space.bodies.get(entry.body).is_some() {
        Ok(())
    } else {
        Err(BodyMutationFailure::StaleRigidBody(body_id))
    }
}

fn checked_body_mut<'space>(
    space: &'space mut NativeSpace,
    entry: BodyEntry,
    body_id: jlong,
) -> Result<&'space mut RigidBody, BodyMutationFailure> {
    space
        .bodies
        .get_mut(entry.body)
        .ok_or(BodyMutationFailure::StaleRigidBody(body_id))
}

fn checked_collider_mut<'space>(
    space: &'space mut NativeSpace,
    entry: BodyEntry,
    body_id: jlong,
) -> Result<&'space mut Collider, BodyMutationFailure> {
    space
        .colliders
        .get_mut(entry.collider)
        .ok_or(BodyMutationFailure::StaleCollider(body_id))
}

fn with_attached_body_mutation<F>(
    env: &mut JNIEnv<'_>,
    operation: &str,
    space_handle: jlong,
    body_id: jlong,
    f: F,
) where
    F: FnOnce(&mut NativeSpace, BodyEntry) -> Result<(), BodyMutationFailure>,
{
    let result = with_space_checked(space_handle, |space| {
        let entry = checked_body_entry(space, body_id)?;
        checked_body_exists(space, entry, body_id)?;
        f(space, entry)
    });

    match result {
        Ok(Ok(())) => {}
        Ok(Err(failure)) => throw_body_mutation_failure(env, operation, failure),
        Err(failure) => throw_native_space_failure(env, operation, failure),
    }
}

fn with_attached_rigid_body_mutation<F>(
    env: &mut JNIEnv<'_>,
    operation: &str,
    space_handle: jlong,
    body_id: jlong,
    f: F,
) where
    F: FnOnce(&mut RigidBody),
{
    with_attached_body_mutation(env, operation, space_handle, body_id, |space, entry| {
        let body = checked_body_mut(space, entry, body_id)?;
        f(body);
        Ok(())
    });
}

fn with_attached_collider_mutation<F>(
    env: &mut JNIEnv<'_>,
    operation: &str,
    space_handle: jlong,
    body_id: jlong,
    f: F,
) where
    F: FnOnce(&mut Collider),
{
    with_attached_body_mutation(env, operation, space_handle, body_id, |space, entry| {
        let collider = checked_collider_mut(space, entry, body_id)?;
        f(collider);
        Ok(())
    });
}

fn fill_snapshot_body_values<'space>(
    space: &'space mut NativeSpace,
    env: &JNIEnv<'_>,
    body_ids: &JLongArray,
    count: usize,
) -> Result<&'space [jfloat], SnapshotFailure> {
    let value_count = count
        .checked_mul(BODY_SNAPSHOT_FLOATS)
        .ok_or(SnapshotFailure::BufferAllocation)?;
    let NativeSpace {
        snapshot_body_ids,
        snapshot_body_values,
        handles,
        bodies,
        colliders,
        ..
    } = space;

    if !resize_reused_buffer(snapshot_body_ids, count, 0_i64) {
        return Err(SnapshotFailure::BufferAllocation);
    }
    if env
        .get_long_array_region(body_ids, 0, &mut snapshot_body_ids[..count])
        .is_err()
    {
        return Err(SnapshotFailure::BodyIdRead);
    }

    if !resize_reused_buffer(snapshot_body_values, value_count, 0.0_f32) {
        return Err(SnapshotFailure::BufferAllocation);
    }
    snapshot_body_values[..value_count].fill(0.0);

    for (index, body_id) in snapshot_body_ids[..count].iter().enumerate() {
        let Some(entry) = handles.get(body_id).copied() else {
            return Err(SnapshotFailure::StaleBodyHandle(*body_id));
        };
        let Some(body) = bodies.get(entry.body) else {
            return Err(SnapshotFailure::StaleRigidBody(*body_id));
        };
        let Some(collider) = colliders.get(entry.collider) else {
            return Err(SnapshotFailure::StaleCollider(*body_id));
        };

        let offset = index * BODY_SNAPSHOT_FLOATS;
        let translation = body.translation();
        let rotation = body.rotation().to_array();
        let linear_velocity = body.linvel();
        let angular_velocity = body.angvel();

        snapshot_body_values[offset] = translation.x;
        snapshot_body_values[offset + 1] = translation.y;
        snapshot_body_values[offset + 2] = translation.z;
        snapshot_body_values[offset + 3] = rotation[0];
        snapshot_body_values[offset + 4] = rotation[1];
        snapshot_body_values[offset + 5] = rotation[2];
        snapshot_body_values[offset + 6] = rotation[3];
        snapshot_body_values[offset + 7] = linear_velocity.x;
        snapshot_body_values[offset + 8] = linear_velocity.y;
        snapshot_body_values[offset + 9] = linear_velocity.z;
        snapshot_body_values[offset + 10] = angular_velocity.x;
        snapshot_body_values[offset + 11] = angular_velocity.y;
        snapshot_body_values[offset + 12] = angular_velocity.z;
        snapshot_body_values[offset + 13] = body_type_to_int(body) as f32;
        snapshot_body_values[offset + 14] = if body.is_sleeping() { 1.0 } else { 0.0 };
        snapshot_body_values[offset + 15] = if collider.is_sensor() { 1.0 } else { 0.0 };
    }

    Ok(&snapshot_body_values[..value_count])
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
    with_space(space_handle, 0, |space| {
        let body_builder = RigidBodyBuilder::new(body_type_from_int(body_type))
            .pose(Pose::from_parts(
                finite_vector_or_zero(pos_x, pos_y, pos_z),
                rotation_from_xyzw(rot_x, rot_y, rot_z, rot_w),
            ))
            .linvel(finite_vector_or_zero(lin_vel_x, lin_vel_y, lin_vel_z))
            .angvel(finite_vector_or_zero(ang_vel_x, ang_vel_y, ang_vel_z))
            .linear_damping(finite_nonnegative(linear_damping))
            .angular_damping(finite_nonnegative(angular_damping))
            .ccd_enabled(bool_from_jboolean(ccd_enabled));
        let mut rigid_body = body_builder.build();
        if body_type == BODY_TYPE_DYNAMIC {
            apply_dynamic_sleep_tuning(
                &mut rigid_body,
                space.dynamic_sleep_linear_threshold,
                space.dynamic_sleep_angular_threshold,
                space.dynamic_sleep_time_until_sleep,
            );
        }
        let body_handle = space.bodies.insert(rigid_body);

        let mut collider = build_collider(
            shape_type,
            half_x,
            half_y,
            half_z,
            radius,
            half_height,
            axis,
        )
        .friction(finite_nonnegative(friction))
        .friction_combine_rule(CoefficientCombineRule::Multiply)
        .restitution_combine_rule(CoefficientCombineRule::Multiply)
        .restitution(finite_nonnegative(restitution))
        .sensor(bool_from_jboolean(sensor))
        .collision_groups(interaction_groups(collision_group, collision_mask));

        let mass = finite_nonnegative(mass);
        if body_type == BODY_TYPE_DYNAMIC && mass > 0.0 {
            collider = collider.mass(mass);
        } else {
            collider = collider.density(0.0);
        }

        let collider_handle =
            space
                .colliders
                .insert_with_parent(collider, body_handle, &mut space.bodies);
        space.refresh_query_aabb(collider_handle);

        space.insert_entry(body_handle, collider_handle) as jlong
    })
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_removeBodyNative(
    mut env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
) {
    with_attached_body_mutation(
        &mut env,
        "remove body",
        space_handle,
        body_id,
        |space, entry| {
            space.handles.remove(&body_id);
            space.collider_to_body_id.remove(&entry.collider);
            space.bodies.remove(
                entry.body,
                &mut space.islands,
                &mut space.colliders,
                &mut space.impulse_joints,
                &mut space.multibody_joints,
                true,
            );
            Ok(())
        },
    );
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_snapshotBodiesNative(
    mut env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_ids: JLongArray,
    body_count: jint,
    out: JFloatArray,
) -> jint {
    let Ok(handle_capacity) = env.get_array_length(&body_ids) else {
        return -1;
    };
    let Ok(out_capacity) = env.get_array_length(&out) else {
        return -1;
    };

    let requested = body_count.max(0) as usize;
    let count = requested
        .min(handle_capacity as usize)
        .min(out_capacity as usize / BODY_SNAPSHOT_FLOATS);
    if count == 0 {
        return 0;
    }

    let snapshot_start = Instant::now();
    let result = with_space_checked(space_handle, |space| {
        let result = match fill_snapshot_body_values(space, &env, &body_ids, count) {
            Ok(values) => {
                if env.set_float_array_region(&out, 0, values).is_ok() {
                    Ok(())
                } else {
                    Err(SnapshotFailure::OutputWrite)
                }
            }
            Err(failure) => Err(failure),
        };
        space
            .step_phase_stats
            .add_snapshot_time(snapshot_start.elapsed());
        result
    });

    match result {
        Ok(Ok(())) => count as jint,
        Ok(Err(failure)) => {
            throw_snapshot_failure(&mut env, failure);
            -1
        }
        Err(failure) => {
            throw_native_space_failure(&mut env, "snapshot bodies", failure);
            -1
        }
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
    let values = with_space(space_handle, [0.0, 0.0, 0.0], |space| {
        if let Some(entry) = space.entry(body_id) {
            if let Some(body) = space.bodies.get(entry.body) {
                let pos = body.translation();
                return [pos.x, pos.y, pos.z];
            }
        }
        [0.0, 0.0, 0.0]
    });
    let _ = env.set_float_array_region(&out, 0, &values);
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodyPositionNative(
    mut env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    x: jfloat,
    y: jfloat,
    z: jfloat,
) {
    with_attached_body_mutation(
        &mut env,
        "set body position",
        space_handle,
        body_id,
        |space, entry| {
            {
                let body = checked_body_mut(space, entry, body_id)?;
                body.set_translation(finite_vector_or_zero(x, y, z), true);
            }
            space.refresh_query_aabb(entry.collider);
            Ok(())
        },
    );
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_getBodyRotationNative(
    env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    out: JFloatArray,
) {
    let values = with_space(space_handle, [0.0, 0.0, 0.0, 1.0], |space| {
        if let Some(entry) = space.entry(body_id) {
            if let Some(body) = space.bodies.get(entry.body) {
                let rot = body.rotation().to_array();
                return [rot[0], rot[1], rot[2], rot[3]];
            }
        }
        [0.0, 0.0, 0.0, 1.0]
    });
    let _ = env.set_float_array_region(&out, 0, &values);
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodyRotationNative(
    mut env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    x: jfloat,
    y: jfloat,
    z: jfloat,
    w: jfloat,
) {
    with_attached_body_mutation(
        &mut env,
        "set body rotation",
        space_handle,
        body_id,
        |space, entry| {
            {
                let body = checked_body_mut(space, entry, body_id)?;
                body.set_rotation(rotation_from_xyzw(x, y, z, w), true);
            }
            space.refresh_query_aabb(entry.collider);
            Ok(())
        },
    );
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_getBodyLinearVelocityNative(
    env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    out: JFloatArray,
) {
    let values = with_space(space_handle, [0.0, 0.0, 0.0], |space| {
        if let Some(entry) = space.entry(body_id) {
            if let Some(body) = space.bodies.get(entry.body) {
                let vel = body.linvel();
                return [vel.x, vel.y, vel.z];
            }
        }
        [0.0, 0.0, 0.0]
    });
    let _ = env.set_float_array_region(&out, 0, &values);
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodyLinearVelocityNative(
    mut env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    x: jfloat,
    y: jfloat,
    z: jfloat,
) {
    with_attached_rigid_body_mutation(
        &mut env,
        "set body linear velocity",
        space_handle,
        body_id,
        |body| {
            body.set_linvel(finite_vector_or_zero(x, y, z), true);
        },
    );
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_getBodyAngularVelocityNative(
    env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    out: JFloatArray,
) {
    let values = with_space(space_handle, [0.0, 0.0, 0.0], |space| {
        if let Some(entry) = space.entry(body_id) {
            if let Some(body) = space.bodies.get(entry.body) {
                let vel = body.angvel();
                return [vel.x, vel.y, vel.z];
            }
        }
        [0.0, 0.0, 0.0]
    });
    let _ = env.set_float_array_region(&out, 0, &values);
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodyAngularVelocityNative(
    mut env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    x: jfloat,
    y: jfloat,
    z: jfloat,
) {
    with_attached_rigid_body_mutation(
        &mut env,
        "set body angular velocity",
        space_handle,
        body_id,
        |body| {
            body.set_angvel(finite_vector_or_zero(x, y, z), true);
        },
    );
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodyTypeNative(
    mut env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    body_type: jint,
) {
    with_attached_body_mutation(
        &mut env,
        "set body type",
        space_handle,
        body_id,
        |space, entry| {
            let linear_threshold = space.dynamic_sleep_linear_threshold;
            let angular_threshold = space.dynamic_sleep_angular_threshold;
            let time_until_sleep = space.dynamic_sleep_time_until_sleep;
            let body = checked_body_mut(space, entry, body_id)?;
            body.set_body_type(body_type_from_int(body_type), true);
            if body_type == BODY_TYPE_DYNAMIC {
                apply_dynamic_sleep_tuning(
                    body,
                    linear_threshold,
                    angular_threshold,
                    time_until_sleep,
                );
            }
            Ok(())
        },
    );
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_getBodyTypeNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
) -> jint {
    with_space(space_handle, BODY_TYPE_STATIC, |space| {
        if let Some(entry) = space.entry(body_id) {
            if let Some(body) = space.bodies.get(entry.body) {
                return body_type_to_int(body);
            }
        }
        BODY_TYPE_STATIC
    })
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodyMassNative(
    mut env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    mass: jfloat,
) {
    with_attached_body_mutation(
        &mut env,
        "set body mass",
        space_handle,
        body_id,
        |space, entry| {
            let mass = finite_nonnegative(mass);
            {
                let collider = checked_collider_mut(space, entry, body_id)?;
                if mass > 0.0 {
                    collider.set_mass(mass);
                } else {
                    collider.set_density(0.0);
                }
            }
            if mass <= 0.0 {
                let body = checked_body_mut(space, entry, body_id)?;
                body.set_body_type(RigidBodyType::Fixed, true);
            }
            Ok(())
        },
    );
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_getBodyMassNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
) -> jfloat {
    with_space(space_handle, 0.0, |space| {
        if let Some(entry) = space.entry(body_id) {
            if let Some(body) = space.bodies.get(entry.body) {
                return finite_or(body.mass(), 0.0);
            }
        }
        0.0
    })
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodyDampingNative(
    mut env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    linear_damping: jfloat,
    angular_damping: jfloat,
) {
    with_attached_rigid_body_mutation(
        &mut env,
        "set body damping",
        space_handle,
        body_id,
        |body| {
            body.set_linear_damping(finite_nonnegative(linear_damping));
            body.set_angular_damping(finite_nonnegative(angular_damping));
        },
    );
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_getBodyDampingNative(
    env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    out: JFloatArray,
) {
    let values = with_space(space_handle, [0.0, 0.0], |space| {
        if let Some(entry) = space.entry(body_id) {
            if let Some(body) = space.bodies.get(entry.body) {
                return [
                    finite_or(body.linear_damping(), 0.0),
                    finite_or(body.angular_damping(), 0.0),
                ];
            }
        }
        [0.0, 0.0]
    });
    let _ = env.set_float_array_region(&out, 0, &values);
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_isBodySleepingNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
) -> jboolean {
    with_space(space_handle, 0, |space| {
        if let Some(entry) = space.entry(body_id) {
            if let Some(body) = space.bodies.get(entry.body) {
                return jboolean_from_bool(body.is_sleeping());
            }
        }
        0
    })
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_sleepBodyNative(
    mut env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
) {
    with_attached_rigid_body_mutation(&mut env, "sleep body", space_handle, body_id, |body| {
        body.sleep();
    });
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_activateBodyNative(
    mut env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
) {
    with_attached_rigid_body_mutation(&mut env, "activate body", space_handle, body_id, |body| {
        body.wake_up(true);
    });
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_applyBodyCentralForceNative(
    mut env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    x: jfloat,
    y: jfloat,
    z: jfloat,
) {
    with_attached_rigid_body_mutation(
        &mut env,
        "apply body central force",
        space_handle,
        body_id,
        |body| {
            body.add_force(finite_vector_or_zero(x, y, z), true);
        },
    );
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_applyBodyForceNative(
    mut env: JNIEnv,
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
    with_attached_rigid_body_mutation(
        &mut env,
        "apply body force",
        space_handle,
        body_id,
        |body| {
            let point = body.translation() + finite_vector_or_zero(offset_x, offset_y, offset_z);
            body.add_force_at_point(
                finite_vector_or_zero(force_x, force_y, force_z),
                point,
                true,
            );
        },
    );
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_applyBodyCentralImpulseNative(
    mut env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    x: jfloat,
    y: jfloat,
    z: jfloat,
) {
    with_attached_rigid_body_mutation(
        &mut env,
        "apply body central impulse",
        space_handle,
        body_id,
        |body| {
            body.apply_impulse(finite_vector_or_zero(x, y, z), true);
        },
    );
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_applyBodyImpulseNative(
    mut env: JNIEnv,
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
    with_attached_rigid_body_mutation(
        &mut env,
        "apply body impulse",
        space_handle,
        body_id,
        |body| {
            let point = body.translation() + finite_vector_or_zero(offset_x, offset_y, offset_z);
            body.apply_impulse_at_point(
                finite_vector_or_zero(impulse_x, impulse_y, impulse_z),
                point,
                true,
            );
        },
    );
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_applyBodyTorqueNative(
    mut env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    x: jfloat,
    y: jfloat,
    z: jfloat,
) {
    with_attached_rigid_body_mutation(
        &mut env,
        "apply body torque",
        space_handle,
        body_id,
        |body| {
            body.add_torque(finite_vector_or_zero(x, y, z), true);
        },
    );
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_applyBodyTorqueImpulseNative(
    mut env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    x: jfloat,
    y: jfloat,
    z: jfloat,
) {
    with_attached_rigid_body_mutation(
        &mut env,
        "apply body torque impulse",
        space_handle,
        body_id,
        |body| {
            body.apply_torque_impulse(finite_vector_or_zero(x, y, z), true);
        },
    );
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_clearBodyForcesNative(
    mut env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
) {
    with_attached_rigid_body_mutation(
        &mut env,
        "clear body forces",
        space_handle,
        body_id,
        |body| {
            body.reset_forces(true);
            body.reset_torques(true);
        },
    );
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodyFrictionNative(
    mut env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    friction: jfloat,
) {
    with_attached_collider_mutation(
        &mut env,
        "set body friction",
        space_handle,
        body_id,
        |collider| {
            collider.set_friction(finite_nonnegative(friction));
        },
    );
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodyRestitutionNative(
    mut env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    restitution: jfloat,
) {
    with_attached_collider_mutation(
        &mut env,
        "set body restitution",
        space_handle,
        body_id,
        |collider| {
            collider.set_restitution(finite_nonnegative(restitution));
        },
    );
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodySensorNative(
    mut env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    sensor: jboolean,
) {
    with_attached_collider_mutation(
        &mut env,
        "set body sensor",
        space_handle,
        body_id,
        |collider| {
            collider.set_sensor(bool_from_jboolean(sensor));
        },
    );
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_isBodySensorNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
) -> jboolean {
    with_space(space_handle, 0, |space| {
        if let Some(entry) = space.entry(body_id) {
            if let Some(collider) = space.colliders.get(entry.collider) {
                return jboolean_from_bool(collider.is_sensor());
            }
        }
        0
    })
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodyCollisionFilterNative(
    mut env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    group: jint,
    mask: jint,
) {
    with_attached_collider_mutation(
        &mut env,
        "set body collision filter",
        space_handle,
        body_id,
        |collider| {
            collider.set_collision_groups(interaction_groups(group, mask));
        },
    );
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_getBodyCollisionFilterNative(
    env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    out: JIntArray,
) {
    let values = with_space(space_handle, [1, 1], |space| {
        if let Some(entry) = space.entry(body_id) {
            if let Some(collider) = space.colliders.get(entry.collider) {
                let groups = collider.collision_groups();
                return [
                    groups.memberships.bits() as i32,
                    groups.filter.bits() as i32,
                ];
            }
        }
        [1, 1]
    });
    let _ = env.set_int_array_region(&out, 0, &values);
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_setBodyCcdNative(
    mut env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
    enabled: jboolean,
) {
    with_attached_rigid_body_mutation(&mut env, "set body ccd", space_handle, body_id, |body| {
        body.enable_ccd(bool_from_jboolean(enabled));
    });
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_isBodyCcdNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id: jlong,
) -> jboolean {
    with_space(space_handle, 0, |space| {
        if let Some(entry) = space.entry(body_id) {
            if let Some(body) = space.bodies.get(entry.body) {
                return jboolean_from_bool(body.is_ccd_enabled());
            }
        }
        0
    })
}
