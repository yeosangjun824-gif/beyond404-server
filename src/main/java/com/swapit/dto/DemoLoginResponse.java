package com.swapit.dto;

public record DemoLoginResponse(
        long userId,
        String userName,
        String phoneNumber,
        String thinqUserKey
) {
}
