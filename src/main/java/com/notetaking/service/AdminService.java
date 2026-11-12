package com.notetaking.service;

import com.notetaking.entity.User;
import com.notetaking.exception.NotFoundException;
import com.notetaking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final TokenRevocationService revocationService;

    @Transactional
    public void setUserRole(String username, User.Role role) {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found: " + username));
        user.setRole(role);
        // Revoke all existing tokens so the role change takes effect immediately
        revocationService.revokeAllTokensForUser(username);
    }

    public void revokeAllTokensForUser(String username) {
        if (!userRepository.existsByUsername(username)) {
            throw new NotFoundException("User not found: " + username);
        }
        revocationService.revokeAllTokensForUser(username);
    }
}
