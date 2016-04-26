package org.reactivecouchbase.microservices;

public class Runner {
    public static void main(String... args) {
    	int port = Integer.valueOf(System.getProperty("port"));
        new ServiceProxyServer(port).run();
        new BikesServer().run();
        new ContainersServer().run();
        new AggregatorServer().run();
    }
}
