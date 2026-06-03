package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@Document(collection = "catalog")
public class CatalogItem {
    @Id
    private String id;
    private String name;
    private String price;
    private String description;
    private String category;
}
