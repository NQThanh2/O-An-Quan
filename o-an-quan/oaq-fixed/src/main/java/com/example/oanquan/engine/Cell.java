package com.example.oanquan.engine;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Cell {
    private int dan;
    private boolean quan;

    public Cell() {
    }

    public Cell(int dan, boolean quan) {
        this.dan = dan;
        this.quan = quan;
    }

    public int getDan() {
        return dan;
    }

    public void setDan(int dan) {
        this.dan = dan;
    }

    public boolean isQuan() {
        return quan;
    }

    public void setQuan(boolean quan) {
        this.quan = quan;
    }

    @JsonIgnore
    public int totalPieces() {
        return dan + (quan ? 1 : 0);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return dan == 0 && !quan;
    }

    public Cell copy() {
        return new Cell(dan, quan);
    }
}
