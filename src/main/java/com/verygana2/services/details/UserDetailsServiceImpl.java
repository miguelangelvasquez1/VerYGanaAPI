package com.verygana2.services.details;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.ObjectNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.user.admin.EditBasicInfoRequestDTO;
import com.verygana2.dtos.user.admin.UserSummaryResponseDTO;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.mappers.UserMapper;
import com.verygana2.models.AccountStatusHistory;
import com.verygana2.models.User;
import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.AccountStatus;
import com.verygana2.models.userDetails.UserDetails;
import com.verygana2.repositories.AccountStatusHistoryRepository;
import com.verygana2.repositories.UserRepository;
import com.verygana2.repositories.details.UserDetailsRepository;
import com.verygana2.services.interfaces.AccountStatusService;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.interfaces.PhoneNumberChangeService;
import com.verygana2.services.interfaces.details.UserDetailsService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService{

    private final UserDetailsRepository userDetailsRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final UserMapper userMapper;
    private final AccountStatusService accountStatusService;
    private final AccountStatusHistoryRepository accountStatusHistoryRepository;
    private final PhoneNumberChangeService phoneNumberChangeService;

    @Override
    public UserDetails getUserById(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User id must be positive");
        }

        return userDetailsRepository.findById(userId).orElseThrow(() -> new ObjectNotFoundException("User with id: " + userId + " not found " , UserDetails.class));
    }

    @Override
    public UserDetails getUserByPublicId(UUID publicId) {
        return userDetailsRepository.findByPublicId(publicId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User with public id: " + publicId + " not found"));
    }

    @Override
    public Integer countActiveUsersByRole(Role role) {
        return userDetailsRepository.countActiveUsersByRole(Objects.requireNonNull(role, "Role cannot be null"));
    }

    @Override
    public PagedResponse<UserSummaryResponseDTO> getNewUsers(ZonedDateTime startDate, ZonedDateTime endDate, String search, Pageable pageable) {
        return PagedResponse.from(userRepository.findNewUsers(startDate, endDate, search, pageable)).map(userMapper::toUserSummaryResponseDTO);
    }

    @Override
    public void blockUser(UUID publicId, String reason, String actor) {
        UserDetails user = userDetailsRepository.findByPublicId(publicId).orElseThrow(() -> new EntityNotFoundException("User with public id: " + publicId + " not found"));

        if (user.getUser().getAccountStatus().equals(AccountStatus.SUSPENDED)) {
            throw new InvalidRequestException("You cannot block user who is already blocked");
        }

        accountStatusService.transition(user.getId(), AccountStatus.SUSPENDED, reason, actor);
        notificationService.createInternalNotification(user.getId(), "Cuenta bloqueada", reason, Instant.now());
    }

    @Override
    public void unblockUser(UUID publicId, String reason, String actor) {
        UserDetails user = userDetailsRepository.findByPublicId(publicId).orElseThrow(() -> new EntityNotFoundException("User with public id: " + publicId + " not found"));

        if (!user.getUser().getAccountStatus().equals(AccountStatus.SUSPENDED)) {
            throw new InvalidRequestException("You can only unblock an account that is currently blocked");
        }

        // Vuelve al estado en el que estaba justo antes de la última suspensión
        // (ej. PENDING_ACTIVATION si el KYC fue rechazado), no siempre a ACTIVE.
        // Cuentas suspendidas antes de que existiera este historial caen al ACTIVE de siempre.
        AccountStatus target = accountStatusHistoryRepository
                .findFirstByUserIdAndToStatusOrderByOccurredAtDesc(user.getId(), AccountStatus.SUSPENDED)
                .map(AccountStatusHistory::getFromStatus)
                .orElse(AccountStatus.ACTIVE);

        accountStatusService.transition(user.getId(), target, reason, actor);
        notificationService.createInternalNotification(user.getId(), "Cuenta desbloqueada", reason, Instant.now());
    }

    @Override
    public EntityUpdatedResponseDTO editBasicInfo(UUID publicId, EditBasicInfoRequestDTO request) {
        UserDetails user = userDetailsRepository.findByPublicId(publicId).orElseThrow(() -> new EntityNotFoundException("User with public id: " + publicId + " not found"));
        User u = user.getUser();

        if (!u.getPhoneNumber().equals(request.getPhoneNumber())) {
            phoneNumberChangeService.adminChangePhone(u.getId(), request.getPhoneNumber());
        }

        u.setEmail(request.getEmail());

        userDetailsRepository.save(user);

        return new EntityUpdatedResponseDTO(user.getId(), "Basic info updated", Instant.now());
    }


}
