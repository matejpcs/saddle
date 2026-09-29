#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
OVERLAY="$ROOT/overlay"

test -f "$OVERLAY/common/src/main/java/dev/ryanhcode/sable/mixin/udp/ConnectionMixin.java"
test -f "$OVERLAY/common/src/main/java/dev/ryanhcode/sable/network/packets/tcp/ClientboundSableUDPActivationPacket.java"

if grep -q "syncUninterruptibly()" "$OVERLAY/common/src/main/java/dev/ryanhcode/sable/mixin/udp/ConnectionMixin.java"; then
  echo "ERROR: blocking UDP bootstrap still present in Saddle overlay" >&2
  exit 1
fi

grep -q "awaitUninterruptibly" "$OVERLAY/common/src/main/java/dev/ryanhcode/sable/mixin/udp/ConnectionMixin.java"
grep -q "channel == null" "$OVERLAY/common/src/main/java/dev/ryanhcode/sable/network/packets/tcp/ClientboundSableUDPActivationPacket.java"
echo "Saddle source verification passed."