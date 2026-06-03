package com.example.oanquan.service;

import com.example.oanquan.dto.LeaderboardDTO;
import com.example.oanquan.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LeaderboardService {
    private final UserRepository userRepository;

    public LeaderboardService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<LeaderboardDTO> top10() {
        return userRepository.findTop10ByOrderByTotalWinsDescTotalDrawsDesc()
                .stream()
                .map(u -> new LeaderboardDTO(
                        u.getUsername(),
                        u.getTotalWins(),
                        u.getTotalLosses(),
                        u.getTotalDraws()
                ))
                .toList();
    }
}
