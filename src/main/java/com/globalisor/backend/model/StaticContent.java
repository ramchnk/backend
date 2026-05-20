package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Document(collection = "staticContent")
public class StaticContent {
    @Id
    private String id;
    private String title;
    private String description;
    private String content;
    private String portal;
    private String category;
    private Boolean isPublished = true;
    private Boolean isPinned = false;
    private Long createdAt = System.currentTimeMillis();
    private Long updatedAt = System.currentTimeMillis();
}
