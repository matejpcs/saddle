package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.mixinterface.udp.ConnectionExtension;
import dev.ryanhcode.sable.network.packets.udp.SableUDPAuthenticationPacket;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.network.udp.AddressedSableUDPPacket;
import foundry.veil.api.network.handler.PacketContext;
import io.netty.channel.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.net.InetSocketAddress;
import java.util.UUID;

public record ClientboundSableUDPActivationPacket(UUID uuid) implements SableTCPPacket {
    public static final Type<ClientboundSableUDPActivationPacket> TYPE =
            new Type<>(Sable.sablePath("udp_activation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSableUDPActivationPacket> CODEC =
            StreamCodec.of((buf, value) -> value.write(buf), ClientboundSableUDPActivationPacket::read);

    private void write(final FriendlyByteBuf buf) {
        buf.writeUUID(this.uuid);
    }

    private static ClientboundSableUDPActivationPacket read(final FriendlyByteBuf buf) {
        return new ClientboundSableUDPActivationPacket(buf.readUUID());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public void handle(final PacketContext context) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            Sable.LOGGER.warn("[saddle-udp] UDP activation arrived without a client connection");
            return;
        }

        final Connection connection = minecraft.getConnection().getConnection();
        final Channel channel = ((ConnectionExtension) connection).sable$getUDPChannel();
        if (channel == null || !channel.isOpen()) {
            Sable.LOGGER.warn(
                    "[saddle-udp] UDP activation arrived but UDP is unavailable; staying TCP-only");
            return;
        }

        if (!(connection.getRemoteAddress() instanceof final InetSocketAddress remote)) {
            Sable.LOGGER.warn("[saddle-udp] UDP activation has unsupported remote address {}; staying TCP-only",
                    connection.getRemoteAddress());
            return;
        }

        final InetSocketAddress udpRemote = new InetSocketAddress(remote.getAddress(), remote.getPort());
        channel.eventLoop().execute(() -> {
            if (!channel.isActive()) {
                Sable.LOGGER.debug("[saddle-udp] UDP channel became inactive before authentication");
                return;
            }

            final Channel authChannel = channel;
            authChannel.writeAndFlush(new AddressedSableUDPPacket(
                    new SableUDPAuthenticationPacket(this.uuid.toString()), udpRemote))
                    .addListener(f -> {
                        if (!f.isSuccess()) {
                            Sable.LOGGER.warn("[saddle-udp] UDP authentication send failed to {}",
                                    udpRemote, f.cause());
                        }
                    });
        });
    }
}
