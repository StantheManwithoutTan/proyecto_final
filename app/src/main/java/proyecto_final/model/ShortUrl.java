package proyecto_final.model;

import java.util.Date;

public class ShortUrl {
    private String shortCode;
    private String originalUrl;
    private String createdBy;
    private Date createdAt;
    private int accessCount;

    public ShortUrl(String shortCode, String originalUrl, String createdBy) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.createdBy = createdBy;
        this.createdAt = new Date();
        this.accessCount = 0;
    }

    // Getters and setters
    public String getShortCode() {
        return shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public int getAccessCount() {
        return accessCount;
    }

    public void incrementAccessCount() {
        this.accessCount++;
    }

    public void setAccessCount(int accessCount) {
        this.accessCount = accessCount;
    }
}