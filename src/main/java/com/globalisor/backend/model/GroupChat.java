package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Document(collection = "group_chats")
public class GroupChat {
    @Id
    private String id;
    private String name;
    private String description;
    private List<String> memberIds = new ArrayList<>();
    private String createdBy;
    private String icon = "💬";
    private Long createdTime = System.currentTimeMillis();
    
    private String privacy = "Public";
    private List<String> admins = new ArrayList<>();
    private List<String> pinnedMessages = new ArrayList<>();
    private List<String> announcements = new ArrayList<>();
    private Boolean isArchived = false;
    private List<String> mutedBy = new ArrayList<>();
    private List<String> starredBy = new ArrayList<>();
}

