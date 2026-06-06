package com.example.oanquan.controller;

import com.example.oanquan.dto.ApiError;
import com.example.oanquan.dto.GameStateDTO;
import com.example.oanquan.dto.MoveRequest;
import com.example.oanquan.model.CurrentTurn;
import com.example.oanquan.model.Direction;
import com.example.oanquan.model.GamePhase;
import com.example.oanquan.service.GameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameWebSocketControllerTest {
    @Mock
    private GameService gameService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private GameWebSocketController controller;

    @BeforeEach
    void setUp() {
        controller = new GameWebSocketController(gameService, messagingTemplate);
    }

    @Test
    void handleMovePublishesUpdatedGameState() {
        MoveRequest request = new MoveRequest(1L, 0, Direction.RIGHT, CurrentTurn.A, "alice");
        GameStateDTO state = state(1L);
        when(gameService.processMove(request)).thenReturn(state);

        controller.handleMove(request);

        verify(messagingTemplate).convertAndSend("/topic/game/1", state);
    }

    @Test
    void handleMovePublishesValidationErrorForQuanCell() {
        MoveRequest request = new MoveRequest(1L, 5, Direction.RIGHT, CurrentTurn.A, "alice");

        controller.handleMove(request);

        ArgumentCaptor<ApiError> captor = ArgumentCaptor.forClass(ApiError.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/1/errors"), captor.capture());
        assertThat(captor.getValue().message()).contains("Quan");
        verifyNoInteractions(gameService);
    }

    @Test
    void handleMovePublishesSystemErrorWhenServiceThrowsUnexpectedException() {
        MoveRequest request = new MoveRequest(1L, 0, Direction.RIGHT, CurrentTurn.A, "alice");
        when(gameService.processMove(request)).thenThrow(new RuntimeException("boom"));

        controller.handleMove(request);

        ArgumentCaptor<ApiError> captor = ArgumentCaptor.forClass(ApiError.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/1/errors"), captor.capture());
        assertThat(captor.getValue().message()).contains("boom");
    }

    private GameStateDTO state(Long id) {
        return new GameStateDTO(
                id,
                null,
                null,
                null,
                false,
                "MEDIUM",
                "Medium",
                List.of(),
                CurrentTurn.A,
                0,
                0,
                GamePhase.PLAYING,
                null,
                null,
                null,
                null,
                List.of()
        );
    }
}
