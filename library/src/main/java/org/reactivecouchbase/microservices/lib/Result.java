package org.reactivecouchbase.microservices.lib;

import com.google.common.base.Joiner;
import io.netty.handler.codec.http.cookie.Cookie;
import okhttp3.Response;
import org.reactivecouchbase.json.JsValue;
import org.reactivecouchbase.json.Json;
import rx.Observable;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Result {

    public String body = "";
    public String contentType;
    public Map<String, String> headers = new HashMap<>(16);
    public Map<String, Cookie> cookies = new HashMap<>(16);
    public int status = 200;
    public File direct;

    public static Result status(int status, String content, String contentType) {
        Result r = new Result();
        r.body = content;
        r.status = status;
        r.contentType = contentType;
        return r;
    }

    public static Result status(int status, JsValue content) {
        return status(status, Json.stringify(content), "application/json");
    }

    public static Result ok(String content) {
        return status(200, content, "text/plain");
    }

    public static Result ok(Response content) {
        try {
            return status(200, content.body().string(), "text/plain");
        } catch (Exception e) {
            return status(500, e.getMessage() + "\n\n" + Joiner.on("\n").join(Arrays.asList(e.getStackTrace())), "text/plain");
        }
    }

    public static Result ok(Response content, String type) {
        try {
            return status(200, content.body().string(), type);
        } catch (Exception e) {
            return status(500, e.getMessage() + "\n\n" + Joiner.on("\n").join(Arrays.asList(e.getStackTrace())), type);
        }
    }

    public static Result ok(String content, String type) {
        return status(200, content, type);
    }

    public static Result ok() {
        return status(200, "", "text/plain");
    }

    public static Result ok(JsValue content) {
        return status(200, Json.stringify(content), "application/json");
    }

    public static Result error(String content) {
        return status(500, content, "text/plain");
    }

    public static Result error(JsValue content) {
        return status(500, Json.stringify(content), "application/json");
    }

    public static Result notFound(String content) {
        return status(404, content, null);
    }

    public static Result notFound(JsValue content) {
        return status(404, Json.stringify(content), "application/json");
    }

    public static Result file(File file) {
        return file(file, "application/octet-stream");
    }

    public static Result file(File file, String type) {
        Result r = new Result();
        r.status = 200;
        r.contentType = type;
        r.direct = file;
        return r;
    }

    public Result as(String contentType) {
        this.contentType = contentType;
        return this;
    }

    public Result withCookie(Cookie cookie) {
        this.cookies.put(cookie.name(), cookie);
        return this;
    }

    public Result withHeader(String name, String header) {
        this.headers.put(name, header);
        return this;
    }

    public Observable<Result> asObservable() {
        Result r = this;
        return Observable.create(s -> {
            s.onNext(r);
            s.onCompleted();
        });
    }
}
