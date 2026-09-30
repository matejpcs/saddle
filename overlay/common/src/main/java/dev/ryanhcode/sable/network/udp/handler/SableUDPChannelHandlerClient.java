package dev.ryanhcode.sable.network.udp.handler;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableClient;
import dev.ryanhcode.sable.mixinterface.udp.ConnectionExtension;
import dev.ryanhcode.sable.network.packets.udp.SableUDPAuthenticationPacket;
import dev.ryanhcode.sable.network.udp.AddressedSableUDPPacket;
import dev.ryanhcode.sable.network.udp.SableUDPAddress;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.Connection;

import java.net.InetSocketAddress;
import java.util.UUID;

public class SableUDPChannelHandlerClient extends SimpleChannelInboundHandler<AddressedSableUDPPacket> {

    private final Connection connection;
    private Channel channel;
    private UUID pendingAuthentication;

    public SableUDPChannelHandlerClient(final Connection connection) {
        super(AddressedSableUDPPacket.class);
        this.connection = connection;
    }

    public void requestAuthentication(final UUID token) {
        this.pendingAuthentication = token;
        final Channel activeChannel = this.channel;
        if (activeChannel == null || !activeChannel.isActive()) {
            return;
        }

        activeChannel.eventLoop().execute(() -> {
            if (this.pendingAuthentication == null || !activeChannel.isActive()) {
                return;
            }

            final UUID pendingToken = this.pendingAuthentication;
            this.pendingAuthentication = null;
            this.sendAuthentication(activeChannel, pendingToken);
        });
    }

    private void sendAuthentication(final Channel activeChannel, final UUID token) {
        if (!(this.connection.getRemoteAddress() instanceof final InetSocketAddress minecraftEndpoint)) {
            Sable.LOGGER.warn("[sable-udp] Cannot authenticate: TCP remote address is not an InetSocketAddress ({})", this.connection.getRemoteAddress());
            return;
        }

        final InetSocketAddress udpEndpoint = SableUDPAddress.clientEndpoint(minecraftEndpoint);
        Sable.LOGGER.info("Sending UDP authentication to {}", udpEndpoint);
        final AddressedSableUDPPacket envelope = new AddressedSableUDPPacket(new SableUDPAuthenticationPacket(token.toString()), udpEndpoint);
        final ChannelFuture writeFuture = activeChannel.writeAndFlush(envelope);
        writeFuture.addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                Sable.LOGGER.warn("[sable-udp] Failed to send UDP auth response to {}", udpEndpoint, future.cause());
            }
        });
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        Sable.LOGGER.error("UDP channel exception caught (local={}, remote={})",
                ctx.channel().localAddress(), ctx.channel().remoteAddress(), cause);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        Sable.LOGGER.info("Client UDP channel active");
        Sable.LOGGER.debug("[sable-udp] channel id={}, local={}, remote={}",
                ctx.channel().id(), ctx.channel().localAddress(), ctx.channel().remoteAddress());

        this.channel = ctx.channel();
        ((ConnectionExtension) this.connection).sable$setUDPChannel(this.channel);

        if (this.pendingAuthentication != null) {
            this.requestAuthentication(this.pendingAuthentication);
        }
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        Sable.LOGGER.info("Client UDP channel inactive");

        final ConnectionExtension connectionExtension = (ConnectionExtension) this.connection;
        if (connectionExtension.sable$getUDPChannel() == ctx.channel()) {
            connectionExtension.sable$setUDPChannel(null);
        }
        if (this.channel == ctx.channel()) {
            this.channel = null;
        }
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final AddressedSableUDPPacket msg) throws Exception {
        final Minecraft client = Minecraft.getInstance();
        final ClientLevel level = client.level;

        if (level == null) {
            Sable.LOGGER.debug("[sable-udp] Dropping UDP packet while the client world is not ready: {}", msg.packet().getClass().getName());
            return;
        }

        SableClient.NETWORK_EVENT_LOOP.tell(() -> msg.packet().handleClient(level));
    }
}
