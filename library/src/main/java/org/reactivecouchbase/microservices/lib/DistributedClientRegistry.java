package org.reactivecouchbase.microservices.lib;

import com.google.common.collect.ImmutableList;
import com.hazelcast.core.IMap;
import org.reactivecouchbase.client.AsyncClientRegistry;
import org.reactivecouchbase.client.ClientRegistry;
import org.reactivecouchbase.client.Registration;
import org.reactivecouchbase.client.ServiceDescriptor;
import org.reactivecouchbase.common.Duration;
import org.reactivecouchbase.concurrent.Future;
import org.reactivecouchbase.concurrent.NamedExecutors;
import org.reactivecouchbase.concurrent.Promise;
import org.reactivecouchbase.functional.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class DistributedClientRegistry implements ClientRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedClientRegistry.class);
    private static final ScheduledExecutorService ec = Executors.newScheduledThreadPool(1);
    public static final String REGISTRY_NAME = "service-locator-map";
    public static final Duration HEARTBEAT_DURATION = Duration.parse("60s");
    private Cluster cluster;
    private ConcurrentMap<String, ServiceDescriptor> locallyRegisteredServices = new ConcurrentHashMap<>();

    private DistributedClientRegistry(Cluster cluster) {
        this.cluster = cluster;
        cluster.getCluster().getMap(REGISTRY_NAME);
        ec.scheduleAtFixedRate(this::produceHeartbeat, 0, HEARTBEAT_DURATION.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void produceHeartbeat() {
        IMap<String, ServiceDescriptor> serviceCache = cluster.getCluster().getMap(REGISTRY_NAME);
        LOGGER.info("Registering services again");
        locallyRegisteredServices.entrySet().forEach(entry -> {
            serviceCache.put(entry.getKey(), entry.getValue());
        });
    }

    public static ClientRegistry build(Cluster cluster) {
        return new DistributedClientRegistry(cluster);
    }

    @Override
    public List<ServiceDescriptor> allServices() {
        return measure("get allServices", () -> {
            IMap<String, ServiceDescriptor> serviceCache =
                    cluster.getCluster().getMap(REGISTRY_NAME);
            return ImmutableList.copyOf(serviceCache.values());
        });
    }

    @Override
    public Registration register(ServiceDescriptor desc) {
        IMap<String, ServiceDescriptor> serviceCache =
                cluster.getCluster().getMap(REGISTRY_NAME);
        serviceCache.put(desc.uid, desc);
        locallyRegisteredServices.put(desc.uid, desc);
        return () -> {
            locallyRegisteredServices.remove(desc.uid);
            serviceCache.remove(desc.uid);
        };
    }

    @Override
    public void unregister(String uid) {
        locallyRegisteredServices.remove(uid);
        cluster.getCluster().getMap(REGISTRY_NAME).remove(uid);
    }

    private static <T> T measure(String name, Supplier<T> supplier) {
        Duration.Measurable measure = Duration.measure();
        try {
            return supplier.get();
        } finally {
            LOGGER.trace(name + " in " + measure.stop().toHumanReadable());
        }
    }

    public static class AsyncDistributedClientRegistry implements AsyncClientRegistry {

        private final ClientRegistry clientRegistry;

        private static final ExecutorService ec = NamedExecutors.newAutoFixedThreadPool("ASYNC-CLIENT-REGISTRY-THREAD");

        AsyncDistributedClientRegistry(ClientRegistry clientRegistry) {
            this.clientRegistry = clientRegistry;
        }

        private <T> Future<T> async(Supplier<T> supplier) {
            final Promise<T> promise = new Promise<>(ec);
            ec.submit((Runnable) () -> {
                try {
                    promise.trySuccess(supplier.get());
                } catch (Throwable e) {
                    promise.tryFailure(e);
                }
            });
            return promise.future();
        }

        private Future<Unit> asyncUnit(Runnable supplier) {
            final Promise<Unit> promise = new Promise<>(ec);
            ec.submit((Runnable) () -> {
                try {
                    supplier.run();
                    promise.trySuccess(Unit.unit());
                } catch (Throwable e) {
                    promise.tryFailure(e);
                }
            });
            return promise.future();
        }

        @Override
        public Future<List<ServiceDescriptor>> allServices(ExecutorService ec) {
            return async(clientRegistry::allServices);
        }

        @Override
        public Future<Registration> register(ServiceDescriptor desc, ExecutorService ec) {
            return async(() -> clientRegistry.register(desc));
        }

        @Override
        public Future<Unit> unregister(String uuid, ExecutorService ec) {
            return asyncUnit(() -> clientRegistry.unregister(uuid));
        }
    }
}
