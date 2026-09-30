package dev.ryanhcode.sable.network.udp;

import dev.ryanhcode.sable.SableClientConfig;

import java.net.InetSocketAddress;

/** Resolves the client-side UDP destination without doing synchronous DNS work. */
public final class SableUDPAddress {

    private SableUDPAddress() {
    }

    public static InetSocketAddress clientEndpoint(final InetSocketAddress minecraftEndpoint) {
        final String configuredHost = SableClientConfig.UDP_SERVER_ADDRESS.get().trim();
        final int configuredPort = SableClientConfig.UDP_SERVER_PORT.get();
        final int port = configuredPort < 0 ? minecraftEndpoint.getPort() : configuredPort;

        if (configuredHost.isEmpty()) {
            if (configuredPort < 0) {
                return minecraftEndpoint;
            }
            return InetSocketAddress.createUnresolved(minecraftEndpoint.getHostString(), port);
        }

        // Bootstrap.connect(SocketAddress) resolves this asynchronously in Netty's resolver.
        return InetSocketAddress.createUnresolved(configuredHost, port);
    }
}
