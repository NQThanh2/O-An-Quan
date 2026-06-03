package com.example.oanquan.model;

public enum Direction {
    LEFT(-1),
    RIGHT(1);

    private final int step;

    Direction(int step) {
        this.step = step;
    }

    public int step() {
        return step;
    }
}
