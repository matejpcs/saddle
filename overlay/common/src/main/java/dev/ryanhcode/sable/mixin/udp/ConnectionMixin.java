package dev.ryanhcode.sable.mixin.udp;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableClient;
import dev.ryanhcode.sable.SableClientConfig;
import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.mixinterface.udp.ConnectionExtension;
import dev.ryanhcode.sable.network.udp.SableUDPAddress;
import dev.ryanhcode.sable.network.udp.SableUDPPacket;
import dev.ryanhcode.sable.network.udp.handler.SableUDPChannelHandlerClient;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Mixin(Connection.class)
public abstract class ConnectionMixin implements ConnectionExtension {

    @Unique
    private Channel sable$udpChannel = null;

    @Unique
    private UUID sable$pendingUDPAuthentication = null;

    @Override
    public void sable$setUDPChannel(final Channel channel) {
        this.sable$udpChannel = channel;

        final UUID pendingToken = this.sable$pendingUDPAuthentication;
        if (channel != null && pendingToken != null) {
            final SableUDPChannelHandlerClient handler = channel.pipeline().get(SableUDPChannelHandlerClient.class);
            if (handler != null) {
                this.sable$pendingUDPAuthentication = null;
                handler.requestAuthentication(pendingToken);
            }
        }
    }

    @Override
    public void sable$queueUDPAuthentication(final UUID token) {
        final Channel channel = this.sable$udpChannel;
        if (channel == null || !channel.isActive()) {
            this.sable$pendingUDPAuthentication = token;
            return;
        }

        final SableUDPChannelHandlerClient handler = channel.pipeline().get(SableUDPChannelHandlerClient.class);
        if (handler == null) {
            this.sable$pendingUDPAuthentication = token;
            return;
        }

        handler.requestAuthentication(token);
    }

    @Inject(method = "disconnect(Lnet/minecraft/network/DisconnectionDetails;)V", at = @At("TAIL"))
    private void sable$onDisconnect(final DisconnectionDetails disconnectionDetails, final CallbackInfo ci) {
        final Channel channel = this.sable$udpChannel;
        this.sable$udpChannel = null;
        this.sable$pendingUDPAuthentication = null;

        if (channel == null || !channel.isOpen()) {
            return;
        }

        Sable.LOGGER.debug("[sable-udp] closing UDP channel on disconnect, reason={}",
                disconnectionDetails != null ? disconnectionDetails.reason().getString() : "<no details>");
        channel.close().addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                Sable.LOGGER.info("Closed UDP channel");
            } else {
                Sable.LOGGER.warn("Failed to close UDP channel", future.cause());
            }
        });
    }

    @Override
    public Channel sable$getUDPChannel() {
        return this.sable$udpChannel;
    }

    @Inject(method = "connect", at = @At("TAIL"))
    private static void sable$connect(final InetSocketAddress inetSocketAddress, final boolean bl, final Connection connection, final CallbackInfoReturnable<ChannelFuture> cir) {
        if (SableConfig.DISABLE_UDP_PIPELINE.get() || !SableClientConfig.ATTEMPT_UDP_NETWORKING.get()) {
            Sable.LOGGER.debug("[sable-udp] client UDP pipeline disabled; skipping bootstrap for {}", inetSocketAddress);
            return;
        }

        final boolean useNativeTransport = SableClient.useNativeTransport();
        final Class<? extends Channel> channelClass;
        final EventLoopGroup eventLoopGroup;

        if (Epoll.isAvailable() && useNativeTransport) {
            channelClass = EpollDatagramChannel.class;
            eventLoopGroup = Connection.NETWORK_EPOLL_WORKER_GROUP.get();
        } else {
            channelClass = NioDatagramChannel.class;
            eventLoopGroup = Connection.NETWORK_WORKER_GROUP.get();
        }

        final InetSocketAddress udpEndpoint = SableUDPAddress.clientEndpoint(inetSocketAddress);
        Sable.LOGGER.info("Starting asynchronous remote client UDP channel (minecraft={}, udp={}, transport={})",
                inetSocketAddress, udpEndpoint, channelClass.getSimpleName());

        final ChannelFuture channelFuture;
        try {
            channelFuture = new Bootstrap().group(eventLoopGroup).handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(final Channel channel) {
                            channel.config().setOption(ChannelOption.SO_KEEPALIVE, true);
                            SableUDPPacket.configureSerialization(channel.pipeline(), PacketFlow.CLIENTBOUND, false, null);
                            sable$setupChannel(channel, connection);
                        }
                    })
                    .channel(channelClass)
                    .connect(udpEndpoint);
        } catch (final Throwable t) {
            Sable.LOGGER.error("[sable-udp] Bootstrap.connect threw for {}; continuing without UDP", udpEndpoint, t);
            return;
        }

        channelFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                Sable.LOGGER.debug("[sable-udp] UDP connect succeeded for {}", udpEndpoint);
            } else if (!future.isCancelled()) {
                Sable.LOGGER.warn("[sable-udp] UDP connect failed for {}; continuing with TCP", udpEndpoint, future.cause());
            }
        });

        final long timeoutMs = TimeUnit.SECONDS.toMillis(SableClientConfig.UDP_CONNECT_TIMEOUT_SECONDS.get());
        channelFuture.channel().eventLoop().schedule(() -> {
            if (!channelFuture.isDone()) {
                Sable.LOGGER.warn("[sable-udp] UDP connect still pending after {} seconds for {}; cancelling without interrupting the TCP connection",
                        SableClientConfig.UDP_CONNECT_TIMEOUT_SECONDS.get(), udpEndpoint);
                channelFuture.cancel(false);
                channelFuture.channel().close();
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);
    }

    @Inject(method = "connectToLocalServer", at = @At("TAIL"))
    private static void sable$connectToLocalServer(final SocketAddress socketAddress, final CallbackInfoReturnable<Connection> cir, @Local final Connection connection) {
        if (SableConfig.DISABLE_UDP_PIPELINE.get() || !SableClientConfig.ATTEMPT_UDP_NETWORKING.get()) {
            return;
        }

        Sable.LOGGER.info("Starting asynchronous local client UDP channel");
        try {
            final ChannelFuture channelFuture = new Bootstrap().group(Connection.LOCAL_WORKER_GROUP.get()).handler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(final Channel channel) {
                    SableUDPPacket.configureInMemoryPipeline(channel.pipeline(), PacketFlow.CLIENTBOUND);
                    sable$setupChannel(channel, connection);
                }
            }).channel(LocalChannel.class).connect(socketAddress);

            channelFuture.addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    Sable.LOGGER.warn("[sable-udp] local UDP connect failed; continuing with TCP", future.cause());
                }
            });
        } catch (final Throwable t) {
            Sable.LOGGER.error("[sable-udp] local Bootstrap.connect threw; continuing without UDP", t);
        }
    }

    @Unique
    private static void sable$setupChannel(final Channel channel, final Connection connection) {
        channel.pipeline().addLast(new SableUDPChannelHandlerClient(connection));
    }
}
