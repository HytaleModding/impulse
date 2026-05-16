#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CRUCIBLE_VERSION="${CRUCIBLE_VERSION:-1.0.0}"
SERVER_VERSION="${1:-}"

if [[ -z "${SERVER_VERSION}" ]]; then
    SERVER_VERSION="$(awk -F= '$1 == "hytale_version" { print $2 }' "${ROOT_DIR}/gradle.properties")"
fi

CRUCIBLE_CACHE_DIR="${HOME}/.gradle/caches/modules-2/files-2.1/com.ionforgelabs/crucible/${CRUCIBLE_VERSION}"
SOURCE_JAR="$(find "${CRUCIBLE_CACHE_DIR}" -name "crucible-${CRUCIBLE_VERSION}.jar" -type f | head -n 1)"

if [[ -z "${SOURCE_JAR}" ]]; then
    echo "Could not find Crucible ${CRUCIBLE_VERSION} in Gradle cache." >&2
    echo "Run ./gradlew :impulse-examples:dependencies --configuration compileClasspath first." >&2
    exit 1
fi

mkdir -p "${ROOT_DIR}/run/mods"

python3 - "${SOURCE_JAR}" "${ROOT_DIR}/run/mods/crucible-${CRUCIBLE_VERSION}.jar" "${SERVER_VERSION}" <<'PY'
import json
import sys
import zipfile
from pathlib import Path

source = Path(sys.argv[1])
target = Path(sys.argv[2])
server_version = sys.argv[3]

with zipfile.ZipFile(source, "r") as src:
    manifest = json.loads(src.read("manifest.json"))
    manifest["ServerVersion"] = server_version

    with zipfile.ZipFile(target, "w") as dst:
        for info in src.infolist():
            if info.filename == "manifest.json":
                continue
            dst.writestr(info, src.read(info.filename))

        dst.writestr(
            "manifest.json",
            json.dumps(manifest, indent=2).encode("utf-8") + b"\n",
        )
PY

echo "Installed patched Crucible ${CRUCIBLE_VERSION} for Hytale ${SERVER_VERSION}:"
echo "${ROOT_DIR}/run/mods/crucible-${CRUCIBLE_VERSION}.jar"
