package com.example.oanquan.entity;

import com.example.oanquan.model.CurrentTurn;
import com.example.oanquan.model.GamePhase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "games")
public class Game {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne
    @JoinColumn(name = "player_a_id")
    private User playerA;

    @ManyToOne
    @JoinColumn(name = "player_b_id")
    private User playerB;

    @Lob
    @Column(nullable = false)
    private String boardStateJson;

    @Enumerated(EnumType.STRING)
    private CurrentTurn currentTurn = CurrentTurn.A;

    private int scoreA;
    private int scoreB;

    /**
     * true: ván chơi với máy. Người thật luôn là A, AI là B.
     * false: ván local hoặc ván online 2 người.
     */
    private boolean aiGame = false;

    @Enumerated(EnumType.STRING)
    private GamePhase phase = GamePhase.PLAYING;

    @ManyToOne
    @JoinColumn(name = "winner_id")
    private User winner;

    private LocalDateTime startedAt = LocalDateTime.now();
    private LocalDateTime endedAt;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Game() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }
    public User getPlayerA() { return playerA; }
    public void setPlayerA(User playerA) { this.playerA = playerA; }
    public User getPlayerB() { return playerB; }
    public void setPlayerB(User playerB) { this.playerB = playerB; }
    public String getBoardStateJson() { return boardStateJson; }
    public void setBoardStateJson(String boardStateJson) { this.boardStateJson = boardStateJson; }
    public CurrentTurn getCurrentTurn() { return currentTurn; }
    public void setCurrentTurn(CurrentTurn currentTurn) { this.currentTurn = currentTurn; }
    public int getScoreA() { return scoreA; }
    public void setScoreA(int scoreA) { this.scoreA = scoreA; }
    public int getScoreB() { return scoreB; }
    public void setScoreB(int scoreB) { this.scoreB = scoreB; }
    public boolean isAiGame() { return aiGame; }
    public void setAiGame(boolean aiGame) { this.aiGame = aiGame; }
    public GamePhase getPhase() { return phase; }
    public void setPhase(GamePhase phase) { this.phase = phase; }
    public User getWinner() { return winner; }
    public void setWinner(User winner) { this.winner = winner; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
