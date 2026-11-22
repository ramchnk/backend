package com.globalisor.backend.service;

import com.globalisor.backend.model.AmlWatchlistRecord;
import com.globalisor.backend.repository.AmlWatchlistRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
public class AmlDataIngestionService {

    @Autowired
    private AmlWatchlistRepository amlWatchlistRepository;

    private static final String UN_CONSOLIDATED_XML_URL = "https://scsanctions.un.org/resources/xml/en/consolidated.xml";
    private static final String OFAC_SDN_XML_URL = "https://www.treasury.gov/ofac/downloads/sdn.xml";

    private volatile Date lastSyncTime = null;
    private volatile String syncStatus = "IDLE";
    private volatile long lastIngestedCount = 0;
    private volatile String lastSyncError = null;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    @PostConstruct
    public void init() {
        // Check if database is empty; if so, populate baseline seed so screening works
        // immediately
        long count = amlWatchlistRepository.count();
        if (count == 0) {
            log.info("AML Watchlist database is empty. Populating baseline sanctions records...");
            seedBaselineWatchlists();
        }
    }

    public Map<String, Object> getSyncStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRecords", amlWatchlistRepository.count());
        stats.put("unRecords", amlWatchlistRepository.countBySource("UN"));
        stats.put("ofacRecords", amlWatchlistRepository.countBySource("OFAC"));
        stats.put("seedRecords", amlWatchlistRepository.countBySource("BASELINE"));
        stats.put("lastSyncTime", lastSyncTime);
        stats.put("syncStatus", syncStatus);
        stats.put("lastIngestedCount", lastIngestedCount);
        stats.put("lastSyncError", lastSyncError);
        return stats;
    }

    /**
     * Trigger on-demand sync from live XML feeds
     */
    public synchronized Map<String, Object> syncLiveWatchlistsAsync() {
        if ("SYNCING".equals(syncStatus)) {
            Map<String, Object> res = new HashMap<>();
            res.put("status", "already_running");
            res.put("message", "AML ingestion sync is already in progress.");
            return res;
        }

        Thread syncThread = new Thread(this::performFullSync, "aml-ingestion-worker");
        syncThread.setDaemon(true);
        syncThread.start();

        Map<String, Object> res = new HashMap<>();
        res.put("status", "started");
        res.put("message", "AML live XML data synchronization started in background.");
        return res;
    }

    /**
     * Nightly scheduled sync at 2:00 AM server time
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledNightlySync() {
        log.info("Starting scheduled nightly AML sanctions data sync...");
        performFullSync();
    }

    public synchronized void performFullSync() {
        syncStatus = "SYNCING";
        lastSyncError = null;
        long startTime = System.currentTimeMillis();
        int totalSaved = 0;

        try {
            log.info("Initiating UN Sanctions XML download from: {}", UN_CONSOLIDATED_XML_URL);
            int unCount = ingestUnSanctionsXml();
            totalSaved += unCount;
            log.info("Successfully ingested {} UN sanction records.", unCount);
        } catch (Exception e) {
            log.warn("UN Sanctions XML ingestion encountered an issue: {}", e.getMessage());
            lastSyncError = "UN Feed: " + e.getMessage();
        }

        try {
            log.info("Initiating OFAC SDN XML download from: {}", OFAC_SDN_XML_URL);
            int ofacCount = ingestOfacSdnXml();
            totalSaved += ofacCount;
            log.info("Successfully ingested {} OFAC SDN sanction records.", ofacCount);
        } catch (Exception e) {
            log.warn("OFAC SDN XML ingestion encountered an issue: {}", e.getMessage());
            if (lastSyncError != null) {
                lastSyncError += " | OFAC Feed: " + e.getMessage();
            } else {
                lastSyncError = "OFAC Feed: " + e.getMessage();
            }
        }

        // If both feeds failed or 0 records, ensure baseline seed is retained
        if (amlWatchlistRepository.count() == 0) {
            seedBaselineWatchlists();
        }

        lastSyncTime = new Date();
        lastIngestedCount = totalSaved;
        syncStatus = "COMPLETED";
        long duration = System.currentTimeMillis() - startTime;
        log.info("AML Sanctions Sync completed in {} ms. Total current warehouse records: {}",
                duration, amlWatchlistRepository.count());
    }

    /**
     * Streaming parse of UN Consolidated Sanctions XML
     */
    public int ingestUnSanctionsXml() throws Exception {
        InputStream is = null;
        boolean closeIs = false;

        java.io.File localFile = new java.io.File("data/aml/un_consolidated.xml");
        if (!localFile.exists()) {
            localFile = new java.io.File("../data/aml/un_consolidated.xml");
        }

        if (localFile.exists() && localFile.length() > 0) {
            log.info("Loading UN Sanctions XML from local file cache: {}", localFile.getAbsolutePath());
            is = new java.io.FileInputStream(localFile);
            closeIs = true;
        } else {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(UN_CONSOLIDATED_XML_URL))
                    .timeout(Duration.ofSeconds(60))
                    .header("User-Agent", "Globalisor-AML-Screening-Engine/1.0")
                    .GET()
                    .build();

            HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                throw new RuntimeException("UN XML server returned HTTP status " + resp.statusCode());
            }
            is = resp.body();
        }

        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);

        final InputStream streamToUse = is;
        try (streamToUse) {
            XMLStreamReader reader = factory.createXMLStreamReader(is);
            List<AmlWatchlistRecord> batch = new ArrayList<>();
            int count = 0;

            String currentTag = "";
            String currentSection = ""; // "INDIVIDUAL" or "ENTITY"
            AmlWatchlistRecord record = null;

            StringBuilder textBuffer = new StringBuilder();

            while (reader.hasNext()) {
                int event = reader.next();

                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        currentTag = reader.getLocalName();
                        textBuffer.setLength(0);

                        if ("INDIVIDUAL".equalsIgnoreCase(currentTag)) {
                            currentSection = "INDIVIDUAL";
                            record = new AmlWatchlistRecord();
                            record.setSource("UN");
                            record.setEntityType("INDIVIDUAL");
                            record.setRawSourceUrl(UN_CONSOLIDATED_XML_URL);
                        } else if ("ENTITY".equalsIgnoreCase(currentTag)) {
                            currentSection = "ENTITY";
                            record = new AmlWatchlistRecord();
                            record.setSource("UN");
                            record.setEntityType("ENTITY");
                            record.setRawSourceUrl(UN_CONSOLIDATED_XML_URL);
                        }
                        break;

                    case XMLStreamConstants.CHARACTERS:
                        textBuffer.append(reader.getText());
                        break;

                    case XMLStreamConstants.END_ELEMENT:
                        String endTag = reader.getLocalName();
                        String val = textBuffer.toString().trim();

                        if (record != null) {
                            if ("DATAID".equalsIgnoreCase(endTag)) {
                                record.setId("UN-" + currentSection.substring(0, 3) + "-" + val);
                            } else if ("FIRST_NAME".equalsIgnoreCase(endTag)) {
                                record.setPrimaryName(val);
                            } else if ("SECOND_NAME".equalsIgnoreCase(endTag) && !val.isEmpty()) {
                                record.setPrimaryName(
                                        record.getPrimaryName() != null ? record.getPrimaryName() + " " + val : val);
                            } else if ("THIRD_NAME".equalsIgnoreCase(endTag) && !val.isEmpty()) {
                                record.setPrimaryName(
                                        record.getPrimaryName() != null ? record.getPrimaryName() + " " + val : val);
                            } else if ("FOURTH_NAME".equalsIgnoreCase(endTag) && !val.isEmpty()) {
                                record.setPrimaryName(
                                        record.getPrimaryName() != null ? record.getPrimaryName() + " " + val : val);
                            } else if ("ALIAS_NAME".equalsIgnoreCase(endTag) && !val.isEmpty()) {
                                record.getAliases().add(val);
                            } else if ("DATE".equalsIgnoreCase(endTag) && !val.isEmpty()) {
                                record.getDatesOfBirth().add(val);
                            } else if ("VALUE".equalsIgnoreCase(endTag) && "NATIONALITY".equalsIgnoreCase(currentTag)
                                    && !val.isEmpty()) {
                                record.getNationalities().add(val);
                            } else if ("NUMBER".equalsIgnoreCase(endTag) && !val.isEmpty()) {
                                record.getPassportsOrIds().add(val);
                            } else if ("COMMENTS1".equalsIgnoreCase(endTag) && !val.isEmpty()) {
                                record.setRemarks(val);
                            } else if ("UN_LIST_TYPE".equalsIgnoreCase(endTag) && !val.isEmpty()) {
                                record.getPrograms().add(val);
                            }

                            if (("INDIVIDUAL".equalsIgnoreCase(endTag) || "ENTITY".equalsIgnoreCase(endTag))) {
                                if (record.getId() != null && record.getPrimaryName() != null) {
                                    record.setNormalizedName(normalizeName(record.getPrimaryName()));
                                    for (String alias : record.getAliases()) {
                                        String norm = normalizeName(alias);
                                        if (!norm.isEmpty() && !record.getNormalizedAliases().contains(norm)) {
                                            record.getNormalizedAliases().add(norm);
                                        }
                                    }
                                    record.setLastUpdated(new Date());
                                    batch.add(record);
                                    count++;

                                    if (batch.size() >= 500) {
                                        amlWatchlistRepository.saveAll(batch);
                                        batch.clear();
                                    }
                                }
                                record = null;
                            }
                        }
                        currentTag = "";
                        break;
                }
            }

            if (!batch.isEmpty()) {
                amlWatchlistRepository.saveAll(batch);
            }
            return count;
        }
    }

    /**
     * Streaming parse of OFAC SDN XML
     */
    public int ingestOfacSdnXml() throws Exception {
        InputStream is = null;

        java.io.File localFile = new java.io.File("data/aml/sdn.xml");
        if (!localFile.exists()) {
            localFile = new java.io.File("../data/aml/sdn.xml");
        }

        if (localFile.exists() && localFile.length() > 0) {
            log.info("Loading OFAC SDN XML from local file cache: {}", localFile.getAbsolutePath());
            is = new java.io.FileInputStream(localFile);
        } else {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(OFAC_SDN_XML_URL))
                    .timeout(Duration.ofSeconds(60))
                    .header("User-Agent", "Globalisor-AML-Screening-Engine/1.0")
                    .GET()
                    .build();

            HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                throw new RuntimeException("OFAC XML server returned HTTP status " + resp.statusCode());
            }
            is = resp.body();
        }

        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);

        final InputStream streamToUse = is;
        try (streamToUse) {
            XMLStreamReader reader = factory.createXMLStreamReader(is);
            List<AmlWatchlistRecord> batch = new ArrayList<>();
            int count = 0;

            String currentTag = "";
            AmlWatchlistRecord record = null;
            StringBuilder textBuffer = new StringBuilder();

            String firstName = "";
            String lastName = "";
            String akaFirstName = "";
            String akaLastName = "";

            while (reader.hasNext()) {
                int event = reader.next();

                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        currentTag = reader.getLocalName();
                        textBuffer.setLength(0);

                        if ("sdnEntry".equalsIgnoreCase(currentTag)) {
                            record = new AmlWatchlistRecord();
                            record.setSource("OFAC");
                            record.setRawSourceUrl(OFAC_SDN_XML_URL);
                            firstName = "";
                            lastName = "";
                        } else if ("aka".equalsIgnoreCase(currentTag)) {
                            akaFirstName = "";
                            akaLastName = "";
                        }
                        break;

                    case XMLStreamConstants.CHARACTERS:
                        textBuffer.append(reader.getText());
                        break;

                    case XMLStreamConstants.END_ELEMENT:
                        String endTag = reader.getLocalName();
                        String val = textBuffer.toString().trim();

                        if (record != null) {
                            if ("uid".equalsIgnoreCase(endTag)) {
                                record.setId("OFAC-" + val);
                            } else if ("firstName".equalsIgnoreCase(endTag)
                                    && "sdnEntry".equalsIgnoreCase(currentTag)) {
                                firstName = val;
                            } else if ("lastName".equalsIgnoreCase(endTag) && "sdnEntry".equalsIgnoreCase(currentTag)) {
                                lastName = val;
                            } else if ("sdnType".equalsIgnoreCase(endTag)) {
                                record.setEntityType("Individual".equalsIgnoreCase(val) ? "INDIVIDUAL" : "ENTITY");
                            } else if ("program".equalsIgnoreCase(endTag) && !val.isEmpty()) {
                                record.getPrograms().add(val);
                            } else if ("idNumber".equalsIgnoreCase(endTag) && !val.isEmpty()) {
                                record.getPassportsOrIds().add(val);
                            } else if ("dateOfBirth".equalsIgnoreCase(endTag) && !val.isEmpty()) {
                                record.getDatesOfBirth().add(val);
                            } else if ("remarks".equalsIgnoreCase(endTag) && !val.isEmpty()) {
                                record.setRemarks(val);
                            } else if ("aka".equalsIgnoreCase(endTag)) {
                                String fullAka = (akaFirstName + " " + akaLastName).trim();
                                if (!fullAka.isEmpty()) {
                                    record.getAliases().add(fullAka);
                                }
                            }

                            if ("sdnEntry".equalsIgnoreCase(endTag)) {
                                String fullName = (firstName + " " + lastName).trim();
                                if (!fullName.isEmpty() && record.getId() != null) {
                                    record.setPrimaryName(fullName);
                                    record.setNormalizedName(normalizeName(fullName));
                                    for (String alias : record.getAliases()) {
                                        String norm = normalizeName(alias);
                                        if (!norm.isEmpty() && !record.getNormalizedAliases().contains(norm)) {
                                            record.getNormalizedAliases().add(norm);
                                        }
                                    }
                                    record.setLastUpdated(new Date());
                                    batch.add(record);
                                    count++;

                                    if (batch.size() >= 500) {
                                        amlWatchlistRepository.saveAll(batch);
                                        batch.clear();
                                    }
                                }
                                record = null;
                            }
                        }
                        currentTag = "";
                        break;
                }
            }

            if (!batch.isEmpty()) {
                amlWatchlistRepository.saveAll(batch);
            }
            return count;
        }
    }

    /**
     * Baseline sanctions dataset populated on first run
     */
    public void seedBaselineWatchlists() {
        List<AmlWatchlistRecord> seeds = new ArrayList<>();

        // High-profile sample sanctions records for instant offline validation
        seeds.add(createSeedRecord("OFAC-SAMPLE-101", "OFAC", "INDIVIDUAL", "Vladimir Petrov",
                List.of("Vladimir Vladimirovich Petrov", "Vlad Petrov"),
                List.of("1968-10-07"), List.of("Russian Federation"), List.of("RU-78901234"),
                List.of("RUSSIA-EO14024"), "Specially Designated National. Sectoral Sanctions."));

        seeds.add(createSeedRecord("UN-SAMPLE-202", "UN", "INDIVIDUAL", "Mohammad Al-Rawi",
                List.of("Abu Omar Al-Rawi", "Mohamed Rawi"),
                List.of("1974-03-12"), List.of("Syrian Arab Republic", "Iraq"), List.of("SY-P981240"),
                List.of("AL-QAIDA-ISIL-UNSCR1267"),
                "Designated under UN Security Council resolution 1267 for terrorism financing."));

        seeds.add(createSeedRecord("OFAC-SAMPLE-303", "OFAC", "INDIVIDUAL", "Marcus Ng",
                List.of("Marcus Ng Teck", "Ng Boon Marcus"),
                List.of("1982-11-20"), List.of("Singapore"), List.of("S8211200A", "E7891234"),
                List.of("CYBER2-EO13694"),
                "Designated for malicious cyber activities and illicit AML fund transfers."));

        seeds.add(createSeedRecord("UN-SAMPLE-404", "UN", "ENTITY", "Al-Barakaat Financial Network",
                List.of("Barakaat Wire Transfer", "Al Barakat Trading"),
                List.of(), List.of("Somalia", "UAE"), List.of("AE-COM-99120"),
                List.of("TERRORISM-FINANCING"), "Front entity for illicit sanctions evasion and money laundering."));

        seeds.add(createSeedRecord("OFAC-SAMPLE-505", "OFAC", "ENTITY", "Ocean Maritime Management Company",
                List.of("OMM Company Ltd", "Donghae Shipping"),
                List.of(), List.of("North Korea"), List.of("KP-IMO-1234567"),
                List.of("DPRK-UNSCR1718"), "Entity controlled by North Korean maritime illicit cargo transport."));

        seeds.add(createSeedRecord("MAS-SAMPLE-606", "MAS", "INDIVIDUAL", "Alexander Thorne",
                List.of("Alex Thorne", "Alexander James Thorne"),
                List.of("1977-08-15"), List.of("United Kingdom", "Cyprus"), List.of("GB-99238410"),
                List.of("MAS-TSOFA"),
                "Targeted financial sanction under Singapore Terrorism (Suppression of Financing) Act."));

        amlWatchlistRepository.saveAll(seeds);
        log.info("Baseline AML sanctions seed dataset initialized ({} records).", seeds.size());
    }

    private AmlWatchlistRecord createSeedRecord(String id, String source, String type, String name,
            List<String> aliases, List<String> dobs,
            List<String> nationalities, List<String> ids,
            List<String> programs, String remarks) {
        AmlWatchlistRecord r = new AmlWatchlistRecord();
        r.setId(id);
        r.setSource(source);
        r.setEntityType(type);
        r.setPrimaryName(name);
        r.setNormalizedName(normalizeName(name));
        r.setAliases(new ArrayList<>(aliases));
        List<String> normAliases = new ArrayList<>();
        for (String a : aliases) {
            String norm = normalizeName(a);
            if (!norm.isEmpty())
                normAliases.add(norm);
        }
        r.setNormalizedAliases(normAliases);
        r.setDatesOfBirth(new ArrayList<>(dobs));
        r.setNationalities(new ArrayList<>(nationalities));
        r.setPassportsOrIds(new ArrayList<>(ids));
        r.setPrograms(new ArrayList<>(programs));
        r.setRemarks(remarks);
        r.setRawSourceUrl("https://globalisor.com/compliance/aml/watchlists/" + id);
        r.setLastUpdated(new Date());
        return r;
    }

    public static String normalizeName(String name) {
        if (name == null)
            return "";
        // 1. Strip script tags and content, HTML/XML tags, and malicious script
        // keywords
        String sanitized = name.replaceAll("(?is)<script.*?>.*?</script>", " ")
                .replaceAll("(?i)<[^>]+>", " ")
                .replaceAll("(?i)fetch\\s*\\([^)]*\\)", " ")
                .replaceAll("(?i)document\\.cookie", " ")
                .replaceAll("(?i)javascript:", " ");
        // 2. Remove accents (e.g. é -> e)
        String decomposed = Normalizer.normalize(sanitized, Normalizer.Form.NFD);
        String stripped = decomposed.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        // 3. Lowercase and keep alphanumeric + spaces
        String alpha = stripped.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").trim();
        return alpha.replaceAll("\\s+", " ");
    }
}
