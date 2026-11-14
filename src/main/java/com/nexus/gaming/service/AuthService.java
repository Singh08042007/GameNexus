package com.nexus.gaming.service;

import com.nexus.gaming.dto.DtoMapper;
import com.nexus.gaming.exception.AuthenticationException;
import com.nexus.gaming.exception.NexusException;
import com.nexus.gaming.model.User;
import com.nexus.gaming.repository.DataRepository;
import com.nexus.gaming.repository.SupabaseClient;
import com.nexus.gaming.util.SecurityUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service handling user registration, authentication, session management,
 * and Supabase Auth synchronization.
 */
public class AuthService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[A-Za-z0-9_]{3,24}$");

    private final DataRepository repository;
    private final SupabaseClient supabaseClient;

    public AuthService(DataRepository repository, SupabaseClient supabaseClient) {
        this.repository = repository;
        this.supabaseClient = supabaseClient;
    }

    public synchronized Map<String, Object> register(String email, String username, String password) {
        String cleanEmail = (email == null) ? "" : email.trim().toLowerCase();
        String cleanUsername = (username == null) ? "" : username.trim();
        String rawPassword = (password == null) ? "" : password;

        if (cleanEmail.isEmpty() || !EMAIL_PATTERN.matcher(cleanEmail).matches()) {
            throw new NexusException("Please enter a valid email address.", 400, "INVALID_EMAIL");
        }
        if (cleanUsername.isEmpty() || !USERNAME_PATTERN.matcher(cleanUsername).matches()) {
            throw new NexusException(
                    "Username must be 3 to 24 characters using letters, numbers, or underscores.",
                    400,
                    "INVALID_USERNAME"
            );
        }
        if (rawPassword.length() < 6) {
            throw new NexusException("Password must be at least 6 characters long.", 400, "WEAK_PASSWORD");
        }

        if (repository.findByEmail(cleanEmail).isPresent()) {
            throw new NexusException("An account with this email already exists.", 409, "EMAIL_TAKEN");
        }
        if (repository.findByUsername(cleanUsername).isPresent()) {
            throw new NexusException("This username is already taken.", 409, "USERNAME_TAKEN");
        }

        // Register in Supabase Auth if reachable, otherwise generate standard UUID
        String supabaseUserId = supabaseClient.registerUserInSupabaseAuth(cleanEmail, rawPassword, cleanUsername);
        String userId = (supabaseUserId != null && !supabaseUserId.trim().isEmpty())
                ? supabaseUserId
                : UUID.randomUUID().toString();

        User user = new User(userId, cleanEmail, cleanUsername);
        user.setPasswordHash(SecurityUtil.hashPassword(rawPassword));
        repository.save(user);

        String sessionToken = SecurityUtil.generateSessionToken();
        repository.saveSessionToken(sessionToken, user.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", sessionToken);
        result.put("user", DtoMapper.toUserDto(user));
        return result;
    }

    public Map<String, Object> login(String identifier, String password) {
        String cleanId = (identifier == null) ? "" : identifier.trim();
        String rawPassword = (password == null) ? "" : password;

        if (cleanId.isEmpty() || rawPassword.isEmpty()) {
            throw new AuthenticationException("Email/username and password are required.", "MISSING_CREDENTIALS");
        }

        Optional<User> localUserOpt = cleanId.contains("@")
                ? repository.findByEmail(cleanId.toLowerCase())
                : repository.findByUsername(cleanId);

        if (localUserOpt.isPresent()) {
            User user = localUserOpt.get();
            if (!SecurityUtil.verifyPassword(rawPassword, user.getPasswordHash())) {
                throw new AuthenticationException("Invalid email/username or password.", "INVALID_CREDENTIALS");
            }
            String token = SecurityUtil.generateSessionToken();
            repository.saveSessionToken(token, user.getId());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("token", token);
            result.put("user", DtoMapper.toUserDto(user));
            return result;
        }

        // If user exists in Supabase Auth from another instance, verify via Supabase Auth API
        if (cleanId.contains("@")) {
            Map<String, Object> sbAuth = supabaseClient.signInWithSupabaseAuth(cleanId.toLowerCase(), rawPassword);
            if (sbAuth != null && sbAuth.get("id") != null) {
                String uid = String.valueOf(sbAuth.get("id"));
                String uname = sbAuth.get("username") != null
                        ? String.valueOf(sbAuth.get("username"))
                        : cleanId.substring(0, cleanId.indexOf('@'));
                User syncedUser = new User(uid, cleanId.toLowerCase(), uname);
                syncedUser.setPasswordHash(SecurityUtil.hashPassword(rawPassword));
                repository.save(syncedUser);

                String token = SecurityUtil.generateSessionToken();
                repository.saveSessionToken(token, syncedUser.getId());

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("token", token);
                result.put("user", DtoMapper.toUserDto(syncedUser));
                return result;
            }
        }

        throw new AuthenticationException("Invalid email/username or password.", "INVALID_CREDENTIALS");
    }

    public void logout(String token) {
        if (token != null && !token.trim().isEmpty()) {
            repository.removeSessionToken(token.trim());
        }
    }

    public User requireAuthenticatedUser(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new AuthenticationException("Authentication token is required. Please log in.", "TOKEN_MISSING");
        }
        String normalized = token.trim();
        if (normalized.toLowerCase().startsWith("bearer ")) {
            normalized = normalized.substring(7).trim();
        }
        return repository.findUserByToken(normalized)
                .orElseThrow(() -> new AuthenticationException(
                        "Invalid or expired authentication session. Please log in again.",
                        "TOKEN_INVALID"
                ));
    }
}
