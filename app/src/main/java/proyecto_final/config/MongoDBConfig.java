package proyecto_final.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerApi;
import com.mongodb.ServerApiVersion;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection; 
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;

public class MongoDBConfig {
    private static MongoClient mongoClient;
    private static MongoDatabase database;
    
    public static void initialize() {
        // Read environment variables
        String connectionString = System.getenv("URL_MONGO");
        if (connectionString == null) {
            connectionString = System.getenv("MONGODB_URL"); // Fallback
        }
        
        String dbName = System.getenv("DB_NOMBRE");
        if (dbName == null || dbName.isEmpty()) {
            dbName = "proyecto_finalDB"; // Default value
        }
        
        if (connectionString == null || connectionString.isEmpty()) {
            System.err.println("ERROR: URL_MONGO environment variable is not set");
            System.err.println("Please set URL_MONGO with your MongoDB Atlas connection string");
            System.err.println("Example: set URL_MONGO=mongodb+srv://<username>:<password>@<cluster>.mongodb.net/<dbname>?retryWrites=true&w=majority");
            System.exit(1);
        }
        
        try {
            ConnectionString connStr = new ConnectionString(connectionString);
            
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(connStr)
                    .serverApi(ServerApi.builder()
                            .version(ServerApiVersion.V1)
                            .build())
                    .build();
            
            mongoClient = MongoClients.create(settings);
            database = mongoClient.getDatabase(dbName);
            
            // Ensure indexes exist for username uniqueness
            MongoCollection<Document> usersCollection = database.getCollection("User");
            IndexOptions indexOptions = new IndexOptions().unique(true);
            usersCollection.createIndex(new Document("username", 1), indexOptions);
            
            System.out.println("MongoDB connection initialized successfully to database: " + dbName);
        } catch (Exception e) {
            System.err.println("Failed to connect to MongoDB: " + e.getMessage());
            System.exit(1);
        }
    }
    
    public static MongoDatabase getDatabase() {
        if (database == null) {
            initialize();
        }
        return database;
    }
    
    public static void close() {
        if (mongoClient != null) {
            mongoClient.close();
            System.out.println("MongoDB connection closed.");
        }
    }
}