package com.example.oanquan.service;

import com.example.oanquan.dto.GameStateDTO;
import com.example.oanquan.dto.MoveHistoryDTO;
import com.example.oanquan.dto.MoveRequest;
import com.example.oanquan.engine.BoardState;
import com.example.oanquan.engine.OAnQuanEngine;
import com.example.oanquan.entity.Game;
import com.example.oanquan.entity.Move;
import com.example.oanquan.entity.User;
import com.example.oanquan.model.AiDifficulty;
import com.example.oanquan.model.CurrentTurn;
import com.example.oanquan.model.Direction;
import com.example.oanquan.model.GamePhase;
import com.example.oanquan.repository.GameRepository;
import com.example.oanquan.repository.MoveRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {
    @Mock
    private GameRepository gameRepository;

    @Mock
    private MoveRepository moveRepository;

    private ObjectMapper objectMapper;
    private GameService gameService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        gameService = new GameService(gameRepository, moveRepository, new OAnQuanEngine(), objectMapper);
    }

    @Test
    void createLocalGameInitializesAndPersistsPlayingGame() {
        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> {
            Game game = invocation.getArgument(0);
            game.setId(100L);
            return game;
        });

        GameStateDTO state = gameService.createLocalGame();

        assertThat(state.gameId()).isEqualTo(100L);
        assertThat(state.currentTurn()).isEqualTo(CurrentTurn.A);
        assertThat(state.phase()).isEqualTo(GamePhase.PLAYING);
        assertThat(state.aiGame()).isFalse();
        assertThat(state.board()).hasSize(BoardState.BOARD_SIZE);
        assertThat(state.board().get(0).dan()).isEqualTo(5);

        ArgumentCaptor<Game> captor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(captor.capture());
        Game saved = captor.getValue();
        assertThat(saved.getCurrentTurn()).isEqualTo(CurrentTurn.A);
        assertThat(saved.getPhase()).isEqualTo(GamePhase.PLAYING);
        assertThat(saved.isAiGame()).isFalse();
        assertThat(saved.getBoardStateJson()).isNotBlank();
    }

    @Test
    void getGameStateRejectsMissingGame() {
        when(gameRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameService.getGameState(404L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getMoveHistoryMapsMovesInRepositoryOrder() {
        Game game = playableGame(1L, BoardState.initial());
        Move move = new Move();
        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 6, 10, 0);
        move.setMoveOrder(1);
        move.setCellIndex(0);
        move.setDirection(Direction.RIGHT);
        move.setCapturedDan(6);
        move.setCapturedQuan(0);
        move.setCapturedPoints(6);
        move.setCreatedAt(createdAt);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(moveRepository.findByGameOrderByMoveOrderAsc(game)).thenReturn(List.of(move));

        List<MoveHistoryDTO> history = gameService.getMoveHistory(1L);

        assertThat(history).containsExactly(
                new MoveHistoryDTO(1, 0, Direction.RIGHT, 6, 0, 6, createdAt)
        );
    }

    @Test
    void processMoveUpdatesScoresTurnBoardAndSavesMoveHistory() {
        Game game = playableGame(1L, BoardState.initial());
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(moveRepository.countByGame(game)).thenReturn(0L);
        when(moveRepository.save(any(Move.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameStateDTO state = gameService.processMove(
                new MoveRequest(1L, 0, Direction.RIGHT, CurrentTurn.A, "alice"));

        assertThat(game.getScoreA()).isEqualTo(6);
        assertThat(game.getScoreB()).isZero();
        assertThat(game.getCurrentTurn()).isEqualTo(CurrentTurn.B);
        assertThat(game.getPhase()).isEqualTo(GamePhase.PLAYING);
        assertThat(state.capturedPoints()).isEqualTo(6);
        assertThat(state.lastCellIndex()).isEqualTo(0);
        assertThat(state.lastDirection()).isEqualTo(Direction.RIGHT);
        assertThat(state.board().get(1).dan()).isZero();

        ArgumentCaptor<Move> moveCaptor = ArgumentCaptor.forClass(Move.class);
        verify(moveRepository).save(moveCaptor.capture());
        Move savedMove = moveCaptor.getValue();
        assertThat(savedMove.getGame()).isSameAs(game);
        assertThat(savedMove.getCellIndex()).isEqualTo(0);
        assertThat(savedMove.getDirection()).isEqualTo(Direction.RIGHT);
        assertThat(savedMove.getCapturedDan()).isEqualTo(6);
        assertThat(savedMove.getCapturedQuan()).isZero();
        assertThat(savedMove.getCapturedPoints()).isEqualTo(6);
        assertThat(savedMove.getMoveOrder()).isEqualTo(1);
        assertThat(savedMove.getBoardAfterJson()).isEqualTo(game.getBoardStateJson());
    }

    @Test
    void processMoveRejectsWaitingAndEndedGames() {
        Game waitingGame = playableGame(1L, BoardState.initial());
        waitingGame.setPhase(GamePhase.WAITING);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(waitingGame));

        assertThatThrownBy(() -> gameService.processMove(
                new MoveRequest(1L, 0, Direction.RIGHT, CurrentTurn.A, "alice")))
                .isInstanceOf(IllegalArgumentException.class);

        Game endedGame = playableGame(2L, BoardState.initial());
        endedGame.setPhase(GamePhase.ENDED);
        when(gameRepository.findById(2L)).thenReturn(Optional.of(endedGame));

        assertThatThrownBy(() -> gameService.processMove(
                new MoveRequest(2L, 0, Direction.RIGHT, CurrentTurn.A, "alice")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(moveRepository, never()).save(any(Move.class));
    }

    @Test
    void processMoveRejectsWrongPlayerTurnForOnlineGame() {
        Game game = playableGame(1L, BoardState.initial());
        game.setPlayerA(user("alice"));
        game.setPlayerB(user("bob"));
        game.setCurrentTurn(CurrentTurn.A);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        assertThatThrownBy(() -> gameService.processMove(
                new MoveRequest(1L, 6, Direction.RIGHT, CurrentTurn.B, "bob")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(moveRepository, never()).save(any(Move.class));
    }

    @Test
    void createAiGameDefaultsUnknownDifficultyAndAcceptsAiFirstTurn() {
        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> {
            Game game = invocation.getArgument(0);
            game.setId(200L);
            return game;
        });

        GameStateDTO state = gameService.createAiGame("not-a-level", "AI");

        assertThat(state.gameId()).isEqualTo(200L);
        assertThat(state.aiGame()).isTrue();
        assertThat(state.aiDifficulty()).isEqualTo(AiDifficulty.MEDIUM.name());
        assertThat(state.currentTurn()).isEqualTo(CurrentTurn.B);

        ArgumentCaptor<Game> captor = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(captor.capture());
        Game saved = captor.getValue();
        assertThat(saved.isAiGame()).isTrue();
        assertThat(saved.getAiDifficulty()).isEqualTo(AiDifficulty.MEDIUM);
        assertThat(saved.getCurrentTurn()).isEqualTo(CurrentTurn.B);
        assertThat(saved.getPhase()).isEqualTo(GamePhase.PLAYING);
    }

    @Test
    void processAiMoveRejectsNonAiGame() {
        Game game = playableGame(1L, BoardState.initial());
        game.setAiGame(false);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        assertThatThrownBy(() -> gameService.processAiMove(1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void processAiMoveReturnsCurrentStateWhenItIsNotAiTurn() {
        Game game = playableGame(1L, BoardState.initial());
        game.setAiGame(true);
        game.setCurrentTurn(CurrentTurn.A);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        GameStateDTO state = gameService.processAiMove(1L);

        assertThat(state.currentTurn()).isEqualTo(CurrentTurn.A);
        assertThat(state.phase()).isEqualTo(GamePhase.PLAYING);
        verifyNoInteractions(moveRepository);
    }

    private Game playableGame(Long id, BoardState board) {
        Game game = new Game();
        game.setId(id);
        game.setBoardStateJson(toJson(board));
        game.setCurrentTurn(CurrentTurn.A);
        game.setPhase(GamePhase.PLAYING);
        return game;
    }

    private User user(String username) {
        return new User(username, "password", username + "@demo.local");
    }

    private String toJson(BoardState board) {
        try {
            return objectMapper.writeValueAsString(board);
        } catch (JsonProcessingException ex) {
            throw new AssertionError(ex);
        }
    }
}
