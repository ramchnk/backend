package com.globalisor.backend.controller;

import com.globalisor.backend.model.AcraEntity;
import com.globalisor.backend.repository.AcraEntityRepository;
import com.globalisor.backend.service.AcraDataIngestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/admin/acra")
public class AcraWarehouseController {

    @Autowired
    private AcraEntityRepository acraEntityRepository;

    @Autowired
    private AcraDataIngestionService acraDataIngestionService;

    /**
     * Warehouse status and statistics
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(acraDataIngestionService.getWarehouseStatus());
    }

    /**
     * Trigger live background synchronization from data.gov.sg
     */
    @PostMapping("/sync")
    public ResponseEntity<?> triggerSync(
            @RequestParam(value = "maxRecords", defaultValue = "0") int maxRecords) {
        acraDataIngestionService.triggerSyncAsync(maxRecords);
        Map<String, Object> resp = new HashMap<>();
        resp.put("message", "Live ACRA synchronization initiated from data.gov.sg in background.");
        resp.put("maxRecords", maxRecords > 0 ? maxRecords : "ALL (~2.1 Million Entities)");
        resp.put("status", "IN_PROGRESS");
        return ResponseEntity.ok(resp);
    }

    /**
     * Search Singapore registered entities by Name or UEN
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchEntities(
            @RequestParam(value = "q", defaultValue = "") String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "registrationDate"));
        Page<AcraEntity> results;

        if (query == null || query.trim().isEmpty()) {
            results = acraEntityRepository.findAll(pageRequest);
        } else {
            results = acraEntityRepository.searchEntities(query.trim(), pageRequest);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("records", results.getContent());
        response.put("totalElements", results.getTotalElements());
        response.put("totalPages", results.getTotalPages());
        response.put("currentPage", results.getNumber());

        return ResponseEntity.ok(response);
    }

    /**
     * Lookup a specific Singapore entity by Unique Entity Number (UEN)
     */
    @GetMapping("/uen/{uen}")
    public ResponseEntity<?> getByUen(@PathVariable("uen") String uen) {
        Optional<AcraEntity> entity = acraEntityRepository.findByUen(uen.trim().toUpperCase());
        if (entity.isPresent()) {
            return ResponseEntity.ok(entity.get());
        } else {
            Map<String, String> err = new HashMap<>();
            err.put("error", "No Singapore registered entity found with UEN: " + uen);
            return ResponseEntity.status(404).body(err);
        }
    }
}
