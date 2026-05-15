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
