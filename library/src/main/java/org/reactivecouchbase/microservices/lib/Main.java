package org.reactivecouchbase.microservices.lib;

import com.google.common.base.Throwables;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.server.RatpackServer;

import java.util.List;
import java.util.stream.Collectors;

public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(WS.class);

    public static void main(String... args) {
        Reflections reflections = new Reflections("");
        List<RatpackServer> serverClasses =
            reflections.getSubTypesOf(Server.class).stream()
                .map(serverClazz -> {
                    try {
                        LOGGER.info("Found server class : " + serverClazz.getName());
                        Server server = serverClazz.newInstance();
                        return server.run();
                    } catch (Exception e) {
                        throw Throwables.propagate(e);
                    }
                }).collect(Collectors.toList());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            serverClasses.forEach(server -> {
                try {
                    server.stop();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }));
    }
}

