package com.example.oanquan.controller;

import com.example.oanquan.dto.AuthResponse;
import com.example.oanquan.dto.LoginRequest;
import com.example.oanquan.dto.RegisterRequest;
import com.example.oanquan.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {
    @Mock
    private AuthService authService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService);
    }

    @Test
    void registerDelegatesToAuthService() {
        RegisterRequest request = new RegisterRequest("alice", "secret123", "alice@example.com");
        AuthResponse response = new AuthResponse(1L, "alice", "token");
        when(authService.register(request)).thenReturn(response);

        assertThat(controller.register(request)).isSameAs(response);
        verify(authService).register(request);
    }

    @Test
    void loginDelegatesToAuthService() {
        LoginRequest request = new LoginRequest("alice", "secret123");
        AuthResponse response = new AuthResponse(1L, "alice", "token");
        when(authService.login(request)).thenReturn(response);

        assertThat(controller.login(request)).isSameAs(response);
        verify(authService).login(request);
    }
}
