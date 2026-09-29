# Saddle

Saddle is a compatibility-focused rework of the official Sable library for Minecraft NeoForge 1.21.1.

## Current implementation

Saddle keeps Sable's Java package/API surface and mod ID (`sable`) while changing the networking bootstrap behavior. The upstream Sable source is pinned as `sable-upstream/` and Saddle-owned replacements live under `overlay/`.

### The demonstrated join failure

The upstream `ConnectionMixin` synchronously waited for the UDP `Bootstrap.connect()` future with `syncUninterruptibly()`. That operation runs on the Minecraft connection path. Sable upstream PR #1141 documents that a Windows socket/filter/VPN stack can stall that UDP connect for 20–30+ seconds, long enough for the Minecraft TCP login/configuration connection to time out.

Saddle replaces that unbounded wait with a 5-second bounded wait. If UDP is slow, fails, or throws:

UDP bootstrap -> bounded wait -> TCP remains authoritative -> player continues joining

UDP is therefore an optimization rather than a prerequisite for establishing the Minecraft connection.

## Changes in this revision

- Client UDP bootstrap no longer blocks indefinitely.
- `disable_udp_pipeline` is honored client-side.
- UDP bootstrap exceptions cannot abort the Minecraft login path.
- A late UDP connection is closed instead of being attached after the login path has moved on.
- UDP activation safely handles a missing/inactive channel.
- UDP channel loss clears the connection's channel reference.
- UDP packet handling does not call into a missing client level.
- The same NeoForge artifact is intended for client and dedicated server use.
- Sable's existing API/package names remain intact for Aeronautics and other Sable integrations.

## Netflared

The inspected `matejpcs/netflared-server` implementation exposes Minecraft through a Cloudflare Tunnel using `tcp://localhost:<minecraft-port>`. Its companion client is also TCP-oriented. It is therefore incorrect to assume that Netflared provides transparent UDP.

Saddle treats this as a supported degraded transport:

TCP Minecraft connection -> Sable UDP unavailable -> TCP fallback

The player is not disconnected solely because the optional UDP path cannot be established.

## Compatibility

- Minecraft 1.21.1
- NeoForge 21.1.x
- Sable API/package compatibility
- Aeronautics/Sable-dependent integrations where they use the preserved API
- Dedicated servers
- Client installations
- TCP-only tunnel environments such as the inspected Netflared setup

## Build

Initialize the pinned upstream source:

    git submodule update --init --recursive

Build:

    bash scripts/build.sh

Verify the networking overlay:

    bash scripts/verify.sh

The CI workflow performs the same source checks, builds Sable's Rust natives, builds the NeoForge artifact, verifies the NeoForge metadata, and uploads the resulting JAR.

Expected artifact:

    dist/saddle-neoforge-1.21.1-2.0.5-saddle.1.jar

## Architecture

    Saddle repository
    ├── sable-upstream/       pinned official Sable source/build
    ├── overlay/              Saddle-owned source replacements
    ├── scripts/              reproducible overlay/build checks
    └── .github/workflows/    CI verification

This structure makes upstream updates auditable: the exact Sable commit is recorded by the submodule, while the networking changes are visible as ordinary Saddle files.

## Important limitation

This revision fixes the proven UDP bootstrap/login failure and the related null-channel failure. It does not honestly claim that the entire larger networking redesign is complete yet. A full new UDP wire protocol with sequence numbers, acknowledgements, fragmentation/reassembly, retransmission windows, resumable synchronization, and packet-loss/reordering test harnesses still requires a larger coordinated client/server protocol change.

Those features should be added and validated before a release is advertised as the complete networking rewrite.

## Upstream evidence

- Official Sable: https://github.com/ryanhcode/sable
- Saddle: https://github.com/matejpcs/saddle
- Sable UDP bootstrap fix PR: https://github.com/ryanhcode/sable/pull/1141
- Sable UDP decode issue: https://github.com/ryanhcode/sable/issues/797

## Licensing

Sable upstream remains governed by its upstream license. Saddle-owned files remain governed by the license in this repository. The upstream license text is retained in the submodule.