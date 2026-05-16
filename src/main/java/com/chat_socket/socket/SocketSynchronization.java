package com.chat_socket.socket;

import org.springframework.transaction.support.TransactionSynchronization;

public class SocketSynchronization implements TransactionSynchronization {

    private final Runnable publish;

    SocketSynchronization(Runnable publish) {
        this.publish = publish;
    }

    @Override
    public void afterCommit() {
        publish.run();
    }
}
