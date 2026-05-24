use super::*;

const RAYCAST_HIT_FLOATS: usize = 9;
const MAX_RAYCAST_HITS: usize = 4_096;
const MAX_RAYCAST_FLOATS: usize = RAYCAST_HIT_FLOATS * MAX_RAYCAST_HITS;

const CONTACT_FLOATS: usize = 13;
const MAX_CONTACT_POINTS: usize = 16_384;
const MAX_CONTACT_FLOATS: usize = CONTACT_FLOATS * MAX_CONTACT_POINTS;

fn append_bounded(values: &mut Vec<jfloat>, record: &[jfloat], max_floats: usize) -> bool {
    if record.len() > max_floats.saturating_sub(values.len()) {
        return false;
    }
    if values.try_reserve(record.len()).is_err() {
        return false;
    }
    values.extend_from_slice(record);
    true
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
    let values = catch_jni_default(Vec::new(), || {
        raycast_all_values(space_handle, from_x, from_y, from_z, to_x, to_y, to_z)
    });
    float_array_or_null(&env, &values)
}

fn raycast_all_values(
    space_handle: jlong,
    from_x: jfloat,
    from_y: jfloat,
    from_z: jfloat,
    to_x: jfloat,
    to_y: jfloat,
    to_z: jfloat,
) -> Vec<jfloat> {
    let mut values: Vec<jfloat> = Vec::new();
    with_space(space_handle, (), |space| {
        let from = finite_vector_or_zero(from_x, from_y, from_z);
        let to = finite_vector_or_zero(to_x, to_y, to_z);
        let delta = to - from;
        let distance = delta.length();
        if distance > 0.0 && distance.is_finite() {
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
                    if !append_bounded(
                        &mut values,
                        &[
                            *body_id as jfloat,
                            point.x,
                            point.y,
                            point.z,
                            hit.normal.x,
                            hit.normal.y,
                            hit.normal.z,
                            hit.time_of_impact / distance,
                            hit.time_of_impact,
                        ],
                        MAX_RAYCAST_FLOATS,
                    ) {
                        break;
                    }
                }
            }
        }
    });
    values
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_getContactsNative(
    env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
) -> jfloatArray {
    let values = catch_jni_default(Vec::new(), || contact_values(space_handle));
    float_array_or_null(&env, &values)
}

fn contact_values(space_handle: jlong) -> Vec<jfloat> {
    let mut values: Vec<jfloat> = Vec::new();
    with_space(space_handle, (), |space| {
        'contacts: for pair in space.narrow_phase.contact_pairs() {
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
                    if !append_bounded(
                        &mut values,
                        &[
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
                        ],
                        MAX_CONTACT_FLOATS,
                    ) {
                        break 'contacts;
                    }
                }
            }
        }
    });
    values
}
