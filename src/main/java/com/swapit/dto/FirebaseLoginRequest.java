package com.swapit.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FirebaseLoginRequest(
        @NotBlank String firebaseUid,
        @NotBlank @Email String email,
        boolean emailVerified,
        String userName,
        @Size(max = 30) String phoneNumber
) {
}
