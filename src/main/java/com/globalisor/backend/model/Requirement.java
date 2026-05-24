package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;
import java.util.Date;

@Data
@NoArgsConstructor
@Document(collection = "requirements")
public class Requirement {
    @Id
    private String id;
    private String userId;
    private String status = "pending";
    private String staff = "Sarah Lim";
    private Date createdAt = new Date();
    private Date updatedAt = new Date();
    private Map<String, Object> data;

    public Requirement(String userId, Map<String, Object> data) {
        this.userId = userId;
        this.data = data;
        this.createdAt = new Date();
        this.updatedAt = new Date();
    }
}
