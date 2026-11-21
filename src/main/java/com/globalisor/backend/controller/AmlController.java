package com.globalisor.backend.controller;

import com.globalisor.backend.model.AmlWatchlistRecord;
import com.globalisor.backend.repository.AmlWatchlistRepository;
import com.globalisor.backend.service.AmlDataIngestionService;
import com.globalisor.backend.service.AmlScreeningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/admin/aml")
public class AmlController {

    @Autowired
    private AmlDataIngestionService amlDataIngestionService;

    @Autowired
    private AmlScreeningService amlScreeningService;

    @Autowired
    private AmlWatchlistRepository amlWatchlistRepository;

    /**
     * Get AML Watchlist Warehouse status & statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        return ResponseEntity.ok(amlDataIngestionService.getSyncStats());
    }

    /**
     * Trigger live XML ingestion from UN & OFAC feeds
     */
    @PostMapping("/sync")
    public ResponseEntity<?> triggerLiveSync() {
        return ResponseEntity.ok(amlDataIngestionService.syncLiveWatchlistsAsync());
    }

    /**
     * Test screening endpoint for any individual or entity
     */
    @PostMapping("/screen")
    public ResponseEntity<?> screenPerson(@RequestBody Map<String, String> payload) {
        String name = payload.getOrDefault("name", "");
        String idNumber = payload.getOrDefault("idNumber", "");
        String nationality = payload.getOrDefault("nationality", "");
        String dob = payload.getOrDefault("dob", "");

        AmlScreeningService.AmlScreeningResult result = amlScreeningService.screenPerson(name, idNumber, nationality, dob);
        return ResponseEntity.ok(result);
    }

    /**
     * Search sanctions warehouse records
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchRecords(
            @RequestParam(value = "q", defaultValue = "") String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastUpdated"));
        Page<AmlWatchlistRecord> records;

        if (query == null || query.trim().isEmpty()) {
            records = amlWatchlistRepository.findAll(pageRequest);
        } else {
            records = amlWatchlistRepository.searchRecords(query.trim(), pageRequest);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("records", records.getContent());
        response.put("totalElements", records.getTotalElements());
        response.put("totalPages", records.getTotalPages());
        response.put("currentPage", records.getNumber());

        return ResponseEntity.ok(response);
    }
}
