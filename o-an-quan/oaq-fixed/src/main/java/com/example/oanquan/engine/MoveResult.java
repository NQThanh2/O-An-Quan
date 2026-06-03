package com.example.oanquan.engine;

import java.util.List;

public class MoveResult {
    private final BoardState board;
    private final int capturedDan;
    private final int capturedQuan;
    private final int capturedPoints;
    private final boolean gameOver;
    private final String message;
    private final List<AnimationStep> animationSteps;

    public MoveResult(BoardState board,
                      int capturedDan,
                      int capturedQuan,
                      boolean gameOver,
                      String message,
                      List<AnimationStep> animationSteps) {
        this.board = board;
        this.capturedDan = capturedDan;
        this.capturedQuan = capturedQuan;
        this.capturedPoints = capturedDan + capturedQuan * OAnQuanEngine.QUAN_VALUE;
        this.gameOver = gameOver;
        this.message = message;
        this.animationSteps = animationSteps == null ? List.of() : animationSteps;
    }

    public BoardState getBoard() { return board; }
    public int getCapturedDan() { return capturedDan; }
    public int getCapturedQuan() { return capturedQuan; }
    public int getCapturedPoints() { return capturedPoints; }
    public boolean isGameOver() { return gameOver; }
    public String getMessage() { return message; }
    public List<AnimationStep> getAnimationSteps() { return animationSteps; }
}
