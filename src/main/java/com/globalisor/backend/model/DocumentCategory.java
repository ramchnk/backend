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
    private String parentKey;   // Key of parent category/subfolder, null for root
    private String parentId;    // ID of parent category/subfolder, null for root
    private String rootKey;     // Key of top-level Root Category (Level 0)
    @Builder.Default
    private Integer level = 0;  // 0 = Root Category, 1 = Sub-Folder Level 1, 2 = Nested Sub-Folder Level 2 (Max depth)
    private String fullPath;    // e.g. "Tax/FY 2024/Q1 Returns"
    @Builder.Default
    private java.util.List<String> subFolders = new java.util.ArrayList<>();
}
