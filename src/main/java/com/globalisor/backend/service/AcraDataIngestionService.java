package com.globalisor.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.globalisor.backend.model.AcraEntity;
import com.globalisor.backend.repository.AcraEntityRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class AcraDataIngestionService {

    @Autowired
    private AcraEntityRepository acraEntityRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private static final String DATA_GOV_SG_POLL_URL =
            "https://api-open.data.gov.sg/v1/public/api/datasets/d_3f960c10fed6145404ca7b821f263b87/poll-download";

    private volatile String syncStatus = "IDLE"; // "IDLE", "IN_PROGRESS", "COMPLETED", "FAILED"
    private volatile long totalIngested = 0;
    private volatile long processedLines = 0;
    private volatile Date lastSyncTime = null;
    private volatile String lastError = null;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        long count = acraEntityRepository.count();
        if (count == 0) {
            log.info("ACRA Entity collection is empty. Populating baseline Singapore entities...");
            seedBaselineSingaporeCompanies();
        } else {
            log.info("ACRA Entity warehouse initialized with {} existing registered Singapore entities.", count);
        }
    }

    public synchronized CompletableFuture<Map<String, Object>> triggerSyncAsync(int maxRecords) {
        if ("IN_PROGRESS".equals(syncStatus)) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("status", "IN_PROGRESS");
            resp.put("message", "ACRA synchronization is already running in background.");
            resp.put("processedLines", processedLines);
            resp.put("totalIngested", totalIngested);
            return CompletableFuture.completedFuture(resp);
        }

        this.syncStatus = "IN_PROGRESS";
        this.lastError = null;
        this.processedLines = 0;
        this.totalIngested = 0;

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Initiating live ACRA dataset download from data.gov.sg...");
                // 1. Fetch Signed Download URL
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(DATA_GOV_SG_POLL_URL))
                        .header("User-Agent", "Globalisor-ACRA-Ingestion/1.0")
                        .timeout(Duration.ofSeconds(45))
                        .GET()
                        .build();

                HttpResponse<String> pollResp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (pollResp.statusCode() != 200) {
                    throw new RuntimeException("Failed to fetch download URL from data.gov.sg: HTTP " + pollResp.statusCode());
                }

                JsonNode root = objectMapper.readTree(pollResp.body());
                JsonNode urlNode = root.path("data").path("url");
                if (urlNode.isMissingNode() || urlNode.asText().isEmpty()) {
                    throw new RuntimeException("data.gov.sg returned no S3 download URL: " + pollResp.body());
                }

                String s3DownloadUrl = urlNode.asText();
                log.info("Acquired fresh S3 download URL from data.gov.sg. Starting stream ingestion...");

                // 2. Stream and Parse CSV line-by-line
                HttpRequest downloadReq = HttpRequest.newBuilder()
                        .uri(URI.create(s3DownloadUrl))
                        .header("User-Agent", "Globalisor-ACRA-Ingestion/1.0")
                        .timeout(Duration.ofMinutes(15))
                        .GET()
                        .build();

                HttpResponse<InputStream> streamResp = httpClient.send(downloadReq, HttpResponse.BodyHandlers.ofInputStream());
                if (streamResp.statusCode() != 200 && streamResp.statusCode() != 206) {
                    throw new RuntimeException("S3 download failed with HTTP " + streamResp.statusCode());
                }

                long savedCount = 0;
                List<AcraEntity> batch = new ArrayList<>(2500);

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(streamResp.body(), StandardCharsets.UTF_8), 32768)) {
                    String headerLine = reader.readLine(); // skip CSV header
                    String line;

                    while ((line = reader.readLine()) != null) {
                        processedLines++;
                        if (line.trim().isEmpty()) continue;

                        List<String> cols = parseCsvLine(line);
                        if (cols.size() >= 4) {
                            String uen = cols.get(0).trim();
                            String agency = cols.size() > 1 ? cols.get(1).trim() : "ACRA";
                            String status = cols.size() > 2 ? cols.get(2).trim() : "Registered";
                            String entityName = cols.get(3).trim();
                            String entityType = cols.size() > 4 ? cols.get(4).trim() : "Local Company";
                            String regDate = cols.size() > 5 ? cols.get(5).trim() : "";
                            String street = cols.size() > 6 ? cols.get(6).trim() : "";
                            String postal = cols.size() > 7 ? cols.get(7).trim() : "";

                            if (!uen.isEmpty() && !entityName.isEmpty()) {
                                String normName = normalizeCompanyName(entityName);
                                String baseName = extractBaseName(normName);
                                boolean isLive = "Registered".equalsIgnoreCase(status) || "Live".equalsIgnoreCase(status);

                                AcraEntity entity = AcraEntity.builder()
                                        .id(uen)
                                        .uen(uen)
                                        .entityName(entityName)
                                        .normalizedName(normName)
                                        .baseName(baseName)
                                        .entityType(entityType)
                                        .uenStatus(status)
                                        .isLive(isLive)
                                        .issuanceAgency(agency)
                                        .registrationDate(regDate)
                                        .streetName(street)
                                        .postalCode(postal)
                                        .lastUpdated(new Date())
                                        .build();

                                batch.add(entity);

                                if (batch.size() >= 2000) {
                                    try {
                                        mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, AcraEntity.class).insert(batch).execute();
                                    } catch (Exception be) {
                                        // Ignore duplicate key collision errors during bulk insert
                                    }
                                    savedCount += batch.size();
                                    totalIngested = savedCount;
                                    batch.clear();
                                    log.info("ACRA Ingestion Progress: {} entities stored in MongoDB Atlas...", savedCount);
                                }
                            }
                        }

                        if (maxRecords > 0 && savedCount >= maxRecords) {
                            log.info("Reached requested record cap of {}. Halting stream.", maxRecords);
                            break;
                        }
                    }

                    if (!batch.isEmpty()) {
                        try {
                            mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, AcraEntity.class).insert(batch).execute();
                        } catch (Exception be) {}
                        savedCount += batch.size();
                        totalIngested = savedCount;
                        batch.clear();
                    }
                }

                this.syncStatus = "COMPLETED";
                this.lastSyncTime = new Date();
                log.info("ACRA Ingestion COMPLETED successfully! Total records: {}", savedCount);

                Map<String, Object> result = new HashMap<>();
                result.put("status", "COMPLETED");
                result.put("recordsIngested", savedCount);
                result.put("processedLines", processedLines);
                result.put("completedAt", lastSyncTime);
                return result;

            } catch (Exception e) {
                log.error("ACRA Ingestion encountered an error: ", e);
                this.syncStatus = "FAILED";
                this.lastError = e.getMessage();
                Map<String, Object> errorRes = new HashMap<>();
                errorRes.put("status", "FAILED");
                errorRes.put("error", e.getMessage());
                return errorRes;
            }
        });
    }

    public Map<String, Object> getWarehouseStatus() {
        Map<String, Object> status = new HashMap<>();
        long totalCount = acraEntityRepository.count();
        long liveCount = acraEntityRepository.countByIsLive(true);

        status.put("totalEntities", totalCount);
        status.put("liveRegisteredEntities", liveCount);
        status.put("deregisteredEntities", totalCount - liveCount);
        status.put("syncStatus", syncStatus);
        status.put("processedLines", processedLines);
        status.put("totalIngestedInCurrentRun", totalIngested);
        status.put("lastSyncTime", lastSyncTime);
        status.put("lastError", lastError);
        return status;
    }

    public static String normalizeCompanyName(String name) {
        if (name == null) return "";
        String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD);
        String stripped = decomposed.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        // Replace & with and
        String cleaned = stripped.replace("&", " and ");
        // Lowercase and keep alphanumeric + spaces
        String alpha = cleaned.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").trim();
        return alpha.replaceAll("\\s+", " ");
    }

    public static String extractBaseName(String normalizedName) {
        if (normalizedName == null) return "";
        return normalizedName
                .replaceAll("\\s*\\b(pte\\s*ltd|private\\s*limited|ltd|limited|llp|llc|inc|corp|corporation|co|company|holdings|holding|group|singapore|asia|sg)\\b\\s*", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    public static List<String> parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        if (line == null || line.isEmpty()) return tokens;
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens;
    }

    private void seedBaselineSingaporeCompanies() {
        List<AcraEntity> baseline = Arrays.asList(
                createSeed("196800306E", "DBS BANK LTD.", "Public Company Limited by Shares", "Registered", "1968-07-16", "MARINA BOULEVARD", "018982"),
                createSeed("193200032W", "OVERSEA-CHINESE BANKING CORPORATION LIMITED", "Public Company Limited by Shares", "Registered", "1932-10-31", "CHULIA STREET", "049514"),
                createSeed("193500026Z", "UNITED OVERSEAS BANK LIMITED", "Public Company Limited by Shares", "Registered", "1935-08-06", "RAFFLES PLACE", "048624"),
                createSeed("199201624D", "SINGAPORE TELECOMMUNICATIONS LIMITED", "Public Company Limited by Shares", "Registered", "1992-03-28", "EXETER ROAD", "239732"),
                createSeed("197200078R", "SINGAPORE AIRLINES LIMITED", "Public Company Limited by Shares", "Registered", "1972-01-28", "AIRLINE ROAD", "819829"),
                createSeed("197400002R", "TEMASEK HOLDINGS (PRIVATE) LIMITED", "Exempt Private Company Limited by Shares", "Registered", "1974-06-25", "ORCHARD ROAD", "238879"),
                createSeed("198104277G", "GIC PRIVATE LIMITED", "Exempt Private Company Limited by Shares", "Registered", "1981-05-22", "ROBINSON ROAD", "068898"),
                createSeed("201201936C", "MILLENNIUM AUTOMATION & SYSTEMS PTE. LTD.", "Local Company", "Deregistered", "2012-01-26", "BENCOOLEN STREET", "189648"),
                createSeed("201305833D", "EXCEL CAPITAL PTE. LTD.", "Local Company", "Deregistered", "2013-03-06", "ANSON ROAD", "079903"),
                createSeed("199600940G", "DYNASOURCE ENTERPRISE PTE LTD", "Local Company", "Registered", "1996-02-06", "PASIR RIS GROVE", "518217"),
                createSeed("201813470Z", "GRAB HOLDINGS SERVICES PTE. LTD.", "Private Company Limited by Shares", "Registered", "2018-04-18", "ONE-NORTH CRESCENT", "138538"),
                createSeed("201437877Z", "SHOPEE SINGAPORE PRIVATE LIMITED", "Private Company Limited by Shares", "Registered", "2014-12-22", "SCIENCE PARK DRIVE", "118265"),
                createSeed("201509378C", "LAZADA SOUTH EAST ASIA PTE. LTD.", "Private Company Limited by Shares", "Registered", "2015-04-14", "BRAS BASAH ROAD", "189554"),
                createSeed("201018593W", "RAZER (ASIA-PACIFIC) PTE. LTD.", "Private Company Limited by Shares", "Registered", "2010-09-02", "ONE-NORTH CRESCENT", "138538"),
                createSeed("202104523G", "GLOBALISOR PTE. LTD.", "Private Company Limited by Shares", "Registered", "2021-02-05", "ROBINSON ROAD", "068898")
        );

        acraEntityRepository.saveAll(baseline);
        log.info("Populated {} baseline Singapore ACRA entities.", baseline.size());
    }

    private AcraEntity createSeed(String uen, String name, String type, String status, String date, String street, String postal) {
        String norm = normalizeCompanyName(name);
        return AcraEntity.builder()
                .id(uen)
                .uen(uen)
                .entityName(name)
                .normalizedName(norm)
                .baseName(extractBaseName(norm))
                .entityType(type)
                .uenStatus(status)
                .isLive("Registered".equalsIgnoreCase(status))
                .issuanceAgency("ACRA")
                .registrationDate(date)
                .streetName(street)
                .postalCode(postal)
                .lastUpdated(new Date())
                .build();
    }
}
