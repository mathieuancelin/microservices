package org.reactivecouchbase.microservices.lib;

import org.reactivecouchbase.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.handling.Context;
import ratpack.http.Response;
import ratpack.rx.RxRatpack;
import rx.Observable;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Async {

    private static final Logger LOGGER = LoggerFactory.getLogger(Async.class);

    static {
        RxRatpack.initialize();
    }

    public static <T> Observable<T> toObservable(Future<T> future, ExecutorService ec) {
        return Observable.create(subscriber -> {
            future.andThen(tTry -> {
                for (Throwable failure : tTry.asFailure()) {
                    subscriber.onError(failure);
                }
                for (T next : tTry.asSuccess()) {
                    subscriber.onNext(next);
                    subscriber.onCompleted();
                }
            }, ec);
        });
    }

    public static ratpack.handling.Handler observe(Function<Context, Observable<Result>> handler) {
        return ctx -> RxRatpack
            .promiseSingle(handler.apply(ctx))
            .onError(error -> {
                error.printStackTrace();
                ctx.getResponse()
                    .contentType("text/plain")
                    .status(500)
                    .send(Arrays.asList(error.getStackTrace()).stream().map(StackTraceElement::toString).collect(Collectors.joining("\n")));
            }).then(result -> {
                Response response = ctx.getResponse()
                    .contentType(result.contentType)
                    .status(result.status);
                result.cookies.forEach((k, v) -> response.getCookies().add(v));
                result.headers.forEach((k, v) -> response.getHeaders().add(k, v));
                if (result.direct == null) {
                    response.send(result.body);
                } else {
                    response.sendFile(result.direct.toPath());
                }
            });
    }
}
