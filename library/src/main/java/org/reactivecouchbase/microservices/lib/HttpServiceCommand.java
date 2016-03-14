package org.reactivecouchbase.microservices.lib;

import okhttp3.Request;
import org.reactivecouchbase.client.ClientRegistry;
import org.reactivecouchbase.client.Command;
import org.reactivecouchbase.client.ServiceDescriptor;
import org.reactivecouchbase.common.Duration;
import org.reactivecouchbase.common.Invariant;
import org.reactivecouchbase.concurrent.Future;
import org.reactivecouchbase.functional.Option;
import org.reactivecouchbase.functional.Unit;
import org.reactivecouchbase.json.JsValue;
import org.reactivecouchbase.json.Json;

import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

public class HttpServiceCommand extends Command<JsValue> {

    private final JsValue fallback;
    private final int retry;
    private final Duration timeout;
    private final ClientRegistry clientRegistry;
    private final String serviceName;
    private final Function<ServiceDescriptor, Request> call;

    HttpServiceCommand(JsValue fallback, int retry, Duration timeout, String serviceName, ClientRegistry clientRegistry, Function<ServiceDescriptor, Request> call) {
        Invariant.checkNotNull(fallback);
        Invariant.checkNotNull(timeout);
        Invariant.checkNotNull(serviceName);
        Invariant.checkNotNull(clientRegistry);
        Invariant.checkNotNull(call);
        this.fallback = fallback;
        this.retry = retry;
        this.timeout = timeout;
        this.clientRegistry = clientRegistry;
        this.serviceName = serviceName;
        this.call = call;
    }

    @Override
    public String name() {
        return "HttpServiceCommand-" + serviceName;
    }

    @Override
    public Future<JsValue> runAsync(ScheduledExecutorService ec) {
        Option<ServiceDescriptor> service = clientRegistry.service(this.serviceName);
        for (Unit none : service.asNone()) {
            return Future.failed(new RuntimeException(serviceName + " service not found"));
        }
        for (ServiceDescriptor desc : service.asSome()) {
            return WS.callAsync(call.apply(desc)).flatMap(response -> {
                try {
                    return Future.successful(Json.parse(response.body().string()));
                } catch (Exception e) {
                    return Future.failed(e);
                }
            }, ec);
        }
        return Future.failed(new RuntimeException("WUT ???"));
    }

    @Override
    public Duration timeout() {
        return timeout;
    }

    @Override
    public int retry() {
        return retry;
    }

    @Override
    public JsValue fallback() {
        return fallback;
    }

    public static Builder forService(String serviceName) {
        return new Builder(serviceName);
    }

    public static class Builder {
        private JsValue fallback = Json.arr();
        private int retry = 5;
        private Duration timeout = Duration.parse("10s");
        private ClientRegistry clientRegistry;
        private String serviceName;
        private Function<ServiceDescriptor, Request> call;

        Builder(String serviceName) {
            this.serviceName = serviceName;
        }

        public HttpServiceCommand.Builder withFallback(JsValue fallback) {
            this.fallback = fallback;
            return this;
        }

        public HttpServiceCommand.Builder withRetry(int retry) {
            this.retry = retry;
            return this;
        }

        public HttpServiceCommand.Builder withTimeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public HttpServiceCommand.Builder withClientRegistry(ClientRegistry clientRegistry) {
            this.clientRegistry = clientRegistry;
            return this;
        }

        public HttpServiceCommand.Builder withServiceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public HttpServiceCommand.Builder withCall(Function<ServiceDescriptor, Request> call) {
            this.call = call;
            return this;
        }

        public HttpServiceCommand build() {
            return new HttpServiceCommand(fallback, retry, timeout, serviceName, clientRegistry, call);
        }
    }
}
