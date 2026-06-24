package com.globalisor.backend.controller;

import com.globalisor.backend.model.OcrResult;
import com.globalisor.backend.repository.OcrResultRepository;
import com.globalisor.backend.security.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/ocr")
public class OcrController {

    @Autowired
    OcrResultRepository ocrResultRepository;

    /**
     * Save OCR extraction result from client-side processing
     * The actual OCR is done client-side using Tesseract.js; this endpoint stores results.
     */
    @PostMapping("/save")
    public ResponseEntity<?> saveOcrResult(@RequestBody OcrResult ocrResult) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = null;
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl) {
            userId = ((UserDetailsImpl) auth.getPrincipal()).getId();
        }
        if (userId == null && ocrResult.getUserId() != null) {
            userId = ocrResult.getUserId();
        }

        // Upsert: check if we already have a result for this user + fieldPath
        if (userId != null && ocrResult.getFieldPath() != null) {
            Optional<OcrResult> existing = ocrResultRepository.findByUserIdAndFieldPath(userId, ocrResult.getFieldPath());
            if (existing.isPresent()) {
                OcrResult ex = existing.get();
                ex.setStatus(ocrResult.getStatus());
                ex.setConfidence(ocrResult.getConfidence());
                ex.setExtractedFields(ocrResult.getExtractedFields());
                ex.setRawText(ocrResult.getRawText());
                ex.setFileName(ocrResult.getFileName());
                ex.setDocumentType(ocrResult.getDocumentType());
                ex.setReviewedByClient(ocrResult.isReviewedByClient());
                ex.setWarnings(ocrResult.getWarnings());
                ex.setUpdatedAt(new Date());
                return ResponseEntity.ok(ocrResultRepository.save(ex));
            }
        }

        ocrResult.setUserId(userId != null ? userId : ocrResult.getUserId());
        if (ocrResult.getId() == null) {
            ocrResult.setId("ocr-" + System.currentTimeMillis());
        }
        ocrResult.setCreatedAt(new Date());
        ocrResult.setUpdatedAt(new Date());
        return ResponseEntity.ok(ocrResultRepository.save(ocrResult));
    }

    /**
     * Mark OCR result as reviewed by client
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<?> confirmOcr(@PathVariable String id) {
        Optional<OcrResult> opt = ocrResultRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        OcrResult result = opt.get();
        result.setReviewedByClient(true);
        result.setUpdatedAt(new Date());
        return ResponseEntity.ok(ocrResultRepository.save(result));
    }

    /**
     * Get all OCR results for the authenticated user
     */
    @GetMapping("/my")
    public ResponseEntity<?> getMyOcrResults() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetailsImpl)) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        String userId = ((UserDetailsImpl) auth.getPrincipal()).getId();
        return ResponseEntity.ok(ocrResultRepository.findByUserId(userId));
    }

    /**
     * Get OCR results for a requirement (for admin/staff view)
     */
    @GetMapping("/requirement/{requirementId}")
    public ResponseEntity<?> getOcrResultsByRequirement(@PathVariable String requirementId) {
        return ResponseEntity.ok(ocrResultRepository.findByRequirementId(requirementId));
    }

    /**
     * Get all OCR results (admin/staff only)
     */
    @GetMapping("/all")
    public ResponseEntity<?> getAllOcrResults() {
        return ResponseEntity.ok(ocrResultRepository.findAll());
    }
}
