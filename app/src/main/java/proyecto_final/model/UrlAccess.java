package proyecto_final.model;

import java.util.Date;

public class UrlAccess {
    private String shortCode;
    private Date timestamp;
    private String browser;
    private String ip;
    private String domain;
    private String os;

    public UrlAccess(String shortCode, String browser, String ip, String domain, String os) {
        this.shortCode = shortCode;
        this.timestamp = new Date();
        this.browser = browser;
        this.ip = ip;
        this.domain = domain;
        this.os = os;
    }

    // Getters
    public String getShortCode() {
        return shortCode;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public String getBrowser() {
        return browser;
    }

    public String getIp() {
        return ip;
    }

    public String getDomain() {
        return domain;
    }

    public String getOs() {
        return os;
    }
}