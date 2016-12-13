package de.admir.goverdrive.client;

import de.admir.goverdrive.core.config.VertxConfig;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;

public class ClientMain {

    public static void main(String[] args) {
        Vertx.clusteredVertx(VertxConfig.OPTIONS, res -> {
            if (res.succeeded()) {
                System.out.println("The client has the clustered eventBus");

                Vertx vertx = res.result();
                vertx.deployVerticle(new CounterVerticle(), completionResult -> {
                    if (completionResult.succeeded()) {
                        System.out.println("CounterVerticle deployed");
                        vertx.deployVerticle(new WorkerVerticle(), new DeploymentOptions().setWorker(true).setMaxWorkerExecuteTime(Long.MAX_VALUE));
                    }
                });
            } else {
                System.out.println("Client failed to obtain eventBus: " + res.cause());
            }
        });
    }
}
