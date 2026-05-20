package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Document(collection = "documents")
public class ClientDocument {
    @Id
    private String id;
    private String title;
    private String file;
    private String status;
    private String clientId;
    private String date;
}
