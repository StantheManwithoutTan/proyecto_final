package proyecto_final.model;

import java.util.Date;

public class ShortUrl {
    private String shortCode;
    private String originalUrl;
    private String createdBy;
    private Date createdAt;
    private int accessCount;
    private String sessionId; // Para usuarios no registrados
    private boolean isAnonymous; // Para diferenciar URLs de usuarios registrados vs no registrados

    // Constructor para usuario registrado
    public ShortUrl(String shortCode, String originalUrl, String createdBy) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.createdBy = createdBy;
        this.createdAt = new Date();
        this.accessCount = 0;
        this.isAnonymous = false;
    }

    // Constructor para usuario anónimo (añadido un parámetro boolean para diferenciar)
    public ShortUrl(String shortCode, String originalUrl, String sessionId, boolean isAnonymous) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.sessionId = sessionId;
        this.createdBy = "anonymous";
        this.createdAt = new Date();
        this.accessCount = 0;
        this.isAnonymous = true;
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

    public String getSessionId() {
        return sessionId;
    }

    public boolean isAnonymous() {
        return isAnonymous;
    }
}