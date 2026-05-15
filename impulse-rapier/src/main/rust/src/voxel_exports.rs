#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_addVoxelTerrainNative(
    env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    voxel_size_x: jfloat,
    voxel_size_y: jfloat,
    voxel_size_z: jfloat,
    voxel_coordinates: JIntArray,
    pos_x: jfloat,
    pos_y: jfloat,
    pos_z: jfloat,
    friction: jfloat,
    restitution: jfloat,
    collision_group: jint,
    collision_mask: jint,
) -> jlong {
    unsafe {
        let Some(space) = space_mut(space_handle) else {
            return 0;
        };

        let Ok(length) = env.get_array_length(&voxel_coordinates) else {
            return 0;
        };
        if length < 3 {
            return 0;
        }

        let usable_length = length as usize - (length as usize % 3);
        let mut raw = vec![0; usable_length];
        if env
            .get_int_array_region(&voxel_coordinates, 0, &mut raw)
            .is_err()
        {
            return 0;
        }

        let voxels: Vec<IVector> = raw
            .chunks_exact(3)
            .map(|coord| IVector::new(coord[0], coord[1], coord[2]))
            .collect();
        if voxels.is_empty() {
            return 0;
        }

        let body_handle = space
            .bodies
            .insert(RigidBodyBuilder::fixed().translation(Vector::new(pos_x, pos_y, pos_z)).build());
        let collider = ColliderBuilder::voxels(
            Vector::new(
                voxel_size_x.max(0.001),
                voxel_size_y.max(0.001),
                voxel_size_z.max(0.001),
            ),
            &voxels,
        )
        .density(0.0)
        .friction(friction)
        .friction_combine_rule(CoefficientCombineRule::Multiply)
        .restitution(restitution)
        .restitution_combine_rule(CoefficientCombineRule::Multiply)
        .collision_groups(interaction_groups(collision_group, collision_mask));

        let collider_handle =
            space
                .colliders
                .insert_with_parent(collider, body_handle, &mut space.bodies);

        space.insert_entry(body_handle, collider_handle) as jlong
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_combineVoxelTerrainNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
    body_id_a: jlong,
    body_id_b: jlong,
    shift_x: jint,
    shift_y: jint,
    shift_z: jint,
) {
    unsafe {
        let Some(space) = space_mut(space_handle) else {
            return;
        };
        let Some(entry_a) = space.entry(body_id_a) else {
            return;
        };
        let Some(entry_b) = space.entry(body_id_b) else {
            return;
        };

        let (collider_a, collider_b) =
            space.colliders.get_pair_mut(entry_a.collider, entry_b.collider);
        let (Some(collider_a), Some(collider_b)) = (collider_a, collider_b) else {
            return;
        };

        let Some(voxels_a) = collider_a.shape_mut().as_voxels_mut() else {
            return;
        };
        let Some(voxels_b) = collider_b.shape_mut().as_voxels_mut() else {
            return;
        };

        voxels_a.combine_voxel_states(voxels_b, IVector::new(shift_x, shift_y, shift_z));
    }
}
