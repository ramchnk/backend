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
    private String coverImage;
    private Boolean published = true;
    private String publishedTitle;
    private String publishedExcerpt;
    private String publishedContent;
    private String publishedCoverImage;
    private Boolean hasUnpublishedChanges = false;
    private Long createdAt = System.currentTimeMillis();
    private Long updatedAt;
}
