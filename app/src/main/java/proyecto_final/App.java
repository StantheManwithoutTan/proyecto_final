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
import proyecto_final.service.JwtService; // Import JwtService
import com.auth0.jwt.exceptions.JWTVerificationException; // Import exceptions
import com.auth0.jwt.interfaces.DecodedJWT; // Import DecodedJWT
import proyecto_final.grpc.URLShortenerGrpcServer; // Import gRPC server

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
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.EnumMap;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import javax.imageio.ImageIO;

public class App {
    private static UserService userService;
    private static UrlService urlService;
    private static SessionService sessionService;
    private static JwtService jwtService; // Add JwtService instance

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
        
        jwtService = JwtService.getInstance(); // Initialize JwtService
        
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
        app.get("/api/admin/urls", handleGetAllUrls);
        app.delete("/api/admin/urls/{shortCode}", handleDeleteAnyUrl);
        
        // Añade esta nueva ruta junto con las otras rutas admin
        app.get("/api/admin/noauth/urls", ctx -> {
            String requestUsername = ctx.queryParam("username");
            String requestPassword = ctx.queryParam("password");
            
            // Verifica credenciales de administrador directamente
            if ("admin".equals(requestUsername) && "tu_contraseña_segura".equals(requestPassword)) {
                // Esta es una ruta admin que no requiere token JWT
                ctx.json(urlService.getAllUrls());
            } else {
                ctx.status(401).json(Map.of("error", "Acceso no autorizado"));
            }
        });
        
        // URL Shortener API endpoints
        app.post("/api/urls/shorten", handleShortenUrl);
        app.get("/api/urls/user", handleGetUserUrls);
        app.get("/api/urls/analytics/{shortCode}", handleGetAnalytics);
        app.get("/api/urls/chart/{shortCode}", handleGetAccessChart); // Nuevo endpoint
        app.get("/api/urls/qrcode/{shortCode}", handleGetQrCode); // Nuevo endpoint para QR
        app.delete("/api/urls/{shortCode}", handleDeleteUrl);
        app.get("/s/{shortCode}", handleRedirect);
        
        // Anonymous URL Shortener API endpoints
        app.post("/api/urls/anonymous/shorten", handleShortenUrlAnonymous);
        app.get("/api/urls/anonymous/session", handleGetSessionUrls);
        app.get("/api/urls/anonymous/analytics/{shortCode}", handleGetAnonymousAnalytics);
        
        // Sync operations endpoint
        app.post("/api/sync/urls", handleSyncOperations);
        
        // Iniciar servidor gRPC en el puerto 50051
        try {
            URLShortenerGrpcServer grpcServer = new URLShortenerGrpcServer(
                50051, 
                urlService, 
                userService,
                jwtService
            );
            grpcServer.start();
            System.out.println("gRPC server started, listening on port 50051");
        } catch (IOException e) {
            System.err.println("Error starting gRPC server: " + e.getMessage());
        }
        
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
            // Generate JWT instead of simple token
            String token = jwtService.generateToken(user);

            Map<String, Object> response = new HashMap<>();
            response.put("token", token); // Send JWT
            response.put("isAdmin", user.isAdmin()); // Keep isAdmin for initial client setup

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
        DecodedJWT decodedJWT = validateAuthHeader(ctx);
        if (decodedJWT == null) {
            ctx.status(401).json(Map.of("error", "Unauthorized"));
            return;
        }

        // Extract username AND check admin claim from token
        String adminUsername = jwtService.getUsernameFromToken(decodedJWT);
        boolean isAdmin = jwtService.isAdminFromToken(decodedJWT);

        if (!isAdmin) { // Check claim from token
            ctx.status(403).json(Map.of("error", "Forbidden: Admin access required"));
            return;
        }

        // Convert users to a safe format (no passwords)
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

    private static Handler handleGetAllUrls = ctx -> {
        String token = ctx.header("Authorization");
        if (token == null) {
            ctx.status(401).json(Map.of("error", "Unauthorized"));
            return;
        }
        
        String username = token.split("-")[0];
        
        // Verify admin role
        User user = userService.getUser(username);
        if (user == null || !user.isAdmin()) {
            ctx.status(403).json(Map.of("error", "Forbidden - Admin access required"));
            return;
        }
        
        List<Map<String, Object>> urls = urlService.getAllUrls();
        ctx.json(urls);
    };

    private static Handler handleDeleteAnyUrl = ctx -> {
        String token = ctx.header("Authorization");
        if (token == null) {
            ctx.status(401).json(Map.of("error", "Unauthorized"));
            return;
        }
        
        String username = token.split("-")[0];
        String shortCode = ctx.pathParam("shortCode");
        
        // Verify admin role
        User user = userService.getUser(username);
        if (user == null || !user.isAdmin()) {
            ctx.status(403).json(Map.of("error", "Forbidden - Admin access required"));
            return;
        }
        
        boolean deleted = urlService.deleteAnyUrl(shortCode);
        
        if (deleted) {
            ctx.json(Map.of("message", "URL deleted successfully"));
        } else {
            ctx.status(404).json(Map.of("error", "URL not found"));
        }
    };

    private static Handler handleShortenUrl = ctx -> {
        DecodedJWT decodedJWT = validateAuthHeader(ctx);
        if (decodedJWT == null) {
            ctx.status(401).json(Map.of("error", "Unauthorized - Invalid or missing token"));
            return;
        }
        String username = jwtService.getUsernameFromToken(decodedJWT);

        // Rate limit fix - only using the required parameters
        NaiveRateLimit.requestPerTimeUnit(ctx, 10, TimeUnit.MINUTES);

        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String originalUrl = body.get("originalUrl");

        if (originalUrl == null || originalUrl.isEmpty()) {
            ctx.status(400).json(Map.of("error", "URL is required"));
            return;
        }

        if (!Pattern.matches("^https?://.*", originalUrl)) {
            originalUrl = "http://" + originalUrl;
        }

        ShortUrl shortUrl = urlService.createShortUrl(originalUrl, username);

        ctx.json(Map.of(
            "shortCode", shortUrl.getShortCode(),
            "originalUrl", shortUrl.getOriginalUrl()
        ));
    };

    private static Handler handleGetUserUrls = ctx -> {
        DecodedJWT decodedJWT = validateAuthHeader(ctx);
        if (decodedJWT == null) {
            ctx.status(401).json(Map.of("error", "Unauthorized - Invalid or missing token"));
            return;
        }

        String username = jwtService.getUsernameFromToken(decodedJWT);
        List<Map<String, Object>> urls = urlService.getUrlsByUser(username);

        ctx.json(urls);
    };

    private static Handler handleGetAnalytics = ctx -> {
        // Use the helper function to validate JWT
        DecodedJWT decodedJWT = validateAuthHeader(ctx);
        if (decodedJWT == null) {
            ctx.status(401).json(Map.of("error", "Unauthorized - Invalid or missing token"));
            return;
        }
        // Get username correctly from JWT
        String username = jwtService.getUsernameFromToken(decodedJWT);
        String shortCode = ctx.pathParam("shortCode");

        // Verify ownership (or admin status if needed) based on the validated username
        Map<String, Object> analytics = urlService.getAnalytics(shortCode, username);

        if (analytics == null) {
            ctx.status(404).json(Map.of("error", "URL not found or not owned by you"));
            return;
        }

        ctx.json(analytics);
    };

    private static Handler handleGetAccessChart = ctx -> {
        // Use the helper function to validate JWT
        DecodedJWT decodedJWT = validateAuthHeader(ctx);
        if (decodedJWT == null) {
            ctx.status(401).json(Map.of("error", "Unauthorized - Invalid or missing token"));
            return;
        }
        // Get username correctly from JWT
        String username = jwtService.getUsernameFromToken(decodedJWT);
        String shortCode = ctx.pathParam("shortCode");

        // Obtener datos de acceso para este shortCode
        // Ensure getAnalytics checks ownership correctly based on the validated username
        Map<String, Object> analytics = urlService.getAnalytics(shortCode, username);

        if (analytics == null) {
            // Return 404 or 403 depending on whether it's not found or not authorized
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
        
        // Check if there's data before creating the chart
        if (dates.isEmpty() || counts.isEmpty()) {
            // Create a simple "No data" image instead of trying to generate an empty chart
            BufferedImage noDataImage = new BufferedImage(600, 300, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = noDataImage.createGraphics();
            // Set white background
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, 600, 300);
            // Draw "No data available" message
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 20));
            g2d.drawString("No data available for this URL", 150, 150);
            g2d.dispose();
            
            // Convert to bytes and return
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(noDataImage, "PNG", baos);
            ctx.contentType("image/png").result(baos.toByteArray());
            return;
        }
        
        // Simplificación de la generación de gráficos
        CategoryChart chart = new CategoryChartBuilder()
                .width(600)          // Reducir tamaño
                .height(300)         // Reducir tamaño
                .title("Accesos por Hora")
                .xAxisTitle("Fecha y Hora")
                .yAxisTitle("Accesos")
                .build();

        // Personalizar el gráfico con configuración mínima
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setXAxisLabelRotation(45);
        chart.getStyler().setDatePattern("yyyy-MM-dd HH:mm");
        chart.getStyler().setChartBackgroundColor(java.awt.Color.WHITE);
        chart.getStyler().setPlotBackgroundColor(java.awt.Color.WHITE);

        // Limitar la cantidad de datos si hay demasiados
        if (dates.size() > 20) {
            // Tomar solo los últimos 20 puntos de datos
            dates = dates.subList(dates.size() - 20, dates.size());
            counts = counts.subList(counts.size() - 20, counts.size());
        }

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

        // User can only delete their own URL via this endpoint.
        // Admins must use the /api/admin/urls/{shortCode} endpoint to delete others' URLs.
        boolean deleted = urlService.deleteUrl(shortCode, username);

        if (deleted) {
            ctx.json(Map.of("message", "URL deleted successfully"));
        } else {
            // Could be not found OR not owned by this user
            ctx.status(404).json(Map.of("error", "URL not found or you are not authorized to delete it"));
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

    private static Handler handleGetQrCode = ctx -> {
        String shortCode = ctx.pathParam("shortCode");
        // Ideally, verify ownership or admin status if needed, similar to handleGetAccessChart
        // String token = ctx.header("Authorization");
        // if (token == null) { ... return 401 ... }
        // String username = token.split("-")[0];
        // ShortUrl url = urlService.getUrlByShortCode(shortCode); // Fetch URL to check ownership if required
        // if (url == null || (!url.getUsername().equals(username) && !userService.getUser(username).isAdmin())) {
        //     ctx.status(403).json(Map.of("error", "Forbidden"));
        //     return;
        // }

        // Construct the URL the QR code should point to (e.g., the chart endpoint)
        // Adjust the base URL if your server runs elsewhere or uses HTTPS
        String chartUrl = "http://localhost:7000/api/urls/chart/" + shortCode;

        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1); // Adjust margin as needed
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L); // Error correction level

            BitMatrix bitMatrix = qrCodeWriter.encode(chartUrl, BarcodeFormat.QR_CODE, 200, 200, hints); // Adjust size

            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);

            ctx.contentType("image/png").result(pngOutputStream.toByteArray());

        } catch (Exception e) {
            System.err.println("Error generating QR code for " + shortCode + ": " + e.getMessage());
            ctx.status(500).json(Map.of("error", "Failed to generate QR code"));
        }
    };

    private static Handler handleSyncOperations = ctx -> {
        String token = ctx.header("Authorization");
        if (token == null) {
            ctx.status(401).json(Map.of("error", "Unauthorized"));
            return;
        }
        
        String username = token.split("-")[0];
        List<Map<String, Object>> operations = ctx.bodyAsClass(List.class);
        
        boolean success = urlService.syncBatchOperations(operations, username);
        
        if (success) {
            ctx.json(Map.of("success", true, "message", "Operaciones sincronizadas correctamente"));
        } else {
            ctx.status(500).json(Map.of("success", false, "error", "Error sincronizando operaciones"));
        }
    };

    // Helper function to extract and validate JWT
    private static DecodedJWT validateAuthHeader(Context ctx) {
        String authHeader = ctx.header("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null; // No or invalid header format
        }
        String token = authHeader.substring(7); // Remove "Bearer " prefix
        try {
            return jwtService.validateToken(token);
        } catch (JWTVerificationException e) {
            System.err.println("JWT Validation failed: " + e.getMessage());
            return null; // Token invalid or expired
        }
    }
}
