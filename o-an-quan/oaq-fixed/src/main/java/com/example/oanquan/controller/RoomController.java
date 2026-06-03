package com.example.oanquan.controller;

import com.example.oanquan.dto.CreateRoomRequest;
import com.example.oanquan.dto.RoomDTO;
import com.example.oanquan.service.GameService;
import com.example.oanquan.service.RoomService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomService roomService;
    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    public RoomController(RoomService roomService,
                          GameService gameService,
                          SimpMessagingTemplate messagingTemplate) {
        this.roomService = roomService;
        this.gameService = gameService;
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping
    public RoomDTO create(@RequestBody(required = false) CreateRoomRequest request) {
        RoomDTO room = roomService.createRoom(request == null ? null : request.username());
        messagingTemplate.convertAndSend("/topic/rooms", room);
        return room;
    }


    @PostMapping("/matchmaking/find")
    public RoomDTO findMatch(@RequestParam(defaultValue = "player") String username) {
        RoomDTO room = roomService.findOnlineMatch(username);
        messagingTemplate.convertAndSend("/topic/rooms", room);
        if (room.gameId() != null) {
            messagingTemplate.convertAndSend("/topic/room/" + room.roomCode(), room);
            messagingTemplate.convertAndSend("/topic/game/" + room.gameId(), gameService.getGameState(room.gameId()));
        }
        return room;
    }

    @GetMapping
    public List<RoomDTO> waitingRooms() {
        return roomService.waitingRooms();
    }

    @GetMapping("/{code}")
    public RoomDTO getRoom(@PathVariable String code) {
        return roomService.getRoom(code);
    }

    @PostMapping("/{code}/join")
    public RoomDTO join(@PathVariable String code, @RequestParam(defaultValue = "guest") String username) {
        RoomDTO room = roomService.joinRoom(code, username);
        messagingTemplate.convertAndSend("/topic/rooms", room);
        messagingTemplate.convertAndSend("/topic/room/" + room.roomCode(), room);
        if (room.gameId() != null) {
            messagingTemplate.convertAndSend("/topic/game/" + room.gameId(), gameService.getGameState(room.gameId()));
        }
        return room;
    }
}
