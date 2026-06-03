package com.example.oanquan.dto;

import com.example.oanquan.model.CurrentTurn;
import com.example.oanquan.model.Direction;
import jakarta.validation.constraints.NotNull;

public record MoveRequest(
        Long gameId,
        int cellIndex,
        @NotNull Direction direction,
        CurrentTurn playerSide,
        String username
) {}
