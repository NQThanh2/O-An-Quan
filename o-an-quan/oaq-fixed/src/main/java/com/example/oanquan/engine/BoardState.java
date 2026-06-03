package com.example.oanquan.engine;

import com.example.oanquan.model.CurrentTurn;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;

public class BoardState {
    public static final int BOARD_SIZE = 12;
    public static final int RIGHT_QUAN_INDEX = 5;
    public static final int LEFT_QUAN_INDEX = 11;

    private List<Cell> cells = new ArrayList<>();

    public BoardState() {
    }

    public BoardState(List<Cell> cells) {
        this.cells = cells;
    }

    public static BoardState initial() {
        List<Cell> list = new ArrayList<>();
        for (int i = 0; i < BOARD_SIZE; i++) {
            boolean isQuan = i == RIGHT_QUAN_INDEX || i == LEFT_QUAN_INDEX;
            list.add(new Cell(isQuan ? 0 : 5, isQuan));
        }
        return new BoardState(list);
    }

    public List<Cell> getCells() {
        return cells;
    }

    public void setCells(List<Cell> cells) {
        this.cells = cells;
    }

    public Cell cell(int index) {
        return cells.get(index);
    }

    public BoardState copy() {
        List<Cell> copy = new ArrayList<>();
        for (Cell c : cells) {
            copy.add(c.copy());
        }
        return new BoardState(copy);
    }

    public boolean isQuanIndex(int index) {
        return index == RIGHT_QUAN_INDEX || index == LEFT_QUAN_INDEX;
    }

    @JsonIgnore
    public boolean isGameOver() {
        return cell(RIGHT_QUAN_INDEX).isEmpty() && cell(LEFT_QUAN_INDEX).isEmpty();
    }

    public boolean isOwnedCell(CurrentTurn player, int index) {
        if (player == CurrentTurn.A) {
            return index >= 0 && index <= 4;
        }
        return index >= 6 && index <= 10;
    }

    @JsonIgnore
    public boolean isPlayerSideEmpty(CurrentTurn player) {
        int start = player == CurrentTurn.A ? 0 : 6;
        int end = player == CurrentTurn.A ? 4 : 10;
        for (int i = start; i <= end; i++) {
            if (cell(i).getDan() > 0) return false;
        }
        return true;
    }

    public void seedPlayerSide(CurrentTurn player) {
        int start = player == CurrentTurn.A ? 0 : 6;
        int end = player == CurrentTurn.A ? 4 : 10;
        for (int i = start; i <= end; i++) {
            cell(i).setDan(cell(i).getDan() + 1);
        }
    }

    public int collectRemainingDan(CurrentTurn player) {
        int start = player == CurrentTurn.A ? 0 : 6;
        int end = player == CurrentTurn.A ? 4 : 10;
        int total = 0;
        for (int i = start; i <= end; i++) {
            total += cell(i).getDan();
            cell(i).setDan(0);
        }
        return total;
    }
}
