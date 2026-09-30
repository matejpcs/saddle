#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
UPSTREAM="$ROOT/sable-upstream"
OVERLAY="$ROOT/overlay"

if [[ ! -f "$UPSTREAM/settings.gradle" ]]; then
  echo "Saddle: initialize the Sable submodule first:"
  echo "  git submodule update --init --recursive"
  exit 1
fi

copy_overlay() {
  local source="$1"
  local target="$2"
  mkdir -p "$(dirname "$UPSTREAM/$target")"
  cp "$OVERLAY/$source" "$UPSTREAM/$target"
}

copy_overlay common/src/main/java/dev/ryanhcode/sable/mixin/udp/ConnectionMixin.java common/src/main/java/dev/ryanhcode/sable/mixin/udp/ConnectionMixin.java
copy_overlay common/src/main/java/dev/ryanhcode/sable/mixinterface/udp/ConnectionExtension.java common/src/main/java/dev/ryanhcode/sable/mixinterface/udp/ConnectionExtension.java
copy_overlay common/src/main/java/dev/ryanhcode/sable/mixin/udp/ServerConnectionListenerMixin.java common/src/main/java/dev/ryanhcode/sable/mixin/udp/ServerConnectionListenerMixin.java
copy_overlay common/src/main/java/dev/ryanhcode/sable/SableConfig.java common/src/main/java/dev/ryanhcode/sable/SableConfig.java
copy_overlay common/src/main/java/dev/ryanhcode/sable/SableClientConfig.java common/src/main/java/dev/ryanhcode/sable/SableClientConfig.java
copy_overlay common/src/main/java/dev/ryanhcode/sable/network/udp/SableUDPAddress.java common/src/main/java/dev/ryanhcode/sable/network/udp/SableUDPAddress.java
copy_overlay common/src/main/java/dev/ryanhcode/sable/network/packets/tcp/ClientboundSableUDPActivationPacket.java common/src/main/java/dev/ryanhcode/sable/network/packets/tcp/ClientboundSableUDPActivationPacket.java
copy_overlay common/src/main/java/dev/ryanhcode/sable/network/packets/udp/SableUDPClientboundKeepAlivePacket.java common/src/main/java/dev/ryanhcode/sable/network/packets/udp/SableUDPClientboundKeepAlivePacket.java
copy_overlay common/src/main/java/dev/ryanhcode/sable/network/udp/handler/SableUDPChannelHandlerClient.java common/src/main/java/dev/ryanhcode/sable/network/udp/handler/SableUDPChannelHandlerClient.java

python3 - "$UPSTREAM/gradle.properties" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text()
replacements = {
    "version=2.0.5": "version=2.0.5-saddle.1",
    "mod_name=Sable": "mod_name=Saddle",
    "mod_author=RyanHCode": "mod_author=JustMatyys",
    "description=Interactive moving block structures with physics.": "description=Compatibility-focused Sable rework with resilient UDP networking for NeoForge 1.21.1.",
    "issues=https://github.com/ryanhcode/sable/issues": "issues=https://github.com/matejpcs/saddle/issues",
}
for a, b in replacements.items():
    if a not in s:
        raise SystemExit(f"missing expected upstream property: {a}")
    s = s.replace(a, b, 1)
p.write_text(s)
PY

echo "Saddle: applying networking overlay"
cd "$UPSTREAM"
if [[ ! -f "sable_rapier/src/main/resources/natives/sable_rapier/sable_rapier_binaries.zip.l4z" ]]; then
  echo "Saddle: upstream native bundle is missing; refusing to build a broken JAR" >&2
  exit 1
fi

./gradlew --no-daemon :neoforge:build

JAR="$(find neoforge/build/libs -maxdepth 1 -type f -name "*.jar" ! -name "*sources*" ! -name "*dev*" | head -n 1)"
if [[ -z "$JAR" ]]; then
  echo "Saddle: no production NeoForge JAR was produced" >&2
  exit 1
fi

mkdir -p "$ROOT/dist"
OUT="$ROOT/dist/saddle-neoforge-1.21.1-2.0.5-saddle.1.jar"
cp "$JAR" "$OUT"
echo "Saddle: produced $OUT"
