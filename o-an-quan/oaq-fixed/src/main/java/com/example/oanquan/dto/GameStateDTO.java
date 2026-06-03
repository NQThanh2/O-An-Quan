package com.example.oanquan.dto;

import com.example.oanquan.engine.AnimationStep;
import com.example.oanquan.model.CurrentTurn;
import com.example.oanquan.model.Direction;
import com.example.oanquan.model.GamePhase;
import java.util.List;

public record GameStateDTO(
        Long gameId,
        String roomCode,
        String playerAUsername,
        String playerBUsername,
        boolean aiGame,
        List<CellDTO> board,
        CurrentTurn currentTurn,
        int scoreA,
        int scoreB,
        GamePhase phase,
        String message,
        Integer capturedPoints,
        Integer lastCellIndex,
        Direction lastDirection,
        List<AnimationStep> animationSteps
) {}
