package org.reactivecouchbase.microservices;

import okhttp3.Request;
import org.reactivecouchbase.client.*;
import org.reactivecouchbase.common.Duration;
import org.reactivecouchbase.common.IdGenerators;
import org.reactivecouchbase.functional.Option;
import org.reactivecouchbase.json.JsValue;
import org.reactivecouchbase.json.Json;
import org.reactivecouchbase.microservices.lib.*;
import ratpack.handling.Chain;
import ratpack.handling.Context;
import rx.Observable;

public class AggregatorServer extends Server {

    private static final String SERVICE_UID = IdGenerators.uuid();

    private CommandContext context = CommandContext
            .of(Runtime.getRuntime().availableProcessors() +  1)
            .withCache(InMemoryCommandCache.of(Duration.parse("10s")))
            .withCircuitBreakerStrategy(CircuitBreaker.Strategy.UNIQUE_PER_COMMAND);

    public AggregatorServer() {
        super(freePort());
    }

    @Override
    public Chain routes(Chain chain) {
        return chain.get("data", Async.observe(this::service));
    }

    @Override
    public void registerServices(ClientRegistry clientRegistry) {
        Registration registration1 = clientRegistry.register(new ServiceDescriptor(SERVICE_UID, "aggregator-service", url("/data"), Option.some("1.0.0")));
        Registration registration2 = clientRegistry.register(new HttpResource("/data", hostAndPort()));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            registration1.unregister();
            registration2.unregister();
        }));
    }

    public Request buildRequest(ServiceDescriptor desc) {
        return new Request.Builder().url(desc.url).build();
    }

    public Observable<Result> service(Context ctx) {
        ClientRegistry clientRegistry = ctx.get(ClientRegistry.class);
        Command<JsValue> bikeSheltersCommand = HttpServiceCommand
                .forService("bike-shelters-fetcher")
                .withClientRegistry(clientRegistry)
                .withCall(this::buildRequest)
                .build();
        Command<JsValue> glassContainersCommand = HttpServiceCommand
                .forService("glass-containers-fetcher")
                .withClientRegistry(clientRegistry)
                .withCall(this::buildRequest)
                .build();
        return context.execute(bikeSheltersCommand).flatMap(firsJsValue ->
            context.execute(glassContainersCommand).map(secondJsValue ->
                Result.ok(Json.obj().with("glass-containers", secondJsValue).with("bike-shelters", secondJsValue))
            , executor)
        , executor).wrap(f -> Async.toObservable(f, executor));
    }
}
