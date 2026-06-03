package com.example.oanquan.model;

public enum CurrentTurn {
    A, B;

    public CurrentTurn opposite() {
        return this == A ? B : A;
    }
}
