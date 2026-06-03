package com.example.oanquan.controller;

import com.example.oanquan.dto.ApiError;
import com.example.oanquan.dto.GameStateDTO;
import com.example.oanquan.dto.MoveRequest;
import com.example.oanquan.service.GameService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class GameWebSocketController {
    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    public GameWebSocketController(GameService gameService, SimpMessagingTemplate messagingTemplate) {
        this.gameService = gameService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/game.move")
    public void handleMove(MoveRequest request) {
        try {
            // CHẶN HARDCODE QUA WEBSOCKET: Không cho phép bốc ô Quan
            if (request.cellIndex() == 5 || request.cellIndex() == 11) {
                throw new IllegalArgumentException("Luật chơi: Không được bốc quân từ ô Quan!");
            }

            GameStateDTO state = gameService.processMove(request);
            messagingTemplate.convertAndSend("/topic/game/" + request.gameId(), state);
        } catch (IllegalArgumentException ex) {
            messagingTemplate.convertAndSend("/topic/game/" + request.gameId() + "/errors", new ApiError(ex.getMessage()));
        } catch (Exception ex) {
            messagingTemplate.convertAndSend("/topic/game/" + request.gameId() + "/errors", new ApiError("Lỗi hệ thống: " + ex.getMessage()));
        }
    }
}