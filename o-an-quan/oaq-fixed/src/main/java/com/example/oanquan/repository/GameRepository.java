package com.example.oanquan.repository;

import com.example.oanquan.entity.Game;
import com.example.oanquan.entity.Room;
import com.example.oanquan.entity.User;
import com.example.oanquan.model.GamePhase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GameRepository extends JpaRepository<Game, Long> {
    List<Game> findByPlayerAOrPlayerBOrderByCreatedAtDesc(User playerA, User playerB);
    List<Game> findByPhase(GamePhase phase);
    List<Game> findTop20ByOrderByCreatedAtDesc();
    Optional<Game> findByRoom(Room room);
}
