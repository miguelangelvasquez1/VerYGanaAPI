package com.verygana2.services.interfaces;

public interface PhoneNumberChangeService {

    void requestPhoneChange(Long userId, String newPhoneNumber);

    void verifyAndChangePhone(Long userId, String newPhoneNumber, String otpCode);

    void adminChangePhone(Long userId, String newPhoneNumber);
}
