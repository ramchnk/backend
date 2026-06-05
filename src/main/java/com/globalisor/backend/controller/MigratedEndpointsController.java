package com.globalisor.backend.controller;

import com.globalisor.backend.model.*;
import com.globalisor.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api")
public class MigratedEndpointsController {

    @Autowired
    UserRepository userRepository;

    @Autowired
    RequirementRepository requirementRepository;

    @Autowired
    BlogRepository blogRepository;

    @Autowired
    KycRepository kycRepository;

    @Autowired
    ComplianceRepository complianceRepository;

    @Autowired
    ClientDocumentRepository clientDocumentRepository;

    @Autowired
    StaticContentRepository staticContentRepository;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    MessageRepository messageRepository;

    @Autowired
    InvoiceRepository invoiceRepository;

    @Autowired
    CatalogItemRepository catalogItemRepository;

    @Autowired
    CountryRepository countryRepository;

    // --- BLOG ENDPOINTS ---
    @GetMapping("/blogs")
    public ResponseEntity<List<Blog>> getAllBlogs() {
        List<Blog> blogs = blogRepository.findAll();
        // Sort by createdAt descending
        blogs.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        return ResponseEntity.ok(blogs);
    }

    @PostMapping("/blogs")
    public ResponseEntity<Blog> createBlog(@RequestBody Blog blog) {
        if (blog.getId() == null) {
            blog.setId("BLG-" + System.currentTimeMillis());
        }
        if (blog.getCreatedAt() == null) {
            blog.setCreatedAt(System.currentTimeMillis());
        }
        Blog savedBlog = blogRepository.save(blog);

        // If published, create a notification for all clients
        if (Boolean.TRUE.equals(savedBlog.getPublished())) {
            Notification notification = new Notification();
            notification.setId("notif-" + System.currentTimeMillis());
            notification.setClientId("all");
            notification.setTitle("New update from Globalisor");
            notification.setMessage(savedBlog.getTitle() + " has been published.");
            notification.setType("blog");
            notification.setRelatedId(savedBlog.getId());
            notification.setTimestamp(System.currentTimeMillis());
            notification.setReadBy(new ArrayList<>());
            notificationRepository.save(notification);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(savedBlog);
    }

    @PatchMapping("/blogs/{id}")
    public ResponseEntity<Blog> updateBlog(@PathVariable String id, @RequestBody Blog blogUpdates) {
        Optional<Blog> blogOpt = blogRepository.findById(id);
        if (blogOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Blog blog = blogOpt.get();
        if (blogUpdates.getTitle() != null) blog.setTitle(blogUpdates.getTitle());
        if (blogUpdates.getExcerpt() != null) blog.setExcerpt(blogUpdates.getExcerpt());
        if (blogUpdates.getContent() != null) blog.setContent(blogUpdates.getContent());
        if (blogUpdates.getCategory() != null) blog.setCategory(blogUpdates.getCategory());
        if (blogUpdates.getAuthor() != null) blog.setAuthor(blogUpdates.getAuthor());
        if (blogUpdates.getDate() != null) blog.setDate(blogUpdates.getDate());
        if (blogUpdates.getPublished() != null) blog.setPublished(blogUpdates.getPublished());
        if (blogUpdates.getCoverImage() != null) blog.setCoverImage(blogUpdates.getCoverImage());
        
        // Map published version changes
        if (blogUpdates.getPublishedTitle() != null) blog.setPublishedTitle(blogUpdates.getPublishedTitle());
        if (blogUpdates.getPublishedExcerpt() != null) blog.setPublishedExcerpt(blogUpdates.getPublishedExcerpt());
        if (blogUpdates.getPublishedContent() != null) blog.setPublishedContent(blogUpdates.getPublishedContent());
        if (blogUpdates.getPublishedCoverImage() != null) blog.setPublishedCoverImage(blogUpdates.getPublishedCoverImage());
        if (blogUpdates.getHasUnpublishedChanges() != null) blog.setHasUnpublishedChanges(blogUpdates.getHasUnpublishedChanges());
        
        blog.setUpdatedAt(System.currentTimeMillis());
        
        Blog saved = blogRepository.save(blog);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/blogs/{id}")
    public ResponseEntity<Blog> updateBlogPut(@PathVariable String id, @RequestBody Blog blogUpdates) {
        Optional<Blog> blogOpt = blogRepository.findById(id);
        if (blogOpt.isEmpty()) {
            if (blogUpdates.getId() == null) {
                blogUpdates.setId(id);
            }
            if (blogUpdates.getCreatedAt() == null) {
                blogUpdates.setCreatedAt(System.currentTimeMillis());
            }
            Blog saved = blogRepository.save(blogUpdates);
            return ResponseEntity.ok(saved);
        }
        Blog blog = blogOpt.get();
        if (blogUpdates.getTitle() != null) blog.setTitle(blogUpdates.getTitle());
        if (blogUpdates.getExcerpt() != null) blog.setExcerpt(blogUpdates.getExcerpt());
        if (blogUpdates.getContent() != null) blog.setContent(blogUpdates.getContent());
        if (blogUpdates.getCategory() != null) blog.setCategory(blogUpdates.getCategory());
        if (blogUpdates.getAuthor() != null) blog.setAuthor(blogUpdates.getAuthor());
        if (blogUpdates.getDate() != null) blog.setDate(blogUpdates.getDate());
        if (blogUpdates.getPublished() != null) blog.setPublished(blogUpdates.getPublished());
        if (blogUpdates.getCoverImage() != null) blog.setCoverImage(blogUpdates.getCoverImage());
        
        // Map published version changes
        blog.setPublishedTitle(blogUpdates.getPublishedTitle());
        blog.setPublishedExcerpt(blogUpdates.getPublishedExcerpt());
        blog.setPublishedContent(blogUpdates.getPublishedContent());
        blog.setPublishedCoverImage(blogUpdates.getPublishedCoverImage());
        if (blogUpdates.getHasUnpublishedChanges() != null) {
            blog.setHasUnpublishedChanges(blogUpdates.getHasUnpublishedChanges());
        }
        
        blog.setUpdatedAt(System.currentTimeMillis());
        
        Blog saved = blogRepository.save(blog);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/blogs/{id}")
    public ResponseEntity<Void> deleteBlog(@PathVariable String id) {
        blogRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- STATIC CONTENT ENDPOINTS ---
    @GetMapping("/static-content")
    public ResponseEntity<List<StaticContent>> getStaticContent(
            @RequestParam(required = false) String portal,
            @RequestParam(required = false) String category) {
        List<StaticContent> list;
        if (portal != null && category != null) {
            list = staticContentRepository.findByPortalAndCategory(portal, category);
        } else if (portal != null) {
            list = staticContentRepository.findByPortal(portal);
        } else if (category != null) {
            list = staticContentRepository.findByCategory(category);
        } else {
            list = staticContentRepository.findAll();
        }
        // Sort by createdAt descending
        list.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
        return ResponseEntity.ok(list);
    }

    @PostMapping("/static-content")
    public ResponseEntity<StaticContent> createStaticContent(@RequestBody StaticContent sc) {
        if (sc.getId() == null) {
            sc.setId("sc-" + System.currentTimeMillis());
        }
        sc.setCreatedAt(System.currentTimeMillis());
        sc.setUpdatedAt(System.currentTimeMillis());
        StaticContent saved = staticContentRepository.save(sc);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PatchMapping("/static-content/{id}")
    public ResponseEntity<StaticContent> updateStaticContent(@PathVariable String id, @RequestBody StaticContent scUpdates) {
        Optional<StaticContent> scOpt = staticContentRepository.findById(id);
        if (scOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        StaticContent sc = scOpt.get();
        if (scUpdates.getTitle() != null) sc.setTitle(scUpdates.getTitle());
        if (scUpdates.getDescription() != null) sc.setDescription(scUpdates.getDescription());
        if (scUpdates.getContent() != null) sc.setContent(scUpdates.getContent());
        if (scUpdates.getPortal() != null) sc.setPortal(scUpdates.getPortal());
        if (scUpdates.getCategory() != null) sc.setCategory(scUpdates.getCategory());
        if (scUpdates.getIsPublished() != null) sc.setIsPublished(scUpdates.getIsPublished());
        if (scUpdates.getIsPinned() != null) sc.setIsPinned(scUpdates.getIsPinned());
        sc.setUpdatedAt(System.currentTimeMillis());

        StaticContent saved = staticContentRepository.save(sc);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/static-content/{id}")
    public ResponseEntity<Void> deleteStaticContent(@PathVariable String id) {
        staticContentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- KYC ENDPOINTS ---
    @GetMapping("/kyc")
    public ResponseEntity<List<Map<String, Object>>> getAllKyc() {
        List<Kyc> kycList = kycRepository.findAll();
        List<Map<String, Object>> response = kycList.stream().map(k -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", k.getId());
            map.put("clientId", k.getClientId());
            map.put("name", k.getName());
            map.put("idType", k.getIdType());
            map.put("nation", k.getNation());
            map.put("status", k.getStatus());
            map.put("risk", k.getRisk());
            map.put("lastUpdated", k.getLastUpdated());

            Optional<User> userOpt = userRepository.findById(k.getClientId());
            if (userOpt.isPresent()) {
                map.put("clientName", userOpt.get().getFirstName() + " " + userOpt.get().getLastName());
            } else {
                map.put("clientName", "Unknown");
            }
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/kyc/{id}")
    public ResponseEntity<Kyc> updateKyc(@PathVariable String id, @RequestBody Map<String, Object> updates) {
        Optional<Kyc> kycOpt = kycRepository.findById(id);
        if (kycOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Kyc kyc = kycOpt.get();
        if (updates.containsKey("status")) kyc.setStatus((String) updates.get("status"));
        if (updates.containsKey("risk")) kyc.setRisk((String) updates.get("risk"));
        kyc.setLastUpdated(System.currentTimeMillis());
        Kyc saved = kycRepository.save(kyc);
        return ResponseEntity.ok(saved);
    }

    // --- COMPLIANCE ENDPOINTS ---
    @GetMapping("/compliance")
    public ResponseEntity<List<Map<String, Object>>> getAllCompliance() {
        List<Compliance> list = complianceRepository.findAll();
        List<Map<String, Object>> response = list.stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", c.getId());
            map.put("complianceId", c.getId());
            map.put("clientId", c.getClientId());
            map.put("name", c.getName());
            map.put("type", c.getType());
            map.put("status", c.getStatus());
            map.put("risk", c.getRisk());
            map.put("lastUpdated", c.getLastUpdated());

            Optional<User> userOpt = userRepository.findById(c.getClientId());
            if (userOpt.isPresent()) {
                map.put("clientName", userOpt.get().getFirstName() + " " + userOpt.get().getLastName());
            } else {
                map.put("clientName", "Unknown");
            }
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/compliance/{id}")
    public ResponseEntity<Compliance> updateCompliance(@PathVariable String id, @RequestBody Map<String, Object> updates) {
        Optional<Compliance> compOpt = complianceRepository.findById(id);
        if (compOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Compliance comp = compOpt.get();
        if (updates.containsKey("status")) comp.setStatus((String) updates.get("status"));
        if (updates.containsKey("risk")) comp.setRisk((String) updates.get("risk"));
        comp.setLastUpdated(System.currentTimeMillis());
        Compliance saved = complianceRepository.save(comp);
        return ResponseEntity.ok(saved);
    }

    // --- DOCUMENT ENDPOINTS ---
    @GetMapping("/documents")
    public ResponseEntity<List<Map<String, Object>>> getAllDocuments() {
        List<ClientDocument> documents = clientDocumentRepository.findAll();
        List<Requirement> requirements = requirementRepository.findAll();

        List<Map<String, Object>> response = documents.stream().map(d -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", d.getId());
            map.put("title", d.getTitle());
            map.put("file", d.getFile());
            map.put("status", d.getStatus());
            map.put("clientId", d.getClientId());
            map.put("date", d.getDate());

            Optional<User> userOpt = userRepository.findById(d.getClientId());
            String clientName = "Unknown";
            if (userOpt.isPresent()) {
                clientName = userOpt.get().getFirstName() + " " + userOpt.get().getLastName();
            }
            map.put("clientName", clientName);
            map.put("client", clientName + " - " + d.getClientId().replace("C-", "APP-"));

            Optional<Requirement> reqOpt = requirements.stream()
                    .filter(r -> r.getUserId().equals(d.getClientId()))
                    .findFirst();

            String companyName = "Unknown";
            if (reqOpt.isPresent()) {
                Map<String, Object> data = reqOpt.get().getData();
                if (data != null && data.containsKey("names")) {
                    Object namesObj = data.get("names");
                    if (namesObj instanceof List) {
                        List<String> names = (List<String>) namesObj;
                        if (!names.isEmpty()) companyName = names.get(0);
                    }
                }
            }
            map.put("company", companyName);

            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    // --- NOTIFICATION ENDPOINTS ---
    @GetMapping("/notifications")
    public ResponseEntity<List<Notification>> getNotifications(@RequestParam(required = false) String clientId) {
        List<Notification> list;
        if (clientId != null && !clientId.isEmpty()) {
            list = notificationRepository.findNotificationsForClient(clientId);
        } else {
            list = notificationRepository.findAll();
        }
        // Sort by timestamp descending
        list.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return ResponseEntity.ok(list);
    }

    @PostMapping("/notifications/read")
    public ResponseEntity<Map<String, Object>> markNotificationAsRead(@RequestBody Map<String, String> body) {
        String notifId = body.get("notifId");
        String clientId = body.get("clientId");

        if (notifId == null || clientId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "notifId and clientId are required"));
        }

        Optional<Notification> notifOpt = notificationRepository.findById(notifId);
        if (notifOpt.isPresent()) {
            Notification notification = notifOpt.get();
            if (notification.getReadBy() == null) {
                notification.setReadBy(new ArrayList<>());
            }
            if (!notification.getReadBy().contains(clientId)) {
                notification.getReadBy().add(clientId);
                notificationRepository.save(notification);
            }
            return ResponseEntity.ok(Map.of("success", true));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // --- MESSAGE ENDPOINTS ---
    @GetMapping("/messages")
    public ResponseEntity<List<Message>> getMessages(@RequestParam(required = false) String clientId) {
        List<Message> list;
        if (clientId != null && !clientId.isEmpty()) {
            list = messageRepository.findByClientId(clientId);
        } else {
            list = messageRepository.findAll();
        }
        // Sort by timestamp ascending (chronological chat history)
        list.sort(Comparator.comparingLong(Message::getTimestamp));
        return ResponseEntity.ok(list);
    }

    @PostMapping("/messages")
    public ResponseEntity<Message> createMessage(@RequestBody Message message) {
        if (message.getId() == null) {
            message.setId("msg-" + System.currentTimeMillis());
        }
        if (message.getTimestamp() == null) {
            message.setTimestamp(System.currentTimeMillis());
        }
        Message saved = messageRepository.save(message);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/messages/conversations")
    public ResponseEntity<List<Map<String, Object>>> getConversations() {
        List<Message> allMessages = messageRepository.findAll();
        Map<String, Message> latestMessagePerClient = new HashMap<>();

        for (Message m : allMessages) {
            if (m.getClientId() == null) continue;
            Message existing = latestMessagePerClient.get(m.getClientId());
            if (existing == null || m.getTimestamp() > existing.getTimestamp()) {
                latestMessagePerClient.put(m.getClientId(), m);
            }
        }

        List<Map<String, Object>> conversations = new ArrayList<>();
        for (Map.Entry<String, Message> entry : latestMessagePerClient.entrySet()) {
            String clientId = entry.getKey();
            Message latestMsg = entry.getValue();

            Map<String, Object> conv = new HashMap<>();
            conv.put("clientId", clientId);

            Optional<User> userOpt = userRepository.findById(clientId);
            String clientName = userOpt.map(u -> u.getFirstName() + " " + u.getLastName()).orElse("Unknown");
            conv.put("clientName", clientName);
            conv.put("lastMessage", latestMsg.getText());
            conv.put("lastMessageTime", latestMsg.getTimestamp());
            conv.put("unreadCount", 0); // Mock unread count

            conversations.add(conv);
        }

        // Sort by lastMessageTime descending
        conversations.sort((a, b) -> Long.compare((Long) b.get("lastMessageTime"), (Long) a.get("lastMessageTime")));
        return ResponseEntity.ok(conversations);
    }

    // --- APPLICATION ENDPOINTS ---
    @GetMapping("/applications")
    public ResponseEntity<List<Map<String, Object>>> getAllApplications() {
        List<Requirement> requirements = requirementRepository.findAll();
        List<User> users = userRepository.findAll();
        List<Kyc> kycRecords = kycRepository.findAll();
        List<ClientDocument> documents = clientDocumentRepository.findAll();

        List<Map<String, Object>> apps = requirements.stream().map(req -> {
            Map<String, Object> map = new HashMap<>();
            
            // Map ID: e.g. "SRV-1001"
            map.put("id", req.getId() != null ? req.getId().replace("SRV-", "APP-") : "APP-unknown");

            // Extract company name
            String companyName = "N/A";
            Map<String, Object> data = req.getData();
            if (data != null && data.containsKey("names")) {
                Object namesObj = data.get("names");
                if (namesObj instanceof List) {
                    List<String> names = (List<String>) namesObj;
                    if (!names.isEmpty()) companyName = names.get(0);
                }
            }
            map.put("business", companyName);

            // Find client
            Optional<User> userOpt = users.stream().filter(u -> u.getId().equals(req.getUserId())).findFirst();
            map.put("client", userOpt.map(u -> u.getFirstName() + " " + u.getLastName()).orElse("Unknown"));

            map.put("staff", "Sarah Lim");
            map.put("status", req.getStatus() != null ? req.getStatus() : "pending");
            
            // Priority & Deadline
            String priority = "Normal";
            String deadline = "N/A";
            if (data != null) {
                if (data.containsKey("priority")) priority = (String) data.get("priority");
                if (data.containsKey("deadline")) deadline = (String) data.get("deadline");
            }
            map.put("priority", priority);
            map.put("deadline", deadline);

            // Find KYC status
            Optional<Kyc> kycOpt = kycRecords.stream().filter(k -> k.getClientId().equals(req.getUserId())).findFirst();
            map.put("kyc", kycOpt.map(k -> capitalize(k.getStatus())).orElse("Approved"));

            // Documents status
            long pendingDocs = documents.stream()
                    .filter(d -> d.getClientId().equals(req.getUserId()) && "pending".equalsIgnoreCase(d.getStatus()))
                    .count();
            map.put("docs", pendingDocs > 0 ? pendingDocs + " Docs Pending" : "All Docs OK");

            // Date
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            map.put("date", req.getUpdatedAt() != null ? sdf.format(req.getUpdatedAt()) : sdf.format(new Date()));

            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(apps);
    }

    // --- REPORT ENDPOINTS ---
    @GetMapping("/reports")
    public ResponseEntity<Map<String, Object>> getReports() {
        List<Requirement> requirements = requirementRepository.findAll();
        List<Kyc> kycRecords = kycRepository.findAll();

        long totalApps = requirements.size();
        long approved = requirements.stream().filter(r -> "approved".equalsIgnoreCase(r.getStatus()) || "completed".equalsIgnoreCase(r.getStatus())).count();
        long rejected = requirements.stream().filter(r -> "rejected".equalsIgnoreCase(r.getStatus())).count();
        long pendingKyc = kycRecords.stream().filter(k -> "pending".equalsIgnoreCase(k.getStatus())).count();

        // Revenue = SGD 1,500 per approved/completed incorporation
        long revenue = approved * 1500;

        Map<String, Long> byService = new HashMap<>();
        byService.put("Company Incorporation", totalApps);

        Map<String, Object> response = new HashMap<>();
        response.put("totalApplications", totalApps);
        response.put("approved", approved);
        response.put("rejected", rejected);
        response.put("pendingKYC", pendingKyc);
        response.put("revenue", revenue);
        response.put("byService", byService);

        return ResponseEntity.ok(response);
    }

    // --- CATALOG ENDPOINTS ---
    @GetMapping("/catalog")
    public ResponseEntity<List<CatalogItem>> getCatalog() {
        return ResponseEntity.ok(catalogItemRepository.findAll());
    }

    // --- INVOICE ENDPOINTS ---
    @GetMapping("/clients/{id}/invoices")
    public ResponseEntity<List<Invoice>> getClientInvoices(@PathVariable String id) {
        return ResponseEntity.ok(invoiceRepository.findByClientId(id));
    }

    // --- COUNTRY ENDPOINTS ---
    @GetMapping("/countries")
    public ResponseEntity<List<Country>> getAllCountries() {
        List<Country> countries = countryRepository.findAll();
        // If empty in DB, initialize with default countries
        if (countries.isEmpty()) {
            List<Country> defaultCountries = Arrays.asList(
                createDefaultCountry("Singapore", "SG", "9-character alphanumeric", "17% (flat rate)", "99.5%", true, Arrays.asList("Company Incorporation", "Corporate Secretary", "Nominee Director", "Office Address", "Tax & Compliance"), 1315.0, 900.0, 3000.0, 600.0, 1500.0, 500.0),
                createDefaultCountry("Hong Kong", "HK", "8-digit registration no.", "16.5% (two-tier)", "98.8%", true, Arrays.asList("Company Incorporation", "Corporate Secretary", "Office Address", "Tax & Compliance"), 1650.0, 800.0, 2500.0, 500.0, 1200.0, 400.0),
                createDefaultCountry("United States", "USA", "9-digit EIN number", "21% (federal flat)", "97.2%", true, Arrays.asList("Company Incorporation", "Corporate Secretary", "Nominee Director", "Office Address", "Tax & Compliance"), 1200.0, 1000.0, 3000.0, 700.0, 1500.0, 500.0),
                createDefaultCountry("Dubai", "UAE", "Varies by Free Zone", "9% (above 375k AED)", "99.1%", true, Arrays.asList("Company Incorporation", "Corporate Secretary", "Nominee Director", "Office Address", "Tax & Compliance"), 2500.0, 1200.0, 4000.0, 900.0, 1800.0, 600.0),
                createDefaultCountry("Australia", "AUS", "9-digit ACN number", "25% - 30%", "96.8%", true, Arrays.asList("Company Incorporation", "Corporate Secretary", "Nominee Director", "Office Address", "Tax & Compliance"), 1400.0, 950.0, 3100.0, 650.0, 1600.0, 550.0),
                createDefaultCountry("United Kingdom", "UK", "8-digit CRN number", "19% - 25%", "98.5%", true, Arrays.asList("Company Incorporation", "Corporate Secretary", "Nominee Director", "Office Address", "Tax & Compliance"), 1300.0, 850.0, 2800.0, 550.0, 1400.0, 450.0)
            );
            countries = countryRepository.saveAll(defaultCountries);
        }
        return ResponseEntity.ok(countries);
    }

    private Country createDefaultCountry(String name, String code, String uen, String tax, String compliance, boolean published, List<String> services, Double basePrice, Double priceSecretary, Double priceDirector, Double priceAddress, Double priceTax, Double priceBank) {
        Country c = new Country();
        c.setId("CNTRY-" + name.toLowerCase().replace(" ", "-"));
        c.setName(name);
        c.setCode(code);
        c.setUen(uen);
        c.setTax(tax);
        c.setCompliance(compliance);
        c.setStatus("ACTIVE");
        c.setPublished(published);
        c.setServices(services);
        c.setBasePrice(basePrice);
        c.setPriceSecretary(priceSecretary);
        c.setPriceDirector(priceDirector);
        c.setPriceAddress(priceAddress);
        c.setPriceTax(priceTax);
        c.setPriceBank(priceBank);

        Map<String, Object> pubData = new HashMap<>();
        pubData.put("name", name);
        pubData.put("code", code);
        pubData.put("uen", uen);
        pubData.put("tax", tax);
        pubData.put("compliance", compliance);
        pubData.put("services", services);
        pubData.put("basePrice", basePrice);
        pubData.put("priceSecretary", priceSecretary);
        pubData.put("priceDirector", priceDirector);
        pubData.put("priceAddress", priceAddress);
        pubData.put("priceTax", priceTax);
        pubData.put("priceBank", priceBank);
        pubData.put("customPrices", new HashMap<>());
        c.setPublishedData(pubData);

        return c;
    }

    @PostMapping("/countries")
    public ResponseEntity<Country> createCountry(@RequestBody Country country) {
        if (country.getId() == null) {
            country.setId("CNTRY-" + System.currentTimeMillis());
        }
        Country saved = countryRepository.save(country);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/countries/{id}")
    public ResponseEntity<Country> updateCountry(@PathVariable String id, @RequestBody Country countryUpdates) {
        Optional<Country> countryOpt = countryRepository.findById(id);
        if (countryOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Country country = countryOpt.get();
        if (countryUpdates.getName() != null) country.setName(countryUpdates.getName());
        if (countryUpdates.getCode() != null) country.setCode(countryUpdates.getCode());
        if (countryUpdates.getUen() != null) country.setUen(countryUpdates.getUen());
        if (countryUpdates.getTax() != null) country.setTax(countryUpdates.getTax());
        if (countryUpdates.getCompliance() != null) country.setCompliance(countryUpdates.getCompliance());
        if (countryUpdates.getStatus() != null) country.setStatus(countryUpdates.getStatus());
        if (countryUpdates.getPublished() != null) country.setPublished(countryUpdates.getPublished());
        if (countryUpdates.getServices() != null) country.setServices(countryUpdates.getServices());
        if (countryUpdates.getBasePrice() != null) country.setBasePrice(countryUpdates.getBasePrice());
        if (countryUpdates.getPriceSecretary() != null) country.setPriceSecretary(countryUpdates.getPriceSecretary());
        if (countryUpdates.getPriceDirector() != null) country.setPriceDirector(countryUpdates.getPriceDirector());
        if (countryUpdates.getPriceAddress() != null) country.setPriceAddress(countryUpdates.getPriceAddress());
        if (countryUpdates.getPriceTax() != null) country.setPriceTax(countryUpdates.getPriceTax());
        if (countryUpdates.getPriceBank() != null) country.setPriceBank(countryUpdates.getPriceBank());
        if (countryUpdates.getCustomPrices() != null) country.setCustomPrices(countryUpdates.getCustomPrices());
        if (countryUpdates.getPublishedData() != null) country.setPublishedData(countryUpdates.getPublishedData());

        Country saved = countryRepository.save(country);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/countries/{id}")
    public ResponseEntity<Void> deleteCountry(@PathVariable String id) {
        countryRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
