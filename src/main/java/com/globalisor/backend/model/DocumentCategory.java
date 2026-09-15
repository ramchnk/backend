package com.globalisor.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "document_categories")
public class DocumentCategory {
    @Id
    private String id;
    private String key;         // e.g. "KYC", "Invoice", "Board Resolutions"
    private String label;       // e.g. "KYC", "Invoice", "Board Resolutions"
    private String description; // e.g. "Passport, NRIC, Proof of Address"
    private String icon;        // e.g. "shield-check", "file-text", "folder"
    private String color;       // e.g. "blue", "emerald", "purple"
    private Integer sortOrder;
    private Boolean isSystem;
}
