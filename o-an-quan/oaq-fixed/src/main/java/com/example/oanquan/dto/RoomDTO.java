package com.example.oanquan.dto;

import com.example.oanquan.model.RoomStatus;

public record RoomDTO(
        Long id,
        String roomCode,
        String hostUsername,
        String guestUsername,
        RoomStatus status,
        Long gameId
) {}
