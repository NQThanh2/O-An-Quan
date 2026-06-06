package com.example.oanquan.service;

import com.example.oanquan.dto.AuthResponse;
import com.example.oanquan.dto.LoginRequest;
import com.example.oanquan.dto.RegisterRequest;
import com.example.oanquan.entity.User;
import com.example.oanquan.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder);
    }

    @Test
    void registerEncodesPasswordSavesUserAndReturnsDemoToken() {
        RegisterRequest request = new RegisterRequest("alice", "secret123", "alice@example.com");
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-secret");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(42L);
            return user;
        });

        AuthResponse response = authService.register(request);

        assertThat(response.userId()).isEqualTo(42L);
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.token()).startsWith("demo-alice-");
        verify(userRepository).save(argThat(user ->
                user.getUsername().equals("alice")
                        && user.getPassword().equals("encoded-secret")
                        && user.getEmail().equals("alice@example.com")));
    }

    @Test
    void registerRejectsDuplicateUsernameBeforeCheckingEmail() {
        RegisterRequest request = new RegisterRequest("alice", "secret123", "alice@example.com");
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequest request = new RegisterRequest("alice", "secret123", "alice@example.com");
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginReturnsDemoTokenWhenPasswordMatches() {
        User user = new User("alice", "encoded-secret", "alice@example.com");
        user.setId(42L);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "encoded-secret")).thenReturn(true);

        AuthResponse response = authService.login(new LoginRequest("alice", "secret123"));

        assertThat(response.userId()).isEqualTo(42L);
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.token()).startsWith("demo-alice-");
    }

    @Test
    void loginRejectsUnknownUsername() {
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("missing", "secret123")))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = new User("alice", "encoded-secret", "alice@example.com");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad-password", "encoded-secret")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "bad-password")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
