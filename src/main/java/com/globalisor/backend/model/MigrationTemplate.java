package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Map;

@Data
@NoArgsConstructor
@Document(collection = "migration_templates")
public class MigrationTemplate {
    @Id
    private String id;
    private String name;
    private Map<String, String> excelMapping;
    private Long createdAt = System.currentTimeMillis();
}
