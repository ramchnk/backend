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
@Document(collection = "onboarding_config")
public class OnboardingConfig {

    @Id
    private String id;

    // Step identity
    private String key;
    private String field;
    private String title;
    private String icon;
    private String description;
    private String declaration;
    private String helpText;
    private String bannerText;

    // Ordering and state
    private int sortOrder = 0;
    private String status = "DRAFT"; // DRAFT, PUBLISHED, UNPUBLISHED, ARCHIVED
    private String journeyType = "LOCAL"; // LOCAL or FOREIGNER
    private boolean mandatory = true;

    // Dynamic sections config
    private boolean dynamicSection = false;  // true for Director/Shareholder sections
    private String dynamicCountKey = "";     // e.g. "directorCount", "shareholderCount"

    // Form fields config
    private List<FieldConfig> manualFields;

    // Document requirements
    private List<DocumentConfig> requiredDocs;

    // OCR extracted fields
    private List<String> extractedFields;

    // Auto checks (AML, KYC etc)
    private List<String> autoChecks;

    // Version tracking
    private int version = 1;
    private List<VersionLog> history;

    // Audit
    private String createdBy;
    private Date createdAt = new Date();
    private String lastUpdatedBy = "Admin";
    private Date lastUpdatedAt = new Date();

    // ---- Nested Classes ----

    @Data
    @NoArgsConstructor
    public static class FieldConfig {
        private String key;
        private String label;
        private String type; // text, email, phone, nationality, date, number, select, checkbox, textarea, file
        private boolean mandatory = false;
        private boolean readonly = false;
        private String placeholder;
        private List<String> options;
        private String helpText;
        private String conditionalOn;   // field key this field is conditional on
        private String conditionalValue; // value that must be set for this field to appear
        private int sortOrder = 0;
    }

    @Data
    @NoArgsConstructor
    public static class DocumentConfig {
        private String type;
        private String label;
        private boolean required = true;
        private boolean ocrEnabled = false;
        private List<String> ocrMappedFields; // field keys populated by OCR
        private String helpText;
        private String conditionalOn;
        private String conditionalValue;
    }

    @Data
    @NoArgsConstructor
    public static class VersionLog {
        private int version;
        private String updatedBy;
        private Date updatedAt;
        private String changeNote;
        private Object snapshot; // serialized state at that version
    }
}
