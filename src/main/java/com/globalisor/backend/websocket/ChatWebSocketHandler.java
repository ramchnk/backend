package com.globalisor.backend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.globalisor.backend.model.User;
import com.globalisor.backend.model.Notification;
import com.globalisor.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Active WebSocket sessions
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    // Map of userId to sessionIds (since a user can have multiple tabs/connections)
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> userSessions = new ConcurrentHashMap<>();

    // Map of userId to presence status
    private final ConcurrentHashMap<String, String> userPresenceStatus = new ConcurrentHashMap<>();

    @Autowired
    public ChatWebSocketHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Helper method to get map of online users
    public boolean isUserOnline(String userId) {
        CopyOnWriteArrayList<String> active = userSessions.get(userId);
        return active != null && !active.isEmpty();
    }

    public String getUserStatus(String userId) {
        return userPresenceStatus.getOrDefault(userId, "offline");
    }

    public void setUserStatus(String userId, String status) {
        if (status == null) return;
        userPresenceStatus.put(userId, status);
        try {
            Map<String, Object> event = new ConcurrentHashMap<>();
            event.put("type", "presence");
            event.put("userId", userId);
            event.put("status", status);
            String payload = objectMapper.writeValueAsString(event);
            broadcastAll(payload);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);

        // Parse query params to identify user: e.g. ws://localhost:8080/api/ws/chat?userId=...&role=...
        URI uri = session.getUri();
        if (uri != null) {
            String query = uri.getQuery();
            if (query != null) {
                String userId = getQueryParam(query, "userId");
                String role = getQueryParam(query, "role");
                if (userId != null) {
                    session.getAttributes().put("userId", userId);
                    session.getAttributes().put("role", role);

                    userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(session.getId());
                    userPresenceStatus.put(userId, "online");

                    // Broadcast online status
                    broadcastPresence(userId, true, null);
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());

        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            CopyOnWriteArrayList<String> active = userSessions.get(userId);
            if (active != null) {
                active.remove(session.getId());
                if (active.isEmpty()) {
                    userSessions.remove(userId);
                    userPresenceStatus.put(userId, "offline");
                    
                    // Mark last seen in DB
                    Long lastSeen = System.currentTimeMillis();
                    Optional<User> userOpt = userRepository.findById(userId);
                    if (userOpt.isPresent()) {
                        User user = userOpt.get();
                        user.setLastSeenTime(lastSeen);
                        userRepository.save(user);
                    }
                    
                    // Broadcast offline status
                    broadcastPresence(userId, false, lastSeen);
                }
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        // Parse message type
        Map<String, Object> data = objectMapper.readValue(payload, Map.class);
        String type = (String) data.get("type");

        if ("typing".equals(type)) {
            // Broadcast typing event to all sessions so members of group channels see it
            broadcastAll(payload);
        } else if ("call_signal".equals(type)) {
            String targetUserId = (String) data.get("targetUserId");
            if (targetUserId != null) {
                broadcastToUser(targetUserId, payload);
            }
        } else if ("presence".equals(type) || "status_change".equals(type)) {
            broadcastAll(payload);
        }
    }

    public void broadcastEvent(Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            broadcastAll(payload);
        } catch (Exception e) {
            // ignore
        }
    }

    private void broadcastPresence(String userId, boolean isOnline, Long lastSeen) {
        try {
            Map<String, Object> event = new ConcurrentHashMap<>();
            event.put("type", "presence");
            event.put("userId", userId);
            event.put("status", isOnline ? "online" : "offline");
            if (lastSeen != null) {
                event.put("lastSeen", lastSeen);
            }
            String payload = objectMapper.writeValueAsString(event);
            broadcastAll(payload);
        } catch (Exception e) {
            // ignore
        }
    }

    public void broadcastMessageNotification(Object message) {
        try {
            Map<String, Object> event = new ConcurrentHashMap<>();
            event.put("type", "message");
            event.put("message", message);
            String payload = objectMapper.writeValueAsString(event);
            broadcastAll(payload);
        } catch (Exception e) {
            // ignore
        }
    }

    public void broadcastReadReceipt(String clientId, String senderRole) {
        try {
            Map<String, Object> event = new ConcurrentHashMap<>();
            event.put("type", "read_receipt");
            event.put("clientId", clientId);
            event.put("senderRole", senderRole);
            String payload = objectMapper.writeValueAsString(event);
            broadcastAll(payload);
        } catch (Exception e) {
            // ignore
        }
    }

    public void broadcastDocumentSync(Object document) {
        try {
            Map<String, Object> event = new ConcurrentHashMap<>();
            event.put("type", "document_sync");
            event.put("document", document);
            String payload = objectMapper.writeValueAsString(event);
            broadcastAll(payload);
        } catch (Exception e) {
            // ignore
        }
    }

    public void broadcastNotification(Notification notification) {
        try {
            Map<String, Object> event = new ConcurrentHashMap<>();
            event.put("type", "notification");
            event.put("notification", notification);
            String payload = objectMapper.writeValueAsString(event);
            
            String target = notification.getClientId();
            if ("all".equals(target)) {
                broadcastAll(payload);
            } else if ("admin".equals(target) || "staff".equals(target)) {
                broadcastToRole(payload, "admin", "staff");
            } else if (target != null && target.startsWith("chat_")) {
                String[] parts = target.split("_");
                if (parts.length >= 3) {
                    broadcastToUser(parts[1], payload);
                    broadcastToUser(parts[2], payload);
                }
            } else if (target != null && target.startsWith("team_chat_")) {
                String[] parts = target.split("_");
                if (parts.length >= 4) {
                    broadcastToUser(parts[2], payload);
                    broadcastToUser(parts[3], payload);
                }
            } else if (target != null && target.startsWith("team_group_")) {
                broadcastToRole(payload, "admin", "staff");
            } else {
                broadcastToUser(target, payload);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private void broadcastAll(String payload) {
        TextMessage textMessage = new TextMessage(payload);
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private void broadcastToUser(String userId, String payload) {
        CopyOnWriteArrayList<String> active = userSessions.get(userId);
        if (active != null) {
            TextMessage textMessage = new TextMessage(payload);
            for (String sessionId : active) {
                WebSocketSession session = sessions.get(sessionId);
                if (session != null && session.isOpen()) {
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        }
    }

    private void broadcastToRole(String payload, String... roles) {
        TextMessage textMessage = new TextMessage(payload);
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                String sessionRole = (String) session.getAttributes().get("role");
                if (sessionRole != null) {
                    for (String role : roles) {
                        if (role.equalsIgnoreCase(sessionRole)) {
                            try {
                                session.sendMessage(textMessage);
                            } catch (IOException e) {
                                // ignore
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    private String getQueryParam(String query, String name) {
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1 && entry[0].equals(name)) {
                return entry[1];
            }
        }
        return null;
    }
}
