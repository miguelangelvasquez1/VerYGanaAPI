package com.verygana2.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.models.User;
import com.verygana2.dtos.PhoneChangeRequestDTO;
import com.verygana2.dtos.PhoneChangeVerifyDTO;
import com.verygana2.services.interfaces.UserService;
import com.verygana2.services.interfaces.PhoneNumberChangeService;
import com.verygana2.services.UserIdResolver;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PhoneNumberChangeService phoneNumberChangeService;
    private final UserIdResolver userIdResolver;

    
    @GetMapping("/me")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public String me(@AuthenticationPrincipal Jwt jwt) {
        String subject = jwt.getSubject(); // el "sub"
        String scope = jwt.getClaim("scope"); // tu claim personalizado
        String publicId = jwt.getClaimAsString("publicId");
        return "User: " + subject + ", Roles: " + scope + " Id: " + publicId;
    }

    // Obtener usuario por publicId. Devuelve la entidad completa (documento, teléfono,
    // dirección...), por eso queda restringido a administradores. (no se usa)
    @GetMapping("/public-id/{publicId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<User> getUserById(@PathVariable UUID publicId) {
        User foundUser = userService.getUserById(userIdResolver.toInternalId(publicId));
        return ResponseEntity.ok(foundUser);
    }

    // Obtener usuario por email. Misma exposición de PII que getUserById. (no se usa)
    @GetMapping("/email/{email}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<User> getUserByEmail(@PathVariable String email) {
        User foundUser = userService.getUserByEmail(email);
        return ResponseEntity.ok(foundUser);
    }

    // Verificar si un email ya existe (no se usa)
    @GetMapping("/exists/email/{email}")
    public ResponseEntity<Boolean> emailExists(@PathVariable String email) {
        return ResponseEntity.ok(userService.emailExists(email));
    }

    // Verificar si un número de teléfono ya existe (no se usa)
    @GetMapping("/exists/phoneNumber/{phoneNumber}")
    public ResponseEntity<Boolean> phoneExists(@PathVariable String phoneNumber) {
        return ResponseEntity.ok(userService.phoneExists(phoneNumber));
    }

    @PostMapping("/me/phone/request-change")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> requestPhoneChange(
            @Valid @RequestBody PhoneChangeRequestDTO request,
            @AuthenticationPrincipal Jwt jwt) {
        phoneNumberChangeService.requestPhoneChange(jwt.getClaim("userId"), request.getNewPhoneNumber());
        return ResponseEntity.accepted().body(Map.of("message", "Código OTP enviado al nuevo número."));
    }

    @PostMapping("/me/phone/verify-change")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> verifyPhoneChange(
            @Valid @RequestBody PhoneChangeVerifyDTO request,
            @AuthenticationPrincipal Jwt jwt) {
        phoneNumberChangeService.verifyAndChangePhone(
                jwt.getClaim("userId"), request.getNewPhoneNumber(), request.getOtpCode());
        return ResponseEntity.ok(Map.of("message", "Número telefónico actualizado exitosamente."));
    }

    // Borrar un usuario por publicId
    @DeleteMapping("/delete/public-id/{publicId}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public void deleteById(@PathVariable UUID publicId){
        userService.deleteById(userIdResolver.toInternalId(publicId));
    }
}
