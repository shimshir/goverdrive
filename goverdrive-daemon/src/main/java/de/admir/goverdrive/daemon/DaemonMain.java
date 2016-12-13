package de.admir.goverdrive.daemon;

import de.admir.goverdrive.core.config.VertxConfig;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;

public class DaemonMain {
    public static void main(String[] args) {
        Vertx.clusteredVertx(VertxConfig.OPTIONS, res -> {
            if (res.succeeded()) {
                System.out.println("The daemon has the clustered eventBus");

                Vertx vertx = res.result();
                vertx.eventBus().consumer("goverdrive.daemon.test", (Message<Integer> message) -> {
                    System.out.println(message.body());
                    message.reply("response: " + message.body());
                });

            } else {
                System.out.println("Daemon failed to obtain eventBus: " + res.cause());
            }
        });
    }
}
