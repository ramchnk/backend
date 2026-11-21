package com.globalisor.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "aml_watchlists")
public class AmlWatchlistRecord {

    @Id
    private String id; // e.g. "UN-IND-12345", "OFAC-9876"

    @Indexed
    private String source; // "UN", "OFAC", "EU", "OPENSANCTIONS", "MAS"

    @Indexed
    private String entityType; // "INDIVIDUAL", "ENTITY"

    private String primaryName;

    @Indexed
    private String normalizedName; // lowercased, stripped accents and symbols

    @Builder.Default
    private List<String> aliases = new ArrayList<>();

    @Builder.Default
    private List<String> normalizedAliases = new ArrayList<>();

    @Builder.Default
    private List<String> datesOfBirth = new ArrayList<>();

    @Builder.Default
    private List<String> nationalities = new ArrayList<>();

    @Builder.Default
    private List<String> passportsOrIds = new ArrayList<>();

    @Builder.Default
    private List<String> addresses = new ArrayList<>();

    @Builder.Default
    private List<String> programs = new ArrayList<>();

    private String remarks;

    private String rawSourceUrl;

    @Builder.Default
    private Date lastUpdated = new Date();
}
