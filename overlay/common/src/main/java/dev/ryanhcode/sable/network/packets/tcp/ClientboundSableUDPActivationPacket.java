package dev.ryanhcode.sable.network.packets.tcp;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableClientConfig;
import dev.ryanhcode.sable.mixinterface.udp.ConnectionExtension;
import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import foundry.veil.api.network.handler.PacketContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record ClientboundSableUDPActivationPacket(UUID uuid) implements SableTCPPacket {

    public static final Type<ClientboundSableUDPActivationPacket> TYPE = new Type<>(Sable.sablePath("udp_activation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSableUDPActivationPacket> CODEC = StreamCodec.of((buf, value) -> value.write(buf), ClientboundSableUDPActivationPacket::read);

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
        if (!SableClientConfig.ATTEMPT_UDP_NETWORKING.get()) {
            Sable.LOGGER.info("Received UDP authentication request, ignoring because client UDP networking is disabled");
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            Sable.LOGGER.warn("[sable-udp] Received UDP activation without an active Minecraft connection; remaining on TCP-only");
            return;
        }

        final Connection connection = minecraft.getConnection().getConnection();
        ((ConnectionExtension) connection).sable$queueUDPAuthentication(this.uuid);
    }
}
