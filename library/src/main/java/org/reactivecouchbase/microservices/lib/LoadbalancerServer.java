package org.reactivecouchbase.microservices.lib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.handling.Chain;
import ratpack.http.client.HttpClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class LoadbalancerServer extends Server {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadbalancerServer.class);

    private List<String> hosts;
    private AtomicInteger counter;

    public LoadbalancerServer(int port, Set<String> hosts) {
        super(port);
        this.hosts = new ArrayList<>(hosts);
    }

    @Override
    public Chain routes(Chain chain) {
        return chain.all(ctx -> {
            ratpack.http.Request request =  ctx.getRequest();
            ratpack.http.Response response =  ctx.getResponse();
            URI originalURI = new URI(request.getRawUri());
            if (hosts.isEmpty()) {
                ctx.getResponse()
                        .contentType("text/plain")
                        .status(404)
                        .send("Service with URI " + originalURI.toString() + " not found :(");
            } else {
                HttpClient client = ctx.get(HttpClient.class);
                int index = counter.incrementAndGet();
                String hostAndPort = hosts.get(index % hosts.size());
                String[] hostParts = hostAndPort.split(":");
                String host = hostParts[0];
                int port = Integer.valueOf(hostParts[1]);
                // TODO : avoid hardcoded protocol
                URI newURI = new URI("http",  originalURI.getUserInfo(), host, port, originalURI.getPath(), originalURI.getQuery(), originalURI.getRawFragment());
                LOGGER.trace("Balance : " + url("") + request.getRawUri() + " to " + newURI.toString());
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