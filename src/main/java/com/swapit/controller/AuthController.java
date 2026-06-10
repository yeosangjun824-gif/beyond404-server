package com.swapit.controller;

import com.swapit.dto.DemoLoginRequest;
import com.swapit.dto.DemoLoginResponse;
import com.swapit.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;

    @PostMapping("/demo-login")
    public DemoLoginResponse demoLogin(@Valid @RequestBody DemoLoginRequest request) {
        return userService.demoLogin(request);
    }
}
