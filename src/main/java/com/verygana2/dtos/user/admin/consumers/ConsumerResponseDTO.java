package com.verygana2.dtos.user.admin.consumers;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

import com.verygana2.dtos.wallet.responses.KeyWalletResponseDTO;
import com.verygana2.models.enums.DocumentType;
import com.verygana2.models.enums.Gender;
import com.verygana2.models.enums.IncomeRange;
import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.UserState;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConsumerResponseDTO {

    private UUID publicId;
    private Role role;
    private String email;
    private String phoneNumber;
    private UserState userState;
    private ZonedDateTime registeredDate;
    private int failedLoginAttempts;
    private Instant accountLockedAt;

    private String userName;
    private KeyWalletResponseDTO keyWallet;
    private Integer adsWatched;
    private boolean hasPet;
    private String referralCode;
    private String avatarUrl;
    private String name;
    private String lastName;
    private String departmentName;
    private String municipalityName;
    private Integer age;
    private Gender gender;
    private ZonedDateTime lastDailyLoginDate;
    private Long referredBy;
    private DocumentType documentType;
    private String documentNumber;
    private String occupation;
    private IncomeRange monthlyIncomeRange;
    private boolean pep;
}
