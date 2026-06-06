package com.example.oanquan.controller;

import com.example.oanquan.dto.GameStateDTO;
import com.example.oanquan.dto.MoveHistoryDTO;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameControllerTest {
    @Mock
    private GameService gameService;

    private GameController controller;

    @BeforeEach
    void setUp() {
        controller = new GameController(gameService);
    }

    @Test
    void createLocalGameDelegatesToService() {
        GameStateDTO state = state(1L);
        when(gameService.createLocalGame()).thenReturn(state);

        assertThat(controller.createLocalGame()).isSameAs(state);
    }

    @Test
    void createAiGamePassesDifficultyAndFirstTurn() {
        GameStateDTO state = state(2L);
        when(gameService.createAiGame("HARD", "AI")).thenReturn(state);

        assertThat(controller.createAiGame("HARD", "AI")).isSameAs(state);
        verify(gameService).createAiGame("HARD", "AI");
    }

    @Test
    void getGameDelegatesToService() {
        GameStateDTO state = state(1L);
        when(gameService.getGameState(1L)).thenReturn(state);

        assertThat(controller.getGame(1L)).isSameAs(state);
    }

    @Test
    void moveOverridesBodyGameIdWithPathGameId() {
        GameStateDTO state = state(7L);
        when(gameService.processMove(any(MoveRequest.class))).thenReturn(state);

        GameStateDTO result = controller.move(
                7L,
                new MoveRequest(999L, 0, Direction.RIGHT, CurrentTurn.A, "alice"));

        assertThat(result).isSameAs(state);
        ArgumentCaptor<MoveRequest> captor = ArgumentCaptor.forClass(MoveRequest.class);
        verify(gameService).processMove(captor.capture());
        assertThat(captor.getValue().gameId()).isEqualTo(7L);
        assertThat(captor.getValue().cellIndex()).isZero();
        assertThat(captor.getValue().direction()).isEqualTo(Direction.RIGHT);
        assertThat(captor.getValue().playerSide()).isEqualTo(CurrentTurn.A);
        assertThat(captor.getValue().username()).isEqualTo("alice");
    }

    @Test
    void moveRejectsQuanCellsBeforeCallingService() {
        assertThatThrownBy(() -> controller.move(
                7L,
                new MoveRequest(7L, 5, Direction.RIGHT, CurrentTurn.A, "alice")))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(gameService);
    }

    @Test
    void aiMoveDelegatesToService() {
        GameStateDTO state = state(7L);
        when(gameService.processAiMove(7L)).thenReturn(state);

        assertThat(controller.aiMove(7L)).isSameAs(state);
    }

    @Test
    void movesDelegatesToService() {
        MoveHistoryDTO move = new MoveHistoryDTO(1, 0, Direction.RIGHT, 6, 0, 6, null);
        when(gameService.getMoveHistory(7L)).thenReturn(List.of(move));

        assertThat(controller.moves(7L)).containsExactly(move);
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
