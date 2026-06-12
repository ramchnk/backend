package com.globalisor.backend.controller;

import com.globalisor.backend.model.*;
import com.globalisor.backend.repository.*;
import com.globalisor.backend.websocket.ChatWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
public class MessagesEnhancementController {

    @Autowired
    private GroupChatRepository groupChatRepository;

    @Autowired
    private CallHistoryRepository callHistoryRepository;

    @Autowired
    private StarredMessageRepository starredMessageRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatWebSocketHandler chatWebSocketHandler;

    // --- Dynamic Group Chats ---

    @GetMapping("/groups")
    public ResponseEntity<List<GroupChat>> getGroups(@RequestParam String userId) {
        List<GroupChat> groups = groupChatRepository.findByMemberIdsContaining(userId);
        return ResponseEntity.ok(groups);
    }

    @PostMapping("/groups")
    public ResponseEntity<GroupChat> createGroup(@RequestBody GroupChat group) {
        if (group.getId() == null) {
            group.setId("group-" + System.currentTimeMillis());
        }
        if (group.getCreatedTime() == null) {
            group.setCreatedTime(System.currentTimeMillis());
        }
        GroupChat saved = groupChatRepository.save(group);
        
        // Notify members about new group creation
        broadcastGroupUpdate(saved);
        
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/groups/{id}")
    public ResponseEntity<GroupChat> renameGroup(@PathVariable String id, @RequestBody Map<String, String> body) {
        Optional<GroupChat> groupOpt = groupChatRepository.findById(id);
        if (groupOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        GroupChat group = groupOpt.get();
        if (body.containsKey("name")) {
            group.setName(body.get("name"));
        }
        if (body.containsKey("description")) {
            group.setDescription(body.get("description"));
        }
        GroupChat saved = groupChatRepository.save(group);
        
        // Notify members
        broadcastGroupUpdate(saved);
        
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/groups/{id}/members")
    public ResponseEntity<GroupChat> addMembers(@PathVariable String id, @RequestBody Map<String, List<String>> body) {
        Optional<GroupChat> groupOpt = groupChatRepository.findById(id);
        if (groupOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        GroupChat group = groupOpt.get();
        List<String> userIds = body.get("userIds");
        if (userIds != null) {
            for (String uId : userIds) {
                if (!group.getMemberIds().contains(uId)) {
                    group.getMemberIds().add(uId);
                }
            }
        }
        GroupChat saved = groupChatRepository.save(group);
        
        // Notify members
        broadcastGroupUpdate(saved);
        
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/groups/{id}/members/{userId}")
    public ResponseEntity<GroupChat> removeMember(@PathVariable String id, @PathVariable String userId) {
        Optional<GroupChat> groupOpt = groupChatRepository.findById(id);
        if (groupOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        GroupChat group = groupOpt.get();
        group.getMemberIds().remove(userId);
        GroupChat saved = groupChatRepository.save(group);
        
        // Notify members
        broadcastGroupUpdate(saved);
        
        return ResponseEntity.ok(saved);
    }

    // --- Call History ---

    @GetMapping("/calls/history")
    public ResponseEntity<List<CallHistory>> getCallHistory(@RequestParam String userId) {
        List<CallHistory> history = callHistoryRepository.findByCallerIdOrReceiverIdOrderByTimestampDesc(userId, userId);
        return ResponseEntity.ok(history);
    }

    @PostMapping("/calls")
    public ResponseEntity<CallHistory> logCall(@RequestBody CallHistory call) {
        if (call.getId() == null) {
            call.setId("call-" + System.currentTimeMillis());
        }
        if (call.getTimestamp() == null) {
            call.setTimestamp(System.currentTimeMillis());
        }
        CallHistory saved = callHistoryRepository.save(call);
        return ResponseEntity.ok(saved);
    }

    // --- Starred Messages ---

    @GetMapping("/starred")
    public ResponseEntity<List<Message>> getStarredMessages(@RequestParam String userId) {
        List<StarredMessage> starredLinks = starredMessageRepository.findByUserId(userId);
        List<String> messageIds = starredLinks.stream()
                .map(StarredMessage::getMessageId)
                .collect(Collectors.toList());
        
        Iterable<Message> messages = messageRepository.findAllById(messageIds);
        List<Message> list = new ArrayList<>();
        messages.forEach(list::add);
        list.sort(Comparator.comparingLong(Message::getTimestamp));
        return ResponseEntity.ok(list);
    }

    @PostMapping("/starred/{messageId}")
    public ResponseEntity<?> starMessage(@PathVariable String messageId, @RequestParam String userId) {
        Optional<StarredMessage> existing = starredMessageRepository.findByUserIdAndMessageId(userId, messageId);
        if (existing.isEmpty()) {
            StarredMessage star = new StarredMessage(userId, messageId);
            star.setId("star-" + System.currentTimeMillis());
            starredMessageRepository.save(star);
            
            // Notify other tabs
            broadcastStarredUpdate(userId, messageId, true);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/starred/{messageId}")
    public ResponseEntity<?> unstarMessage(@PathVariable String messageId, @RequestParam String userId) {
        Optional<StarredMessage> existing = starredMessageRepository.findByUserIdAndMessageId(userId, messageId);
        if (existing.isPresent()) {
            starredMessageRepository.delete(existing.get());
            
            // Notify other tabs
            broadcastStarredUpdate(userId, messageId, false);
        }
        return ResponseEntity.ok().build();
    }

    // --- Users directory for Dynamic Groups ---

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        List<User> users = userRepository.findAll();
        List<Map<String, Object>> userList = new ArrayList<>();
        
        for (User u : users) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("name", (u.getFirstName() + " " + u.getLastName()).trim());
            map.put("role", u.getRole());
            map.put("email", u.getEmail());
            userList.add(map);
        }
        return ResponseEntity.ok(userList);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMessage(@PathVariable String id) {
        Optional<Message> msgOpt = messageRepository.findById(id);
        if (msgOpt.isPresent()) {
            messageRepository.deleteById(id);
            try {
                Map<String, Object> event = new HashMap<>();
                event.put("type", "message_deleted");
                event.put("messageId", id);
                event.put("clientId", msgOpt.get().getClientId());
                chatWebSocketHandler.broadcastEvent(event);
            } catch (Exception e) {}
        }
        return ResponseEntity.noContent().build();
    }

    private void broadcastGroupUpdate(GroupChat group) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "group_update");
            event.put("group", group);
            chatWebSocketHandler.broadcastEvent(event);
        } catch (Exception e) {
            // ignore
        }
    }

    private void broadcastStarredUpdate(String userId, String messageId, boolean isStarred) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "starred_update");
            event.put("userId", userId);
            event.put("messageId", messageId);
            event.put("isStarred", isStarred);
            chatWebSocketHandler.broadcastEvent(event);
        } catch (Exception e) {
            // ignore
        }
    }
}
