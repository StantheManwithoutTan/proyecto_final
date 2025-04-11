/*
 * This source file was generated by the Gradle 'init' task
 */
package proyecto_final;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.staticfiles.Location;
import io.javalin.http.util.NaiveRateLimit;
import proyecto_final.config.MongoDBConfig;
import proyecto_final.model.User;
import proyecto_final.service.UserService;
import proyecto_final.service.UrlService;
import proyecto_final.service.SessionService;
import proyecto_final.model.ShortUrl;
import proyecto_final.model.Session;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.SimpleTimeZone;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.BitmapEncoder;
import java.awt.image.BufferedImage;
import org.knowm.xchart.BitmapEncoder.BitmapFormat;
import org.knowm.xchart.style.Styler;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class App {
    private static UserService userService;
    private static UrlService urlService;
    private static SessionService sessionService;

    public String getGreeting() {
        return "Hello World!";
    }

    public static void main(String[] args) {
        // Initialize MongoDB connection first
        MongoDBConfig.initialize();
        
        // Then get user service (which will create admin if needed)
        userService = UserService.getInstance();
        
        // Initialize URL service
        urlService = UrlService.getInstance();
        
        // Initialize session service
        sessionService = SessionService.getInstance();
        
        // Create and configure Javalin app
        Javalin app = Javalin.create(config -> {
            // Enable static files from the resources/public directory
            config.staticFiles.add("/public", Location.CLASSPATH);
        }).start(7000); // Start on port 7000

        // Define routes
        app.get("/", ctx -> ctx.redirect("/index.html"));
        
        // Auth API endpoints
        app.post("/api/auth/login", handleLogin);
        app.post("/api/auth/register", handleRegister);
        
        // Admin API endpoints
        app.post("/api/admin/promote", handlePromote);
        app.get("/api/admin/users", handleGetUsers);
        app.delete("/api/admin/users/username", handleDeleteUser);
        
        // URL Shortener API endpoints
        app.post("/api/urls/shorten", handleShortenUrl);
        app.get("/api/urls/user", handleGetUserUrls);
        app.get("/api/urls/analytics/{shortCode}", handleGetAnalytics);
        app.get("/api/urls/chart/{shortCode}", handleGetAccessChart); // Nuevo endpoint
        app.delete("/api/urls/{shortCode}", handleDeleteUrl);
        app.get("/s/{shortCode}", handleRedirect);
        
        // Anonymous URL Shortener API endpoints
        app.post("/api/urls/anonymous/shorten", handleShortenUrlAnonymous);
        app.get("/api/urls/anonymous/session", handleGetSessionUrls);
        app.get("/api/urls/anonymous/analytics/{shortCode}", handleGetAnonymousAnalytics);
        
        // Add shutdown hook to close MongoDB connection
        Runtime.getRuntime().addShutdownHook(new Thread(MongoDBConfig::close));
        
        // Log a message when the server starts
        System.out.println("Server started on http://localhost:7000");
    }

    private static Handler handleLogin = ctx -> {
        Map<String, String> credentials = ctx.bodyAsClass(Map.class);
        String username = credentials.get("username");
        String password = credentials.get("password");
        
        System.out.println("Login attempt: " + username);
        
        // Check if the user exists in the database
        User user = userService.getUser(username);
        if (user == null) {
            System.out.println("User not found: " + username);
            ctx.status(401).json(Map.of("error", "Invalid credentials"));
            return;
        }
        
        // Check if password matches
        if (userService.validateCredentials(username, password)) {
            Map<String, Object> response = new HashMap<>();
            String token = username + "-" + System.currentTimeMillis();
            
            response.put("token", token);
            response.put("isAdmin", user.isAdmin());
            
            System.out.println("User " + username + " logged in, isAdmin: " + user.isAdmin());
            ctx.json(response);
        } else {
            System.out.println("Invalid password for user: " + username);
            ctx.status(401).json(Map.of("error", "Invalid credentials"));
        }
    };
    
    private static Handler handleRegister = ctx -> {
        Map<String, String> userInfo = ctx.bodyAsClass(Map.class);
        String username = userInfo.get("username");
        String password = userInfo.get("password");
        
        if (username == null || password == null || username.trim().isEmpty() || password.trim().isEmpty()) {
            ctx.status(400).json(Map.of("error", "Username and password are required"));
            return;
        }
        
        if (userService.registerUser(username, password)) {
            ctx.status(201).json(Map.of("message", "User registered successfully"));
        } else {
            ctx.status(409).json(Map.of("error", "Username already exists"));
        }
    };
    
    private static Handler handlePromote = ctx -> {
        Map<String, String> promotionInfo = ctx.bodyAsClass(Map.class);
        String adminUsername = promotionInfo.get("adminUsername");
        String userToPromote = promotionInfo.get("userToPromote");
        
        // Verificar token del administrador
        String token = ctx.header("Authorization");
        if (token == null || !token.startsWith(adminUsername)) {
            ctx.status(401).json(Map.of("error", "Unauthorized"));
            return;
        }
        
        if (userService.promoteToAdmin(userToPromote, adminUsername)) {
            ctx.json(Map.of("message", "User promoted to admin successfully"));
        } else {
            ctx.status(400).json(Map.of("error", "Failed to promote user"));
        }
    };
    
    private static Handler handleGetUsers = ctx -> {
        // Verificar que sea un administrador
        String token = ctx.header("Authorization");
        if (token == null) {
            ctx.status(401).json(Map.of("error", "Unauthorized"));
            return;
        }
        
        // Extraer username del token
        String adminUsername = token.split("-")[0];
        User admin = userService.getUser(adminUsername);
        
        if (admin == null || !admin.isAdmin()) {
            ctx.status(403).json(Map.of("error", "Forbidden: Admin access required"));
            return;
        }
        
        // Convertir usuarios a un formato seguro para enviar (sin passwords)
        Map<String, Map<String, Object>> usersInfo = new HashMap<>();
        userService.getAllUsers().forEach((username, user) -> {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("isAdmin", user.isAdmin());
            userInfo.put("isRootAdmin", user.isRootAdmin());
            usersInfo.put(username, userInfo);
        });
        
        ctx.json(usersInfo);
    };
    
    private static Handler handleDeleteUser = ctx -> {
        String userToDelete = ctx.pathParam("username");
        String token = ctx.header("Authorization");
        
        if (token == null) {
            ctx.status(401).json(Map.of("error", "Unauthorized"));
            return;
        }
        
        // Extraer username del admin del token
        String adminUsername = token.split("-")[0];
        
        if (userService.deleteUser(userToDelete, adminUsername)) {
            ctx.json(Map.of("message", "User deleted successfully"));
        } else {
            ctx.status(400).json(Map.of("error", "Failed to delete user"));
        }
    };

    private static Handler handleShortenUrl = ctx -> {
        // Verify authentication
        String token = ctx.header("Authorization");
        if (token == null) {
            ctx.status(401).json(Map.of("error", "Unauthorized"));
            return;
        }
        
        String username = token.split("-")[0];
        
        // Rate limit fix - only using the required parameters
        NaiveRateLimit.requestPerTimeUnit(ctx, 10, TimeUnit.MINUTES);
        
        // Obtener y validar URL
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String originalUrl = body.get("originalUrl");
        
        if (originalUrl == null || originalUrl.isEmpty()) {
            ctx.status(400).json(Map.of("error", "URL is required"));
            return;
        }
        
        // Asegúrate de que la URL sea válida (esquema http o https)
        if (!Pattern.matches("^https?://.*", originalUrl)) {
            originalUrl = "http://" + originalUrl;
        }
        
        // Crear URL acortada
        ShortUrl shortUrl = urlService.createShortUrl(originalUrl, username);
        
        ctx.json(Map.of(
            "shortCode", shortUrl.getShortCode(),
            "originalUrl", shortUrl.getOriginalUrl()
        ));
    };

    private static Handler handleGetUserUrls = ctx -> {
        String token = ctx.header("Authorization");
        if (token == null) {
            ctx.status(401).json(Map.of("error", "Unauthorized"));
            return;
        }
        
        String username = token.split("-")[0];
        List<Map<String, Object>> urls = urlService.getUrlsByUser(username);
        
        ctx.json(urls);
    };

    private static Handler handleGetAnalytics = ctx -> {
        String token = ctx.header("Authorization");
        if (token == null) {
            ctx.status(401).json(Map.of("error", "Unauthorized"));
            return;
        }
        
        String username = token.split("-")[0];
        String shortCode = ctx.pathParam("shortCode");
        
        Map<String, Object> analytics = urlService.getAnalytics(shortCode, username);
        
        if (analytics == null) {
            ctx.status(404).json(Map.of("error", "URL not found or not owned by you"));
            return;
        }
        
        ctx.json(analytics);
    };

    private static Handler handleGetAccessChart = ctx -> {
        String token = ctx.header("Authorization");
        if (token == null) {
            ctx.status(401).json(Map.of("error", "Unauthorized"));
            return;
        }
        
        String username = token.split("-")[0];
        String shortCode = ctx.pathParam("shortCode");
        
        // Obtener datos de acceso para este shortCode
        Map<String, Object> analytics = urlService.getAnalytics(shortCode, username);
        
        if (analytics == null) {
            ctx.status(404).json(Map.of("error", "URL not found or not authorized"));
            return;
        }
        
        // Crear la gráfica usando XChart
        List<Map<String, Object>> accesses = (List<Map<String, Object>>) analytics.get("accesses");
        
        // Preparar los datos para la gráfica
        List<Date> dates = new ArrayList<>();
        List<Number> counts = new ArrayList<>();
        
        // Agrupar accesos por hora
        Map<String, Integer> accessesByHour = new HashMap<>();
        
        for (Map<String, Object> access : accesses) {
            Date timestamp = (Date) access.get("timestamp");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:00");
            String hourKey = sdf.format(timestamp);
            
            accessesByHour.put(hourKey, accessesByHour.getOrDefault(hourKey, 0) + 1);
        }
        
        // Ordenar los datos por fecha
        List<String> sortedHours = new ArrayList<>(accessesByHour.keySet());
        Collections.sort(sortedHours);
        
        for (String hour : sortedHours) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:00");
                dates.add(sdf.parse(hour));
                counts.add(accessesByHour.get(hour));
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        
        // Crear el gráfico de categoría
        CategoryChart chart = new CategoryChartBuilder()
                .width(800)
                .height(400)
                .title("Accesos por Hora")
                .xAxisTitle("Fecha y Hora")
                .yAxisTitle("Número de Accesos")
                .build();
        
        // Personalizar el gráfico
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setXAxisLabelRotation(45);
        chart.getStyler().setDatePattern("yyyy-MM-dd HH:mm");
        
        // Añadir la serie de datos
        chart.addSeries("Accesos", dates, counts);
        
        // Convertir el gráfico a un array de bytes
        //BitmapEncoder.setBufferedImageType(BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            BitmapEncoder.saveBitmap(chart, baos, BitmapEncoder.BitmapFormat.PNG);
        } catch (IOException e) {
            ctx.status(500).json(Map.of("error", "Failed to generate chart"));
            return;
        }
        
        // Devolver la imagen como respuesta
        ctx.contentType("image/png").result(baos.toByteArray());
    };

    private static Handler handleDeleteUrl = ctx -> {
        String token = ctx.header("Authorization");
        if (token == null) {
            ctx.status(401).json(Map.of("error", "Unauthorized"));
            return;
        }
        
        String username = token.split("-")[0];
        String shortCode = ctx.pathParam("shortCode");
        
        // Verificar si el usuario es admin
        User user = userService.getUser(username);
        boolean isAdmin = (user != null && user.isAdmin());
        
        // Si es admin, puede eliminar cualquier URL
        boolean deleted = isAdmin 
            ? urlService.deleteAnyUrl(shortCode) 
            : urlService.deleteUrl(shortCode, username);
        
        if (deleted) {
            ctx.json(Map.of("message", "URL deleted successfully"));
        } else {
            ctx.status(404).json(Map.of("error", "URL not found or not authorized to delete"));
        }
    };

    private static Handler handleRedirect = ctx -> {
        String shortCode = ctx.pathParam("shortCode");
        ShortUrl url = urlService.getUrlByShortCode(shortCode);
        
        if (url == null) {
            ctx.status(404).redirect("/404.html");
            return;
        }
        
        // Extract information for analytics
        String userAgent = ctx.header("User-Agent");
        String ip = ctx.ip();
        String referer = ctx.header("Referer");
        
        // Parse user agent to determine browser and OS (simplified here)
        String browser = "Unknown";
        String os = "Unknown";
        
        if (userAgent != null) {
            if (userAgent.contains("Firefox")) browser = "Firefox";
            else if (userAgent.contains("Chrome")) browser = "Chrome";
            else if (userAgent.contains("Safari")) browser = "Safari";
            else if (userAgent.contains("Edge")) browser = "Edge";
            else if (userAgent.contains("MSIE") || userAgent.contains("Trident")) browser = "Internet Explorer";
            
            if (userAgent.contains("Windows")) os = "Windows";
            else if (userAgent.contains("Mac OS")) os = "MacOS";
            else if (userAgent.contains("Linux")) os = "Linux";
            else if (userAgent.contains("Android")) os = "Android";
            else if (userAgent.contains("iPhone") || userAgent.contains("iPad")) os = "iOS";
        }
        
        // Try to extract domain from referer
        String domain = "Direct";
        if (referer != null && !referer.isEmpty()) {
            try {
                java.net.URI uri = new java.net.URI(referer);
                domain = uri.getHost();
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
        
        // Create final copies of all variables for use in the lambda
        final String finalShortCode = shortCode;
        final String finalBrowser = browser;
        final String finalIp = ip;
        final String finalDomain = domain;
        final String finalOs = os;
        
        // Record access asynchronously using the final copies
        new Thread(() -> {
            urlService.recordAccess(finalShortCode, finalBrowser, finalIp, finalDomain, finalOs);
        }).start();
        
        // Redirect to the original URL
        ctx.redirect(url.getOriginalUrl());
    };

    private static Handler handleShortenUrlAnonymous = ctx -> {
        // Verificar si hay un sessionId o crear uno nuevo
        String sessionId = ctx.cookie("sessionId");
        if (sessionId == null || !sessionService.isSessionValid(sessionId)) {
            Session newSession = sessionService.createSession();
            sessionId = newSession.getSessionId();
            ctx.cookie("sessionId", sessionId, 86400); // 24 horas
        }
        
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String originalUrl = body.get("originalUrl");
        
        if (originalUrl == null || originalUrl.isEmpty()) {
            ctx.status(400).json(Map.of("error", "URL is required"));
            return;
        }
        
        // Validar URL
        if (!Pattern.matches("^https?://.*", originalUrl)) {
            originalUrl = "http://" + originalUrl;
        }
        
        // Crear URL acortada para usuario anónimo
        ShortUrl shortUrl = urlService.createShortUrlAnonymous(originalUrl, sessionId);
        
        ctx.json(Map.of(
            "shortCode", shortUrl.getShortCode(),
            "originalUrl", shortUrl.getOriginalUrl()
        ));
    };

    private static Handler handleGetSessionUrls = ctx -> {
        String sessionId = ctx.cookie("sessionId");
        if (sessionId == null || !sessionService.isSessionValid(sessionId)) {
            ctx.status(401).json(Map.of("error", "No valid session"));
            return;
        }
        
        List<Map<String, Object>> urls = urlService.getUrlsBySession(sessionId);
        
        ctx.json(urls);
    };

    private static Handler handleGetAnonymousAnalytics = ctx -> {
        String sessionId = ctx.cookie("sessionId");
        if (sessionId == null || !sessionService.isSessionValid(sessionId)) {
            ctx.status(401).json(Map.of("error", "No valid session"));
            return;
        }
        
        String shortCode = ctx.pathParam("shortCode");
        
        // Verificar que la URL pertenezca a esa sesión
        if (!urlService.isUrlOwnedBySession(shortCode, sessionId)) {
            ctx.status(403).json(Map.of("error", "URL not owned by this session"));
            return;
        }
        
        Map<String, Object> analytics = urlService.getAnalyticsForSession(shortCode, sessionId);
        
        if (analytics == null) {
            ctx.status(404).json(Map.of("error", "URL not found"));
            return;
        }
        
        ctx.json(analytics);
    };
}
