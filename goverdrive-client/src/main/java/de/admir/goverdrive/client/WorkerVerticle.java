package de.admir.goverdrive.client;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.eventbus.Message;

public class WorkerVerticle extends AbstractVerticle {

    @Override
    public void start() throws Exception {
        for (int i = 1; i <= 1000000; i++) {
            final int innerCounter = i;
            //Thread.sleep(10);
            vertx.eventBus().send("goverdrive.daemon.test", innerCounter, (AsyncResult<Message<String>> response) -> {
                if (!response.succeeded()) {
                    response.cause().printStackTrace();
                } else {
                    vertx.eventBus().send("goverdrive.client.count", innerCounter);
                }
            });
        }
    }
}
