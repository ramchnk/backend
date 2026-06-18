package com.globalisor.backend.service;

import com.globalisor.backend.model.Notification;
import com.globalisor.backend.repository.NotificationRepository;
import com.globalisor.backend.websocket.ChatWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ChatWebSocketHandler chatWebSocketHandler;

    public void sendNotification(String targetClientId, String title, String message, String type, String relatedId, String priority) {
        Notification notif = new Notification();
        notif.setId("notif-" + System.currentTimeMillis());
        notif.setClientId(targetClientId);
        notif.setTitle(title);
        notif.setMessage(message);
        notif.setType(type);
        notif.setRelatedId(relatedId);
        notif.setPriority(priority != null ? priority : "Info");
        notif.setTimestamp(System.currentTimeMillis());
        notif.setReadBy(new ArrayList<>());
        
        notificationRepository.save(notif);
        chatWebSocketHandler.broadcastNotification(notif);
    }
}
