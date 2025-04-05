package proyecto_final.service;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import org.bson.Document;
import proyecto_final.config.MongoDBConfig;
import proyecto_final.model.User;

import java.util.HashMap;
import java.util.Map;

public class UserService {
    private static final UserService instance = new UserService();
    private final MongoCollection<Document> usersCollection;
    
    private UserService() {
        // Get environment variables for database name
        String dbName = System.getenv("DB_NOMBRE") != null ? 
                        System.getenv("DB_NOMBRE") : "proyecto_finalDB";
        
        // Get MongoDB database
        MongoDatabase database = MongoDBConfig.getDatabase();
        
        // Use "User" collection name instead of "users"
        this.usersCollection = database.getCollection("User");
        
        // Check if admin user exists, create if not
        createAdminIfNotExists();
    }
    
    private void createAdminIfNotExists() {
        // Check if admin exists
        Document adminUser = usersCollection.find(Filters.eq("username", "admin")).first();
        
        if (adminUser == null) {
            // Create admin user document
            Document adminDoc = new Document()
                .append("username", "admin")
                .append("password", "admin")  // Use a stronger password in production!
                .append("isAdmin", true)
                .append("isRootAdmin", true);
            
            usersCollection.insertOne(adminDoc);
            System.out.println("Root admin user created successfully");
        } else {
            System.out.println("Admin user already exists");
        }
    }
    
    public static UserService getInstance() {
        return instance;
    }
    
    public boolean registerUser(String username, String password) {
        // Check if user already exists
        if (getUser(username) != null) {
            return false;
        }
        
        // Create new user document
        Document userDoc = new Document()
            .append("username", username)
            .append("password", password)
            .append("isAdmin", false)
            .append("isRootAdmin", false);
        
        try {
            usersCollection.insertOne(userDoc);
            return true;
        } catch (Exception e) {
            System.err.println("Error registering user: " + e.getMessage());
            return false;
        }
    }
    
    public User getUser(String username) {
        Document userDoc = usersCollection.find(Filters.eq("username", username)).first();
        
        if (userDoc == null) {
            return null;
        }
        
        return new User(
            userDoc.getString("username"),
            userDoc.getString("password"),
            userDoc.getBoolean("isAdmin", false),
            userDoc.getBoolean("isRootAdmin", false)
        );
    }
    
    public boolean validateCredentials(String username, String password) {
        Document userDoc = usersCollection.find(
            Filters.and(
                Filters.eq("username", username),
                Filters.eq("password", password)
            )
        ).first();
        
        return userDoc != null;
    }
    
    public boolean promoteToAdmin(String username, String adminUsername) {
        // Check if the requesting user is an admin
        User admin = getUser(adminUsername);
        if (admin == null || !admin.isAdmin()) {
            return false;
        }
        
        // Check if target user exists
        User userToPromote = getUser(username);
        if (userToPromote == null) {
            return false;
        }
        
        // Update the user to be an admin
        usersCollection.updateOne(
            Filters.eq("username", username),
            Updates.set("isAdmin", true)
        );
        
        return true;
    }
    
    public boolean deleteUser(String username, String adminUsername) {
        // Check if the requesting user is an admin
        User admin = getUser(adminUsername);
        if (admin == null || !admin.isAdmin()) {
            return false;
        }
        
        // Check if target user exists and is not the root admin
        User userToDelete = getUser(username);
        if (userToDelete == null || userToDelete.isRootAdmin()) {
            return false;
        }
        
        // Delete the user
        DeleteResult result = usersCollection.deleteOne(Filters.eq("username", username));
        return result.getDeletedCount() > 0;
    }
    
    public Map<String, User> getAllUsers() {
        Map<String, User> users = new HashMap<>();
        FindIterable<Document> userDocs = usersCollection.find();
        
        for (Document doc : userDocs) {
            String username = doc.getString("username");
            User user = new User(
                username,
                doc.getString("password"),
                doc.getBoolean("isAdmin", false),
                doc.getBoolean("isRootAdmin", false)
            );
            users.put(username, user);
        }
        
        return users;
    }
}