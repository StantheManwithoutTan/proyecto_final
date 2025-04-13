package proyecto_final.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import proyecto_final.model.User;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class JwtService {
    // IMPORTANT: Keep this secret secure and load from config/env variables in production!
    private static final String SECRET_KEY = "your-very-secure-secret-key";
    private static final Algorithm ALGORITHM = Algorithm.HMAC256(SECRET_KEY);
    private static final long EXPIRATION_TIME = TimeUnit.HOURS.toMillis(24); // 24 hours validity
    private static final String ISSUER = "url-shortener-app";

    private static final JwtService instance = new JwtService();

    private JwtService() {}

    public static JwtService getInstance() {
        return instance;
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + EXPIRATION_TIME);

        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(user.getUsername())
                .withClaim("isAdmin", user.isAdmin()) // Include roles/claims as needed
                .withIssuedAt(now)
                .withExpiresAt(expiryDate)
                .sign(ALGORITHM);
    }

    public DecodedJWT validateToken(String token) throws JWTVerificationException {
        JWTVerifier verifier = JWT.require(ALGORITHM)
                .withIssuer(ISSUER)
                .build();
        return verifier.verify(token);
    }

    public String getUsernameFromToken(DecodedJWT decodedJWT) {
        return decodedJWT.getSubject();
    }

     public boolean isAdminFromToken(DecodedJWT decodedJWT) {
        return decodedJWT.getClaim("isAdmin").asBoolean();
    }
}