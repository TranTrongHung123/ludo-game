# Sơ đồ Kết thúc Trận, Tính điểm & Chơi lại

## Điều kiện kết thúc trận

```mermaid
flowchart TD
    Start["Sau mỗi nước đi / forfeit"] --> Check{"Kiểm tra<br/>kết thúc trận"}

    Check --> C1{"Người chơi hoàn thành<br/>4 quân FINISHED?"}
    C1 -->|Có| Complete["COMPLETED<br/>Gán hạng tốt nhất còn trống"]
    C1 -->|Không| C2

    Complete --> ActiveCheck

    C2{"Người chơi forfeit?<br/>(quit / hết grace)"}
    C2 -->|Có| Forfeit["FORFEITED<br/>Gán hạng thấp nhất còn trống<br/>0 điểm, quân về bãi"]
    C2 -->|Không| Continue["Tiếp tục trận"]

    Forfeit --> ActiveCheck

    ActiveCheck{"Còn bao nhiêu<br/>ACTIVE?"}
    ActiveCheck -->|"> 1"| Continue
    ActiveCheck -->|"= 1"| LastMan["Người cuối ACTIVE<br/>→ COMPLETED<br/>Gán hạng tốt nhất còn trống"]
    ActiveCheck -->|"= 0"| GameEnd["Trận kết thúc"]
    LastMan --> GameEnd

    GameEnd --> Persist["Lưu kết quả vào DB"]
    Persist --> Broadcast["Broadcast GAME_OVER"]
```

## Hệ thống tính điểm

```mermaid
graph TB
    subgraph Score2["Phòng 2 người"]
        S2R1["Hạng 1: 3.0 điểm"]
        S2R2["Hạng 2: 0 điểm"]
    end

    subgraph Score3["Phòng 3 người"]
        S3R1["Hạng 1: 3.0 điểm"]
        S3R2["Hạng 2: 1.5 điểm"]
        S3R3["Hạng 3: 0 điểm"]
    end

    subgraph Score4["Phòng 4 người"]
        S4R1["Hạng 1: 3.0 điểm"]
        S4R2["Hạng 2: 1.5 điểm"]
        S4R3["Hạng 3: 0.5 điểm"]
        S4R4["Hạng 4: 0 điểm"]
    end

    subgraph ForfeitRule["Quy tắc Forfeit"]
        FR1["Forfeit luôn = 0 điểm"]
        FR2["Forfeit nhận hạng thấp nhất<br/>còn trống tại thời điểm bỏ cuộc"]
    end
```

## Ví dụ tính hạng (4 người)

```mermaid
sequenceDiagram
    participant A as Player A
    participant B as Player B
    participant C as Player C
    participant D as Player D
    participant GE as GameEngine

    Note over A,D: Trận 4 người bắt đầu

    D->>GE: Quit (forfeit)
    GE->>GE: nextWorstRank() = 4
    Note over D: Hạng 4, 0 điểm<br/>Quân về bãi

    C->>GE: Mất mạng, hết grace
    GE->>GE: nextWorstRank() = 3
    Note over C: Hạng 3, 0 điểm<br/>(forfeit)

    Note over A,B: 2 ACTIVE còn lại

    A->>GE: Hoàn thành 4 quân
    GE->>GE: nextBestRank() = 1
    Note over A: Hạng 1, 3.0 điểm<br/>COMPLETED

    GE->>GE: finishIfOnlyOneActive()
    GE->>GE: nextBestRank() = 2
    Note over B: Hạng 2, 1.5 điểm<br/>COMPLETED (tự động)

    Note over GE: Trận kết thúc!
```

## Luồng lưu kết quả (persistCompletedMatch)

```mermaid
sequenceDiagram
    participant RS as RoomService
    participant GR as GameRoom
    participant MS as MatchService
    participant MR as MatchRepository
    participant SM as SessionManager
    participant DB as MySQL

    RS->>GR: completedMatchSnapshot()
    Note over GR: Chỉ khi roomState = FINISHED<br/>và chưa markMatchPersisted

    GR-->>RS: CompletedMatchRecord<br/>{matchId, startedAt, endedAt, players[]}

    RS->>MS: completeMatch(roomId, match)

    MS->>MR: saveCompletedMatch(match)
    MR->>DB: BEGIN TRANSACTION

    MR->>DB: INSERT INTO matches<br/>(public_id, started_at, ended_at, status)
    
    loop Mỗi participant
        MR->>DB: INSERT INTO match_players<br/>(match_id, user_id, color, rank,<br/>score_earned, result)
        
        alt score > 0
            MR->>DB: UPDATE users<br/>SET score = score + earned
        end
        
        alt rank = 1
            MR->>DB: UPDATE users<br/>SET first_place_count += 1
        end
    end

    MR->>DB: COMMIT
    
    Note over MR: public_id đảm bảo<br/>retry không cộng điểm 2 lần

    MR->>DB: SELECT updated user stats
    MR-->>MS: PersistedMatchResult

    MS->>SM: updateStatisticsForUser()<br/>cho mỗi player
    Note over SM: Đồng bộ điểm vào session<br/>để sảnh hiển thị đúng

    MS-->>RS: GameOverDto
    RS->>GR: markMatchPersisted(gameOver)

    Note over GR: Đánh dấu đã lưu<br/>Lần gọi sau sẽ bỏ qua
```

## Luồng Chơi lại (Rematch)

```mermaid
sequenceDiagram
    participant P1 as Player 1
    participant P2 as Player 2
    participant AMH as AuthMessageHandler
    participant RS as RoomService
    participant GR as GameRoom
    participant SM as SessionManager

    Note over P1,P2: Màn kết quả (Result Screen)

    alt Chơi tiếp
        P1->>AMH: READY {roomId, ready: true}
        AMH->>RS: setReady(...)

        RS->>RS: persistCompletedMatch()
        Note over RS: Đảm bảo kết quả đã lưu<br/>trước khi mở lại

        RS->>GR: reopenForRematch(userId)

        Note over GR: Phòng FINISHED → WAITING<br/>- Loại thành viên đã rời<br/>- Giữ roomId, slot, màu, host<br/>- Reset Ready tất cả<br/>- Đặt Ready cho người yêu cầu

        RS->>RS: timeoutManager.cancel(roomId)
        RS->>RS: invalidateInvitationsForRoom()

        loop Mỗi thành viên còn lại
            RS->>SM: updatePresenceForUser(IN_ROOM)
        end

        GR->>GR: setReady(userId, true)

        RS-->>P1: READY response + RoomDto
        RS-->>P1: ROOM_UPDATED (state: WAITING)
        RS-->>P2: ROOM_UPDATED (state: WAITING)

        Note over P1,P2: Client xóa cache trận cũ<br/>Quay về giao diện phòng chờ
    end

    alt Về sảnh
        P2->>AMH: LEAVE_ROOM {roomId}
        AMH->>RS: leaveRoom(...)
        RS->>SM: updatePresence(IDLE)
        RS-->>P2: LEAVE_ROOM response
        RS-->>P1: ROOM_UPDATED
        Note over P2: Chuyển về Sảnh (Lobby)
    end
```

## Vòng đời participant trong trận (MatchParticipantStatus)

```mermaid
stateDiagram-v2
    [*] --> ACTIVE: START_GAME

    ACTIVE --> ACTIVE: Di chuyển quân<br/>Tung xúc xắc<br/>Mất kết nối (trong grace)

    ACTIVE --> COMPLETED: Hoàn thành 4 quân<br/>hoặc là ACTIVE cuối cùng
    ACTIVE --> FORFEITED: Quit chủ động<br/>hoặc hết grace period

    COMPLETED --> [*]: Nhận hạng + điểm
    FORFEITED --> [*]: Hạng thấp nhất + 0 điểm
```
