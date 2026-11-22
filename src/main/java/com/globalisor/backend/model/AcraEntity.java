package com.globalisor.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "acra_entities")
@CompoundIndexes({
        @CompoundIndex(name = "norm_isLive_idx", def = "{'normalizedName': 1, 'isLive': 1}"),
        @CompoundIndex(name = "base_isLive_idx", def = "{'baseName': 1, 'isLive': 1}")
})
public class AcraEntity {

    @Id
    private String id; // Typically UEN, e.g. "201201936C"

    @Indexed(unique = true)
    private String uen;

    private String entityName; // e.g. "MILLENNIUM AUTOMATION & SYSTEMS PTE. LTD."

    @Indexed
    private String normalizedName; // e.g. "millennium automation systems pte ltd"

    @Indexed
    private String baseName; // e.g. "millennium automation systems" (without legal suffixes)

    @Indexed
    private String entityType; // "Local Company", "Sole Proprietorship/ Partnership", etc.

    @Indexed
    private String uenStatus; // "Registered", "Deregistered", "Struck Off"

    @Indexed
    private boolean isLive; // true if uenStatus is "Registered"

    private String issuanceAgency; // "ACRA"

    private String registrationDate; // "2012-01-26"

    private String streetName;

    private String postalCode;

    private Date lastUpdated;
}
