package org.reactivecouchbase.microservices;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.reactivecouchbase.client.AsyncClientRegistry;
import org.reactivecouchbase.client.ClientRegistry;
import org.reactivecouchbase.client.ServiceDescriptor;
import org.reactivecouchbase.json.JsObject;
import org.reactivecouchbase.json.Json;
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
        return chain.get("__registry", ctx -> {
            if (Config.config().mode().equals("dev")) {
                ClientRegistry clientRegistry = ctx.get(ClientRegistry.class);
                List<String> source = Lists.newArrayList(
                        "<!DOCTYPE html>",
                        "<html>",
                        "<head>",
                        "<title>Resources</title>",
                        "<link rel=\"stylesheet\" href=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/css/bootstrap.min.css\" integrity=\"sha384-1q8mTJOASx8j1Au+a5WDVnPi2lkFfwwEAa8hDDdjZlpLegxhjVME1fgjWPGmkzs7\" crossorigin=\"anonymous\">",
                        "<link rel=\"stylesheet\" href=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/css/bootstrap-theme.min.css\" integrity=\"sha384-fLW2N01lMqjakBkx3l/M9EahuwpSfeNvV63J5ezn3uZzapT0u7EYsXMjQV+0En5r\" crossorigin=\"anonymous\">",
                        "<script src=\"http://code.jquery.com/jquery-1.12.2.min.js\"></script>",
                        "<script src=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/js/bootstrap.min.js\" integrity=\"sha384-0mSbJDEHialfmuBBQP6A4Qrprq5OVfW37PRR3j5ELqxss1yVqOtnepnHVP9aJ7xS\" crossorigin=\"anonymous\"></script>",
                        "</head>",
                        "<body>",
                        "<div class=\"container\">",
                        "<h3>Services registry</h3>",
                        "<table class=\"table table-striped table-condensed table-bordered table-hover\">",
                        "<thead><tr><th>UUID</th><th>Name</th><th>URL</th><th>Version</th><th>Metadata</th></tr></thead>",
                        "<tbody>"
                );
                clientRegistry.allServices().forEach(desc -> {
                    source.add("<tr>"
                            + "<td style=\"vertical-align: middle;\">" + desc.uid + "</td>"
                            + "<td style=\"vertical-align: middle;\">" + desc.name + "</td>"
                            + "<td style=\"vertical-align: middle;\">" + desc.url + "</td>"
                            + "<td style=\"vertical-align: middle;\">" + desc.version.getOrElse("0.0.0") + "</td>"
                            + "<td style=\"vertical-align: middle;\" class=\"metadata\">" + Json.wrap(desc.metadata).stringify() + "</td>"
                            + "</tr>");
                });
                source.addAll(Lists.newArrayList(
                        "</tbody>",
                        "</table>",
                        "</div>",
                        "<script>",
                        "  Array.from(document.querySelectorAll('.metadata')).forEach(function(node) {",
                        "    node.innerHTML = '<pre style=\"margin-bottom: 0px;\"><code>' + JSON.stringify(JSON.parse(node.innerHTML), null, 2) + '</code></pre>';",
                        "  });",
                        "</script>",
                        "</body>",
                        "</html>"
                ));
                ctx.getResponse()
                        .contentType("text/html")
                        .status(200)
                        .send(Joiner.on("\n").join(source));
            }
        }).all(ctx -> {
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
