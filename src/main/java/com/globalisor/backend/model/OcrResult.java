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
@Document(collection = "ocr_results")
public class OcrResult {
    @Id
    private String id;

    private String userId;
    private String requirementId;
    private String documentType; // NRIC, FIN, Passport, AddressProof, Bizfile, CertIncorporation, Constitution
    private String fieldPath; // e.g., "directors[0].docs.idDoc"
    private String fileName;
    private String fileBase64Preview; // small thumbnail base64
    private String status; // UPLOADING, PROCESSING, COMPLETE, FAILED
    private Double confidence; // 0.0 - 1.0
    private Map<String, Object> extractedFields;
    private String rawText;
    private Date createdAt = new Date();
    private Date updatedAt = new Date();
    private boolean reviewedByClient = false;
    private List<String> warnings;
}
