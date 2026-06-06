package com.example.oanquan.controller;

import com.example.oanquan.dto.CreateRoomRequest;
import com.example.oanquan.dto.GameStateDTO;
import com.example.oanquan.dto.RoomDTO;
import com.example.oanquan.model.CurrentTurn;
import com.example.oanquan.model.GamePhase;
import com.example.oanquan.model.RoomStatus;
import com.example.oanquan.service.GameService;
import com.example.oanquan.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomControllerTest {
    @Mock
    private RoomService roomService;

    @Mock
    private GameService gameService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private RoomController controller;

    @BeforeEach
    void setUp() {
        controller = new RoomController(roomService, gameService, messagingTemplate);
    }

    @Test
    void createAllowsNullBodyAndPublishesRoomListUpdate() {
        RoomDTO room = room("ABC123", RoomStatus.WAITING, null);
        when(roomService.createRoom(null)).thenReturn(room);

        RoomDTO result = controller.create(null);

        assertThat(result).isSameAs(room);
        verify(messagingTemplate).convertAndSend("/topic/rooms", room);
    }

    @Test
    void createPassesUsernameFromBody() {
        RoomDTO room = room("ABC123", RoomStatus.WAITING, null);
        when(roomService.createRoom("alice")).thenReturn(room);

        assertThat(controller.create(new CreateRoomRequest("alice"))).isSameAs(room);
    }

    @Test
    void findMatchPublishesRoomAndGameWhenMatchedRoomHasGame() {
        RoomDTO room = room("ABC123", RoomStatus.PLAYING, 7L);
        GameStateDTO state = state(7L);
        when(roomService.findOnlineMatch("alice")).thenReturn(room);
        when(gameService.getGameState(7L)).thenReturn(state);

        RoomDTO result = controller.findMatch("alice");

        assertThat(result).isSameAs(room);
        verify(messagingTemplate).convertAndSend("/topic/rooms", room);
        verify(messagingTemplate).convertAndSend("/topic/room/ABC123", room);
        verify(messagingTemplate).convertAndSend("/topic/game/7", state);
    }

    @Test
    void waitingRoomsDelegatesToService() {
        RoomDTO room = room("ABC123", RoomStatus.WAITING, null);
        when(roomService.waitingRooms()).thenReturn(List.of(room));

        assertThat(controller.waitingRooms()).containsExactly(room);
    }

    @Test
    void getRoomDelegatesToService() {
        RoomDTO room = room("ABC123", RoomStatus.WAITING, null);
        when(roomService.getRoom("ABC123")).thenReturn(room);

        assertThat(controller.getRoom("ABC123")).isSameAs(room);
    }

    @Test
    void joinPublishesRoomAndGameUpdates() {
        RoomDTO room = room("ABC123", RoomStatus.PLAYING, 7L);
        GameStateDTO state = state(7L);
        when(roomService.joinRoom("ABC123", "bob")).thenReturn(room);
        when(gameService.getGameState(7L)).thenReturn(state);

        RoomDTO result = controller.join("ABC123", "bob");

        assertThat(result).isSameAs(room);
        verify(messagingTemplate).convertAndSend("/topic/rooms", room);
        verify(messagingTemplate).convertAndSend("/topic/room/ABC123", room);
        verify(messagingTemplate).convertAndSend("/topic/game/7", state);
    }

    private RoomDTO room(String code, RoomStatus status, Long gameId) {
        return new RoomDTO(1L, code, "alice", null, status, gameId);
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
