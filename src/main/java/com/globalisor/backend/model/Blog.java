package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Document(collection = "blogs")
public class Blog {
    @Id
    private String id;
    private String title;
    private String excerpt;
    private String content;
    private String category;
    private String author;
    private String date; // e.g. "May 10, 2026"
    private Boolean published = true;
    private Long createdAt = System.currentTimeMillis();
    private Long updatedAt;
}
