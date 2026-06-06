package com.example.oanquan.controller;

import com.example.oanquan.dto.ApiError;
import com.example.oanquan.dto.LeaderboardDTO;
import com.example.oanquan.service.LeaderboardService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OtherControllerTest {

    @Test
    void healthReturnsOk() {
        assertThat(new HealthController().health()).isEqualTo("OK");
    }

    @Test
    void pageControllerReturnsTemplateNames() {
        PageController controller = new PageController();

        assertThat(controller.index()).isEqualTo("index");
        assertThat(controller.game(1L)).isEqualTo("game");
        assertThat(controller.lobby()).isEqualTo("lobby");
    }

    @Test
    void leaderboardControllerDelegatesToService() {
        LeaderboardService service = mock(LeaderboardService.class);
        LeaderboardController controller = new LeaderboardController(service);
        LeaderboardDTO dto = new LeaderboardDTO("alice", 3, 1, 0);
        when(service.top10()).thenReturn(List.of(dto));

        assertThat(controller.top10()).containsExactly(dto);
        verify(service).top10();
    }

    @Test
    void restExceptionHandlerMapsIllegalArgumentToBadRequest() {
        RestExceptionHandler handler = new RestExceptionHandler();

        ResponseEntity<ApiError> response = handler.handleBadRequest(new IllegalArgumentException("bad"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(new ApiError("bad"));
    }

    @Test
    void restExceptionHandlerMapsUnexpectedExceptionToServerError() {
        RestExceptionHandler handler = new RestExceptionHandler();

        ResponseEntity<ApiError> response = handler.handleServerError(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).contains("boom");
    }
}
