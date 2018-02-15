package cc.blynk.server.api.http.handlers;

import cc.blynk.core.http.handlers.StaticFile;
import cc.blynk.core.http.handlers.StaticFileEdsWith;
import cc.blynk.core.http.handlers.StaticFileHandler;
import cc.blynk.core.http.handlers.url.UrlReWriterHandler;
import cc.blynk.server.Holder;
import cc.blynk.server.core.dao.CSVGenerator;
import cc.blynk.server.core.protocol.handlers.decoders.MessageDecoder;
import cc.blynk.server.core.protocol.handlers.encoders.MessageEncoder;
import cc.blynk.server.handlers.common.AlreadyLoggedHandler;
import cc.blynk.server.handlers.common.HardwareNotLoggedHandler;
import cc.blynk.server.hardware.handlers.hardware.HardwareChannelStateHandler;
import cc.blynk.server.hardware.handlers.hardware.auth.HardwareLoginHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerKeepAliveHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 18.01.18.
 */
public class HttpAndHardwareUnificationHandler extends BaseHttpAndBlynkUnificationHandler {

    private final Holder holder;
    private final HardwareLoginHandler hardwareLoginHandler;
    private final HardwareChannelStateHandler hardwareChannelStateHandler;
    private final AlreadyLoggedHandler alreadyLoggedHandler;
    private final HttpAndWebSocketUnificatorHandler httpAndWebSocketUnificatorHandler;
    private final LetsEncryptHandler letsEncryptHandler;
    private final int hardTimeoutSecs;

    public HttpAndHardwareUnificationHandler(Holder holder,
                                             HardwareLoginHandler hardwareLoginHandler,
                                             HardwareChannelStateHandler hardwareChannelStateHandler,
                                             AlreadyLoggedHandler alreadyLoggedHandler,
                                             HttpAndWebSocketUnificatorHandler httpAndWebSocketUnificatorHandler,
                                             LetsEncryptHandler letsEncryptHandler) {
        this.holder = holder;
        this.hardwareLoginHandler = hardwareLoginHandler;
        this.hardwareChannelStateHandler = hardwareChannelStateHandler;
        this.alreadyLoggedHandler = alreadyLoggedHandler;
        this.hardTimeoutSecs = holder.limits.hardwareIdleTimeout;
        this.httpAndWebSocketUnificatorHandler = httpAndWebSocketUnificatorHandler;
        this.letsEncryptHandler = letsEncryptHandler;
    }

    @Override
    public ChannelPipeline buildHttpPipeline(ChannelPipeline pipeline) {
        log.trace("HTTP connection detected.", pipeline.channel());
        return pipeline
                .addLast("HttpServerCodec", new HttpServerCodec())
                .addLast("HttpServerKeepAlive", new HttpServerKeepAliveHandler())
                .addLast("HttpObjectAggregator", new HttpObjectAggregator(holder.limits.webRequestMaxSize, true))
                .addLast(letsEncryptHandler)
                .addLast("HttpChunkedWrite", new ChunkedWriteHandler())
                .addLast("HttpUrlMapper", new UrlReWriterHandler("/favicon.ico", "/static/favicon.ico"))
                .addLast("HttpStaticFile", new StaticFileHandler(holder.props, new StaticFile("/static"),
                        new StaticFileEdsWith(CSVGenerator.CSV_DIR, ".csv.gz")))
                .addLast("HttpWebSocketUnificator", httpAndWebSocketUnificatorHandler);
    }

    @Override
    public ChannelPipeline buildBlynkPipeline(ChannelPipeline pipeline) {
        log.trace("Blynk protocol connection detected.", pipeline.channel());
        return pipeline
                .addLast("H_IdleStateHandler", new IdleStateHandler(hardTimeoutSecs, hardTimeoutSecs, 0))
                .addLast("H_ChannelState", hardwareChannelStateHandler)
                .addLast("H_MessageDecoder", new MessageDecoder(holder.stats))
                .addLast("H_MessageEncoder", new MessageEncoder(holder.stats))
                .addLast("H_Login", hardwareLoginHandler)
                .addLast("H_NotLogged", new HardwareNotLoggedHandler())
                .addLast("H_AlreadyLogged", alreadyLoggedHandler);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        handleUnexpectedException(ctx, cause);
    }
}