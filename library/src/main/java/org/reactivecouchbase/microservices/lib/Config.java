package org.reactivecouchbase.microservices.lib;

import com.typesafe.config.ConfigFactory;

public class Config {

    private static final com.typesafe.config.Config CONFIG =
            ConfigFactory.load().withFallback(ConfigFactory.empty());

    public static com.typesafe.config.Config underlying() {
        return CONFIG;
    }

    public static Config config() {
        return new Config("application");
    }

    private final String root;

    public Config(String root) {
        this.root = root;
    }

    public String mode() {
        String mode = CONFIG.getString("application.mode");
        if (mode == null) {
            return "dev";
        }
        return mode;
    }

    public boolean getBoolean(String path, boolean def) {
        if (CONFIG.hasPath(root + "." + path)) {
            return CONFIG.getBoolean(path);
        }
        return def;
    }
    public int getInt(String path, int def) {
        if (CONFIG.hasPath(root + "." + path)) {
            return CONFIG.getInt(path);
        }
        return def;
    }
    public long getLong(String path, long def) {
        if (CONFIG.hasPath(root + "." + path)) {
            return CONFIG.getLong(path);
        }
        return def;
    }
    public double getDouble(String path, double def) {
        if (CONFIG.hasPath(root + "." + path)) {
            return CONFIG.getDouble(path);
        }
        return def;
    }
    public String getString(String path, String def) {
        if (CONFIG.hasPath(root + "." + path)) {
            return CONFIG.getString(path);
        }
        return def;
    }
}
