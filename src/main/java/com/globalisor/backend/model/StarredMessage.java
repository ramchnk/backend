package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Document(collection = "starred_messages")
public class StarredMessage {
    @Id
    private String id;
    private String userId;
    private String messageId;
    private Long timestamp = System.currentTimeMillis();

    public StarredMessage(String userId, String messageId) {
        this.userId = userId;
        this.messageId = messageId;
    }
}
