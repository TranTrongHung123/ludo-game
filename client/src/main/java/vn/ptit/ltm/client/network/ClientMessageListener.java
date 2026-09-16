package vn.ptit.ltm.client.network;

import vn.ptit.ltm.common.protocol.MessageEnvelope;

public interface ClientMessageListener {
    void onMessage(MessageEnvelope message);

    default void onDisconnected(Throwable cause) {
    }
}
