#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "ERROR: $1" >&2
  exit 1
}

: "${RAPIER_VERSION:?RAPIER_VERSION is required}"
: "${PATCHED_RAPIER_PATH:?PATCHED_RAPIER_PATH is required}"
: "${RAPIER_PATCH_PATH:?RAPIER_PATCH_PATH is required}"

rapier_version="$RAPIER_VERSION"
patched_rapier_path="$PATCHED_RAPIER_PATH"
rapier_patch_path="$RAPIER_PATCH_PATH"

echo "Preparing patched Rapier: version=$rapier_version, final=$patched_rapier_path"

registry_root="${CARGO_HOME:-$HOME/.cargo}/registry/src"
echo "Using cargo registry root: $registry_root"

find_rapier_source() {
  find "$registry_root" -type d -name "rapier3d-$rapier_version" | sort | head -n 1
}

base_dir="$(find_rapier_source || true)"

if [[ -z "${base_dir:-}" ]]; then
  echo "rapier3d-$rapier_version not found; running cargo fetch..."
  fetch_dir="$(dirname "$patched_rapier_path")/_fetch"
  mkdir -p "$fetch_dir/src"
  cat > "$fetch_dir/Cargo.toml" <<EOF
[package]
name = "impulse-rapier-fetch"
version = "0.0.0"
edition = "2021"
publish = false

[dependencies]
rapier3d = { version = "=$rapier_version", default-features = false, features = ["dim3", "f32", "parallel", "profiler"] }
EOF
  echo 'pub fn _fetch_marker() {}' > "$fetch_dir/src/lib.rs"
  cargo fetch --manifest-path "$fetch_dir/Cargo.toml"
  base_dir="$(find_rapier_source || true)"
fi

[[ -n "${base_dir:-}" ]] || fail "Unable to locate rapier3d-$rapier_version in registry after fetch."

echo "Found rapier source at: $base_dir"

patched_parent="$(dirname "$patched_rapier_path")"
tmp_dir="$patched_parent/_tmp_$(date +%s%N)"
mkdir -p "$tmp_dir"

cleanup() {
  rm -rf "$tmp_dir"
}
trap cleanup EXIT

echo "Copying source to temp..."
cp -a "$base_dir"/. "$tmp_dir"/

echo "Applying patch..."
if command -v git >/dev/null 2>&1; then
  git -C "$tmp_dir" apply "$rapier_patch_path"
elif command -v patch >/dev/null 2>&1; then
  patch -d "$tmp_dir" -p1 -i "$rapier_patch_path"
else
  fail "Neither git nor patch found in PATH."
fi

[[ -f "$tmp_dir/Cargo.toml" ]] || fail "Cargo.toml missing in staged copy; aborting."

rm -rf "$patched_rapier_path"
mv "$tmp_dir" "$patched_rapier_path"
trap - EXIT
echo "Prepared patched rapier at: $patched_rapier_path"