static SUPPRESSED_WARNINGS: &str = "#![allow(dead_code, unused, non_camel_case_types, non_snake_case, non_upper_case_globals, unsafe_op_in_unsafe_fn)]";

static ALLOWED_FN: &str = "eng_.*";
static ALLOWED_TYPES: &str = "Vector3f|Quaternion|RuntimeStats|StepPhaseStats|RayHit|Contact|Axis|BodyType|BodyShapeType|eng_JointType";
static BLOCKLIST: &str = ".*__.*";

static GENERATE_COMMENTS: bool = true;

static HEADERS_DIR: &str = "../include";
static GENERATED_DIR: &str = "generated";

static NATIVE_HEADER: &str = "../include/engine_api.h";

static JAVA_HEADER: &str = "../include/java_bridge.h";

fn main() {
    println!("cargo:rerun-if-changed={HEADERS_DIR}/*");
    println!("cargo:rerun-if-changed={HEADERS_DIR}/engine/*");
    println!("cargo:rerun-if-changed=cbindgen-java.toml");

    // --- PART 1a: CONSUME – Generate runtime Engine struct via libloading ---
    // Uses dynamic_library_name so the DLL is loaded at runtime, not at link
    // time.  The linker never sees the engine symbols, so the build succeeds
    // even when the engine DLL has not yet been compiled.
    let bindings = bindgen::Builder::default()
        .dynamic_library_name("Engine")
        .dynamic_link_require_all(true)
        .header(NATIVE_HEADER)
        .raw_line(SUPPRESSED_WARNINGS)
        .allowlist_function(ALLOWED_FN)
        .allowlist_type(ALLOWED_TYPES)
        .blocklist_item(BLOCKLIST)
        .generate_comments(GENERATE_COMMENTS)
        .default_enum_style(bindgen::EnumVariation::Rust {
            non_exhaustive: true,
        })
        .generate()
        .expect("Unable to generate engine bindings");

    bindings
        .write_to_file(format!("{GENERATED_DIR}/engine_ffi.rs"))
        .expect("Couldn't write engine bindings!");

    // --- PART 1b: Generate a signature-only file for the dispatch macros ---
    // This file is intentionally NOT declared as a Rust module (`mod`), so it
    // is never compiled and never causes linker errors.  The proc-macro reads
    // it at compile time via the filesystem to extract function signatures.
    let signatures = bindgen::Builder::default()
        .header(NATIVE_HEADER)
        .raw_line(
            "#![allow(non_camel_case_types, non_snake_case, non_upper_case_globals, dead_code)]",
        )
        .generate_comments(false)
        .default_enum_style(bindgen::EnumVariation::Rust {
            non_exhaustive: true,
        })
        .generate()
        .expect("Unable to generate engine signature stubs");

    signatures
        .write_to_file(format!("{GENERATED_DIR}/engine_ffi_signs.rs"))
        .expect("Couldn't write engine signature stubs!");

    // --- PART 2: PRODUCE (Generate your headers for Java) ---
    cbindgen::Builder::new()
        .with_config(cbindgen::Config::from_file("cbindgen-java.toml").unwrap())
        .with_include_guard("JAVA_BRIDGE_H")
        .generate()
        .expect("Unable to generate java_bridge.h")
        .write_to_file(JAVA_HEADER);
}
