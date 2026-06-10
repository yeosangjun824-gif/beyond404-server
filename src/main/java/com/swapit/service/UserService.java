package com.swapit.service;

import com.swapit.domain.entity.UserEntity;
import com.swapit.dto.DemoLoginRequest;
import com.swapit.dto.DemoLoginResponse;
import com.swapit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    @Transactional
    public DemoLoginResponse demoLogin(DemoLoginRequest request) {
        String thinqUserKey = toThinqUserKey(request.phoneNumber(), request.userName());
        UserEntity user = userRepository.findByThinqUserKey(thinqUserKey)
                .map(existingUser -> {
                    existingUser.updateProfile(request.userName(), request.phoneNumber());
                    return existingUser;
                })
                .orElseGet(() -> UserEntity.create(thinqUserKey, request.userName(), request.phoneNumber()));
        UserEntity savedUser = userRepository.save(user);
        return new DemoLoginResponse(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getPhoneNumber(),
                savedUser.getThinqUserKey()
        );
    }

    public static String toThinqUserKey(String phoneNumber, String userName) {
        String normalizedPhone = phoneNumber == null ? "" : phoneNumber.replaceAll("[^0-9A-Za-z]", "");
        String keySource = normalizedPhone.isBlank() ? userName : normalizedPhone;
        return "phone:" + keySource.toLowerCase(Locale.ROOT);
    }
}
