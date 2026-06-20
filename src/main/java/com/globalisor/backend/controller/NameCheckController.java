package com.globalisor.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/name-check")
public class NameCheckController {

    // Common/generic words that are always "taken" or invalid
    private static final Set<String> TAKEN_NAMES = new HashSet<>(Arrays.asList(
        "singapore", "singapore pte ltd", "sg", "global", "global pte ltd",
        "tech", "tech pte ltd", "solutions pte ltd", "solutions", "group pte ltd",
        "group", "holdings", "holdings pte ltd", "international", "enterprises",
        "enterprise pte ltd", "consulting pte ltd", "consulting", "management",
        "management pte ltd", "services pte ltd", "services", "trading pte ltd",
        "trading", "investment pte ltd", "investments", "capital pte ltd", "capital",
        "ventures", "venture pte ltd", "systems pte ltd", "systems", "digital pte ltd",
        "digital", "media pte ltd", "media", "network pte ltd", "network", "networks",
        "Asia Pacific Pte Ltd", "asia pacific", "asia", "pacific",
        "nexus", "nexus pte ltd", "prime", "prime pte ltd", "apex", "apex pte ltd"
    ));

    // Names that trigger "similar" (generate alternatives)
    private static final Set<String> GENERIC_WORDS = new HashSet<>(Arrays.asList(
        "tech", "technology", "technologies", "solution", "solutions", "service",
        "services", "digital", "global", "group", "holding", "holdings",
        "capital", "venture", "ventures", "system", "systems", "network",
        "media", "consulting", "management", "international", "enterprise",
        "innovation", "creative", "dynamic", "smart", "elite", "pro"
    ));

    private static final String[] SUFFIXES = {"Pte Ltd", "LLP", "Corp", "Co", "Group", "Holdings"};
    private static final String[] PREFIXES = {"SG", "Asia", "Pacific", "United", "Premier", "Elite"};

    @PostMapping
    public ResponseEntity<?> checkName(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "").trim();

        Map<String, Object> response = new HashMap<>();
        response.put("checkedAt", Instant.now().toString());
        response.put("name", name);

        // Validate name length
        if (name.length() < 3) {
            response.put("status", "invalid");
            response.put("message", "Name must be at least 3 characters long.");
            response.put("suggestions", Collections.emptyList());
            return ResponseEntity.ok(response);
        }

        String nameLower = name.toLowerCase().trim();
        // Remove common suffixes for comparison
        String baseName = nameLower
            .replaceAll("\\s*(pte\\.?\\s*ltd\\.?|llp|corp\\.?|inc\\.?)\\s*$", "")
            .trim();

        // Check if taken
        if (isTaken(nameLower, baseName)) {
            response.put("status", "taken");
            response.put("message", "This name already exists or is reserved. Please choose another name.");
            response.put("suggestions", generateAlternatives(baseName, name, 5));
            return ResponseEntity.ok(response);
        }

        // Check if similar (generic words involved)
        if (hasSimilarNames(baseName)) {
            response.put("status", "similar");
            response.put("message", "Similar names found. Consider one of the alternatives or make your name more unique.");
            response.put("suggestions", generateAlternatives(baseName, name, 5));
            return ResponseEntity.ok(response);
        }

        // Available
        response.put("status", "available");
        response.put("message", "This name appears to be available for registration.");
        response.put("suggestions", Collections.emptyList());
        return ResponseEntity.ok(response);
    }

    private boolean isTaken(String nameLower, String baseName) {
        // Exact match in taken list
        if (TAKEN_NAMES.contains(nameLower) || TAKEN_NAMES.contains(baseName)) return true;

        // Very short names (< 4 chars base) are usually reserved
        if (baseName.length() < 4) return true;

        // Names that are just numbers
        if (baseName.matches("^[0-9\\s]+$")) return true;

        // Simulate some known taken company patterns (hash-based for consistency)
        int hash = Math.abs(baseName.hashCode());
        return (hash % 7 == 0); // ~14% of names are "taken"
    }

    private boolean hasSimilarNames(String baseName) {
        // If base name contains any generic word, flag as similar
        for (String word : GENERIC_WORDS) {
            if (baseName.contains(word)) return true;
        }
        // Additional 20% of "available" names show similar results (hash-based)
        int hash = Math.abs(baseName.hashCode());
        return (hash % 5 == 1); // ~20% of remaining names have similar
    }

    private List<String> generateAlternatives(String baseName, String originalName, int count) {
        List<String> suggestions = new ArrayList<>();
        Random random = new Random(baseName.hashCode()); // Deterministic based on name

        // Strategy 1: Add distinctive prefix
        for (String prefix : PREFIXES) {
            if (suggestions.size() >= count) break;
            String candidate = capitalize(prefix + " " + baseName + " Pte Ltd");
            if (!candidate.equalsIgnoreCase(originalName)) {
                suggestions.add(candidate);
            }
        }

        // Strategy 2: Add year or unique identifier
        int year = 2024 + (Math.abs(baseName.hashCode()) % 2);
        suggestions.add(capitalize(baseName) + " " + year + " Pte Ltd");

        // Strategy 3: Add adjective
        String[] adjectives = {"Advanced", "Premier", "United", "Allied", "Apex"};
        for (String adj : adjectives) {
            if (suggestions.size() >= count) break;
            suggestions.add(adj + " " + capitalize(baseName) + " Pte Ltd");
        }

        // Trim to requested count
        if (suggestions.size() > count) {
            suggestions = suggestions.subList(0, count);
        }

        return suggestions;
    }

    private String capitalize(String input) {
        if (input == null || input.isEmpty()) return input;
        String[] words = input.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            // Don't capitalize conjunctions/prepositions in company names unless first word
            if (sb.length() > 0 && (word.equalsIgnoreCase("and") || word.equalsIgnoreCase("of") || word.equalsIgnoreCase("for"))) {
                sb.append(word.toLowerCase());
            } else {
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1).toLowerCase());
            }
            sb.append(" ");
        }
        return sb.toString().trim();
    }
}
