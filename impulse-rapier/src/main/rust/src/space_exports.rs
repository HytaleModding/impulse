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
