package org.reactivecouchbase.microservices;

import okhttp3.Request;
import org.reactivecouchbase.client.*;
import org.reactivecouchbase.common.Duration;
import org.reactivecouchbase.common.IdGenerators;
import org.reactivecouchbase.functional.Option;
import org.reactivecouchbase.json.JsValue;
import org.reactivecouchbase.microservices.lib.*;
import ratpack.handling.Chain;
import ratpack.handling.Context;
import rx.Observable;

public class BikesServer extends Server {

    private CommandContext context = CommandContext
        .of(Runtime.getRuntime().availableProcessors() +  1)
        .withCache(InMemoryCommandCache.of(Duration.parse("5s")))
        .withCircuitBreakerStrategy(CircuitBreaker.Strategy.UNIQUE_PER_COMMAND);

    private static final String SERVICE_UID = IdGenerators.uuid();

    public BikesServer() {
        super(freePort());
    }

    @Override
    public Chain routes(Chain chain) {
        return chain.get("bike-shelters", Async.observe(this::service));
    }

    @Override
    public void registerServices(ClientRegistry clientRegistry) {
        Registration registration1 = clientRegistry.register(new ServiceDescriptor(SERVICE_UID, "bike-shelters-fetcher", url("/bike-shelters"), Option.some("1.0.0")));
        Registration registration2 = clientRegistry.register(new HttpResource("/bike-shelters", hostAndPort()));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            registration1.unregister();
            registration2.unregister();
        }));
    }

    public Request buildRequest() {
        return new Request.Builder()
                .url("http://open-data-poitiers.herokuapp.com/api/v2/bike-shelters/all")
                .build();
    }

    public Observable<Result> service(Context ctx) {
        Command<JsValue> command = HttpCommand
                .builder("bike-shelters")
                .withCall(this::buildRequest)
                .build();
        return context
                .execute(command)
                .map(Result::ok, executor)
                .wrap(f -> Async.toObservable(f, executor));
    }
}
