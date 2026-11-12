package com.notetaking.dto.request;

import com.notetaking.entity.User;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SetRoleRequest {
    @NotNull
    private User.Role role;
}
