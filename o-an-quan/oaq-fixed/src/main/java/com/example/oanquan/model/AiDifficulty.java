package com.example.oanquan.model;

public enum AiDifficulty {
    EASY("Dễ"),
    MEDIUM("Trung bình"),
    HARD("Khó");

    private final String label;

    AiDifficulty(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}