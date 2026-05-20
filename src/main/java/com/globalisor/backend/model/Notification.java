package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Document(collection = "notifications")
public class Notification {
    @Id
    private String id;
    private String clientId;
    private String title;
    private String message;
    private String type;
    private String relatedId;
    private Long timestamp = System.currentTimeMillis();
    private List<String> readBy = new ArrayList<>();
}
