package com.example.oanquan.entity;

import com.example.oanquan.model.Direction;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "moves")
public class Move {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "game_id")
    private Game game;

    @ManyToOne
    @JoinColumn(name = "player_id")
    private User player;

    private int cellIndex;

    @Enumerated(EnumType.STRING)
    private Direction direction;

    private int capturedDan;
    private int capturedQuan;
    private int capturedPoints;

    @Lob
    @Column(nullable = false)
    private String boardAfterJson;

    private int moveOrder;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Move() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Game getGame() { return game; }
    public void setGame(Game game) { this.game = game; }
    public User getPlayer() { return player; }
    public void setPlayer(User player) { this.player = player; }
    public int getCellIndex() { return cellIndex; }
    public void setCellIndex(int cellIndex) { this.cellIndex = cellIndex; }
    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }
    public int getCapturedDan() { return capturedDan; }
    public void setCapturedDan(int capturedDan) { this.capturedDan = capturedDan; }
    public int getCapturedQuan() { return capturedQuan; }
    public void setCapturedQuan(int capturedQuan) { this.capturedQuan = capturedQuan; }
    public int getCapturedPoints() { return capturedPoints; }
    public void setCapturedPoints(int capturedPoints) { this.capturedPoints = capturedPoints; }
    public String getBoardAfterJson() { return boardAfterJson; }
    public void setBoardAfterJson(String boardAfterJson) { this.boardAfterJson = boardAfterJson; }
    public int getMoveOrder() { return moveOrder; }
    public void setMoveOrder(int moveOrder) { this.moveOrder = moveOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
