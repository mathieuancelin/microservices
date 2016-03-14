package org.reactivecouchbase.microservices.lib;

import com.google.common.collect.ImmutableList;
import org.reactivecouchbase.client.ClientRegistry;
import org.reactivecouchbase.client.Registration;
import org.reactivecouchbase.client.ServiceDescriptor;
import org.reactivecouchbase.common.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;

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
        ConcurrentMap<String, ServiceDescriptor> serviceCache = cluster.getCluster().getMap(REGISTRY_NAME);
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
        ConcurrentMap<String, ServiceDescriptor> serviceCache =
                cluster.getCluster().getMap(REGISTRY_NAME);
        return ImmutableList.copyOf(serviceCache.values());
    }

    @Override
    public Registration register(ServiceDescriptor desc) {
        ConcurrentMap<String, ServiceDescriptor> serviceCache =
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

}
