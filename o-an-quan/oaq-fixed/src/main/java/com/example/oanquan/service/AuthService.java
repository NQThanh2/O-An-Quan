package com.example.oanquan.service;

import com.example.oanquan.dto.AuthResponse;
import com.example.oanquan.dto.LoginRequest;
import com.example.oanquan.dto.RegisterRequest;
import com.example.oanquan.entity.User;
import com.example.oanquan.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username đã tồn tại.");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email đã tồn tại.");
        }
        User user = new User(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.email()
        );
        userRepository.save(user);
        return new AuthResponse(user.getId(), user.getUsername(), demoToken(user.getUsername()));
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalArgumentException("Sai username hoặc password."));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new IllegalArgumentException("Sai username hoặc password.");
        }
        return new AuthResponse(user.getId(), user.getUsername(), demoToken(user.getUsername()));
    }

    private String demoToken(String username) {
        return "demo-" + username + "-" + UUID.randomUUID();
    }
}
