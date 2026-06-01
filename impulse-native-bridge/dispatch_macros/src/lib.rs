//! Procedural macros for generating FFI dispatchers from enum definitions.
//!
//! `auto_dispatch_from_enum` reads a Rust source file that contains `extern "C"`
//! declarations, matches those declarations against enum variants, and generates a
//! dispatcher that loads arguments from a raw `u64` buffer.

use proc_macro::TokenStream;
use quote::{format_ident, quote};
use syn::parse::{Parse, ParseStream};
use syn::{parse_macro_input, Expr, ExprLit, ItemEnum, Lit, LitStr, Token};

use std::collections::HashMap;
use std::path::PathBuf;

#[proc_macro_attribute]
/// Generates a subcommand dispatcher from an enum and a matching FFI source file.
///
/// The macro expects two attribute arguments:
///
/// - `path`: a path to a Rust source file that contains the target `extern "C"`
///   declarations, relative to the current crate's manifest directory.
/// - `prefix`: an optional function-name prefix. When omitted, the macro uses
///   `"eng_"`.
///
/// Each enum variant must have an explicit integer discriminant. The macro converts
/// the variant name from `CamelCase` to `snake_case`, prepends the prefix, and looks
/// for a matching foreign function in the target file. For example, `GetGravity = 1`
/// maps to `eng_space_get_gravity` when the prefix is `"eng_space_"`.
///
/// The generated code keeps the original enum and adds an `execute_subcommand`
/// function with this shape:
///
/// ```text
/// pub fn execute_subcommand(subcommand_type: u32, buffer_ptr: *mut u64, offset: usize) -> usize
/// ```
///
/// At runtime, the dispatcher:
///
/// - matches on the enum discriminant value,
/// - reads each argument from `buffer_ptr.add(offset)` using unaligned loads,
/// - calls the matching FFI function,
/// - writes any returned value back to `buffer_ptr`, and
/// - returns the number of 64-bit slots consumed by the call.
///
/// # Example
///
/// ```ignore
/// use ffi_dispatch_macros::auto_dispatch_from_enum;
///
/// #[repr(u32)]
/// #[auto_dispatch_from_enum("src/ffi_engine.rs", "eng_space_")]
/// enum SpaceCommand {
///     Step = 0,
///     GetGravity = 1,
/// }
/// ```
///
/// The referenced FFI file must contain matching declarations such as:
///
/// ```text
/// extern "C" {
///     pub fn eng_space_step(space_id: u32, steps: f32);
///     pub fn eng_space_get_gravity(space_id: u32) -> Vector3f;
/// }
/// ```
pub fn auto_dispatch_from_enum(attr: TokenStream, item: TokenStream) -> TokenStream {
    // The attribute accepts exactly two string literals:
    // the relative path to the FFI source file and the function-name prefix.
    struct AttrArgs {
        path: LitStr,
        prefix: LitStr,
    }

    impl Parse for AttrArgs {
        // Read those two literals from the attribute input in order.
        fn parse(input: ParseStream) -> syn::Result<Self> {
            let path: LitStr = input.parse()?;
            let _comma: Token![,] = input.parse()?;
            let prefix = input.parse()?;
            Ok(AttrArgs { path, prefix })
        }
    }

    // Turn the raw attribute tokens into structured values.
    let attr = parse_macro_input!(attr as AttrArgs);
    let file_path = attr.path.value();
    let prefix = attr.prefix.value();

    // Parse the enum that the macro is attached to so we can inspect its
    // variants and also reproduce the original enum in the generated output.
    let input_enum = parse_macro_input!(item as ItemEnum);
    let enum_ident = &input_enum.ident;

    // Keep the original enum in the expanded code.  The enum itself is never
    // referenced by name in the generated dispatcher (match arms use integer
    // literals), so suppress the dead_code lint that would otherwise fire.
    let enum_tokens = quote! {
        #[allow(dead_code)]
        #input_enum
    };

    // Store each variant name together with its explicit discriminant value.
    // The generated dispatcher matches on those numeric values directly.
    let mut enum_variants = Vec::<(syn::Ident, u32)>::new();

    for variant in input_enum.variants.iter() {
        let ident = variant.ident.clone();

        // Accept only explicit integer discriminants like `Step = 0`.
        // Variants without a value would not give us a stable dispatch key.
        let discr = variant.discriminant.as_ref().and_then(|(_, expr)| {
            if let Expr::Lit(ExprLit {
                lit: Lit::Int(lit_int),
                ..
            }) = expr
            {
                lit_int.base10_parse().ok()
            } else {
                None
            }
        });

        if let Some(val) = discr {
            enum_variants.push((ident, val));
        } else {
            return syn::Error::new_spanned(
                &ident,
                "Each enum variant must have an explicit integer discriminant.",
            )
            .to_compile_error()
            .into();
        }
    }

    // Resolve the FFI file relative to the crate that uses the macro.
    let manifest_dir = std::env::var("CARGO_MANIFEST_DIR").unwrap_or_else(|_| ".".to_string());
    let ffi_path = PathBuf::from(&manifest_dir).join(file_path);
    let ffi_src = match std::fs::read_to_string(&ffi_path) {
        Ok(val) => val,
        Err(err) => {
            return syn::Error::new_spanned(
                enum_ident,
                format!("Failed to read ffi file {}: {}", ffi_path.display(), err),
            )
            .to_compile_error()
            .into();
        }
    };

    // Parse the external declarations so we can inspect every foreign function
    // signature declared in the file.
    let ffi_parsed = match syn::parse_file(&ffi_src) {
        Ok(val) => val,
        Err(err) => {
            return syn::Error::new_spanned(
                enum_ident,
                format!("Failed to parse ffi file {}: {}", ffi_path.display(), err),
            )
            .to_compile_error()
            .into();
        }
    };

    // Build a lookup table from function name to signature for quick matching.
    let mut ffi_map: HashMap<String, syn::Signature> = HashMap::new();

    // Walk the top-level items and keep only `extern "C" { ... }` blocks.
    for item in ffi_parsed.items {
        if let syn::Item::ForeignMod(fm) = item {
            // Each foreign module item is a single declared foreign symbol.
            for foreign in fm.items {
                if let syn::ForeignItem::Fn(f) = foreign {
                    ffi_map.insert(f.sig.ident.to_string(), f.sig);
                }
            }
        }
    }

    // Convert enum variant names like `GetGravity` into `get_gravity` so they
    // can be matched against the FFI naming convention.
    fn camel_to_snake(name: &str) -> String {
        let mut out = String::new();
        for (i, ch) in name.chars().enumerate() {
            if ch.is_uppercase() {
                if i != 0 {
                    out.push('_');
                }
                for lower in ch.to_lowercase() {
                    out.push(lower);
                }
            } else {
                out.push(ch);
            }
        }
        out
    }

    // Build one `match` arm per enum variant.
    let mut match_arms_token = Vec::new();
    for (variant_ident, discr) in enum_variants.iter() {
        let variant_name = variant_ident.to_string();
        let snake_name = camel_to_snake(&variant_name);
        // Combine the prefix and variant name to form the expected foreign symbol.
        let fn_name = prefix.clone() + &snake_name;

        // Look up the matching declaration in the parsed FFI file.
        let fn_sign = match ffi_map.get(&fn_name) {
            Some(val) => val,
            None => {
                return syn::Error::new_spanned(
                    variant_ident,
                    format!(
                        "Could not find function `{}` in {} (expected to match variant `{}`)",
                        fn_name,
                        ffi_path.display(),
                        variant_name
                    ),
                )
                .to_compile_error()
                .into();
            }
        };

        let mut read_stmts = Vec::new();
        let mut args_idents = Vec::new();

        // Translate each declared parameter into code that reads a value out of
        // the raw buffer.
        for (i, arg) in fn_sign.inputs.iter().enumerate() {
            // Skip receiver arguments; FFI declarations should only have plain
            // typed inputs.
            if let syn::FnArg::Typed(pat_type) = arg {
                // Not every Rust parameter is a single identifier. If the pattern
                // is more complex, synthesize a stable temporary name.
                let arg_ident = if let syn::Pat::Ident(pi) = &*pat_type.pat {
                    pi.ident.clone()
                } else {
                    format_ident!("arg{}", i)
                };

                // Capture the declared argument type so we can generate the read.
                let ty = &*pat_type.ty;

                args_idents.push(quote! {#arg_ident});

                // Read the value from the current byte offset, then advance by the
                // exact size of the argument.
                read_stmts.push(quote! {
                  let #arg_ident: #ty = read_struct::<#ty>(data_ptr, byte_offset);
                  byte_offset += bytes_of::<#ty>();
                });
            }
        }

        // Generate a fully qualified path to the bound foreign function.
        let ffi_ident = syn::Ident::new(&fn_name, proc_macro2::Span::call_site());

        // Generate the dispatch arm for this discriminant.
        // Calls are routed through the global engine singleton so that the
        // DLL has no static link dependency on the backend engine library.
        let arm = match &fn_sign.output {
            // If the foreign function returns nothing, just call it and report
            // how many 64-bit slots the packed payload consumed.
            syn::ReturnType::Default => {
                quote! {
                    #discr => {
                        unsafe {
                            // Start at the beginning of the packed argument block.
                            let mut byte_offset = 0usize;
                            #(#read_stmts)*
                            engine.#ffi_ident( #(#args_idents),* );

                            // Convert bytes consumed into the caller's slot-based
                            // unit so the outer loop can keep advancing correctly.
                            bytes_to_slots(byte_offset)
                        }
                    }
                }
            }
            // If the foreign function returns a value, write it back to the
            // front of the payload before returning the slot count.
            syn::ReturnType::Type(_, ty) => {
                quote! {
                    #discr => {
                        unsafe {
                            // The output buffer starts with the packed arguments.
                            let mut byte_offset = 0usize;
                            #(#read_stmts)*

                            let _result: #ty = engine.#ffi_ident( #(#args_idents),* );

                            // Store the return value at the start of the payload.
                            write_ret(data_ptr as *mut u8, 0, _result);

                            // Return how much of the slot-based buffer was consumed.
                            bytes_to_slots(byte_offset)
                        }
                    }
                }
            }
        };
        match_arms_token.push(arm);
    }

    // Assemble the final output: original enum plus generated helper functions
    // and the dispatcher entry point.
    let expanded = quote! {
        #enum_tokens

        // Size helper used to advance through the raw buffer in bytes.
        #[allow(dead_code)]
        #[inline]
        fn bytes_of<T>() -> usize { std::mem::size_of::<T>() }

        // Convert a byte count into the number of u64 slots consumed by the
        // packed payload. The caller advances the outer buffer in slots, while
        // this dispatcher reads the arguments densely in bytes.
        #[allow(dead_code)]
        #[inline]
        fn bytes_to_slots(bytes: usize) -> usize { (bytes + 7) / 8 }

        // Read a Copy value from the buffer without assuming alignment.
        #[allow(dead_code)]
        unsafe fn read_struct<T: Copy>(base: *const u8, byte_offset: usize) -> T {
            std::ptr::read_unaligned(base.add(byte_offset) as *const T)
        }

        // Write a Copy value back into the buffer without assuming alignment.
        #[allow(dead_code)]
        unsafe fn write_ret<T: Copy>(buffer_ptr: *mut u8, byte_offset: usize, value: T) {
            std::ptr::write_unaligned(buffer_ptr.add(byte_offset) as *mut T, value)
        }

        // Generated dispatcher that routes each enum discriminant to its FFI call.
        #[allow(dead_code)]
        pub fn execute_subcommand(engine: &Engine, subcommand_type: u32, buffer_ptr: *mut u64, offset: usize) -> usize {
            // The outer command buffer uses slot-based offsets; convert the slot
            // index into a byte pointer before reading the packed payload.
            let data_ptr = unsafe { (buffer_ptr as *mut u8).add(offset * 8) as *const u8 };
            match subcommand_type {
                #(#match_arms_token)*
                _ => panic!("unknown subcommand type: {}", subcommand_type),
            }
        }
    };

    expanded.into()
}
