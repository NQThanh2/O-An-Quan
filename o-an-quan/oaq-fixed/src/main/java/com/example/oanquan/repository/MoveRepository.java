package com.example.oanquan.repository;

import com.example.oanquan.entity.Game;
import com.example.oanquan.entity.Move;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MoveRepository extends JpaRepository<Move, Long> {
    List<Move> findByGameOrderByMoveOrderAsc(Game game);
    long countByGame(Game game);
}
