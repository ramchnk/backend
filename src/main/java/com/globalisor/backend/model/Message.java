package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Document(collection = "messages")
public class Message {
    @Id
    private String id;
    private String clientId;
    private String senderId;
    private String senderName;
    private String senderRole;
    private String text;
    private Long timestamp = System.currentTimeMillis();
}
