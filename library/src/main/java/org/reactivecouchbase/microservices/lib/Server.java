package org.reactivecouchbase.microservices.lib;

import com.google.common.base.Throwables;
import org.reactivecouchbase.client.ClientRegistry;
import org.reactivecouchbase.concurrent.NamedExecutors;
import org.reactivecouchbase.json.JsValue;
import org.reactivecouchbase.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.form.Form;
import ratpack.handling.Chain;
import ratpack.handling.Context;
import ratpack.registry.RegistrySpec;
import ratpack.rx.RxRatpack;
import ratpack.server.BaseDir;
import ratpack.server.RatpackServer;
import ratpack.server.ServerConfig;
import rx.Observable;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.ExecutorService;

import static ratpack.jackson.Jackson.jsonNode;

public abstract class Server {

    public final ExecutorService executor = NamedExecutors.newCachedThreadPool("SERVER-EXECUTOR-THREAD");
    public final Logger logger;
    protected final String host;
    protected final int port;
    private final String configPath;

    public Server(String host, int port, String configPath) {
        this.logger = LoggerFactory.getLogger(getClass());
        this.host = host;
        this.port = port;
        this.configPath = configPath;
    }

    public Server(String host, int port) {
        this.logger = LoggerFactory.getLogger(getClass());
        this.host = host;
        this.port = port;
        this.configPath = "application";
    }

    public Server(int port) {
        this.logger = LoggerFactory.getLogger(getClass());
        this.host = "0.0.0.0";
        this.port = port;
        this.configPath = "application";
    }

    public Server() {
        this.logger = LoggerFactory.getLogger(getClass());
        this.host = "0.0.0.0";
        this.port = 9000;
        this.configPath = "application";
    }

    public Server(String configPath) {
        this.logger = LoggerFactory.getLogger(getClass());
        this.host = "0.0.0.0";
        this.port = 9000;
        this.configPath = configPath;
    }

    public String url(String path) {
        // TODO : avoid hardcoded protocol
        return "http://" + machineHost() + ":" + port + (path == null ? "" : path);
    }

    public String hostAndPort() {
        return machineHost() + ":" + port;
    }

    public abstract Chain routes(Chain chain);

    public void registry(RegistrySpec registry) {}

    public void registerServices(ClientRegistry clientRegistry) {}

    public RatpackServer run() {
        try {
            Path base = BaseDir.find("public");
            ServerConfig config = ServerConfig
                    .embedded()
                    .port(port)
                    .baseDir(base)
                    .development(Config.config().mode().equalsIgnoreCase("dev"))
                    .publicAddress(uri(host))
                    .threads(Runtime.getRuntime().availableProcessors() + 1)
                    .build();
            Cluster cluster = new Cluster();
            this.registerServices(cluster.getDistributedServiceRegistry());
            return RatpackServer.start(server -> server
                    .serverConfig(config)
                    .registryOf(r -> {
                        r.add(Config.class, new Config(configPath));
                        r.add(Cluster.class, cluster);
                        r.add(ClientRegistry.class, cluster.getDistributedServiceRegistry());
                        this.registry(r);
                    })
                    .handlers(this::routes)
            );
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public Observable<JsValue> jsonBody(Context ctx) {
        return RxRatpack.observe(ctx.parse(jsonNode())).map(Json::fromJsonNode);
    }

    public Observable<Form> formBody(Context ctx) {
        return RxRatpack.observe(ctx.parse(Form.class));
    }

    private static URI uri(String uri) {
        try {
            return new URI(uri);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public static int freePort() {
        try {
            ServerSocket ss = new ServerSocket(0);
            int port = ss.getLocalPort();
            ss.close();
            return port;
        } catch (Exception e) {
            return new Random().nextInt(1000) + 42000;
        }
    }

    public static String machineHost() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
