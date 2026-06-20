package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Document(collection = "compliance")
public class Compliance {
    @Id
    private String id;
    private String clientId;
    private String name;
    private String type;
    private String status = "pending";
    private String risk = "Low";
    private Long lastUpdated = System.currentTimeMillis();

    // Shufti sub-verification statuses
    private String amlStatus = "pending";      // pending, clean, flagged
    private String pepStatus = "pending";      // pending, clean, match
    private String sanctionsStatus = "pending";  // pending, clean, match

    // Manual review overrides
    private String overrideNotes;
    private String overrideBy;
    private Long overrideAt;

    private java.util.List<String> auditLogs = new java.util.ArrayList<>();
}
