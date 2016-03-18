package org.reactivecouchbase.microservices;

import org.reactivecouchbase.client.AsyncClientRegistry;
import org.reactivecouchbase.client.ServiceDescriptor;
import org.reactivecouchbase.microservices.lib.Config;
import org.reactivecouchbase.microservices.lib.HttpResource;
import org.reactivecouchbase.microservices.lib.LoadbalancerServer;
import org.reactivecouchbase.microservices.lib.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.exec.Promise;
import ratpack.exec.Upstream;
import ratpack.handling.Chain;
import ratpack.http.client.HttpClient;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class ServiceProxyServer extends Server {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceProxyServer.class);

    public ServiceProxyServer() {
        super(Config.config().getInt("http.port", 9000));
    }

    @Override
    public Chain routes(Chain chain) {
        return chain.all(ctx -> {
            AsyncClientRegistry clientRegistry = ctx.get(AsyncClientRegistry.class);
            HttpClient client = ctx.get(HttpClient.class);
            ratpack.http.Request request =  ctx.getRequest();
            ratpack.http.Response response =  ctx.getResponse();
            URI originalURI = new URI(request.getRawUri());
            Promise.of((Upstream<List<ServiceDescriptor>>) f -> {
                clientRegistry.services(HttpResource.SERVICE_NAME, executor)
                    .andThen(ttry -> {
                        for (Throwable failure : ttry.asFailure()) {
                            f.error(failure);
                        }
                        for (List<ServiceDescriptor> descriptors : ttry.asSuccess()) {
                            f.success(descriptors);
                        }
                    }, executor);
            }).then(services -> {
                List<String> hosts = services.stream()
                        .filter(desc -> desc.url.toLowerCase().startsWith(originalURI.getPath().toLowerCase()))
                        .map(desc -> desc.metadata.get("hostAndPort"))
                        .collect(Collectors.toList());
                if (hosts.isEmpty()) {
                    ctx.getResponse()
                        .contentType("text/plain")
                        .status(404)
                        .send("Service with URI " + originalURI.toString() + " not found :(");
                } else {
                    try {
                        int index = new Random().nextInt(hosts.size());
                        String[] hostPart = hosts.get(index).split(":");
                        String host = hostPart[0];
                        int port = Integer.valueOf(hostPart[1]);
                        // TODO : avoid hardcoded protocol
                        URI newURI = new URI("http", originalURI.getUserInfo(), host, port, originalURI.getPath(), originalURI.getQuery(), originalURI.getRawFragment());
                        LOGGER.info("Proxy : " + url("") + request.getRawUri() + " to " + newURI.toString());
                        LoadbalancerServer.forwardRequest(request, response, client, newURI);
                    } catch (Exception e) {
                        ctx.getResponse()
                            .contentType("text/plain")
                            .status(500)
                            .send(Arrays.asList(e.getStackTrace()).stream().map(StackTraceElement::toString).collect(Collectors.joining("\n")));
                    }
                }
            });
        });
    }
}
