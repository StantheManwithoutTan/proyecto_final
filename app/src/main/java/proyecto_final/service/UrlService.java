package proyecto_final.service;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import proyecto_final.config.MongoDBConfig;
import proyecto_final.model.ShortUrl;
import proyecto_final.model.UrlAccess;

import java.security.SecureRandom;
import java.util.*;

public class UrlService {
    private static final UrlService instance = new UrlService();
    private final MongoCollection<Document> urlsCollection;
    private final MongoCollection<Document> accessesCollection;
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 6;
    private final SecureRandom random = new SecureRandom();

    private UrlService() {
        MongoDatabase database = MongoDBConfig.getDatabase();
        this.urlsCollection = database.getCollection("ShortUrl");
        this.accessesCollection = database.getCollection("UrlAccess");
        
        // Create index for shortCode to ensure uniqueness and quick lookups
        urlsCollection.createIndex(new Document("shortCode", 1));
    }

    public static UrlService getInstance() {
        return instance;
    }

    // Create a new shortened URL
    public ShortUrl createShortUrl(String originalUrl, String username) {
        try {
            // Ensure URL is properly formatted
            if (!originalUrl.startsWith("http://") && !originalUrl.startsWith("https://")) {
                originalUrl = "https://" + originalUrl;
            }
            
            // Validate URL format
            new java.net.URL(originalUrl);
            
            // Generate a unique short code
            String shortCode;
            do {
                shortCode = generateShortCode();
            } while (getUrlByShortCode(shortCode) != null);

            ShortUrl shortUrl = new ShortUrl(shortCode, originalUrl, username);

            Document urlDoc = new Document()
                .append("shortCode", shortUrl.getShortCode())
                .append("originalUrl", shortUrl.getOriginalUrl())
                .append("createdBy", shortUrl.getCreatedBy())
                .append("createdAt", shortUrl.getCreatedAt())
                .append("accessCount", shortUrl.getAccessCount());

            urlsCollection.insertOne(urlDoc);
            return shortUrl;
        } catch (Exception e) {
            System.err.println("Error creating short URL: " + e.getMessage());
            throw new RuntimeException("Invalid URL format: " + e.getMessage());
        }
    }

    // Generate a random short code
    private String generateShortCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    // Get URL by shortCode
    public ShortUrl getUrlByShortCode(String shortCode) {
        Document urlDoc = urlsCollection.find(Filters.eq("shortCode", shortCode)).first();
        if (urlDoc == null) return null;

        ShortUrl shortUrl = new ShortUrl(
            urlDoc.getString("shortCode"),
            urlDoc.getString("originalUrl"),
            urlDoc.getString("createdBy")
        );
        shortUrl.setAccessCount(urlDoc.getInteger("accessCount", 0));
        return shortUrl;
    }

    // Record an access to a URL
    public void recordAccess(String shortCode, String browser, String ip, String domain, String os) {
        // Update access count
        urlsCollection.updateOne(
            Filters.eq("shortCode", shortCode),
            Updates.inc("accessCount", 1)
        );

        // Record access details
        UrlAccess access = new UrlAccess(shortCode, browser, ip, domain, os);
        Document accessDoc = new Document()
            .append("shortCode", access.getShortCode())
            .append("timestamp", access.getTimestamp())
            .append("browser", access.getBrowser())
            .append("ip", access.getIp())
            .append("domain", access.getDomain())
            .append("os", access.getOs());

        accessesCollection.insertOne(accessDoc);
    }

    // Delete a URL
    public boolean deleteUrl(String shortCode, String username) {
        Bson filter = Filters.and(
            Filters.eq("shortCode", shortCode),
            Filters.eq("createdBy", username)
        );
        
        DeleteResult deleteResult = urlsCollection.deleteOne(filter);
        
        // Delete related accesses
        if (deleteResult.getDeletedCount() > 0) {
            accessesCollection.deleteMany(Filters.eq("shortCode", shortCode));
            return true;
        }
        
        return false;
    }

    // Delete any URL regardless of owner (admin only)
    public boolean deleteAnyUrl(String shortCode) {
        Document urlDoc = urlsCollection.find(Filters.eq("shortCode", shortCode)).first();
        
        if (urlDoc == null) {
            return false;
        }
        
        DeleteResult deleteResult = urlsCollection.deleteOne(Filters.eq("shortCode", shortCode));
        
        // Delete related accesses
        if (deleteResult.getDeletedCount() > 0) {
            accessesCollection.deleteMany(Filters.eq("shortCode", shortCode));
            return true;
        }
        
        return false;
    }

    // Get all URLs for a user
    public List<Map<String, Object>> getUrlsByUser(String username) {
        List<Map<String, Object>> urls = new ArrayList<>();
        FindIterable<Document> docs = urlsCollection.find(Filters.eq("createdBy", username));
        
        for (Document doc : docs) {
            Map<String, Object> url = new HashMap<>();
            url.put("shortCode", doc.getString("shortCode"));
            url.put("originalUrl", doc.getString("originalUrl"));
            url.put("createdAt", doc.getDate("createdAt"));
            url.put("accessCount", doc.getInteger("accessCount", 0));
            urls.add(url);
        }
        
        return urls;
    }

    // Get analytics for a URL
    public Map<String, Object> getAnalytics(String shortCode, String username) {
        Map<String, Object> analytics = new HashMap<>();
        
        // Get the URL info
        Document urlDoc = urlsCollection.find(
            Filters.and(
                Filters.eq("shortCode", shortCode),
                Filters.eq("createdBy", username)
            )
        ).first();
        
        if (urlDoc == null) {
            return null;
        }
        
        analytics.put("shortCode", shortCode);
        analytics.put("originalUrl", urlDoc.getString("originalUrl"));
        analytics.put("createdAt", urlDoc.getDate("createdAt"));
        analytics.put("accessCount", urlDoc.getInteger("accessCount", 0));
        
        // Get access information
        List<Map<String, Object>> accesses = new ArrayList<>();
        FindIterable<Document> accessDocs = accessesCollection
            .find(Filters.eq("shortCode", shortCode))
            .sort(new Document("timestamp", -1)) // Most recent first
            .limit(100); // Limit to last 100 accesses
        
        Map<String, Integer> browsers = new HashMap<>();
        Map<String, Integer> operatingSystems = new HashMap<>();
        
        for (Document doc : accessDocs) {
            Map<String, Object> access = new HashMap<>();
            access.put("timestamp", doc.getDate("timestamp"));
            access.put("browser", doc.getString("browser"));
            access.put("ip", doc.getString("ip"));
            access.put("domain", doc.getString("domain"));
            access.put("os", doc.getString("os"));
            accesses.add(access);
            
            // Count browser usage
            String browser = doc.getString("browser");
            browsers.put(browser, browsers.getOrDefault(browser, 0) + 1);
            
            // Count OS usage
            String os = doc.getString("os");
            operatingSystems.put(os, operatingSystems.getOrDefault(os, 0) + 1);
        }
        
        analytics.put("accesses", accesses);
        analytics.put("browsers", browsers);
        analytics.put("operatingSystems", operatingSystems);
        
        return analytics;
    }

    // Create URL for anonymous user
    public ShortUrl createShortUrlAnonymous(String originalUrl, String sessionId) {
        // Generate unique short code
        String shortCode;
        do {
            shortCode = generateShortCode();
        } while (getUrlByShortCode(shortCode) != null);

        // Create shortened URL for anonymous user using new constructor
        ShortUrl shortUrl = new ShortUrl(shortCode, originalUrl, sessionId, true);

        Document urlDoc = new Document()
            .append("shortCode", shortUrl.getShortCode())
            .append("originalUrl", shortUrl.getOriginalUrl())
            .append("sessionId", shortUrl.getSessionId())
            .append("createdBy", "anonymous")
            .append("createdAt", shortUrl.getCreatedAt())
            .append("accessCount", shortUrl.getAccessCount())
            .append("isAnonymous", true);

        urlsCollection.insertOne(urlDoc);
        return shortUrl;
    }

    // Get URLs by session
    public List<Map<String, Object>> getUrlsBySession(String sessionId) {
        List<Map<String, Object>> urls = new ArrayList<>();
        FindIterable<Document> docs = urlsCollection.find(Filters.eq("sessionId", sessionId));
        
        for (Document doc : docs) {
            Map<String, Object> url = new HashMap<>();
            url.put("shortCode", doc.getString("shortCode"));
            url.put("originalUrl", doc.getString("originalUrl"));
            url.put("createdAt", doc.getDate("createdAt"));
            url.put("accessCount", doc.getInteger("accessCount", 0));
            urls.add(url);
        }
        
        return urls;
    }

    // Check if a URL belongs to a session
    public boolean isUrlOwnedBySession(String shortCode, String sessionId) {
        Document urlDoc = urlsCollection.find(
            Filters.and(
                Filters.eq("shortCode", shortCode),
                Filters.eq("sessionId", sessionId)
            )
        ).first();
        
        return urlDoc != null;
    }

    // Get analytics for anonymous user
    public Map<String, Object> getAnalyticsForSession(String shortCode, String sessionId) {
        Map<String, Object> analytics = new HashMap<>();
        
        Document urlDoc = urlsCollection.find(
            Filters.and(
                Filters.eq("shortCode", shortCode),
                Filters.eq("sessionId", sessionId)
            )
        ).first();
        
        if (urlDoc == null) {
            return null;
        }
        
        // The rest is the same as getAnalytics for registered users
        analytics.put("shortCode", shortCode);
        analytics.put("originalUrl", urlDoc.getString("originalUrl"));
        analytics.put("createdAt", urlDoc.getDate("createdAt"));
        analytics.put("accessCount", urlDoc.getInteger("accessCount", 0));
        
        // Get access information same as before...
        // ...
        
        return analytics;
    }

    // Get all URLs (admin only)
    public List<Map<String, Object>> getAllUrls() {
        List<Map<String, Object>> urls = new ArrayList<>();
        FindIterable<Document> docs = urlsCollection.find();
        
        for (Document doc : docs) {
            Map<String, Object> url = new HashMap<>();
            url.put("shortCode", doc.getString("shortCode"));
            url.put("originalUrl", doc.getString("originalUrl"));
            url.put("createdBy", doc.getString("createdBy"));
            url.put("createdAt", doc.getDate("createdAt"));
            url.put("accessCount", doc.getInteger("accessCount", 0));
            url.put("isAnonymous", doc.getBoolean("isAnonymous", false));
            
            // For anonymous URLs, add sessionId
            if (doc.getBoolean("isAnonymous", false)) {
                url.put("sessionId", doc.getString("sessionId"));
            }
            
            urls.add(url);
        }
        
        return urls;
    }

    // Add this method to UrlService.java
    public boolean syncBatchOperations(List<Map<String, Object>> operations, String username) {
        try {
            for (Map<String, Object> operation : operations) {
                String operationType = (String) operation.get("type");
                
                switch (operationType) {
                    case "create":
                        Map<String, Object> urlData = (Map<String, Object>) operation.get("data");
                        createShortUrl((String) urlData.get("originalUrl"), username);
                        break;
                        
                    case "delete":
                        String shortCode = (String) operation.get("shortCode");
                        deleteUrl(shortCode, username);
                        break;
                        
                    // Add more operations as needed
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println("Error synchronizing operations: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get all URLs with pagination support
     * @param page Page number (starting from 1)
     * @param limit Items per page
     * @return Map with urls, pagination info and total count
     */
    public Map<String, Object> getAllUrlsPaginated(int page, int limit) {
        Map<String, Object> result = new HashMap<>();
        
        // Get all URLs first (we'll implement proper DB-level pagination later)
        List<Map<String, Object>> allUrls = getAllUrls();
        
        int total = allUrls.size();
        int startIndex = (page - 1) * limit;
        int endIndex = Math.min(startIndex + limit, total);
        
        // Validate indices to prevent out of bounds errors
        if (startIndex >= total) {
            startIndex = Math.max(0, total - limit);
            endIndex = total;
        }
        
        List<Map<String, Object>> paginatedUrls = allUrls.subList(startIndex, endIndex);
        
        result.put("urls", paginatedUrls);
        result.put("total", total);
        result.put("page", page);
        result.put("limit", limit);
        
        return result;
    }
}