package com.example.oanquan.dto;

public record LeaderboardDTO(
        String username,
        int wins,
        int losses,
        int draws
) {}
