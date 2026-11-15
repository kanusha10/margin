package com.margin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public record Credentials(String email, String password, String inviteCode) {}

    private final UserService users;
    private final JwtService jwt;
    private final PasswordEncoder encoder;
    private final RateLimiter limiter;
    private final String signupCode;

    public AuthController(UserService users, JwtService jwt, PasswordEncoder encoder, RateLimiter limiter,
                          @Value("${app.signup-code}") String signupCode) {
        this.users = users;
        this.jwt = jwt;
        this.encoder = encoder;
        this.limiter = limiter;
        this.signupCode = signupCode;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Credentials c) {
        String email = clean(c.email());
        if (!signupCode.isBlank() && !signupCode.equals(c.inviteCode()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "That invite code isn't valid.");
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address.");
        if (c.password() == null || c.password().length() < 8)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a password with at least 8 characters.");
        if (users.findByEmail(email).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists. Sign in instead.");
        return session(users.create(email, encoder.encode(c.password())));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Credentials c) {
        String email = clean(c.email());
        if (!limiter.tryAcquire("login:" + email, 10, Duration.ofMinutes(15)))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many sign-in attempts. Wait 15 minutes and try again.");
        User u = users.findByEmail(email).orElse(null);
        if (u == null || c.password() == null || !encoder.matches(c.password(), u.passwordHash()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong email or password.");
        return session(u);
    }

    private Map<String, Object> session(User u) {
        return Map.of("token", jwt.issue(u.id(), u.email()), "email", u.email());
    }

    private static String clean(String email) { return email == null ? "" : email.trim().toLowerCase(); }
}
