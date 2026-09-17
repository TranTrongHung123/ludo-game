package vn.ptit.ltm.client.controller;

import vn.ptit.ltm.client.state.ConnectionState;

public interface ConnectionAwareController {
    void onConnectionStateChanged(ConnectionState state);

    default void dispose() {
    }
}
