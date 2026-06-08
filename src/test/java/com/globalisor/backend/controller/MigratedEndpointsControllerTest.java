package com.globalisor.backend.controller;

import com.globalisor.backend.model.Country;
import com.globalisor.backend.repository.CountryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
public class MigratedEndpointsControllerTest {

    @Mock
    private CountryRepository countryRepository;

    @InjectMocks
    private MigratedEndpointsController controller;

    @Test
    public void testUpdateCountryCustomPrices() {
        Country existingCountry = new Country();
        existingCountry.setId("CNTRY-sg");
        existingCountry.setName("Singapore");
        existingCountry.setCode("SG");
        existingCountry.setCustomPrices(new HashMap<>());

        Mockito.when(countryRepository.findById("CNTRY-sg")).thenReturn(Optional.of(existingCountry));
        Mockito.when(countryRepository.save(any(Country.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Country updates = new Country();
        Map<String, Double> customPrices = new HashMap<>();
        customPrices.put("Custom Document Review", 250.0);
        updates.setCustomPrices(customPrices);

        ResponseEntity<Country> response = controller.updateCountry("CNTRY-sg", updates);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        
        Country saved = response.getBody();
        assertNotNull(saved);
        assertEquals(250.0, saved.getCustomPrices().get("Custom Document Review"));
    }

    @Test
    public void testDraftPublishWorkflow() {
        Country existingCountry = new Country();
        existingCountry.setId("CNTRY-in");
        existingCountry.setName("India");
        existingCountry.setCode("IN");
        existingCountry.setPublished(false);
        existingCountry.setPublishedData(new HashMap<>());

        Mockito.when(countryRepository.findById("CNTRY-in")).thenReturn(Optional.of(existingCountry));
        Mockito.when(countryRepository.save(any(Country.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 1. Edit the country (creates a draft change)
        Country draftUpdates = new Country();
        draftUpdates.setName("India Hub");
        draftUpdates.setBasePrice(1500.0);
        
        ResponseEntity<Country> response1 = controller.updateCountry("CNTRY-in", draftUpdates);
        assertNotNull(response1);
        Country savedDraft = response1.getBody();
        assertNotNull(savedDraft);
        assertEquals("India Hub", savedDraft.getName());
        assertEquals(1500.0, savedDraft.getBasePrice());
        
        // Ensure publishedData is still empty/original (separated from draft edits)
        Map<String, Object> pubData = savedDraft.getPublishedData();
        assertNotNull(pubData);
        assertEquals(0, pubData.size());

        // 2. Publish/Republish the country (copies draft to publishedData)
        Country publishUpdates = new Country();
        publishUpdates.setPublished(true);
        
        Map<String, Object> newPubData = new HashMap<>();
        newPubData.put("name", "India Hub");
        newPubData.put("code", "IN");
        newPubData.put("basePrice", 1500.0);
        newPubData.put("customPrices", new HashMap<>());
        publishUpdates.setPublishedData(newPubData);

        ResponseEntity<Country> response2 = controller.updateCountry("CNTRY-in", publishUpdates);
        assertNotNull(response2);
        Country savedPublished = response2.getBody();
        assertNotNull(savedPublished);
        assertEquals(true, savedPublished.getPublished());
        assertEquals("India Hub", savedPublished.getPublishedData().get("name"));
        assertEquals(1500.0, savedPublished.getPublishedData().get("basePrice"));
    }
}
