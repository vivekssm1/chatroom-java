/**
 * ════════════════════════════════════════════════════════════════
 * FRONTEND WEBSOCKET MIGRATION: Socket.io → SockJS + STOMP
 * ════════════════════════════════════════════════════════════════
 *
 * Add these two scripts to index.html <head> (replace socket.io CDN):
 *
 * <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
 * <script src="https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js"></script>
 *
 * Then replace the entire connectSocket() function in index.html with this:
 */

// ══ STATE (same as before) ════════════════════════════════════════════════════
let stompClient = null;   // replaces: let socket = null

// ══ CONNECT (replaces connectSocket()) ═══════════════════════════════════════
function connectSocket() {
    if (stompClient && stompClient.connected) return;

    // SockJS replaces: io(window.location.origin, { auth: { token } })
    const sockJS = new SockJS("/ws");
    stompClient = Stomp.over(sockJS);

    // Suppress STOMP debug logs in production
    stompClient.debug = null;

    stompClient.connect(
        { token: getCookie("token") },   // replaces: auth: { token } in socket.io
        (frame) => {
            console.log("✅ STOMP connected");
        },
        (error) => {
            console.error("❌ STOMP error:", error);
            setTimeout(connectSocket, 3000);  // auto-reconnect
        }
    );
}

// ══ JOIN ROOM (replaces: socket.emit("join_room", { roomCode })) ══════════════
function enterRoom(room) {
    document.getElementById("chat-box").innerHTML = "";
    document.getElementById("pinned-bar").classList.remove("visible");
    showChatScreen(room);

    const roomCode = room.roomCode;

    // 1. Subscribe to new messages — replaces: socket.on("new_message", ...)
    stompClient.subscribe(`/topic/room/${roomCode}/messages`, (frame) => {
        const msg = JSON.parse(frame.body);
        renderMessage(msg);
        scrollBottom();
    });

    // 2. Subscribe to room events (delete, pin, reactions, room_deleted)
    //    replaces: socket.on("message_deleted"), socket.on("pin_updated"), etc.
    stompClient.subscribe(`/topic/room/${roomCode}/events`, (frame) => {
        const event = JSON.parse(frame.body);
        handleRoomEvent(event, roomCode);
    });

    // 3. Subscribe to user presence — replaces: socket.on("room_update", ...)
    stompClient.subscribe(`/topic/room/${roomCode}/presence`, (frame) => {
        const { userCount, users } = JSON.parse(frame.body);
        document.getElementById("chat-user-count").textContent = userCount;
        document.getElementById("chat-online").textContent = userCount + " online";
        document.getElementById("users-list").innerHTML = users.map(u => `
            <div style="display:flex;align-items:center;gap:8px;font-size:13px">
                <div class="avatar" style="width:26px;height:26px;font-size:10px;background:${u.avatarColor}">${esc(u.initials)}</div>
                <span>${esc(u.name)}</span>
            </div>`).join("");
    });

    // 4. Subscribe to typing events — replaces: socket.on("user_typing", ...)
    stompClient.subscribe(`/topic/room/${roomCode}/typing`, (frame) => {
        const { username, typing } = JSON.parse(frame.body);
        if (username === currentUser?.name) return;
        if (typing) {
            typingUsers.add(username);
        } else {
            typingUsers.delete(username);
        }
        updateTypingBar();
    });

    // 5. Subscribe to personal history — replaces: socket.on("chat_history", ...)
    stompClient.subscribe(`/user/queue/history-${roomCode}`, (frame) => {
        const messages = JSON.parse(frame.body);
        const box = document.getElementById("chat-box");
        box.innerHTML = "";
        messages.forEach(renderMessage);
        scrollBottom();
    });

    // 6. Subscribe to personal errors — replaces: socket.on("error_msg", ...)
    stompClient.subscribe(`/user/queue/errors`, (frame) => {
        const { error } = JSON.parse(frame.body);
        alert(error);
    });

    // 7. Send join — replaces: socket.emit("join_room", { roomCode })
    stompClient.send(`/app/room/${roomCode}/join`, {}, JSON.stringify({}));
}

// ══ HANDLE ROOM EVENTS ════════════════════════════════════════════════════════
function handleRoomEvent(event, roomCode) {
    switch (event.type) {

        // replaces: socket.on("message_deleted", ...)
        case "message_deleted": {
            const el = document.getElementById("msg-" + event.messageId);
            if (el) el.remove();
            break;
        }

        // replaces: socket.on("pin_updated", ...)
        case "pin_updated": {
            document.querySelectorAll(".pinned-bubble").forEach(el => el.classList.remove("pinned-bubble"));
            const bar = document.getElementById("pinned-bar");
            const pinnedMsg = event.pinnedMessage;
            if (pinnedMsg && pinnedMsg._id && !pinnedMsg.deleted) {
                bar.classList.add("visible");
                document.getElementById("pinned-text").textContent =
                    (pinnedMsg.username || "") + ": " + (pinnedMsg.text || "");
                document.getElementById("unpin-btn").style.display = currentRoom?.isOwner ? "block" : "none";
                const el = document.getElementById("msg-" + pinnedMsg._id);
                if (el) el.querySelector(".bubble")?.classList.add("pinned-bubble");
            } else {
                bar.classList.remove("visible");
            }
            break;
        }

        // replaces: socket.on("reaction_updated", ...)
        case "reaction_updated": {
            const rrow = document.getElementById("react-" + event.messageId);
            if (rrow) {
                rrow.innerHTML = event.reactions && event.reactions.length > 0
                    ? buildReactionPills(event.reactions, event.messageId, roomCode)
                    : "";
            }
            break;
        }

        // replaces: socket.on("room_deleted", ...)
        case "room_deleted": {
            alert("This room was deleted.");
            leaveRoom();
            break;
        }
    }
}

// ══ SEND MESSAGE (replaces: socket.emit("send_message", { roomCode, text })) ══
function sendMessage() {
    const input = document.getElementById("msg-input");
    const text = input.value.trim();
    if (!text || !stompClient || !currentRoom) return;

    // replaces: socket.emit("send_message", { roomCode, text })
    stompClient.send(`/app/room/${currentRoom.roomCode}/message`, {},
        JSON.stringify({ text }));

    input.value = "";
    input.style.height = "auto";
    stopTyping();
}

// ══ TYPING (replaces socket.emit("typing_start/stop", ...)) ══════════════════
function startTypingEmit() {
    // replaces: socket?.emit("typing_start", { roomCode })
    stompClient?.send(`/app/room/${currentRoom?.roomCode}/typing`, {}, "{}");
}
function stopTypingEmit() {
    // replaces: socket?.emit("typing_stop", { roomCode })
    stompClient?.send(`/app/room/${currentRoom?.roomCode}/typing-stop`, {}, "{}");
}

// ══ DELETE MESSAGE (replaces socket.emit("delete_message", ...)) ══════════════
function deleteMessageEmit(messageId) {
    // replaces: socket.emit("delete_message", { messageId, roomCode })
    stompClient?.send(`/app/room/${currentRoom?.roomCode}/delete`, {},
        JSON.stringify({ messageId, roomCode: currentRoom?.roomCode }));
}

// ══ REACT (replaces socket.emit("toggle_reaction", ...)) ══════════════════════
function toggleReaction(msgId, roomCode, emoji) {
    if (!stompClient || !roomCode) return;
    // replaces: socket.emit("toggle_reaction", { messageId, roomCode, emoji })
    stompClient.send(`/app/room/${roomCode}/react`, {},
        JSON.stringify({ messageId: msgId, roomCode, emoji }));
}

// ══ SEND MEDIA (replaces socket.emit("send_media", ...)) ══════════════════════
function confirmSendMedia() {
    if (!pendingFileData || !currentRoom || !stompClient) return;
    const caption = document.getElementById("preview-caption").value.trim()
        || (pendingFileData.type === "video" ? "📹 Video" : "📷 Photo");

    document.getElementById("preview-actions").style.display = "none";
    document.getElementById("preview-progress").style.display = "block";

    // replaces: socket.emit("send_media", { roomCode, mediaData, ... })
    stompClient.send(`/app/room/${currentRoom.roomCode}/media`, {},
        JSON.stringify({
            roomCode:  currentRoom.roomCode,
            mediaData: pendingFileData.rawBase64,
            mediaMime: pendingFileData.mime,
            mediaType: pendingFileData.type,
            mediaMode: pendingMediaMode,
            caption
        })
    );

    setTimeout(cancelMediaPreview, 2000);
}

// ══ LEAVE ROOM ════════════════════════════════════════════════════════════════
function leaveRoom() {
    // Unsubscribe from all room topics
    if (stompClient && currentRoom) {
        // STOMP auto-unsubscribes when you navigate away; explicit unsubscribe optional
    }
    currentRoom = null;
    document.getElementById("chat-box").innerHTML = "";
    showScreen("screen-rooms");
    loadRooms();
}

// ══ LOGOUT (replaces socket.disconnect()) ════════════════════════════════════
async function doLogout() {
    await fetch("/api/logout", { method: "POST" });
    stompClient?.disconnect();
    stompClient = null;
    currentUser = null;
    currentRoom = null;
    showScreen("screen-auth");
}
