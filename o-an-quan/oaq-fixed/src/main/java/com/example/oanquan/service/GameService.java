package com.example.oanquan.service;

import com.example.oanquan.dto.CellDTO;
import com.example.oanquan.dto.GameStateDTO;
import com.example.oanquan.dto.MoveHistoryDTO;
import com.example.oanquan.dto.MoveRequest;
import com.example.oanquan.engine.AnimationStep;
import com.example.oanquan.engine.BoardState;
import com.example.oanquan.engine.MoveResult;
import com.example.oanquan.engine.OAnQuanEngine;
import com.example.oanquan.entity.Game;
import com.example.oanquan.entity.Move;
import com.example.oanquan.entity.User;
import com.example.oanquan.model.CurrentTurn;
import com.example.oanquan.model.Direction;
import com.example.oanquan.model.GamePhase;
import com.example.oanquan.repository.GameRepository;
import com.example.oanquan.repository.MoveRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class GameService {
    private static final String AI_NAME = "Máy AI";

    private final GameRepository gameRepository;
    private final MoveRepository moveRepository;
    private final OAnQuanEngine engine;
    private final ObjectMapper objectMapper;

    public GameService(GameRepository gameRepository,
                       MoveRepository moveRepository,
                       OAnQuanEngine engine,
                       ObjectMapper objectMapper) {
        this.gameRepository = gameRepository;
        this.moveRepository = moveRepository;
        this.engine = engine;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public GameStateDTO createLocalGame() {
        Game game = new Game();
        game.setBoardStateJson(toJson(engine.initBoard()));
        game.setCurrentTurn(CurrentTurn.A);
        game.setPhase(GamePhase.PLAYING);
        game.setAiGame(false);
        gameRepository.save(game);
        return toStateDTO(game, "Tạo ván local thành công.", null, null, null, List.of());
    }

    public GameStateDTO getGameState(Long id) {
        Game game = findGame(id);
        return toStateDTO(game, null, null, null, null, List.of());
    }

    public List<MoveHistoryDTO> getMoveHistory(Long gameId) {
        Game game = findGame(gameId);
        return moveRepository.findByGameOrderByMoveOrderAsc(game).stream()
                .map(m -> new MoveHistoryDTO(
                        m.getMoveOrder(),
                        m.getCellIndex(),
                        m.getDirection(),
                        m.getCapturedDan(),
                        m.getCapturedQuan(),
                        m.getCapturedPoints(),
                        m.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public GameStateDTO processMove(MoveRequest request) {
        Game game = findGame(request.gameId());
        if (game.getPhase() == GamePhase.WAITING) {
            throw new IllegalArgumentException("Ván online đang chờ người chơi B vào phòng.");
        }
        if (game.getPhase() == GamePhase.ENDED) {
            throw new IllegalArgumentException("Ván chơi đã kết thúc.");
        }
        validateTurn(game, request);

        BoardState board = fromJson(game.getBoardStateJson());
        StringBuilder turnNotice = new StringBuilder();

        autoSeedSideIfNeeded(game, board, game.getCurrentTurn(), turnNotice);
        if (game.getPhase() == GamePhase.ENDED) {
            game.setBoardStateJson(toJson(board));
            updateWinner(game);
            gameRepository.save(game);
            return toStateDTO(game, turnNotice.toString(), null,
                    request.cellIndex(), request.direction(), List.of());
        }

        MoveResult result = engine.applyMove(board, request.cellIndex(), request.direction(), game.getCurrentTurn());

        CurrentTurn mover = game.getCurrentTurn();
        if (mover == CurrentTurn.A) {
            game.setScoreA(game.getScoreA() + result.getCapturedPoints());
        } else {
            game.setScoreB(game.getScoreB() + result.getCapturedPoints());
        }

        StringBuilder message = new StringBuilder();
        if (!turnNotice.isEmpty()) message.append(turnNotice).append(" ");
        message.append(mover == CurrentTurn.A ? "Người A" : "Người B")
                .append(" đi ô ").append(request.cellIndex())
                .append(request.direction() == Direction.LEFT ? " sang trái. " : " sang phải. ")
                .append(result.getMessage());

        if (result.isGameOver()) {
            int restA = result.getBoard().collectRemainingDan(CurrentTurn.A);
            int restB = result.getBoard().collectRemainingDan(CurrentTurn.B);
            game.setScoreA(game.getScoreA() + restA);
            game.setScoreB(game.getScoreB() + restB);
            game.setPhase(GamePhase.ENDED);
            game.setEndedAt(LocalDateTime.now());
            updateWinner(game);
            message.append(" Hai ô quan đã hết, ván kết thúc.");
        } else {
            CurrentTurn nextTurn = mover.opposite();
            game.setCurrentTurn(nextTurn);
            autoSeedSideIfNeeded(game, result.getBoard(), nextTurn, message);
            if (game.getPhase() == GamePhase.ENDED) {
                updateWinner(game);
            } else {
                message.append(" Tới lượt Người ").append(nextTurn).append(".");
            }
        }

        game.setBoardStateJson(toJson(result.getBoard()));
        gameRepository.save(game);

        Move move = new Move();
        move.setGame(game);
        move.setPlayer(resolvePlayer(game, request.playerSide()));
        move.setCellIndex(request.cellIndex());
        move.setDirection(request.direction());
        move.setCapturedDan(result.getCapturedDan());
        move.setCapturedQuan(result.getCapturedQuan());
        move.setCapturedPoints(result.getCapturedPoints());
        move.setBoardAfterJson(game.getBoardStateJson());
        move.setMoveOrder((int) moveRepository.countByGame(game) + 1);
        moveRepository.save(move);

        String prefix = game.isAiGame() && request.playerSide() == CurrentTurn.B ? "AI đã đi. " : "";
        return toStateDTO(game, prefix + message, result.getCapturedPoints(),
                request.cellIndex(), request.direction(), result.getAnimationSteps());
    }

    @Transactional
    public GameStateDTO createAiGame() {
        Game game = new Game();
        game.setBoardStateJson(toJson(engine.initBoard()));
        game.setCurrentTurn(CurrentTurn.A);
        game.setPhase(GamePhase.PLAYING);
        game.setAiGame(true);
        gameRepository.save(game);
        return toStateDTO(game, "Bạn là Người A. Máy AI là Người B.", null, null, null, List.of());
    }


    @Transactional
    public GameStateDTO processAiMove(Long gameId) {
        Game game = findGame(gameId);
        if (!game.isAiGame()) {
            throw new IllegalArgumentException("Ván này không phải ván chơi với AI.");
        }
        if (game.getPhase() != GamePhase.PLAYING) {
            return toStateDTO(game, "Ván chơi không còn ở trạng thái đang chơi.", null, null, null, List.of());
        }
        if (game.getCurrentTurn() != CurrentTurn.B) {
            return toStateDTO(game, "Chưa tới lượt AI.", null, null, null, List.of());
        }
        MoveRequest aiMove = chooseAiMove(game);
        return processMove(aiMove);
    }


    private void autoSeedSideIfNeeded(Game game, BoardState board, CurrentTurn side, StringBuilder message) {
        if (!board.isPlayerSideEmpty(side)) return;

        if (side == CurrentTurn.A && game.getScoreA() >= 5) {
            board.seedPlayerSide(CurrentTurn.A);
            game.setScoreA(game.getScoreA() - 5);
            message.append(" Người A hết dân nên hệ thống tự rải lại 5 quân.");
            return;
        }
        if (side == CurrentTurn.B && game.getScoreB() >= 5) {
            board.seedPlayerSide(CurrentTurn.B);
            game.setScoreB(game.getScoreB() - 5);
            message.append(" Người B hết dân nên hệ thống tự rải lại 5 quân.");
            return;
        }

        game.setPhase(GamePhase.ENDED);
        game.setEndedAt(LocalDateTime.now());
        message.append(side == CurrentTurn.A
                ? " Người A hết dân và không đủ 5 điểm để rải lại. Ván kết thúc."
                : " Người B hết dân và không đủ 5 điểm để rải lại. Ván kết thúc.");
    }


    /* ====================================================================
       THUẬT TOÁN AI: ĐẠI KIỆN TƯỚNG (MINIMAX + ALPHA-BETA + HEURISTIC NÂNG CAO)
       ==================================================================== */

    private MoveRequest chooseAiMove(Game game) {
        BoardState board = fromJson(game.getBoardStateJson());

        // Nếu AI chưa kịp được rải quân bù tự động mà bị trống bàn, đi bừa 1 ô an toàn
        if (board.isPlayerSideEmpty(CurrentTurn.B)) {
            return new MoveRequest(game.getId(), 6, Direction.RIGHT, CurrentTurn.B, AI_NAME);
        }

        int bestValue = Integer.MIN_VALUE;
        // ĐỘ SÂU THUẬT TOÁN: Nhìn trước 8 nước cờ (Tăng độ khó tối đa)
        int depth = 8;
        List<MoveRequest> bestMoves = new ArrayList<>();

        // AI (Phe B) chỉ tính toán dựa trên các ô từ 6 đến 10 (Tuyệt đối an toàn khỏi ô Quan 5 & 11)
        for (int cell = 6; cell <= 10; cell++) {
            if (board.cell(cell).getDan() <= 0) continue;
            for (Direction direction : Direction.values()) {
                try {
                    // Áp dụng thử nước đi đầu tiên của AI
                    MoveResult simulated = engine.applyMove(board, cell, direction, CurrentTurn.B);

                    // Khởi chạy Minimax để đánh giá độ bá đạo của nhánh tương lai này
                    int boardVal = minimax(simulated.getBoard(), depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, false,
                            game.getScoreA(), game.getScoreB() + simulated.getCapturedPoints());

                    if (boardVal > bestValue) {
                        bestValue = boardVal;
                        bestMoves.clear();
                        bestMoves.add(new MoveRequest(game.getId(), cell, direction, CurrentTurn.B, AI_NAME));
                    } else if (boardVal == bestValue) {
                        bestMoves.add(new MoveRequest(game.getId(), cell, direction, CurrentTurn.B, AI_NAME));
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        // Chọn ngẫu nhiên 1 trong các đường tối ưu nhất để tránh việc AI đánh quá máy móc 1 kiểu
        if (!bestMoves.isEmpty()) {
            return bestMoves.get(new Random().nextInt(bestMoves.size()));
        }

        // Bước lùi an toàn
        return new MoveRequest(game.getId(), 6, Direction.RIGHT, CurrentTurn.B, AI_NAME);
    }

    private int minimax(BoardState board, int depth, int alpha, int beta, boolean isMaximizing, int scoreA, int scoreB) {
        // Dừng khi phân tích đủ số lượt quy định hoặc game đã kết thúc
        if (depth == 0 || board.isGameOver()) {
            return evaluateBoard(board, scoreA, scoreB);
        }

        if (isMaximizing) {
            // LƯỢT AI CHƠI: Cố gắng đạt điểm Heuristic cao nhất (Tìm Max)
            if (board.isPlayerSideEmpty(CurrentTurn.B)) {
                if (scoreB >= 5) { // Mô phỏng mượn quân
                    BoardState nextBoard = board.copy();
                    nextBoard.seedPlayerSide(CurrentTurn.B);
                    return minimax(nextBoard, depth, alpha, beta, isMaximizing, scoreA, scoreB - 5);
                } else {
                    return evaluateBoard(board, scoreA, scoreB);
                }
            }

            int maxEval = Integer.MIN_VALUE;
            boolean hasMove = false;
            for (int cell = 6; cell <= 10; cell++) { // Chỉ phân tích ô dân phe B
                if (board.cell(cell).getDan() <= 0) continue;
                for (Direction dir : Direction.values()) {
                    try {
                        MoveResult res = engine.applyMove(board, cell, dir, CurrentTurn.B);
                        hasMove = true;
                        int eval = minimax(res.getBoard(), depth - 1, alpha, beta, false, scoreA, scoreB + res.getCapturedPoints());
                        maxEval = Math.max(maxEval, eval);
                        alpha = Math.max(alpha, eval);
                        if (beta <= alpha) break; // Cắt tỉa: Nhánh quá phế, khỏi cần duyệt
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            if (!hasMove) return evaluateBoard(board, scoreA, scoreB);
            return maxEval;

        } else {
            // LƯỢT BẠN CHƠI: AI giả định bạn đi giỏi nhất để dìm điểm nó (Tìm Min)
            if (board.isPlayerSideEmpty(CurrentTurn.A)) {
                if (scoreA >= 5) {
                    BoardState nextBoard = board.copy();
                    nextBoard.seedPlayerSide(CurrentTurn.A);
                    return minimax(nextBoard, depth, alpha, beta, isMaximizing, scoreA - 5, scoreB);
                } else {
                    return evaluateBoard(board, scoreA, scoreB);
                }
            }

            int minEval = Integer.MAX_VALUE;
            boolean hasMove = false;
            for (int cell = 0; cell <= 4; cell++) { // Chỉ phân tích ô dân phe A
                if (board.cell(cell).getDan() <= 0) continue;
                for (Direction dir : Direction.values()) {
                    try {
                        MoveResult res = engine.applyMove(board, cell, dir, CurrentTurn.A);
                        hasMove = true;
                        int eval = minimax(res.getBoard(), depth - 1, alpha, beta, true, scoreA + res.getCapturedPoints(), scoreB);
                        minEval = Math.min(minEval, eval);
                        beta = Math.min(beta, eval);
                        if (beta <= alpha) break; // Cắt tỉa: Bạn ăn quá đau, AI sẽ né hướng này từ trước
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            if (!hasMove) return evaluateBoard(board, scoreA, scoreB);
            return minEval;
        }
    }

    // HÀM ĐÁNH GIÁ (HEURISTIC) ĐÃ ĐƯỢC NÂNG CẤP ĐỂ TẠO THẾ TRẬN
    private int evaluateBoard(BoardState board, int scoreA, int scoreB) {
        int remA = 0; int remB = 0;
        int emptyCellsA = 0; int emptyCellsB = 0;

        // Phân tích sân Người A (Bạn)
        for (int i = 0; i <= 4; i++) {
            int dan = board.cell(i).getDan();
            remA += dan;
            if (dan == 0) emptyCellsA++;
        }

        // Phân tích sân Người B (Máy AI)
        for (int i = 6; i <= 10; i++) {
            int dan = board.cell(i).getDan();
            remB += dan;
            if (dan == 0) emptyCellsB++;
        }

        if (board.isGameOver()) {
            scoreA += remA;
            scoreB += remB;
            remA = 0;
            remB = 0;
        }

        // TRỌNG SỐ TÍNH ĐIỂM (Càng cao càng ưu tiên):

        // Ưu tiên 1: Chênh lệch điểm số thực tế (*1000)
        int scoreDiff = (scoreB - scoreA) * 1000;

        // Ưu tiên 2: Phạt nặng nếu để sân có nhiều ô trống (*80)
        // (Nhiều ô trống = Dễ bị đối phương ăn dây chuyền hoặc dễ cạn kiệt phải mượn quân)
        int emptyDiff = (emptyCellsA - emptyCellsB) * 80;

        // Ưu tiên 3: Giữ lại quân dự trữ trên sân để duy trì nhịp độ đi cờ (*10)
        int remDiff = (remB - remA) * 10;

        return scoreDiff + emptyDiff + remDiff;
    }


    private void validateTurn(Game game, MoveRequest request) {
        boolean onlineGame = game.getPlayerA() != null || game.getPlayerB() != null;
        if (!onlineGame) return;

        if (request.playerSide() == null) {
            throw new IllegalArgumentException("Ván online cần xác định bạn là người A hay B.");
        }
        if (request.playerSide() != game.getCurrentTurn()) {
            throw new IllegalArgumentException("Chưa tới lượt của bạn.");
        }

        String username = request.username();
        if (username == null || username.isBlank()) return;

        if (request.playerSide() == CurrentTurn.A && game.getPlayerA() != null
                && !game.getPlayerA().getUsername().equalsIgnoreCase(username.trim())) {
            throw new IllegalArgumentException("Tên người chơi A không khớp với chủ phòng.");
        }
        if (request.playerSide() == CurrentTurn.B && game.getPlayerB() != null
                && !game.getPlayerB().getUsername().equalsIgnoreCase(username.trim())) {
            throw new IllegalArgumentException("Tên người chơi B không khớp với khách vào phòng.");
        }
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value.trim();
    }

    private User resolvePlayer(Game game, CurrentTurn side) {
        if (game.isAiGame()) return null;
        if (side == CurrentTurn.A) return game.getPlayerA();
        if (side == CurrentTurn.B) return game.getPlayerB();
        return null;
    }

    private void updateWinner(Game game) {
        if (game.getScoreA() > game.getScoreB()) {
            game.setWinner(game.getPlayerA());
            if (game.getPlayerA() != null) game.getPlayerA().setTotalWins(game.getPlayerA().getTotalWins() + 1);
            if (game.getPlayerB() != null) game.getPlayerB().setTotalLosses(game.getPlayerB().getTotalLosses() + 1);
        } else if (game.getScoreB() > game.getScoreA()) {
            game.setWinner(game.getPlayerB());
            if (game.getPlayerB() != null) game.getPlayerB().setTotalWins(game.getPlayerB().getTotalWins() + 1);
            if (game.getPlayerA() != null) game.getPlayerA().setTotalLosses(game.getPlayerA().getTotalLosses() + 1);
        } else {
            if (game.getPlayerA() != null) game.getPlayerA().setTotalDraws(game.getPlayerA().getTotalDraws() + 1);
            if (game.getPlayerB() != null) game.getPlayerB().setTotalDraws(game.getPlayerB().getTotalDraws() + 1);
        }
    }

    private Game findGame(Long id) {
        return gameRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ván chơi."));
    }

    private BoardState fromJson(String json) {
        try {
            return objectMapper.readValue(json, BoardState.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không đọc được trạng thái bàn chơi.", e);
        }
    }

    private String toJson(BoardState board) {
        try {
            return objectMapper.writeValueAsString(board);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không lưu được trạng thái bàn chơi.", e);
        }
    }

    private GameStateDTO toStateDTO(Game game,
                                    String message,
                                    Integer capturedPoints,
                                    Integer lastCellIndex,
                                    Direction lastDirection,
                                    List<AnimationStep> animationSteps) {
        BoardState board = fromJson(game.getBoardStateJson());
        List<CellDTO> cells = new ArrayList<>();
        for (int i = 0; i < board.getCells().size(); i++) {
            boolean quanIndex = board.isQuanIndex(i);
            cells.add(new CellDTO(
                    i,
                    board.cell(i).getDan(),
                    board.cell(i).isQuan(),
                    quanIndex,
                    !quanIndex && i >= 0 && i <= 4 && board.cell(i).getDan() > 0,
                    !quanIndex && i >= 6 && i <= 10 && board.cell(i).getDan() > 0
            ));
        }
        String roomCode = game.getRoom() == null ? null : game.getRoom().getRoomCode();
        String playerA = game.isAiGame()
                ? "Bạn"
                : (game.getPlayerA() == null ? null : game.getPlayerA().getUsername());
        String playerB = game.isAiGame()
                ? AI_NAME
                : (game.getPlayerB() == null ? null : game.getPlayerB().getUsername());
        return new GameStateDTO(
                game.getId(),
                roomCode,
                playerA,
                playerB,
                game.isAiGame(),
                cells,
                game.getCurrentTurn(),
                game.getScoreA(),
                game.getScoreB(),
                game.getPhase(),
                message,
                capturedPoints,
                lastCellIndex,
                lastDirection,
                animationSteps == null ? List.of() : animationSteps
        );
    }
}