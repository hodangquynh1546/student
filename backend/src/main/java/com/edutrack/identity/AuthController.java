package com.edutrack.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) { }
    public record EmailRequest(@Email @NotBlank String email) { }
    public record TokenPasswordRequest(@NotBlank String token, @Size(min = 12, max = 72) String password) { }
    public record ChangePasswordRequest(@NotBlank String currentPassword, @Size(min = 12, max = 72) String newPassword) { }
    public record SessionView(String accessToken, int expiresIn, AuthService.UserView user) { }
    private final AuthService auth;
    private final Environment environment;
    private final int refreshDays;
    public AuthController(AuthService auth, Environment environment, @Value("${edutrack.refresh-days}") int refreshDays) {
        this.auth = auth; this.environment = environment; this.refreshDays = refreshDays;
    }

    @PostMapping("/login")
    public SessionView login(@Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        var issued = auth.login(request.email(), request.password(), http.getHeader("User-Agent"));
        setCookie(response, issued.refreshToken(), http.isSecure());
        return view(issued);
    }

    @PostMapping("/refresh")
    public SessionView refresh(@CookieValue(name = "edutrack_refresh", required = false) String refresh,
                               HttpServletRequest http, HttpServletResponse response) {
        try {
            var issued = auth.refresh(refresh, http.getHeader("User-Agent"));
            setCookie(response, issued.refreshToken(), http.isSecure());
            return view(issued);
        } catch (AuthService.InvalidRefreshException ex) {
            clearCookie(response, http.isSecure());
            throw new BadCredentialsException("Refresh session is invalid");
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = "edutrack_refresh", required = false) String refresh,
                       HttpServletRequest http, HttpServletResponse response) {
        auth.logout(refresh); clearCookie(response, http.isSecure());
    }

    @GetMapping("/me")
    public AuthService.UserView me(@AuthenticationPrincipal Jwt jwt) { return auth.me(UUID.fromString(jwt.getSubject())); }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgot(@Valid @RequestBody EmailRequest request) {
        auth.requestReset(request.email()); return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody TokenPasswordRequest request) { auth.resetPassword(request.token(), request.password()); }

    @PostMapping("/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activate(@Valid @RequestBody TokenPasswordRequest request) { auth.activate(request.token(), request.password()); }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        auth.changePassword(UUID.fromString(jwt.getSubject()), request.currentPassword(), request.newPassword());
    }

    private SessionView view(AuthService.IssuedSession issued) {
        return new SessionView(issued.accessToken(), issued.expiresIn(), issued.user());
    }
    private void setCookie(HttpServletResponse response, String raw, boolean secure) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(raw, Duration.ofDays(refreshDays), secure).toString());
    }
    private void clearCookie(HttpServletResponse response, boolean secure) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO, secure).toString());
    }
    private ResponseCookie cookie(String value, Duration age, boolean secure) {
        boolean requireSecure = secure || !environment.acceptsProfiles(Profiles.of("dev", "test"));
        return ResponseCookie.from("edutrack_refresh", value).httpOnly(true).secure(requireSecure)
            .sameSite("Strict").path("/api/v1/auth").maxAge(age).build();
    }
}
