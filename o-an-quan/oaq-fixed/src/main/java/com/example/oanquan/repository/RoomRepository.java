package com.example.oanquan.repository;

import com.example.oanquan.entity.Room;
import com.example.oanquan.model.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByRoomCode(String roomCode);
    List<Room> findByStatusOrderByCreatedAtDesc(RoomStatus status);
}
