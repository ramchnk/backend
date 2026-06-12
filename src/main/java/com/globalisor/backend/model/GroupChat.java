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
    private Long createdTime = System.currentTimeMillis();
}
