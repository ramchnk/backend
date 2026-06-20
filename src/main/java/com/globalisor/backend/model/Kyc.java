package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Document(collection = "kyc")
public class Kyc {
    @Id
    private String id;
    private String clientId;
    private String name;
    private String idType;
    private String idNum;
    private String idExpiry;
    private String nation;
    private String status = "pending";
    private String risk = "Low";
    private Long lastUpdated = System.currentTimeMillis();

    // Shufti sub-verification statuses
    private String identityStatus = "pending"; // pending, verified, failed
    private String amlStatus = "pending";      // pending, clean, flagged
    private String pepStatus = "pending";      // pending, clean, match
    private String sanctionsStatus = "pending";  // pending, clean, match

    // Manual review overrides
    private String overrideNotes;
    private String overrideBy;
    private Long overrideAt;

    private String shuftiRef;

    private java.util.List<String> auditLogs = new java.util.ArrayList<>();
}
