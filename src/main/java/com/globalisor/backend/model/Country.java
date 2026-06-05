package com.globalisor.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Data
@NoArgsConstructor
@Document(collection = "countries")
public class Country {
    @Id
    private String id;
    private String name;
    private String code;
    private String uen;
    private String tax;
    private String compliance;
    private String status;
    private Boolean published = false;
    private List<String> services;
    private Double basePrice = 1200.0;
    private Double priceSecretary = 900.0;
    private Double priceDirector = 3000.0;
    private Double priceAddress = 600.0;
    private Double priceTax = 1500.0;
    private Double priceBank = 500.0;
    private Map<String, Double> customPrices = new HashMap<>();
    private Map<String, Object> publishedData = new HashMap<>();
}
