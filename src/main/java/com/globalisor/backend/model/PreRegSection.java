package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@Document(collection = "prereg_sections")
public class PreRegSection {
    @Id
    private String id;
    private String key;
    private String title;
    private String description;
    private String type = "form"; // "form" or "repeater"
    private Integer sortOrder;
    private String status = "DRAFT"; // "DRAFT", "PUBLISHED", "UNPUBLISHED"
    private List<Map<String, Object>> fields;
    private String applicableServices = "All";
    private String journeyType = "LOCAL"; // "LOCAL" or "FOREIGNER"

    // Checklist, FAQs, Attachments, and Documents
    private List<String> checklists;
    private List<Map<String, String>> faqs;
    private List<Map<String, String>> attachments;
    private List<String> documents;

    // Version History
    private String lastUpdatedBy = "Admin";
    private Date lastUpdatedAt = new Date();

    // Published snapshot data
    private Map<String, Object> publishedData;

    public PreRegSection(String id, String key, String title, String description, String type, Integer sortOrder, List<Map<String, Object>> fields) {
        this(id, key, title, description, type, sortOrder, fields, "LOCAL");
    }

    public PreRegSection(String id, String key, String title, String description, String type, Integer sortOrder, List<Map<String, Object>> fields, String journeyType) {
        this.id = id;
        this.key = key;
        this.title = title;
        this.description = description;
        this.type = type;
        this.sortOrder = sortOrder;
        this.fields = fields;
        this.applicableServices = "All";
        this.journeyType = journeyType;
        this.status = "PUBLISHED";
        this.lastUpdatedAt = new Date();
        this.lastUpdatedBy = "System";
        
        // Auto publish during initialization
        this.publishedData = Map.of(
            "id", id,
            "key", key,
            "title", title,
            "description", description,
            "type", type,
            "sortOrder", sortOrder,
            "fields", fields,
            "applicableServices", "All",
            "journeyType", journeyType
        );
    }
}
