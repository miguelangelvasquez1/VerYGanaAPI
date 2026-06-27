package com.verygana2.services.interfaces;

import com.verygana2.models.User;

public interface PasswordSetupService {
    void initiatePasswordSetup(User user, String designerName, String designerCode);
    void completePasswordSetup(String token, String newPassword);
}
