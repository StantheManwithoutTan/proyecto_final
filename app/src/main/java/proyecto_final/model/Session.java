package proyecto_final.model;

import java.util.Date;
import java.util.UUID;

public class Session {
    private String sessionId;
    private Date createdAt;
    private Date expiresAt;

    public Session() {
        this.sessionId = UUID.randomUUID().toString();
        this.createdAt = new Date();
        // Sesión expira en 24 horas
        this.expiresAt = new Date(System.currentTimeMillis() + 86400000);
    }

    public String getSessionId() {
        return sessionId;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public Date getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return new Date().after(expiresAt);
    }
}