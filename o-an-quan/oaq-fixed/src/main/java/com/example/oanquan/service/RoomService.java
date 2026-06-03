package com.example.oanquan.service;

import com.example.oanquan.dto.RoomDTO;
import com.example.oanquan.engine.OAnQuanEngine;
import com.example.oanquan.entity.Game;
import com.example.oanquan.entity.Room;
import com.example.oanquan.entity.User;
import com.example.oanquan.model.CurrentTurn;
import com.example.oanquan.model.GamePhase;
import com.example.oanquan.model.RoomStatus;
import com.example.oanquan.repository.GameRepository;
import com.example.oanquan.repository.RoomRepository;
import com.example.oanquan.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
public class RoomService {
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    private final PasswordEncoder passwordEncoder;
    private final OAnQuanEngine engine;
    private final ObjectMapper objectMapper;

    public RoomService(RoomRepository roomRepository,
                       UserRepository userRepository,
                       GameRepository gameRepository,
                       PasswordEncoder passwordEncoder,
                       OAnQuanEngine engine,
                       ObjectMapper objectMapper) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.gameRepository = gameRepository;
        this.passwordEncoder = passwordEncoder;
        this.engine = engine;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RoomDTO createRoom(String username) {
        User host = getOrCreateDemoUser(username == null || username.isBlank() ? "host" : username.trim());

        Room room = new Room();
        room.setHost(host);
        room.setRoomCode(generateUniqueCode());
        room.setStatus(RoomStatus.WAITING);
        room = roomRepository.save(room);

        Game game = new Game();
        game.setRoom(room);
        game.setPlayerA(host);
        game.setBoardStateJson(toJson(engine.initBoard()));
        game.setCurrentTurn(CurrentTurn.A);
        game.setPhase(GamePhase.WAITING);
        gameRepository.save(game);

        return toDTO(room);
    }

    @Transactional
    public RoomDTO joinRoom(String code, String username) {
        Room room = roomRepository.findByRoomCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phòng."));
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new IllegalArgumentException("Phòng không còn chờ người chơi.");
        }

        User guest = getOrCreateDemoUser(username == null || username.isBlank() ? "guest" : username.trim());
        if (room.getHost() != null && room.getHost().getUsername().equalsIgnoreCase(guest.getUsername())) {
            throw new IllegalArgumentException("Người chơi B phải có tên khác chủ phòng.");
        }

        room.setGuest(guest);
        room.setStatus(RoomStatus.PLAYING);
        room = roomRepository.save(room);
        final Room playingRoom = room;

        Game game = gameRepository.findByRoom(playingRoom).orElseGet(() -> {
            Game g = new Game();
            g.setRoom(playingRoom);
            g.setPlayerA(playingRoom.getHost());
            g.setBoardStateJson(toJson(engine.initBoard()));
            g.setCurrentTurn(CurrentTurn.A);
            return g;
        });
        game.setPlayerA(playingRoom.getHost());
        game.setPlayerB(guest);
        game.setPhase(GamePhase.PLAYING);
        gameRepository.save(game);

        return toDTO(room);
    }


    @Transactional
    public RoomDTO findOnlineMatch(String username) {
        String cleanName = username == null || username.isBlank() ? "player" : username.trim();

        List<Room> waiting = roomRepository.findByStatusOrderByCreatedAtDesc(RoomStatus.WAITING);

        // Nếu người chơi đã có phòng đang chờ thì trả lại phòng đó, tránh tạo nhiều phòng trùng.
        for (Room room : waiting) {
            if (room.getHost() != null && room.getHost().getUsername().equalsIgnoreCase(cleanName)) {
                return toDTO(room);
            }
        }

        // Ghép vào phòng chờ đầu tiên của người khác.
        for (Room room : waiting) {
            if (room.getHost() == null || !room.getHost().getUsername().equalsIgnoreCase(cleanName)) {
                return joinRoom(room.getRoomCode(), cleanName);
            }
        }

        // Chưa có ai chờ thì tạo phòng mới và đợi đối thủ.
        return createRoom(cleanName);
    }

    public RoomDTO getRoom(String code) {
        Room room = roomRepository.findByRoomCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phòng."));
        return toDTO(room);
    }

    public List<RoomDTO> waitingRooms() {
        return roomRepository.findByStatusOrderByCreatedAtDesc(RoomStatus.WAITING)
                .stream().map(this::toDTO).toList();
    }

    private User getOrCreateDemoUser(String username) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            String safeEmail = username.replaceAll("[^a-zA-Z0-9_.-]", "_") + "@demo.local";
            User user = new User(username, passwordEncoder.encode("demo123"), safeEmail);
            return userRepository.save(user);
        });
    }

    private String generateUniqueCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (roomRepository.findByRoomCode(code).isPresent());
        return code;
    }

    private RoomDTO toDTO(Room room) {
        Long gameId = gameRepository.findByRoom(room).map(Game::getId).orElse(null);
        return new RoomDTO(
                room.getId(),
                room.getRoomCode(),
                room.getHost() == null ? null : room.getHost().getUsername(),
                room.getGuest() == null ? null : room.getGuest().getUsername(),
                room.getStatus(),
                gameId
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không tạo được bàn chơi.", e);
        }
    }
}
