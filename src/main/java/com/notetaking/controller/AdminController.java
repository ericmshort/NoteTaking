package com.notetaking.controller;

import com.notetaking.dto.request.SetRoleRequest;
import com.notetaking.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /** Revoke all active tokens for a user (force logout from all sessions). */
    @DeleteMapping("/users/{username}/tokens")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeUserTokens(@PathVariable String username) {
        adminService.revokeAllTokensForUser(username);
    }

    /** Promote or demote a user's role. Revokes all existing tokens on change. */
    @PutMapping("/users/{username}/role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setUserRole(@PathVariable String username,
                            @Valid @RequestBody SetRoleRequest req) {
        adminService.setUserRole(username, req.getRole());
    }
}
