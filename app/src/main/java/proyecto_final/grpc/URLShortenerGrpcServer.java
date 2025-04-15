package proyecto_final.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import proyecto_final.model.ShortUrl;
import proyecto_final.service.JwtService;
import proyecto_final.service.UrlService;
import proyecto_final.service.UserService;
import com.auth0.jwt.interfaces.DecodedJWT;
import proyecto_final.proto.*;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class URLShortenerGrpcServer {
    private Server server;
    private final int port;
    private final UrlService urlService;
    private final UserService userService;
    private final JwtService jwtService;

    public URLShortenerGrpcServer(int port, UrlService urlService, UserService userService, JwtService jwtService) {
        this.port = port;
        this.urlService = urlService;
        this.userService = userService;
        this.jwtService = jwtService;
    }

    public void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new URLShortenerServiceImpl())
                .build()
                .start();
        
        System.out.println("gRPC Server started, listening on port " + port);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down gRPC server");
            URLShortenerGrpcServer.this.stop();
        }));
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    private class URLShortenerServiceImpl extends URLShortenerServiceGrpc.URLShortenerServiceImplBase {
        @Override
        public void login(LoginRequest request, StreamObserver<LoginResponse> responseObserver) {
            String username = request.getUsername();
            String password = request.getPassword();
            
            var user = userService.getUser(username);
            LoginResponse.Builder responseBuilder = LoginResponse.newBuilder();
            
            if (user == null) {
                responseBuilder.setSuccess(false)
                    .setMessage("Usuario no encontrado");
            } else if (!userService.validateCredentials(username, password)) {
                responseBuilder.setSuccess(false)
                    .setMessage("Credenciales inválidas");
            } else {
                // Crear token JWT
                String token = jwtService.generateToken(user);
                
                responseBuilder.setSuccess(true)
                    .setToken(token)
                    .setIsAdmin(user.isAdmin())
                    .setMessage("Login exitoso");
            }
            
            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        }

        @Override
        public void getUserUrls(UserRequest request, StreamObserver<UserUrlsResponse> responseObserver) {
            String token = request.getToken();
            
            try {
                DecodedJWT decodedJWT = jwtService.validateToken(token);
                String username = jwtService.getUsernameFromToken(decodedJWT);
                
                List<Map<String, Object>> urls = urlService.getUrlsByUser(username);
                UserUrlsResponse.Builder response = UserUrlsResponse.newBuilder();
                
                for (Map<String, Object> url : urls) {
                    ShortUrlInfo.Builder urlInfo = ShortUrlInfo.newBuilder()
                        .setShortCode((String) url.get("shortCode"))
                        .setOriginalUrl((String) url.get("originalUrl"))
                        .setAccessCount((int) url.getOrDefault("accessCount", 0));
                        
                    if (url.get("createdAt") != null) {
                        urlInfo.setCreatedAt(((Date) url.get("createdAt")).toString());
                    } else {
                        urlInfo.setCreatedAt("Desconocido");
                    }
                    
                    response.addUrls(urlInfo);
                }
                
                responseObserver.onNext(response.build());
                responseObserver.onCompleted();
                
            } catch (Exception e) {
                responseObserver.onError(new Throwable("Error de autenticación: " + e.getMessage()));
            }
        }

        @Override
        public void createShortUrl(CreateUrlRequest request, StreamObserver<ShortUrlResponse> responseObserver) {
            String token = request.getToken();
            String originalUrl = request.getOriginalUrl();
            
            try {
                DecodedJWT decodedJWT = jwtService.validateToken(token);
                String username = jwtService.getUsernameFromToken(decodedJWT);
                
                // Validar URL
                if (!originalUrl.startsWith("http://") && !originalUrl.startsWith("https://")) {
                    originalUrl = "http://" + originalUrl;
                }
                
                // Crear URL corta usando el servicio existente
                ShortUrl shortUrl = urlService.createShortUrl(originalUrl, username);
                
                // Crear URL completa para devolver
                String fullShortUrl = "http://localhost:7000/s/" + shortUrl.getShortCode();
                
                // Construir respuesta (sin imagen por ahora)
                ShortUrlResponse response = ShortUrlResponse.newBuilder()
                    .setShortCode(shortUrl.getShortCode())
                    .setOriginalUrl(shortUrl.getOriginalUrl())
                    .setFullShortUrl(fullShortUrl)
                    .setCreatedAt(shortUrl.getCreatedAt().toString())
                    .setPreviewImageBase64("") // Leave empty for now
                    .build();
                
                responseObserver.onNext(response);
                responseObserver.onCompleted();
                
            } catch (Exception e) {
                responseObserver.onError(new Throwable("Error creating URL: " + e.getMessage()));
            }
        }

        @Override
        public void getUrlAnalytics(UrlAnalyticsRequest request, StreamObserver<UrlAnalyticsResponse> responseObserver) {
            String token = request.getToken();
            String shortCode = request.getShortCode();
            
            try {
                DecodedJWT decodedJWT = jwtService.validateToken(token);
                String username = jwtService.getUsernameFromToken(decodedJWT);
                
                // Obtener analíticas usando el servicio existente
                Map<String, Object> analytics = urlService.getAnalytics(shortCode, username);
                
                if (analytics == null) {
                    responseObserver.onError(new Throwable("URL not found or not authorized"));
                    return;
                }
                
                // Construir respuesta
                UrlAnalyticsResponse.Builder response = UrlAnalyticsResponse.newBuilder()
                    .setShortCode(shortCode)
                    .setOriginalUrl((String) analytics.get("originalUrl"))
                    .setAccessCount((int) analytics.getOrDefault("accessCount", 0));
                
                // Si hay fecha de creación, incluirla
                if (analytics.get("createdAt") != null) {
                    response.setCreatedAt(((Date) analytics.get("createdAt")).toString());
                } else {
                    response.setCreatedAt("Unknown");
                }
                
                // Agregar estadísticas de navegadores
                Map<String, Integer> browsers = (Map<String, Integer>) analytics.get("browsers");
                if (browsers != null) {
                    for (Map.Entry<String, Integer> entry : browsers.entrySet()) {
                        response.putBrowsers(entry.getKey(), entry.getValue());
                    }
                }
                
                // Agregar estadísticas de sistemas operativos
                Map<String, Integer> os = (Map<String, Integer>) analytics.get("operatingSystems");
                if (os != null) {
                    for (Map.Entry<String, Integer> entry : os.entrySet()) {
                        response.putOperatingSystems(entry.getKey(), entry.getValue());
                    }
                }
                
                // Agregar accesos
                List<Map<String, Object>> accesses = (List<Map<String, Object>>) analytics.get("accesses");
                if (accesses != null) {
                    for (Map<String, Object> access : accesses) {
                        AccessInfo.Builder accessInfo = AccessInfo.newBuilder()
                            .setIp((String) access.getOrDefault("ip", ""))
                            .setBrowser((String) access.getOrDefault("browser", ""))
                            .setOs((String) access.getOrDefault("os", ""));
                            
                        if (access.get("timestamp") != null) {
                            accessInfo.setTimestamp(((Date) access.get("timestamp")).toString());
                        } else {
                            accessInfo.setTimestamp("Unknown");
                        }
                        
                        response.addAccesses(accessInfo.build());
                    }
                }
                
                responseObserver.onNext(response.build());
                responseObserver.onCompleted();
                
            } catch (Exception e) {
                responseObserver.onError(new Throwable("Error getting analytics: " + e.getMessage()));
            }
        }
    }
}
