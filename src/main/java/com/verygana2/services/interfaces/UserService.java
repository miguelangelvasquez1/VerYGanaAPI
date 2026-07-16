package com.verygana2.services.interfaces;

import com.verygana2.dtos.user.CommercialRegisterDTO;
import com.verygana2.dtos.user.ComplianceOfficerRegisterDTO;
import com.verygana2.dtos.user.ConsumerRegisterDTO;
import com.verygana2.dtos.user.GameDesignerRegisterDTO;
import com.verygana2.models.User;

public interface UserService {
    User registerConsumer(ConsumerRegisterDTO dto);
    User registerCommercial(CommercialRegisterDTO dto);
    User registerGameDesigner(GameDesignerRegisterDTO dto);
    User registerComplianceOfficer(ComplianceOfficerRegisterDTO dto);

    User getUserById(Long id);
    User getUserByEmail(String email);
    boolean emailExists(String email);
    boolean phoneExists(String phoneNumber);
    void deleteById(Long id);

    void verifyEmailCode(String email, String code);
    void resendVerificationEmail(String email);

    /** Alternativa por SMS: envía OTP (Twilio Verify) al teléfono registrado de la cuenta */
    void sendPhoneVerification(String email);
    /** Verifica el OTP recibido por SMS y activa la cuenta */
    void verifyPhoneCode(String email, String code);

    /**
     * Recuperación de contraseña: envía un código al correo si la cuenta existe.
     * No revela si el correo está registrado (anti-enumeración).
     */
    void requestPasswordReset(String email);
    /** Verifica el código y establece la nueva contraseña, revocando las sesiones activas */
    void resetPassword(String email, String code, String newPassword);
}
