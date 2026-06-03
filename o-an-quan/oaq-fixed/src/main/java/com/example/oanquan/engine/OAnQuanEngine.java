package com.example.oanquan.engine;

import com.example.oanquan.model.CurrentTurn;
import com.example.oanquan.model.Direction;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class OAnQuanEngine {
    public static final int BOARD_SIZE = 12;
    public static final int QUAN_VALUE = 10;

    public BoardState initBoard() {
        return BoardState.initial();
    }

    /**
     * LUẬT ĐÚNG - Ô Ăn Quan truyền thống:
     * 1) Chỉ được bốc quân ở 1 ô dân bên mình (không bốc ô quan).
     * 2) Rải từng quân theo hướng đã chọn.
     * - Sau khi rải hết quân trên tay:
     * a) Nếu ô kế tiếp CÓ QUÂN:
     * + NẾU LÀ Ô DÂN: bốc toàn bộ ô đó lên, rải tiếp (gọi là "đi tiếp").
     * + NẾU LÀ Ô QUAN: tuyệt đối không được bốc, phải DỪNG LẠI (chuyển sang bước c).
     * b) Nếu ô kế tiếp TRỐNG rồi đến 1 ô CÓ QUÂN: ăn toàn bộ ô đó.
     * - Sau khi ăn, kiểm tra 2 ô tiếp (ô trống + ô sau): nếu lại trống→có quân thì ăn tiếp (ăn dây chuyền).
     * c) Nếu ô kế tiếp TRỐNG rồi đến ô OAN hay hai ô trống liên tiếp: kết thúc lượt ngay.
     */
    public MoveResult applyMove(BoardState currentBoard,
                                int cellIndex,
                                Direction direction,
                                CurrentTurn player) {
        validateMove(currentBoard, cellIndex, player);

        BoardState board = currentBoard.copy();
        int capturedDan = 0;
        int capturedQuan = 0;
        List<AnimationStep> steps = new ArrayList<>();
        int[] order = {0};

        int stones = board.cell(cellIndex).getDan();
        steps.add(new AnimationStep(order[0]++, cellIndex, cellIndex, "PICKUP", cellIndex,
                "Bốc " + stones + " quân từ ô " + cellIndex));
        board.cell(cellIndex).setDan(0);

        // Rải và đi tiếp (bốc lại khi rơi vào ô có quân)
        int pos = sowAndContinue(board, cellIndex, direction, stones, steps, order);

        // Sau khi dừng, kiểm tra ăn dây chuyền
        int[] captureResult = captureChain(board, pos, direction, steps, order);
        capturedDan += captureResult[0];
        capturedQuan += captureResult[1];

        boolean gameOver = board.isGameOver();
        String message;
        if (capturedDan + capturedQuan > 0) {
            message = "Ăn được " + capturedDan + " dân" + (capturedQuan > 0 ? ", " + capturedQuan + " quan" : "") + ".";
        } else {
            message = "Không ăn được quân nào.";
        }
        return new MoveResult(board, capturedDan, capturedQuan, gameOver, message, steps);
    }

    /**
     * Rải quân và bốc tiếp nếu ô cuối rơi vào ô có quân (CHỈ BỐC Ô DÂN).
     * Trả về vị trí cuối cùng trước khi dừng.
     */
    private int sowAndContinue(BoardState board, int startPos, Direction direction,
                               int stones, List<AnimationStep> steps, int[] order) {
        int pos = startPos;
        while (true) {
            pos = sow(board, pos, direction, stones, steps, order, pos);

            // Kiểm tra ô kế tiếp
            int next = nextIndex(pos, direction);
            Cell nextCell = board.cell(next);

            if (!nextCell.isEmpty()) {
                // SỬA LỖI TẠI ĐÂY: KIỂM TRA XEM Ô KẾ TIẾP CÓ PHẢI LÀ Ô QUAN KHÔNG
                if (board.isQuanIndex(next)) {
                    // Tuyệt đối không được nhấc ô Quan để rải quân tiếp. Dừng lại ngay!
                    steps.add(new AnimationStep(order[0]++, next, next, "TURN_END", null,
                            "Tới ô Quan. Dừng rải quân."));
                    return pos;
                }

                // Nếu là ô Dân bình thường → Bốc lên rải tiếp
                stones = nextCell.getDan();
                steps.add(new AnimationStep(order[0]++, next, next, "PICKUP", next,
                        "Ô kế tiếp có quân, bốc " + stones + " quân tiếp tục rải"));
                nextCell.setDan(0); // Chỉ reset ô dân
                pos = next;
            } else {
                // Ô kế tiếp trống → dừng rải
                return pos;
            }
        }
    }

    /**
     * Ăn dây chuyền: ô trống → ô có quân → ăn → lại ô trống → ô có quân → ăn ...
     * Dừng khi gặp ô trống liên tiếp hoặc ô quan trống.
     */
    private int[] captureChain(BoardState board, int pos, Direction direction,
                               List<AnimationStep> steps, int[] order) {
        int capturedDan = 0;
        int capturedQuan = 0;

        while (true) {
            int next = nextIndex(pos, direction);
            Cell nextCell = board.cell(next);

            if (!nextCell.isEmpty()) {
                // Ô kế tiếp không trống → không ăn, kết thúc
                steps.add(new AnimationStep(order[0]++, next, next, "TURN_END", null,
                        "Ô kế tiếp có quân, không ăn được. Kết thúc lượt."));
                break;
            }

            // Ô kế tiếp trống, kiểm tra ô sau nữa
            int afterNext = nextIndex(next, direction);
            Cell captureCell = board.cell(afterNext);

            if (captureCell.isEmpty()) {
                // Hai ô trống liên tiếp → kết thúc
                steps.add(new AnimationStep(order[0]++, next, next, "TURN_END", null,
                        "Hai ô trống liên tiếp. Kết thúc lượt."));
                break;
            }

            // Ô trống → ô có quân → ĂN!
            int danBefore = captureCell.getDan();
            boolean hasQuan = captureCell.isQuan();
            steps.add(new AnimationStep(order[0]++, next, afterNext, "CAPTURE", null,
                    "Ăn ô " + afterNext + ": " + danBefore + " dân" + (hasQuan ? " và 1 quan" : "") + "."));

            capturedDan += danBefore;
            if (hasQuan) capturedQuan += 1;
            captureCell.setDan(0);
            captureCell.setQuan(false);

            // Tiếp tục kiểm tra ăn dây chuyền từ ô vừa ăn
            pos = afterNext;
        }

        return new int[]{capturedDan, capturedQuan};
    }

    public void validateMove(BoardState board, int cellIndex, CurrentTurn player) {
        if (cellIndex < 0 || cellIndex >= BOARD_SIZE) {
            throw new IllegalArgumentException("Ô không hợp lệ.");
        }
        if (board.isQuanIndex(cellIndex)) {
            throw new IllegalArgumentException("Không được chọn ô quan để đi.");
        }
        if (!board.isOwnedCell(player, cellIndex)) {
            throw new IllegalArgumentException("Bạn chỉ được chọn ô dân bên phía mình.");
        }
        if (board.cell(cellIndex).getDan() <= 0) {
            throw new IllegalArgumentException("Ô được chọn đang trống.");
        }
    }

    private int sow(BoardState board, int startPos, Direction direction, int stones,
                    List<AnimationStep> steps, int[] order, int pickupIndex) {
        int pos = startPos;
        int visualFrom = startPos;
        while (stones > 0) {
            int to = nextIndex(pos, direction);
            board.cell(to).setDan(board.cell(to).getDan() + 1);
            steps.add(new AnimationStep(order[0]++, visualFrom, to, "SOW", pickupIndex,
                    "Rải 1 quân vào ô " + to));
            pos = to;
            visualFrom = to;
            stones--;
        }
        return pos;
    }

    private int nextIndex(int current, Direction direction) {
        return (current + direction.step() + BOARD_SIZE) % BOARD_SIZE;
    }
}