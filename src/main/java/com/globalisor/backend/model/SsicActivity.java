package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Date;

@Data
@NoArgsConstructor
@Document(collection = "ssic_activities")
public class SsicActivity {
    @Id
    private String id;
    private String code;
    private String name;
    private String category;
    private String description;
    private String status = "PUBLISHED"; // "DRAFT", "PUBLISHED", "UNPUBLISHED"
    
    private String lastUpdatedBy = "Admin";
    private Date lastUpdatedAt = new Date();

    public SsicActivity(String id, String code, String name, String category, String description, String status) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.category = category;
        this.description = description;
        this.status = status;
        this.lastUpdatedAt = new Date();
        this.lastUpdatedBy = "System";
    }
}
