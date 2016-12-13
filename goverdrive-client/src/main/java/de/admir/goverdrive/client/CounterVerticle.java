package de.admir.goverdrive.client;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;

public class CounterVerticle extends AbstractVerticle {

    @Override
    public void start() throws Exception {
        vertx.eventBus().consumer("goverdrive.client.count", (Message<Integer> event) -> {
            System.out.println(event.body());
        });
    }
}
