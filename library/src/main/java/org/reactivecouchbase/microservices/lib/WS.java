package org.reactivecouchbase.microservices.lib;

import okhttp3.*;
import org.reactivecouchbase.concurrent.Future;
import org.reactivecouchbase.concurrent.NamedExecutors;
import org.reactivecouchbase.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

public class WS {
    private static final Logger LOGGER = LoggerFactory.getLogger(WS.class);
    private static OkHttpClient client = new OkHttpClient.Builder().build();
    private static final ExecutorService executor = NamedExecutors.newCachedThreadPool("WSCLIENT-EXECUTOR-THREAD");

    public static Observable<Response> callAndObserve(Request request) {
        return Observable.create(subscriber -> {
            try {
                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        LOGGER.error("Error while calling '" + call.request().url() + "' : ", e);
                        subscriber.onError(e);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        subscriber.onNext(response);
                        subscriber.onCompleted();
                    }
                });
            } catch (Exception e) {
                subscriber.onError(e);
            }
        });
    }

    public static Future<Response> callAsync(Request request) {
        Promise<Response> promise = new Promise<>(executor);
        try {
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    LOGGER.error("Error while calling '" + call.request().url() + "' : ", e);
                    promise.tryFailure(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    promise.trySuccess(response);
                }
            });
        } catch (Exception e) {
            promise.tryFailure(e);
        }
        return promise.future();
    }
}
