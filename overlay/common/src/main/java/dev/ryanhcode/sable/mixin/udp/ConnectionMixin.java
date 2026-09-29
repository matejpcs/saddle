package dev.ryanhcode.sable.mixin.udp;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableClient;
import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.mixinterface.udp.ConnectionExtension;
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
import java.util.concurrent.TimeUnit;

@Mixin(Connection.class)
public abstract class ConnectionMixin implements ConnectionExtension {
    @Unique
    private Channel sable$udpChannel;

    @Override
    public void sable$setUDPChannel(final Channel channel) {
        this.sable$udpChannel = channel;
    }

    @Inject(method = "disconnect(Lnet/minecraft/network/DisconnectionDetails;)V", at = @At("TAIL"))
    private void sable$onDisconnect(final DisconnectionDetails details, final CallbackInfo ci) {
        final Channel channel = this.sable$udpChannel;
        this.sable$udpChannel = null;
        if (channel != null && channel.isOpen()) {
            Sable.LOGGER.debug("[saddle-udp] closing UDP channel, reason={}",
                    details != null ? details.reason().getString() : "<no details>");
            channel.close().addListener(f -> {
                if (!f.isSuccess()) {
                    Sable.LOGGER.debug("[saddle-udp] UDP close failed", f.cause());
                }
            });
        }
    }

    @Override
    public Channel sable$getUDPChannel() {
        return this.sable$udpChannel;
    }

    @Unique
    private static final long SADDLE$UDP_CONNECT_TIMEOUT_MS = 5_000L;

    @Inject(method = "connect", at = @At("TAIL"))
    private static void saddle$connect(final InetSocketAddress remote, final boolean useNative,
                                       final Connection connection, final CallbackInfoReturnable<ChannelFuture> cir) {
        if (SableConfig.DISABLE_UDP_PIPELINE.get()) {
            Sable.LOGGER.debug("[saddle-udp] disabled by config; skipping bootstrap for {}", remote);
            return;
        }

        final long start = System.nanoTime();
        final Class<? extends Channel> channelClass;
        final EventLoopGroup group;
        if (Epoll.isAvailable() && SableClient.useNativeTransport()) {
            channelClass = EpollDatagramChannel.class;
            group = Connection.NETWORK_EPOLL_WORKER_GROUP.get();
        } else {
            channelClass = NioDatagramChannel.class;
            group = Connection.NETWORK_WORKER_GROUP.get();
        }

        final ChannelFuture future;
        try {
            future = new Bootstrap()
                    .group(group)
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(final Channel channel) {
                            SableUDPPacket.configureSerialization(
                                    channel.pipeline(), PacketFlow.CLIENTBOUND, false, null);
                            saddle$setupChannel(channel, connection);
                        }
                    })
                    .channel(channelClass)
                    .connect(remote.getAddress(), remote.getPort());
        } catch (final Throwable t) {
            Sable.LOGGER.warn("[saddle-udp] UDP bootstrap failed immediately for {}; continuing TCP-only", remote, t);
            return;
        }

        final boolean completed = future.awaitUninterruptibly(
                SADDLE$UDP_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        final long elapsed = (System.nanoTime() - start) / 1_000_000L;

        if (!completed) {
            Sable.LOGGER.warn(
                    "[saddle-udp] UDP bootstrap exceeded {} ms for {} ({} ms elapsed); cancelling and continuing TCP-only",
                    SADDLE$UDP_CONNECT_TIMEOUT_MS, remote, elapsed);
            future.cancel(true);
            future.addListener(f -> {
                if (f.isSuccess() && f.channel() != null) {
                    Sable.LOGGER.debug("[saddle-udp] late UDP bootstrap succeeded for {}; closing late channel", remote);
                    f.channel().close();
                }
            });
            return;
        }

        if (!future.isSuccess()) {
            Sable.LOGGER.warn("[saddle-udp] UDP bootstrap failed for {} after {} ms; continuing TCP-only",
                    remote, elapsed, future.cause());
            return;
        }

        Sable.LOGGER.debug("[saddle-udp] UDP bootstrap ready for {} in {} ms", remote, elapsed);
    }

    @Inject(method = "connectToLocalServer", at = @At("TAIL"))
    private static void saddle$connectToLocalServer(final SocketAddress address,
                                                    final CallbackInfoReturnable<Connection> cir,
                                                    @Local final Connection connection) {
        if (SableConfig.DISABLE_UDP_PIPELINE.get()) {
            return;
        }

        final ChannelFuture future;
        try {
            future = new Bootstrap()
                    .group(Connection.LOCAL_WORKER_GROUP.get())
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(final Channel channel) {
                            SableUDPPacket.configureInMemoryPipeline(
                                    channel.pipeline(), PacketFlow.CLIENTBOUND);
                            saddle$setupChannel(channel, connection);
                        }
                    })
                    .channel(LocalChannel.class)
                    .connect(address);
        } catch (final Throwable t) {
            Sable.LOGGER.warn("[saddle-udp] local UDP bootstrap failed; continuing", t);
            return;
        }

        if (!future.awaitUninterruptibly(SADDLE$UDP_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            Sable.LOGGER.warn("[saddle-udp] local UDP bootstrap timed out; continuing");
            future.cancel(true);
            return;
        }
        if (!future.isSuccess()) {
            Sable.LOGGER.debug("[saddle-udp] local UDP bootstrap failed", future.cause());
        }
    }

    @Unique
    private static void saddle$setupChannel(final Channel channel, final Connection connection) {
        channel.pipeline().addLast(new SableUDPChannelHandlerClient(connection));
    }
}
