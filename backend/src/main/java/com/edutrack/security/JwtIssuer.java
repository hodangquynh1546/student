package com.edutrack.security;

import com.edutrack.identity.UserAccount;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class JwtIssuer {
    private final JwtEncoder encoder;
    private final String issuer;
    private final int accessMinutes;
    public JwtIssuer(JwtEncoder encoder, @Value("${edutrack.jwt.issuer}") String issuer,
                     @Value("${edutrack.jwt.access-minutes}") int accessMinutes) {
        this.encoder = encoder; this.issuer = issuer; this.accessMinutes = accessMinutes;
        if (accessMinutes < 1 || accessMinutes > 60) throw new IllegalArgumentException("JWT access lifetime must be 1-60 minutes");
    }
    public String issue(UserAccount user) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(user.getId().toString())
            .issuedAt(now).expiresAt(now.plusSeconds(accessMinutes * 60L))
            .claim("role", user.getRole().name()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
    public int accessSeconds() { return accessMinutes * 60; }
}
