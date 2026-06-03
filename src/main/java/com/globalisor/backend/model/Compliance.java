package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Document(collection = "compliance")
public class Compliance {
    @Id
    private String id;
    private String clientId;
    private String name;
    private String type;
    private String status;
    private String risk;
    private Long lastUpdated = System.currentTimeMillis();
}
