package dev.ryanhcode.sable.network.packets.udp;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.udp.ConnectionExtension;
import dev.ryanhcode.sable.network.udp.AddressedSableUDPPacket;
import dev.ryanhcode.sable.network.udp.SableUDPAddress;
import dev.ryanhcode.sable.network.udp.SableUDPPacket;
import dev.ryanhcode.sable.network.udp.SableUDPPacketType;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.Level;

import java.net.InetSocketAddress;

public record SableUDPClientboundKeepAlivePacket() implements SableUDPPacket {
    public static final StreamCodec<RegistryFriendlyByteBuf, SableUDPClientboundKeepAlivePacket> CODEC = StreamCodec.of((buf, value) -> {}, buf -> new SableUDPClientboundKeepAlivePacket());

    @Override
    public SableUDPPacketType getType() {
        return SableUDPPacketType.KEEP_ALIVE_CLIENTBOUND;
    }

    @Override
    public void handleClient(final Level level) {
        if (Minecraft.getInstance().getConnection() == null) {
            return;
        }

        final Connection connection = Minecraft.getInstance().getConnection().getConnection();
        final Channel channel = ((ConnectionExtension) connection).sable$getUDPChannel();
        if (channel == null || !channel.isActive()) {
            Sable.LOGGER.debug("[sable-udp] Dropping keep-alive response because the UDP channel is inactive");
            return;
        }

        if (!(connection.getRemoteAddress() instanceof final InetSocketAddress minecraftEndpoint)) {
            return;
        }

        final InetSocketAddress udpEndpoint = SableUDPAddress.clientEndpoint(minecraftEndpoint);
        channel.eventLoop().execute(() -> {
            final AddressedSableUDPPacket envelope = new AddressedSableUDPPacket(new SableUDPServerboundAlivePacket(), udpEndpoint);
            final ChannelFuture writeFuture = channel.writeAndFlush(envelope);
            writeFuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        });
    }
}
