package com.example.oanquan.service;

import com.example.oanquan.dto.LeaderboardDTO;
import com.example.oanquan.entity.User;
import com.example.oanquan.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {
    @Mock
    private UserRepository userRepository;

    @Test
    void top10MapsUsersToLeaderboardDtos() {
        User alice = user("alice", 5, 1, 2);
        User bob = user("bob", 3, 4, 1);
        when(userRepository.findTop10ByOrderByTotalWinsDescTotalDrawsDesc())
                .thenReturn(List.of(alice, bob));
        LeaderboardService service = new LeaderboardService(userRepository);

        List<LeaderboardDTO> result = service.top10();

        assertThat(result).containsExactly(
                new LeaderboardDTO("alice", 5, 1, 2),
                new LeaderboardDTO("bob", 3, 4, 1)
        );
    }

    private User user(String username, int wins, int losses, int draws) {
        User user = new User(username, "password", username + "@demo.local");
        user.setTotalWins(wins);
        user.setTotalLosses(losses);
        user.setTotalDraws(draws);
        return user;
    }
}
