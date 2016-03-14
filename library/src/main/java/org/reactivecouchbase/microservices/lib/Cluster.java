package org.reactivecouchbase.microservices.lib;

import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MaxSizeConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.reactivecouchbase.client.ClientRegistry;

public class Cluster {

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
        hazelcast = Hazelcast.newHazelcastInstance(config);
        distributedServiceRegistry = DistributedClientRegistry.build(this);
    }

    HazelcastInstance getCluster() {
        return hazelcast;
    }

    ClientRegistry getDistributedServiceRegistry() {
        return distributedServiceRegistry;
    }

}
