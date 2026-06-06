package com.example.oanquan.service;

import com.example.oanquan.dto.RoomDTO;
import com.example.oanquan.engine.BoardState;
import com.example.oanquan.engine.OAnQuanEngine;
import com.example.oanquan.entity.Game;
import com.example.oanquan.entity.Room;
import com.example.oanquan.entity.User;
import com.example.oanquan.model.CurrentTurn;
import com.example.oanquan.model.GamePhase;
import com.example.oanquan.model.RoomStatus;
import com.example.oanquan.repository.GameRepository;
import com.example.oanquan.repository.RoomRepository;
import com.example.oanquan.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {
    @Mock
    private RoomRepository roomRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private ObjectMapper objectMapper;
    private RoomService roomService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        roomService = new RoomService(
                roomRepository,
                userRepository,
                gameRepository,
                passwordEncoder,
                new OAnQuanEngine(),
                objectMapper
        );
    }

    @Test
    void createRoomCreatesDemoHostWaitingRoomAndWaitingGame() {
        AtomicReference<Game> savedGame = new AtomicReference<>();
        when(userRepository.findByUsername("Alice")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("demo123")).thenReturn("encoded-demo");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });
        when(roomRepository.findByRoomCode(any())).thenReturn(Optional.empty());
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            room.setId(10L);
            return room;
        });
        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> {
            Game game = invocation.getArgument(0);
            game.setId(20L);
            savedGame.set(game);
            return game;
        });
        when(gameRepository.findByRoom(any(Room.class)))
                .thenAnswer(invocation -> Optional.ofNullable(savedGame.get()));

        RoomDTO dto = roomService.createRoom(" Alice ");

        assertThat(dto.id()).isEqualTo(10L);
        assertThat(dto.roomCode()).hasSize(6);
        assertThat(dto.hostUsername()).isEqualTo("Alice");
        assertThat(dto.guestUsername()).isNull();
        assertThat(dto.status()).isEqualTo(RoomStatus.WAITING);
        assertThat(dto.gameId()).isEqualTo(20L);

        Game game = savedGame.get();
        assertThat(game.getPlayerA().getUsername()).isEqualTo("Alice");
        assertThat(game.getCurrentTurn()).isEqualTo(CurrentTurn.A);
        assertThat(game.getPhase()).isEqualTo(GamePhase.WAITING);
        assertThat(readBoard(game.getBoardStateJson()).getCells()).hasSize(BoardState.BOARD_SIZE);
    }

    @Test
    void joinRoomAddsGuestAndStartsExistingGame() {
        User host = user("Alice");
        Room room = waitingRoom("ABC123", host);
        Game game = gameForRoom(room, host);
        when(roomRepository.findByRoomCode("ABC123")).thenReturn(Optional.of(room));
        when(userRepository.findByUsername("Bob")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("demo123")).thenReturn("encoded-demo");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(2L);
            return user;
        });
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gameRepository.findByRoom(any(Room.class))).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoomDTO dto = roomService.joinRoom("ABC123", "Bob");

        assertThat(dto.roomCode()).isEqualTo("ABC123");
        assertThat(dto.hostUsername()).isEqualTo("Alice");
        assertThat(dto.guestUsername()).isEqualTo("Bob");
        assertThat(dto.status()).isEqualTo(RoomStatus.PLAYING);
        assertThat(dto.gameId()).isEqualTo(30L);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.PLAYING);
        assertThat(room.getGuest().getUsername()).isEqualTo("Bob");
        assertThat(game.getPlayerB().getUsername()).isEqualTo("Bob");
        assertThat(game.getPhase()).isEqualTo(GamePhase.PLAYING);
    }

    @Test
    void joinRoomRejectsRoomThatIsNotWaiting() {
        Room room = waitingRoom("ABC123", user("Alice"));
        room.setStatus(RoomStatus.PLAYING);
        when(roomRepository.findByRoomCode("ABC123")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.joinRoom("ABC123", "Bob"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(gameRepository, never()).save(any(Game.class));
    }

    @Test
    void joinRoomRejectsSameUsernameAsHost() {
        User host = user("Alice");
        Room room = waitingRoom("ABC123", host);
        when(roomRepository.findByRoomCode("ABC123")).thenReturn(Optional.of(room));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(host));

        assertThatThrownBy(() -> roomService.joinRoom("ABC123", "alice"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(roomRepository, never()).save(any(Room.class));
        verify(gameRepository, never()).save(any(Game.class));
    }

    @Test
    void findOnlineMatchReturnsOwnWaitingRoomInsteadOfCreatingAnother() {
        Room room = waitingRoom("OWN123", user("Alice"));
        when(roomRepository.findByStatusOrderByCreatedAtDesc(RoomStatus.WAITING))
                .thenReturn(List.of(room));
        when(gameRepository.findByRoom(room)).thenReturn(Optional.empty());

        RoomDTO dto = roomService.findOnlineMatch("alice");

        assertThat(dto.roomCode()).isEqualTo("OWN123");
        assertThat(dto.hostUsername()).isEqualTo("Alice");
        assertThat(dto.status()).isEqualTo(RoomStatus.WAITING);
        verify(roomRepository, never()).save(any(Room.class));
        verify(gameRepository, never()).save(any(Game.class));
    }

    private User user(String username) {
        User user = new User(username, "password", username + "@demo.local");
        user.setId((long) username.hashCode());
        return user;
    }

    private Room waitingRoom(String code, User host) {
        Room room = new Room();
        room.setId(10L);
        room.setRoomCode(code);
        room.setHost(host);
        room.setStatus(RoomStatus.WAITING);
        return room;
    }

    private Game gameForRoom(Room room, User host) {
        Game game = new Game();
        game.setId(30L);
        game.setRoom(room);
        game.setPlayerA(host);
        game.setBoardStateJson(toJson(BoardState.initial()));
        game.setCurrentTurn(CurrentTurn.A);
        game.setPhase(GamePhase.WAITING);
        return game;
    }

    private BoardState readBoard(String json) {
        try {
            return objectMapper.readValue(json, BoardState.class);
        } catch (JsonProcessingException ex) {
            throw new AssertionError(ex);
        }
    }

    private String toJson(BoardState board) {
        try {
            return objectMapper.writeValueAsString(board);
        } catch (JsonProcessingException ex) {
            throw new AssertionError(ex);
        }
    }
}
