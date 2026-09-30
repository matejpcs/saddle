#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
OVERLAY="$ROOT/overlay"

test -f "$OVERLAY/common/src/main/java/dev/ryanhcode/sable/mixin/udp/ConnectionMixin.java"
test -f "$OVERLAY/common/src/main/java/dev/ryanhcode/sable/network/packets/tcp/ClientboundSableUDPActivationPacket.java"
test -f "$OVERLAY/common/src/main/java/dev/ryanhcode/sable/network/udp/SableUDPAddress.java"
test -f "$OVERLAY/common/src/main/java/dev/ryanhcode/sable/SableClientConfig.java"
test -f "$OVERLAY/common/src/main/java/dev/ryanhcode/sable/SableConfig.java"

if grep -Eq "syncUninterruptibly\(|awaitUninterruptibly\(" "$OVERLAY/common/src/main/java/dev/ryanhcode/sable/mixin/udp/ConnectionMixin.java"; then
  echo "ERROR: blocking UDP client operation still present in Saddle overlay" >&2
  exit 1
fi

grep -q "minecraft.getConnection() == null" "$OVERLAY/common/src/main/java/dev/ryanhcode/sable/network/packets/tcp/ClientboundSableUDPActivationPacket.java"
grep -q "udp_connect_timeout_seconds" "$OVERLAY/common/src/main/java/dev/ryanhcode/sable/SableClientConfig.java"
grep -q "defineInRange(\"udp_listen_port\"" "$OVERLAY/common/src/main/java/dev/ryanhcode/sable/SableConfig.java"
grep -q "sable\$queueUDPAuthentication" "$OVERLAY/common/src/main/java/dev/ryanhcode/sable/mixinterface/udp/ConnectionExtension.java"
echo "Saddle source verification passed."
