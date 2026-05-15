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
