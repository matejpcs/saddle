package dev.ryanhcode.sable.mixinterface.udp;

import io.netty.channel.Channel;

import java.util.UUID;

public interface ConnectionExtension {

    void sable$setUDPChannel(final Channel channel);

    Channel sable$getUDPChannel();

    void sable$queueUDPAuthentication(final UUID token);
}
