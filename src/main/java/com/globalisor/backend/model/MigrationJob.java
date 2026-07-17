package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

@Data
@NoArgsConstructor
@Document(collection = "migration_jobs")
public class MigrationJob {
    @Id
    private String id;
    private String name;
    private String status = "COMPLETED"; // PENDING, IN_PROGRESS, COMPLETED, FAILED, PAUSED
    private int totalRecords = 0;
    private int processedRecords = 0;
    private int successCount = 0;
    private int failedCount = 0;
    private int duplicateCount = 0;
    private double avgOcrConfidence = 0.98;
    private Long createdAt = System.currentTimeMillis();
    private List<String> logs = new ArrayList<>();
    private List<Map<String, Object>> records = new ArrayList<>();
}
