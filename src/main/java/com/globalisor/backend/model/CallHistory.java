package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Document(collection = "call_history")
public class CallHistory {
    @Id
    private String id;
    private String callerId;
    private String callerName;
    private String receiverId;
    private String receiverName;
    private String mediaType; // voice, video
    private Long timestamp = System.currentTimeMillis();
    private Long duration = 0L; // in seconds
    private String status; // completed, missed, declined
}
