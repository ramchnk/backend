package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Document(collection = "kyc")
public class Kyc {
    @Id
    private String id;
    private String clientId;
    private String name;
    private String idType;
    private String nation;
    private String status;
    private String risk;
    private Long lastUpdated = System.currentTimeMillis();
}
