package org.reactivecouchbase.microservices;

import okhttp3.Request;
import org.reactivecouchbase.client.*;
import org.reactivecouchbase.common.IdGenerators;
import org.reactivecouchbase.concurrent.Future;
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
            .withCircuitBreakerStrategy(CircuitBreaker.Strategy.UNIQUE_PER_COMMAND);

    public AggregatorServer() {
        super(freePort());
    }

    @Override
    public Chain routes(Chain chain) {
        return chain
            .get("data", Async.observe(this::service))
            .post("ping", Async.observe(this::ping));
    }

    @Override
    public void registerServices(ClientRegistry clientRegistry) {
        Registration registration1 = clientRegistry.register(new ServiceDescriptor(SERVICE_UID, "aggregator-service", url("/data"), Option.some("1.0.0")));
        Registration registration2 = clientRegistry.register(new HttpResource("/data", hostAndPort()));
        Registration registration3 = clientRegistry.register(new HttpResource("/ping", hostAndPort()));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            registration1.unregister();
            registration2.unregister();
            registration3.unregister();
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
        Future<JsValue> commandExecution1 = context.execute(bikeSheltersCommand);
        Future<JsValue> commandExecution2 = context.execute(glassContainersCommand);
        return commandExecution1.flatMap(firsJsValue ->
            commandExecution2.map(secondJsValue ->
                Result.ok(Json.obj().with("glass-containers", secondJsValue).with("bike-shelters", secondJsValue))
            , executor)
        , executor).wrap(f -> Async.toObservable(f, executor));
    }

    public Observable<Result> ping(Context ctx) {
        return jsonBody(ctx).map(value -> Result.ok(value.asObject().with("Via", "Ratpack ;-)")));
    }
}
