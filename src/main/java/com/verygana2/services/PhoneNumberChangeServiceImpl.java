package com.verygana2.services;

import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.exceptions.PhoneNumberAlreadyExistsException;
import com.verygana2.models.PhoneNumberHistory;
import com.verygana2.models.User;
import com.verygana2.repositories.PhoneNumberHistoryRepository;
import com.verygana2.repositories.UserRepository;
import com.verygana2.services.interfaces.PhoneNumberChangeService;
import com.verygana2.services.interfaces.TwilioSmsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhoneNumberChangeServiceImpl implements PhoneNumberChangeService {

    private final UserRepository userRepository;
    private final PhoneNumberHistoryRepository historyRepository;
    private final TwilioSmsService twilioSmsService;

    @Override
    public void requestPhoneChange(Long userId, String newPhoneNumber) {
        User user = findUser(userId);
        validateNewPhone(user, newPhoneNumber);
        twilioSmsService.sendOtp(newPhoneNumber);
        log.info("Phone change OTP requested for user {}", userId);
    }

    @Override
    @Transactional
    public void verifyAndChangePhone(Long userId, String newPhoneNumber, String otpCode) {
        User user = findUser(userId);
        validateNewPhone(user, newPhoneNumber);

        if (!twilioSmsService.verifyOtp(newPhoneNumber, otpCode)) {
            throw new InvalidRequestException("Código OTP inválido o expirado");
        }

        String oldPhoneNumber = user.getPhoneNumber();
        ZonedDateTime now = ZonedDateTime.now();

        // 2. Desvincular el número anterior en el historial (cierra todos los registros abiertos)
        historyRepository.findByPhoneNumberAndDetachedAtIsNull(oldPhoneNumber)
                .stream()
                .filter(history -> history.getUser().getId().equals(userId))
                .forEach(history -> {
                    history.setDetachedAt(now);
                    historyRepository.save(history);
                });

        user.setPhoneNumber(newPhoneNumber);
        userRepository.save(user);

        PhoneNumberHistory newHistory = new PhoneNumberHistory();
        newHistory.setUser(user);
        newHistory.setPhoneNumber(newPhoneNumber);
        newHistory.setAssignedAt(now);
        historyRepository.save(newHistory);

        log.info("Phone number changed for user {}; existing account data preserved", userId);
    }

    @Override
    @Transactional
    public void adminChangePhone(Long userId, String newPhoneNumber) {
        User user = findUser(userId);
        validateNewPhone(user, newPhoneNumber);

        String oldPhoneNumber = user.getPhoneNumber();
        ZonedDateTime now = ZonedDateTime.now();

        // 2. Desvincular el número anterior en el historial (cierra todos los registros abiertos)
        historyRepository.findByPhoneNumberAndDetachedAtIsNull(oldPhoneNumber)
                .stream()
                .filter(history -> history.getUser().getId().equals(userId))
                .forEach(history -> {
                    history.setDetachedAt(now);
                    historyRepository.save(history);
                });

        user.setPhoneNumber(newPhoneNumber);
        userRepository.save(user);

        PhoneNumberHistory newHistory = new PhoneNumberHistory();
        newHistory.setUser(user);
        newHistory.setPhoneNumber(newPhoneNumber);
        newHistory.setAssignedAt(now);
        historyRepository.save(newHistory);

        log.info("Administrative phone number change completed for user {}", userId);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new InvalidRequestException("Usuario no encontrado"));
    }

    private void validateNewPhone(User user, String newPhoneNumber) {
        if (newPhoneNumber == null || newPhoneNumber.isBlank()) {
            throw new InvalidRequestException("El nuevo número es obligatorio");
        }
        if (newPhoneNumber.equals(user.getPhoneNumber())) {
            throw new InvalidRequestException("El nuevo número debe ser diferente al actual");
        }
        // Solo validar si el número está activo en OTRA cuenta (no la del usuario actual)
        userRepository.findByPhoneNumber(newPhoneNumber).ifPresent(otherUser -> {
            if (!otherUser.getId().equals(user.getId())) {
                throw new PhoneNumberAlreadyExistsException(newPhoneNumber);
            }
        });
    }
}
