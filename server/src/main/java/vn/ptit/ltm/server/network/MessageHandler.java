package vn.ptit.ltm.server.network;

import vn.ptit.ltm.common.protocol.MessageEnvelope;

@FunctionalInterface
public interface MessageHandler {
    void handle(ClientConnection connection, MessageEnvelope message) throws Exception;
}
