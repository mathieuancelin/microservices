package org.reactivecouchbase.microservices.lib;

import org.reactivecouchbase.client.ServiceDescriptor;
import org.reactivecouchbase.common.IdGenerators;
import org.reactivecouchbase.functional.Option;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class HttpResource extends ServiceDescriptor {

    public static final String SERVICE_NAME = "HTTP-RESOURCE";

    public HttpResource(String path, String hostAndPort) {
        super(IdGenerators.uuid(), SERVICE_NAME, path, metadata(hostAndPort), new ArrayList<>(), Option.none());
    }

    public static Map<String, String> metadata(String hostAndPort) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("hostAndPort", hostAndPort);
        return metadata;
    }
}
