package org.reactivecouchbase.microservices.lib;

import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MaxSizeConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.reactivecouchbase.client.ClientRegistry;
import org.reactivecouchbase.common.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cluster {

    private static final Logger LOGGER = LoggerFactory.getLogger(Cluster.class);

    private HazelcastInstance hazelcast;
    private com.hazelcast.config.Config config;
    private ClientRegistry distributedServiceRegistry;

    Cluster() {
        config = new com.hazelcast.config.Config().addMapConfig(
            new MapConfig(DistributedClientRegistry.REGISTRY_NAME)
                .setTimeToLiveSeconds((int) DistributedClientRegistry.HEARTBEAT_DURATION.toSeconds())
                .setMaxIdleSeconds(0)
                .setEvictionPolicy(EvictionPolicy.LRU)
                .setMaxSizeConfig(new MaxSizeConfig(5000, MaxSizeConfig.MaxSizePolicy.PER_NODE))
                .setEvictionPercentage(25)
                .setMinEvictionCheckMillis(100L)
        );
        Duration.Measurable measure = Duration.measure();
        hazelcast = Hazelcast.newHazelcastInstance(config);
        LOGGER.info("starting cluster in " + measure.stop().toHumanReadable());
        distributedServiceRegistry = DistributedClientRegistry.build(this);
    }

    HazelcastInstance getCluster() {
        return hazelcast;
    }

    ClientRegistry getDistributedServiceRegistry() {
        return distributedServiceRegistry;
    }

}
