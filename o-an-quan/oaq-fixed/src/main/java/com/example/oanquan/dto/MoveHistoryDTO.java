package com.example.oanquan.dto;

import com.example.oanquan.model.Direction;
import java.time.LocalDateTime;

public record MoveHistoryDTO(
        int moveOrder,
        int cellIndex,
        Direction direction,
        int capturedDan,
        int capturedQuan,
        int capturedPoints,
        LocalDateTime createdAt
) {}
