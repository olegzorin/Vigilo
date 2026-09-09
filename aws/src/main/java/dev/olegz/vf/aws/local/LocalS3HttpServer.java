package dev.olegz.vf.aws.local;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.olegz.vf.common.props.PropertyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embedded HTTP endpoint over the local S3 object store, used in local mode to make presigned URLs
 * reachable from inside a build container.
 * <p>
 * {@code BuildConfig.makeDockerfile} bakes a presigned S3 read URL into the Dockerfile and the build
 * container fetches it with {@code curl}. A {@code file://} URL (Phase 1) is unreachable from a
 * container, so {@link dev.olegz.vf.aws.s3.S3Presigning} instead returns
 * {@code http://<host>:<port>/<bucket>/<key>} in local mode, served by this lazily-started server
 * backed by {@link LocalAws#s3File(String, String)}.
 * <p>
 * Configuration (under {@code vf.aws.local.s3.*}):
 * <ul>
 *   <li>{@code httpPort} - listen port; {@code 0} (default) picks a free ephemeral port.</li>
 *   <li>{@code host} - host placed in generated URLs; default {@code host.docker.internal}, which a
 *       Docker Desktop container resolves to the host. Host-side consumers (e.g. a browser following
 *       a presigned lambda-code download URL) cannot resolve that name - set this to
 *       {@code localhost} for those flows, or front the store differently.</li>
 * </ul>
 * GET/HEAD read objects; PUT writes them, so both read and write presigns work against one endpoint.
 */
public final class LocalS3HttpServer {
    private static final Logger logger = LoggerFactory.getLogger(LocalS3HttpServer.class);

    private static volatile LocalS3HttpServer instance;

    private final HttpServer server;
    private final String baseUrl;

    private LocalS3HttpServer(HttpServer server, String baseUrl) {
        this.server = server;
        this.baseUrl = baseUrl;
    }

    /** A reachable {@code http://<host>:<port>/<bucket>/<key>} URL, starting the server if needed. */
    public static String urlFor(String bucket, String key) {
        return server().baseUrl + '/' + bucket + '/' + key;
    }

    private static LocalS3HttpServer server() {
        LocalS3HttpServer s = instance;
        if (s == null) {
            synchronized (LocalS3HttpServer.class) {
                s = instance;
                if (s == null) {
                    instance = s = start();
                }
            }
        }
        return s;
    }

    private static LocalS3HttpServer start() {
        int port = PropertyStore.getInt("vf.aws.local.s3.httpPort", 0);
        String host = PropertyStore.getString("vf.aws.local.s3.host", "host.docker.internal");
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", LocalS3HttpServer::handle);
            server.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "local-s3-http");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            int actualPort = server.getAddress().getPort();
            String baseUrl = "http://" + host + ':' + actualPort;
            logger.info("Local S3 HTTP endpoint started on port {} (URLs use host '{}')", actualPort, host);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0), "local-s3-http-shutdown"));
            return new LocalS3HttpServer(server, baseUrl);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot start local S3 HTTP endpoint on port " + port, e);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String[] bucketAndKey = parsePath(exchange.getRequestURI().getRawPath());
            if (bucketAndKey == null) {
                respondEmpty(exchange, 400);
                return;
            }
            File file = LocalAws.s3File(bucketAndKey[0], bucketAndKey[1]);
            switch (exchange.getRequestMethod()) {
                case "GET" -> get(exchange, file, true);
                case "HEAD" -> get(exchange, file, false);
                case "PUT" -> put(exchange, file);
                default -> respondEmpty(exchange, 405);
            }
        }
    }

    /** Split {@code /<bucket>/<key...>} into [bucket, key]; key may contain further '/'. */
    private static String[] parsePath(String rawPath) {
        String path = URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
        if (path.startsWith("/")) path = path.substring(1);
        int slash = path.indexOf('/');
        if (slash <= 0 || slash == path.length() - 1) return null;
        return new String[]{path.substring(0, slash), path.substring(slash + 1)};
    }

    private static void get(HttpExchange exchange, File file, boolean withBody) throws IOException {
        if (!file.isFile()) {
            respondEmpty(exchange, 404);
            return;
        }
        long length = file.length();
        if (withBody) {
            exchange.sendResponseHeaders(200, length);
            try (OutputStream out = exchange.getResponseBody()) {
                Files.copy(file.toPath(), out);
            }
        } else {
            exchange.getResponseHeaders().set("Content-Length", Long.toString(length));
            exchange.sendResponseHeaders(200, -1);
        }
    }

    private static void put(HttpExchange exchange, File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Cannot create directory " + parent);
        }
        try (InputStream in = exchange.getRequestBody()) {
            Files.write(file.toPath(), in.readAllBytes());
        }
        respondEmpty(exchange, 200);
    }

    private static void respondEmpty(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
    }
}
