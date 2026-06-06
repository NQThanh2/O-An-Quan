package com.example.oanquan.engine;

import com.example.oanquan.model.CurrentTurn;
import com.example.oanquan.model.Direction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAnQuanEngineTest {
    private final OAnQuanEngine engine = new OAnQuanEngine();

    @Test
    void initBoardDelegatesToInitialBoardState() {
        BoardState board = engine.initBoard();

        assertThat(board.getCells()).hasSize(OAnQuanEngine.BOARD_SIZE);
        assertThat(board.cell(BoardState.RIGHT_QUAN_INDEX).isQuan()).isTrue();
        assertThat(board.cell(BoardState.LEFT_QUAN_INDEX).isQuan()).isTrue();
    }

    @Test
    void validateMoveRejectsInvalidCellIndex() {
        assertThatThrownBy(() -> engine.validateMove(BoardState.initial(), -1, CurrentTurn.A))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.validateMove(BoardState.initial(), 12, CurrentTurn.A))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateMoveRejectsQuanOpponentAndEmptyCells() {
        BoardState board = BoardState.initial();
        board.cell(0).setDan(0);

        assertThatThrownBy(() -> engine.validateMove(board, BoardState.RIGHT_QUAN_INDEX, CurrentTurn.A))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.validateMove(board, 6, CurrentTurn.A))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.validateMove(board, 0, CurrentTurn.A))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void applyMoveDoesNotMutateOriginalBoardAndCapturesAfterAnEmptyCell() {
        BoardState original = BoardState.initial();

        MoveResult result = engine.applyMove(original, 0, Direction.RIGHT, CurrentTurn.A);

        assertThat(original.cell(0).getDan()).isEqualTo(5);
        assertThat(original.cell(1).getDan()).isEqualTo(5);
        assertThat(result.getCapturedDan()).isEqualTo(6);
        assertThat(result.getCapturedQuan()).isZero();
        assertThat(result.getCapturedPoints()).isEqualTo(6);
        assertThat(result.getBoard().cell(0).getDan()).isZero();
        assertThat(result.getBoard().cell(1).isEmpty()).isTrue();
        assertThat(result.getBoard().cell(5).isQuan()).isTrue();
        assertThat(result.getAnimationSteps())
                .extracting(AnimationStep::action)
                .contains("PICKUP", "SOW", "CAPTURE");
    }

    @Test
    void applyMoveStopsInsteadOfPickingUpQuanCell() {
        BoardState board = emptyBoard();
        board.cell(3).setDan(1);
        board.cell(BoardState.RIGHT_QUAN_INDEX).setQuan(true);
        board.cell(BoardState.LEFT_QUAN_INDEX).setQuan(true);

        MoveResult result = engine.applyMove(board, 3, Direction.RIGHT, CurrentTurn.A);

        assertThat(result.getCapturedPoints()).isZero();
        assertThat(result.getBoard().cell(4).getDan()).isEqualTo(1);
        assertThat(result.getBoard().cell(BoardState.RIGHT_QUAN_INDEX).isQuan()).isTrue();
        assertThat(result.getBoard().cell(BoardState.RIGHT_QUAN_INDEX).getDan()).isZero();
        assertThat(result.getAnimationSteps())
                .extracting(AnimationStep::action)
                .contains("TURN_END");
    }

    @Test
    void applyMoveCapturesDanAcrossEmptyCell() {
        BoardState board = emptyBoard();
        board.cell(0).setDan(1);
        board.cell(3).setDan(4);
        board.cell(4).setDan(1);
        board.cell(BoardState.RIGHT_QUAN_INDEX).setQuan(true);
        board.cell(BoardState.LEFT_QUAN_INDEX).setQuan(true);

        MoveResult result = engine.applyMove(board, 0, Direction.RIGHT, CurrentTurn.A);

        assertThat(result.getCapturedDan()).isEqualTo(4);
        assertThat(result.getCapturedQuan()).isZero();
        assertThat(result.getCapturedPoints()).isEqualTo(4);
        assertThat(result.isGameOver()).isFalse();
        assertThat(result.getBoard().cell(1).getDan()).isEqualTo(1);
        assertThat(result.getBoard().cell(3).isEmpty()).isTrue();
    }

    @Test
    void applyMoveCanCaptureDanAndQuanInAChain() {
        BoardState board = emptyBoard();
        board.cell(0).setDan(1);
        board.cell(3).setDan(2);
        board.cell(BoardState.RIGHT_QUAN_INDEX).setQuan(true);
        board.cell(BoardState.LEFT_QUAN_INDEX).setQuan(true);

        MoveResult result = engine.applyMove(board, 0, Direction.RIGHT, CurrentTurn.A);

        assertThat(result.getCapturedDan()).isEqualTo(2);
        assertThat(result.getCapturedQuan()).isEqualTo(1);
        assertThat(result.getCapturedPoints()).isEqualTo(12);
        assertThat(result.getBoard().cell(3).isEmpty()).isTrue();
        assertThat(result.getBoard().cell(BoardState.RIGHT_QUAN_INDEX).isEmpty()).isTrue();
        assertThat(result.isGameOver()).isFalse();
    }

    private BoardState emptyBoard() {
        List<Cell> cells = new ArrayList<>();
        for (int i = 0; i < BoardState.BOARD_SIZE; i++) {
            cells.add(new Cell(0, false));
        }
        return new BoardState(cells);
    }
}
