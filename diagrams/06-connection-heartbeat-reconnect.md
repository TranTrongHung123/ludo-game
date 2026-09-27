# Sơ đồ Kết nối, Heartbeat & Khôi phục phiên

## Giao thức TCP Framing

```mermaid
graph LR
    subgraph Frame["TCP Frame"]
        LEN["4 byte<br/>Big-endian<br/>Độ dài payload"]
        JSON["JSON UTF-8<br/>Payload<br/>(≤ 64KB)"]
        LEN --> JSON
    end

    subgraph Envelope["MessageEnvelope"]
        direction TB
        T["type: MessageType"]
        RID["requestId: string"]
        SID["sessionId: string"]
        SUC["success: bool"]
        D["data: JsonNode"]
        E["error: {code, message}"]
    end
```

## Luồng Heartbeat (PING/PONG)

```mermaid
sequenceDiagram
    participant S as Server<br/>(HeartbeatManager)
    participant C as Client

    Note over S: scheduler chạy mỗi 10s

    loop Mỗi 10 giây
        S->>C: PING {timestamp}
        S->>S: markPingSent()<br/>awaitingPong = true

        alt Client phản hồi kịp
            C->>S: PONG
            S->>S: acknowledge()<br/>missedHeartbeats = 0<br/>awaitingPong = false
        end

        alt Client không phản hồi
            S->>S: recordMissIfAwaiting()<br/>missedHeartbeats++
        end
    end

    alt missedHeartbeats ≥ 3
        S->>S: Đóng connection
        S->>S: onDisconnected()
        Note over S: Chuyển xử lý<br/>mất kết nối
    end
```

## Luồng Mất kết nối & Grace Period

```mermaid
sequenceDiagram
    participant C as Client
    participant TCP as TcpServer
    participant HB as HeartbeatManager
    participant SM as SessionManager
    participant RS as RoomService
    participant GR as GameRoom
    participant GE as GameEngine
    participant Others as Other Players

    Note over C: Mất mạng / đóng app

    alt Heartbeat timeout
        HB->>HB: 3 PING liên tiếp<br/>không có PONG
        HB->>TCP: connection.close()
    end

    TCP->>TCP: onDisconnected(connection)
    TCP->>SM: disconnect(connectionId)

    SM->>SM: markDisconnected(now)
    SM->>SM: Giữ session trong map
    SM->>SM: presenceState = DISCONNECTED
    SM->>SM: Đặt lịch hết hạn<br/>= now + 60s

    SM->>SM: publishSessionsChanged()
    SM->>RS: onSessionsChanged()

    RS->>RS: Cập nhật presence<br/>trong snapshot phòng

    RS-->>Others: ROOM_UPDATED<br/>(player = DISCONNECTED)
    RS-->>Others: GAME_STATE<br/>(presence cập nhật)

    Note over GR: Trận vẫn tiếp tục!<br/>Player DISCONNECTED vẫn ACTIVE<br/>vẫn nhận lượt & deadline vẫn chạy

    alt Reconnect trong 60s
        Note over C: Xem sơ đồ Reconnect bên dưới
    end

    alt Hết 60s grace period
        SM->>SM: expireDisconnectedSession()<br/>Kiểm tra generation khớp
        SM->>SM: removeSession()
        SM->>SM: publishSessionsChanged()

        SM->>RS: onSessionsChanged()
        RS->>RS: Phát hiện userId<br/>không còn trong presence

        RS->>GR: forfeitActivePlayers([userId])
        GR->>GE: forfeitParticipants(state, [playerId])
        GE->>GE: Gán FORFEITED<br/>Hạng thấp nhất còn trống<br/>0 điểm<br/>Quân về bãi
        GE->>GE: finishIfOnlyOneActive()

        RS->>RS: departCurrent(userId)
        RS->>RS: persistCompletedMatch()
        RS->>RS: scheduleTurnTimeout()
        RS-->>Others: ROOM_UPDATED
        RS-->>Others: GAME_STATE_UPDATED

        alt Trận kết thúc
            RS-->>Others: GAME_OVER
        end
    end
```

## Luồng Reconnect

```mermaid
sequenceDiagram
    participant C as Client (reconnecting)
    participant TCP as TcpServer
    participant AMH as AuthMessageHandler
    participant SM as SessionManager
    participant SSP as SessionStateProvider
    participant RS as RoomService
    participant GR as GameRoom
    participant Others as Other Players

    Note over C: Client giữ sessionId trong RAM<br/>Kết nối TCP mới

    C->>TCP: Mở TCP connection mới
    TCP->>TCP: Cấp connectionId mới
    TCP->>HB: onConnected(connection)

    C->>AMH: RECONNECT {sessionId}
    AMH->>SM: reconnect(sessionId, newConnectionId)

    SM->>SM: Kiểm tra session tồn tại
    SM->>SM: Kiểm tra !connected<br/>(phải đang DISCONNECTED)

    alt Session đang connected
        SM-->>AMH: ACCOUNT_ALREADY_LOGGED_IN
        AMH-->>C: ERROR response
    end

    SM->>SM: Kiểm tra grace period
    alt Hết grace period
        SM->>SM: removeSession()
        SM->>SM: publishSessionsChanged()
        SM-->>AMH: SESSION_EXPIRED
        AMH-->>C: ERROR response
    end

    SM->>SM: session.reconnect(newConnectionId)
    SM->>SM: Cập nhật sessionIdByConnectionId
    SM->>SM: publishSessionsChanged()

    SM-->>AMH: PlayerSession

    AMH->>SSP: restore(session)
    SSP->>RS: restoreSession(session)
    RS->>GR: restore(session, presence)

    Note over GR: Chụp snapshot dưới lock:<br/>- RoomDto<br/>- GameStateDto<br/>- GameOverDto (nếu có)

    GR-->>RS: ReconnectResult
    RS-->>SSP: ReconnectResult
    SSP-->>AMH: ReconnectResult

    AMH-->>C: RECONNECT_RESULT response
    Note over C: ReconnectResult chứa:<br/>- restored: true<br/>- presenceState<br/>- room (snapshot phòng)<br/>- gameState (snapshot trận)<br/>- gameOver (kết quả nếu bỏ lỡ)

    SM->>RS: onSessionsChanged()
    RS-->>Others: ROOM_UPDATED (player connected lại)
    RS-->>Others: GAME_STATE (presence mới)

    Note over C: Client khôi phục UI<br/>từ snapshot nhận được<br/>Đặt quân tại vị trí chính thức<br/>KHÔNG phát lại animation
```

## Trạng thái hiện diện (PlayerPresenceState)

```mermaid
stateDiagram-v2
    [*] --> OFFLINE: Chưa đăng nhập

    OFFLINE --> IDLE: LOGIN thành công
    
    IDLE --> IN_ROOM: CREATE_ROOM / JOIN_ROOM
    IDLE --> OFFLINE: LOGOUT

    IN_ROOM --> IDLE: LEAVE_ROOM
    IN_ROOM --> PLAYING: START_GAME
    IN_ROOM --> DISCONNECTED: Mất kết nối

    PLAYING --> IDLE: LEAVE_ROOM<br/>(forfeit + rời)
    PLAYING --> IN_ROOM: Trận kết thúc
    PLAYING --> DISCONNECTED: Mất kết nối

    DISCONNECTED --> IDLE: Reconnect<br/>(nếu không ở phòng)
    DISCONNECTED --> IN_ROOM: Reconnect<br/>(phòng WAITING)
    DISCONNECTED --> PLAYING: Reconnect<br/>(đang trong trận)
    DISCONNECTED --> OFFLINE: Hết grace period<br/>(60 giây)

    OFFLINE --> [*]: Đăng xuất hoàn toàn
```
