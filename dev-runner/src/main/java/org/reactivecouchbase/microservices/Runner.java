package org.reactivecouchbase.microservices;

public class Runner {
    public static void main(String... args) {
        new ServiceProxyServer().run();
        new BikesServer().run();
        new ContainersServer().run();
        new AggregatorServer().run();
    }
}
