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
