package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;

@Data
@NoArgsConstructor
@Document(collection = "onboarding")
public class Onboarding {
    @Id
    private String id;
    private String clientId;
    private String clientEmail;
    private String clientName;

    // Portal activation flag
    private boolean portalActivated = false;
    private Long activatedAt;
    private String activatedBy;

    // Overall onboarding status
    private String status = "pending"; // pending, in_progress, submitted, under_review, approved, rejected

    // Progress percentage (0-100)
    private int progressPercent = 0;

    // Steps
    private OnboardingStep step1IndividualVerification = new OnboardingStep("individual_verification", "Individual Verification");
    private OnboardingStep step2DirectorDetails = new OnboardingStep("director_details", "Director Details");
    private OnboardingStep step3IndividualShareholder = new OnboardingStep("individual_shareholder", "Individual Shareholder Details");
    private OnboardingStep step4CorporateShareholder = new OnboardingStep("corporate_shareholder", "Corporate Shareholder Details");
    private OnboardingStep step5UBO = new OnboardingStep("ubo", "Ultimate Beneficial Owner");
    private OnboardingStep step6CorporateRep = new OnboardingStep("corporate_rep", "Corporate Representative");
    private OnboardingStep step7FinalDeclaration = new OnboardingStep("final_declaration", "Final Declaration & Consent");

    // Audit log
    private List<String> auditLogs = new ArrayList<>();

    // Dynamic steps created from admin
    private Map<String, OnboardingStep> dynamicSteps = new HashMap<>();

    private Long createdAt = System.currentTimeMillis();
    private Long updatedAt = System.currentTimeMillis();

    @Data
    @NoArgsConstructor
    public static class OnboardingStep {
        private String key;
        private String title;
        private String status = "pending"; // pending, submitted, under_review, approved, rejected, additional_info_required
        private Map<String, Object> data = new HashMap<>();
        private List<DocumentUpload> documents = new ArrayList<>();
        private String reviewedBy;
        private Long reviewedAt;
        private String reviewNotes;
        private List<String> auditLogs = new ArrayList<>();

        public OnboardingStep(String key, String title) {
            this.key = key;
            this.title = title;
        }
    }

    @Data
    @NoArgsConstructor
    public static class DocumentUpload {
        private String id;
        private String type; // nric, address_proof, bizfile, constitution, cert_incorporation, etc.
        private String label;
        private String fileName;
        private String fileData; // base64 or URL
        private String mimeType;
        private String status = "pending"; // pending, approved, rejected
        private Map<String, Object> extractedData = new HashMap<>(); // OCR extracted fields
        private Long uploadedAt = System.currentTimeMillis();
        private String uploadedBy;
    }
}
