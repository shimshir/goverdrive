package de.admir.goverdrive.core.config;

import com.hazelcast.config.Config;

import io.vertx.core.VertxOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public class VertxConfig {
    public static final Config HAZELCAST_CONFIG = new Config();
    public static final ClusterManager CLUSTER_MANAGER = new HazelcastClusterManager(HAZELCAST_CONFIG);

    public static final VertxOptions OPTIONS = new VertxOptions()
            .setClusterManager(CLUSTER_MANAGER)
            .setClusterHost("127.0.0.1");
}
