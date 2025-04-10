package proyecto_final.service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import proyecto_final.config.MongoDBConfig;
import proyecto_final.model.Session;

import java.util.Date;

public class SessionService {
    private static final SessionService instance = new SessionService();
    private final MongoCollection<Document> sessionsCollection;
    
    private SessionService() {
        MongoDatabase database = MongoDBConfig.getDatabase();
        this.sessionsCollection = database.getCollection("Session");
    }
    
    public static SessionService getInstance() {
        return instance;
    }
    
    public Session createSession() {
        Session session = new Session();
        
        Document sessionDoc = new Document()
            .append("sessionId", session.getSessionId())
            .append("createdAt", session.getCreatedAt())
            .append("expiresAt", session.getExpiresAt());
            
        sessionsCollection.insertOne(sessionDoc);
        return session;
    }
    
    public Session getSession(String sessionId) {
        Document sessionDoc = sessionsCollection.find(
            Filters.and(
                Filters.eq("sessionId", sessionId),
                Filters.gt("expiresAt", new Date())
            )
        ).first();
        
        if (sessionDoc == null) {
            return null;
        }
        
        Session session = new Session();
        // Aquí se puede implementar un constructor que reciba los campos del documento
        // o usar reflection para setear los campos
        
        return session;
    }
    
    public boolean isSessionValid(String sessionId) {
        Document sessionDoc = sessionsCollection.find(
            Filters.and(
                Filters.eq("sessionId", sessionId),
                Filters.gt("expiresAt", new Date())
            )
        ).first();
        
        return sessionDoc != null;
    }
}