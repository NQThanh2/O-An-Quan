package com.example.oanquan.engine;

import com.example.oanquan.model.CurrentTurn;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoardStateTest {

    @Test
    void initialCreatesTraditionalTwelveCellBoard() {
        BoardState board = BoardState.initial();

        assertThat(board.getCells()).hasSize(BoardState.BOARD_SIZE);
        assertThat(board.cell(BoardState.RIGHT_QUAN_INDEX).isQuan()).isTrue();
        assertThat(board.cell(BoardState.LEFT_QUAN_INDEX).isQuan()).isTrue();
        assertThat(board.cell(BoardState.RIGHT_QUAN_INDEX).getDan()).isZero();
        assertThat(board.cell(BoardState.LEFT_QUAN_INDEX).getDan()).isZero();

        for (int i = 0; i < BoardState.BOARD_SIZE; i++) {
            if (!board.isQuanIndex(i)) {
                assertThat(board.cell(i).getDan()).isEqualTo(5);
                assertThat(board.cell(i).isQuan()).isFalse();
            }
        }
    }

    @Test
    void copyCreatesIndependentCells() {
        BoardState original = BoardState.initial();

        BoardState copy = original.copy();
        copy.cell(0).setDan(99);
        copy.cell(BoardState.RIGHT_QUAN_INDEX).setQuan(false);

        assertThat(original.cell(0).getDan()).isEqualTo(5);
        assertThat(original.cell(BoardState.RIGHT_QUAN_INDEX).isQuan()).isTrue();
        assertThat(copy.cell(0)).isNotSameAs(original.cell(0));
    }

    @Test
    void detectsOwnedCellsByPlayerSide() {
        BoardState board = BoardState.initial();

        assertThat(board.isOwnedCell(CurrentTurn.A, 0)).isTrue();
        assertThat(board.isOwnedCell(CurrentTurn.A, 4)).isTrue();
        assertThat(board.isOwnedCell(CurrentTurn.A, 6)).isFalse();
        assertThat(board.isOwnedCell(CurrentTurn.B, 6)).isTrue();
        assertThat(board.isOwnedCell(CurrentTurn.B, 10)).isTrue();
        assertThat(board.isOwnedCell(CurrentTurn.B, 4)).isFalse();
    }

    @Test
    void seedsAndCollectsPlayerSide() {
        BoardState board = BoardState.initial();
        for (int i = 0; i <= 4; i++) {
            board.cell(i).setDan(0);
        }

        assertThat(board.isPlayerSideEmpty(CurrentTurn.A)).isTrue();

        board.seedPlayerSide(CurrentTurn.A);

        assertThat(board.isPlayerSideEmpty(CurrentTurn.A)).isFalse();
        for (int i = 0; i <= 4; i++) {
            assertThat(board.cell(i).getDan()).isEqualTo(1);
        }

        assertThat(board.collectRemainingDan(CurrentTurn.A)).isEqualTo(5);
        assertThat(board.isPlayerSideEmpty(CurrentTurn.A)).isTrue();
    }

    @Test
    void gameOverOnlyWhenBothQuanCellsAreEmpty() {
        BoardState board = BoardState.initial();

        assertThat(board.isGameOver()).isFalse();

        board.cell(BoardState.RIGHT_QUAN_INDEX).setQuan(false);
        assertThat(board.isGameOver()).isFalse();

        board.cell(BoardState.LEFT_QUAN_INDEX).setQuan(false);
        assertThat(board.isGameOver()).isTrue();
    }

    @Test
    void cellCountsQuanAsOnePieceAndCopiesItself() {
        Cell cell = new Cell(3, true);

        Cell copy = cell.copy();
        copy.setDan(0);
        copy.setQuan(false);

        assertThat(cell.totalPieces()).isEqualTo(4);
        assertThat(cell.isEmpty()).isFalse();
        assertThat(copy.isEmpty()).isTrue();
        assertThat(cell.getDan()).isEqualTo(3);
        assertThat(cell.isQuan()).isTrue();
    }
}
