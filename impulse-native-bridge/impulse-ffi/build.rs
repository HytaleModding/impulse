fn main() {
    println!("cargo:rerun-if-changed=include/");
    println!("cargo:rerun-if-changed=include/engine/");
    println!("cargo:rerun-if-changed=cbindgen-java.toml");

    // --- PART 1: CONSUME (Read the engine's headers) ---
    let bindings = bindgen::Builder::default()
        .header("include/engine_api.h")
        .raw_line("#![allow(non_camel_case_types, non_snake_case, non_upper_case_globals)]")
        .generate_comments(false)
        .default_enum_style(bindgen::EnumVariation::Rust {
            non_exhaustive: true,
        })
        .generate()
        .expect("Unable to generate engine bindings");

    bindings
        .write_to_file("src/ffi_engine.rs")
        .expect("Couldn't write engine bindings!");

    // --- PART 2: PRODUCE (Generate your headers for Java) ---
    cbindgen::Builder::new()
        .with_config(cbindgen::Config::from_file("cbindgen-java.toml").unwrap())
        .with_include_guard("INTERNAL_BRIDGE_H")
        .generate()
        .expect("Unable to generate internal_bridge.h")
        .write_to_file("include/internal_bridge.h");
}
