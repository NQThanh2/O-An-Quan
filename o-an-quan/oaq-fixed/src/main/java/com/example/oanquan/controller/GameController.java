package com.example.oanquan.controller;

import com.example.oanquan.dto.GameStateDTO;
import com.example.oanquan.dto.MoveHistoryDTO;
import com.example.oanquan.dto.MoveRequest;
import com.example.oanquan.service.GameService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/games")
public class GameController {
    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping("/local")
    public GameStateDTO createLocalGame() {
        return gameService.createLocalGame();
    }

    @PostMapping("/ai")
    public GameStateDTO createAiGame(
            @RequestParam(defaultValue = "MEDIUM") String difficulty,
            @RequestParam(defaultValue = "A") String firstTurn
    ) {
        return gameService.createAiGame(difficulty, firstTurn);
    }

    @GetMapping("/{id}")
    public GameStateDTO getGame(@PathVariable Long id) {
        return gameService.getGameState(id);
    }

    @PostMapping("/{id}/move")
    public GameStateDTO move(@PathVariable Long id, @Valid @RequestBody MoveRequest request) {
        // CHẶN HARDCODE: Không cho phép bốc ô Quan (Index 5 và 11)
        if (request.cellIndex() == 5 || request.cellIndex() == 11) {
            throw new IllegalArgumentException("Luật chơi: Không được bốc quân từ ô Quan!");
        }

        MoveRequest fixedRequest = new MoveRequest(id, request.cellIndex(), request.direction(), request.playerSide(), request.username());
        return gameService.processMove(fixedRequest);
    }

    @PostMapping("/{id}/ai-move")
    public GameStateDTO aiMove(@PathVariable Long id) {
        return gameService.processAiMove(id);
    }

    @GetMapping("/{id}/moves")
    public List<MoveHistoryDTO> moves(@PathVariable Long id) {
        return gameService.getMoveHistory(id);
    }
}