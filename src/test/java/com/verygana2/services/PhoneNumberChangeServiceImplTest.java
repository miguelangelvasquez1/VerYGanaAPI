package com.verygana2.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.exceptions.PhoneNumberAlreadyExistsException;
import com.verygana2.models.PhoneNumberHistory;
import com.verygana2.models.User;
import com.verygana2.repositories.PhoneNumberHistoryRepository;
import com.verygana2.repositories.UserRepository;
import com.verygana2.services.interfaces.TwilioSmsService;

@ExtendWith(MockitoExtension.class)
class PhoneNumberChangeServiceImplTest {

    private static final Long USER_ID = 42L;
    private static final String OLD_PHONE = "3000000001";
    private static final String NEW_PHONE = "3000000002";

    @Mock UserRepository userRepository;
    @Mock PhoneNumberHistoryRepository historyRepository;
    @Mock TwilioSmsService twilioSmsService;

    private PhoneNumberChangeServiceImpl service;
    private User user;
    private PhoneNumberHistory currentHistory;

    @BeforeEach
    void setUp() {
        service = new PhoneNumberChangeServiceImpl(userRepository, historyRepository, twilioSmsService);

        user = new User();
        user.setId(USER_ID);
        user.setPhoneNumber(OLD_PHONE);

        currentHistory = new PhoneNumberHistory();
        currentHistory.setUser(user);
        currentHistory.setPhoneNumber(OLD_PHONE);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    @Test
    void verifyAndChangePhone_Success() {
        when(historyRepository.findByPhoneNumberAndDetachedAtIsNull(OLD_PHONE))
                .thenReturn(List.of(currentHistory));
        when(twilioSmsService.verifyOtp(NEW_PHONE, "123456")).thenReturn(true);

        service.verifyAndChangePhone(USER_ID, NEW_PHONE, "123456");

        assertThat(user.getId()).isEqualTo(USER_ID);
        assertThat(user.getPhoneNumber()).isEqualTo(NEW_PHONE);
        assertThat(currentHistory.getDetachedAt()).isNotNull();
        verify(userRepository).save(user);

        ArgumentCaptor<PhoneNumberHistory> captor = ArgumentCaptor.forClass(PhoneNumberHistory.class);
        verify(historyRepository, times(2)).save(captor.capture());
        PhoneNumberHistory savedNewHistory = captor.getAllValues().stream()
                .filter(h -> NEW_PHONE.equals(h.getPhoneNumber()))
                .findFirst()
                .orElseThrow();
        assertThat(savedNewHistory.getUser()).isSameAs(user);
    }

    @Test
    void adminChangePhone_Success() {
        when(historyRepository.findByPhoneNumberAndDetachedAtIsNull(OLD_PHONE))
                .thenReturn(List.of(currentHistory));
        service.adminChangePhone(USER_ID, NEW_PHONE);

        assertThat(user.getId()).isEqualTo(USER_ID);
        assertThat(user.getPhoneNumber()).isEqualTo(NEW_PHONE);
        assertThat(currentHistory.getDetachedAt()).isNotNull();
        verify(userRepository).save(user);

        ArgumentCaptor<PhoneNumberHistory> captor = ArgumentCaptor.forClass(PhoneNumberHistory.class);
        verify(historyRepository, times(2)).save(captor.capture());
        PhoneNumberHistory savedNewHistory = captor.getAllValues().stream()
                .filter(h -> NEW_PHONE.equals(h.getPhoneNumber()))
                .findFirst()
                .orElseThrow();
        assertThat(savedNewHistory.getUser()).isSameAs(user);
    }

    @Test
    void requestPhoneChange_PhoneAlreadyInUse() {
        User otherUser = new User();
        otherUser.setId(999L);
        when(userRepository.findByPhoneNumber(NEW_PHONE)).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> service.requestPhoneChange(USER_ID, NEW_PHONE))
                .isInstanceOf(PhoneNumberAlreadyExistsException.class);

        verify(twilioSmsService, never()).sendOtp(any());
        verify(userRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void adminChangePhone_PhoneAlreadyInUse() {
        User otherUser = new User();
        otherUser.setId(999L);
        when(userRepository.findByPhoneNumber(NEW_PHONE)).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> service.adminChangePhone(USER_ID, NEW_PHONE))
                .isInstanceOf(PhoneNumberAlreadyExistsException.class);

        assertThat(user.getPhoneNumber()).isEqualTo(OLD_PHONE);
        assertThat(currentHistory.getDetachedAt()).isNull();
        verify(userRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }
}
