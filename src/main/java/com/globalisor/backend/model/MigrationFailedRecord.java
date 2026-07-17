package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Document(collection = "migration_failed_records")
public class MigrationFailedRecord {
    @Id
    private String id;
    private String clientName;
    private String email;
    private String errorMessage;
    private String jobName;
    private Long date = System.currentTimeMillis();
}
