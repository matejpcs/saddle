package dev.ryanhcode.sable.network.udp.handler;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableClient;
import dev.ryanhcode.sable.mixinterface.udp.ConnectionExtension;
import dev.ryanhcode.sable.network.udp.AddressedSableUDPPacket;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;

public final class SableUDPChannelHandlerClient extends SimpleChannelInboundHandler<AddressedSableUDPPacket> {
    private final Connection connection;
    private Channel channel;

    public SableUDPChannelHandlerClient(final Connection connection) {
        super(AddressedSableUDPPacket.class);
        this.connection = connection;
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
        Sable.LOGGER.warn("[saddle-udp] UDP channel exception local={} remote={}",
                ctx.channel().localAddress(), ctx.channel().remoteAddress(), cause);
        ctx.close();
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        this.channel = ctx.channel();
        ((ConnectionExtension) this.connection).sable$setUDPChannel(this.channel);
        Sable.LOGGER.debug("[saddle-udp] UDP channel active id={} local={} remote={}",
                ctx.channel().id(), ctx.channel().localAddress(), ctx.channel().remoteAddress());
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        if (((ConnectionExtension) this.connection).sable$getUDPChannel() == this.channel) {
            ((ConnectionExtension) this.connection).sable$setUDPChannel(null);
        }
        Sable.LOGGER.warn("[saddle-udp] UDP channel inactive; TCP connection remains authoritative");
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final AddressedSableUDPPacket msg) {
        final Minecraft client = Minecraft.getInstance();
        SableClient.NETWORK_EVENT_LOOP.tell(() -> {
            if (client.level != null) {
                msg.packet().handleClient(client.level);
            }
        });
    }
}
