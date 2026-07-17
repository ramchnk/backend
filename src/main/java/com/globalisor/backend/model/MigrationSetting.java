package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Document(collection = "migration_settings")
public class MigrationSetting {
    @Id
    private String id = "default";
    private double ocrConfidenceThreshold = 0.85;
    private String deduplicationStrategy = "IGNORE_DUPLICATES"; // IGNORE_DUPLICATES, OVERWRITE, MERGE
}
