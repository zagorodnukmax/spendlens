package com.spendlens.auth;

import com.spendlens.auth.AuthDtos.*;
import com.spendlens.common.ConflictException;
import com.spendlens.user.User;
import com.spendlens.user.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder,
                       TokenService tokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Email is already registered");
        }
        var user = users.save(new User(
                request.email().toLowerCase(),
                passwordEncoder.encode(request.password()),
                request.displayName().strip()));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        var user = users.findByEmailIgnoreCase(request.email())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        return toResponse(user);
    }

    private TokenResponse toResponse(User user) {
        var token = tokenService.issue(user);
        return new TokenResponse(token.value(), "Bearer", token.expiresAt(), toUserResponse(user));
    }

    public static UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
