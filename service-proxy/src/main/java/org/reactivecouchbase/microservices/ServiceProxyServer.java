package org.reactivecouchbase.microservices;

import org.reactivecouchbase.client.ClientRegistry;
import org.reactivecouchbase.microservices.lib.Config;
import org.reactivecouchbase.microservices.lib.HttpResource;
import org.reactivecouchbase.microservices.lib.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.handling.Chain;
import ratpack.http.client.HttpClient;

import java.net.URI;
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
            ClientRegistry clientRegistry = ctx.get(ClientRegistry.class);
            HttpClient client = ctx.get(HttpClient.class);
            ratpack.http.Request request =  ctx.getRequest();
            ratpack.http.Response response =  ctx.getResponse();
            URI originalURI = new URI(request.getRawUri());
            List<String> hosts = clientRegistry.services(HttpResource.SERVICE_NAME)
                    .stream()
                    .filter(desc -> desc.url.toLowerCase().startsWith(originalURI.getPath().toLowerCase()))
                    .map(desc -> desc.metadata.get("hostAndPort"))
                    .collect(Collectors.toList());
            if (hosts.isEmpty()) {
                ctx.getResponse()
                    .contentType("text/plain")
                    .status(404)
                    .send("Service with URI " + originalURI.toString() + " not found :(");
            } else {
                int index = new Random().nextInt(hosts.size());
                String[] hostPart = hosts.get(index).split(":");
                String host = hostPart[0];
                int port = Integer.valueOf(hostPart[1]);
                // TODO : avoid hardcoded protocol
                URI newURI = new URI("http",  originalURI.getUserInfo(), host, port, originalURI.getPath(), originalURI.getQuery(), originalURI.getRawFragment());
                LOGGER.info("Proxy : " + url("") + request.getRawUri() + " to " + newURI.toString());
                client.requestStream(newURI, spec -> {
                    spec.getHeaders().copy(request.getHeaders());
                }).then(responseStream -> {
                    responseStream.forwardTo(response, headers -> {
                        String via = "Ratpack-Proxy";
                        via = headers.get("Via") != null ? via + ", " + headers.get("Via") : via;
                        headers.set("Via", via);
                    });
                });
            }
        });
    }
}
