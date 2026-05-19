#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_createSpaceNative(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    let space = Box::new(NativeSpace::new());
    let handle = Box::into_raw(space);
    if register_space(handle) {
        handle as jlong
    } else {
        unsafe {
            drop(Box::from_raw(handle));
        }
        0
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_hytalemodding_impulse_rapier_RapierNative_destroySpaceNative(
    _env: JNIEnv,
    _class: JClass,
    space_handle: jlong,
) {
    if !unregister_space(space_handle) {
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
    catch_jni_default((), || {
        unsafe {
            if let Some(space) = space_mut(space_handle) {
                space.step(dt);
            }
        }
    });
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
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            space.set_solver_tuning(
                positive_usize(solver_iterations),
                positive_usize(internal_pgs_iterations),
                non_negative_usize(stabilization_iterations),
                positive_usize(min_island_size),
            );
        }
    }
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
    unsafe {
        if let Some(space) = space_mut(space_handle) {
            space.set_dynamic_sleep_tuning(
                linear_threshold,
                angular_threshold,
                time_until_sleep,
            );
        }
    }
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
