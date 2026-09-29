#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
UPSTREAM="$ROOT/sable-upstream"

test -f "$UPSTREAM/common/src/main/java/dev/ryanhcode/sable/mixin/udp/ConnectionMixin.java"
test -f "$UPSTREAM/common/src/main/java/dev/ryanhcode/sable/network/packets/tcp/ClientboundSableUDPActivationPacket.java"

if grep -q "channelFuture.syncUninterruptibly()" "$UPSTREAM/common/src/main/java/dev/ryanhcode/sable/mixin/udp/ConnectionMixin.java"; then
  echo "ERROR: blocking UDP bootstrap still present" >&2
  exit 1
fi

grep -q "awaitUninterruptibly" "$UPSTREAM/common/src/main/java/dev/ryanhcode/sable/mixin/udp/ConnectionMixin.java"
grep -q "channel == null" "$UPSTREAM/common/src/main/java/dev/ryanhcode/sable/network/packets/tcp/ClientboundSableUDPActivationPacket.java"
echo "Saddle source verification passed."