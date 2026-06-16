package com.swapit.service;

import com.swapit.domain.entity.UserEntity;
import com.swapit.dto.DemoLoginRequest;
import com.swapit.dto.DemoLoginResponse;
import com.swapit.dto.FirebaseLoginRequest;
import com.swapit.dto.LoginIdCheckResponse;
import com.swapit.dto.LoginRequest;
import com.swapit.dto.SignupRequest;
import com.swapit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final String DUPLICATE_PHONE_MESSAGE = "이미 가입된 전화번호입니다. 로그인 화면에서 로그인해주세요.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public DemoLoginResponse demoLogin(DemoLoginRequest request) {
        String phoneNumber = formatPhoneNumber(request.phoneNumber());
        String thinqUserKey = toThinqUserKey(phoneNumber, request.userName());
        UserEntity user = userRepository.findByThinqUserKey(thinqUserKey)
                .or(() -> userRepository.findByPhoneNumber(phoneNumber))
                .map(existingUser -> {
                    existingUser.updateProfile(request.userName(), phoneNumber);
                    return existingUser;
                })
                .orElseGet(() -> UserEntity.create(thinqUserKey, request.userName(), phoneNumber));
        UserEntity savedUser = userRepository.save(user);
        return toResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public LoginIdCheckResponse checkLoginId(String loginId) {
        String normalizedLoginId = normalizeLoginId(loginId);
        if (normalizedLoginId.length() < 4) {
            return new LoginIdCheckResponse(false, "아이디는 4자 이상 입력해주세요.");
        }

        boolean available = !userRepository.existsByLoginIdIgnoreCase(normalizedLoginId);
        return new LoginIdCheckResponse(
                available,
                available ? "사용 가능한 아이디입니다." : "이미 사용 중인 아이디입니다."
        );
    }

    @Transactional
    public DemoLoginResponse signup(SignupRequest request) {
        String loginId = normalizeLoginId(request.loginId());
        String phoneNumber = formatPhoneNumber(request.phoneNumber());
        if (userRepository.existsByLoginIdIgnoreCase(loginId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다.");
        }

        assertPhoneNumberAvailableForNewUser(phoneNumber);

        String passwordHash = passwordEncoder.encode(request.password());
        String thinqUserKey = "login:" + loginId.toLowerCase(Locale.ROOT);
        UserEntity user = UserEntity.createWithCredentials(
                loginId,
                passwordHash,
                thinqUserKey,
                request.userName(),
                phoneNumber
        );

        return toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public DemoLoginResponse login(LoginRequest request) {
        String loginId = normalizeLoginId(request.loginId());
        UserEntity user = userRepository.findByLoginIdIgnoreCase(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        return toResponse(user);
    }

    @Transactional
    public DemoLoginResponse firebaseLogin(FirebaseLoginRequest request) {
        if (!request.emailVerified()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 인증이 완료된 계정만 로그인할 수 있습니다.");
        }

        String email = normalizeEmail(request.email());
        String phoneNumber = formatPhoneNumber(request.phoneNumber());
        String userName = request.userName() == null || request.userName().isBlank()
                ? email.substring(0, email.indexOf("@"))
                : request.userName().trim();

        UserEntity user = userRepository.findByFirebaseUid(request.firebaseUid())
                .or(() -> userRepository.findByEmailIgnoreCase(email))
                .map(existingUser -> {
                    String nextName = userName.isBlank() ? existingUser.getName() : userName;
                    String nextPhoneNumber = phoneNumber == null ? existingUser.getPhoneNumber() : phoneNumber;
                    assertPhoneNumberAvailableFor(existingUser, nextPhoneNumber);
                    existingUser.updateFirebaseProfile(email, true, nextName, nextPhoneNumber);
                    return existingUser;
                })
                .orElseGet(() -> {
                    /*
                    if (phoneNumber.isBlank()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "회원가입을 먼저 완료해주세요. 전화번호 확인 후 이메일 인증을 진행해야 합니다.");
                    }
                    assertPhoneNumberAvailableForNewUser(phoneNumber);
                    */
                    return UserEntity.createWithFirebase(
                            request.firebaseUid(),
                            email,
                            true,
                            "firebase:" + request.firebaseUid(),
                            userName,
                            phoneNumber
                    );
                });

        return toResponse(userRepository.save(user));
    }

    public static String toThinqUserKey(String phoneNumber, String userName) {
        String normalizedPhone = phoneNumber == null ? "" : phoneNumber.replaceAll("[^0-9A-Za-z]", "");
        String keySource = normalizedPhone.isBlank() ? userName : normalizedPhone;
        return "phone:" + keySource.toLowerCase(Locale.ROOT);
    }

    public static String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }

        String trimmed = phoneNumber.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String digits = phoneNumber.replaceAll("[^0-9]", "");
        if (digits.length() == 11) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
        }
        if (digits.length() == 10) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6);
        }

        return trimmed;
    }

    private static String normalizeLoginId(String loginId) {
        return loginId == null ? "" : loginId.trim();
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private void assertPhoneNumberAvailableForNewUser(String phoneNumber) {
        if (phoneNumber != null && !phoneNumber.isBlank() && userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, DUPLICATE_PHONE_MESSAGE);
        }
    }

    private void assertPhoneNumberAvailableFor(UserEntity currentUser, String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return;
        }

        userRepository.findByPhoneNumber(phoneNumber)
                .filter(existingUser -> !existingUser.getId().equals(currentUser.getId()))
                .ifPresent(existingUser -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, DUPLICATE_PHONE_MESSAGE);
                });
    }

    private DemoLoginResponse toResponse(UserEntity user) {
        return new DemoLoginResponse(
                user.getId(),
                user.getLoginId(),
                user.getEmail(),
                user.isEmailVerified(),
                user.getName(),
                user.getPhoneNumber(),
                user.getThinqUserKey()
        );
    }
}
