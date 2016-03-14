package org.reactivecouchbase.microservices.lib;

import com.google.common.base.Supplier;
import okhttp3.Request;
import org.reactivecouchbase.client.Command;
import org.reactivecouchbase.common.Duration;
import org.reactivecouchbase.common.Invariant;
import org.reactivecouchbase.concurrent.Future;
import org.reactivecouchbase.json.JsValue;
import org.reactivecouchbase.json.Json;

import java.util.concurrent.ScheduledExecutorService;

public class HttpCommand extends Command<JsValue> {

    private final JsValue fallback;
    private final int retry;
    private final Duration timeout;
    private final Supplier<Request> call;
    private final String name;

    HttpCommand(String name, JsValue fallback, int retry, Duration timeout, Supplier<Request> call) {
        Invariant.checkNotNull(fallback);
        Invariant.checkNotNull(timeout);
        Invariant.checkNotNull(call);
        Invariant.checkNotNull(name);
        this.name = name;
        this.fallback = fallback;
        this.retry = retry;
        this.timeout = timeout;
        this.call = call;
    }

    @Override
    public String name() {
        return "HttpCommand-" + name;
    }

    @Override
    public Future<JsValue> runAsync(ScheduledExecutorService ec) {
        return WS.callAsync(call.get()).flatMap(response -> {
            try {
                return Future.successful(Json.parse(response.body().string()));
            } catch (Exception e) {
                return Future.failed(e);
            }
        }, ec);
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

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private JsValue fallback = Json.arr();
        private int retry = 5;
        private Duration timeout = Duration.parse("10s");
        private Supplier<Request> call;

        public Builder(String name) {
            this.name = name;
        }

        public Builder withFallback(JsValue fallback) {
            this.fallback = fallback;
            return this;
        }

        public Builder withRetry(int retry) {
            this.retry = retry;
            return this;
        }

        public Builder withTimeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder withCall(Supplier<Request> call) {
            this.call = call;
            return this;
        }

        public HttpCommand build() {
            return new HttpCommand(name, fallback, retry, timeout, call);
        }
    }
}

