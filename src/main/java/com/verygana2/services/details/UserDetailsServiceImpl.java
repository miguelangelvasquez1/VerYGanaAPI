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
import com.verygana2.models.User;
import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.UserState;
import com.verygana2.models.userDetails.UserDetails;
import com.verygana2.repositories.UserRepository;
import com.verygana2.repositories.details.UserDetailsRepository;
import com.verygana2.services.interfaces.NotificationService;
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

    @Override
    public UserDetails getUserById(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User id must be positive");
        }

        return userDetailsRepository.findById(userId).orElseThrow(() -> new ObjectNotFoundException("User with id: " + userId + " not found " , UserDetails.class));
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
    public void blockUser(UUID publicId, String reason) {
        UserDetails user = userDetailsRepository.findByPublicId(publicId).orElseThrow(() -> new EntityNotFoundException("User with public id: " + publicId + " not found"));

        if (user.getUser().getUserState().equals(UserState.BLOCKED)) {
            throw new InvalidRequestException("You cannot block user who is already blocked");
        }

        user.getUser().setUserState(UserState.BLOCKED);
        notificationService.createInternalNotification(user.getId(), "Cuenta bloqueada", reason, Instant.now());

        userDetailsRepository.save(user);
    }

    @Override
    public void unblockUser(UUID publicId, String reason) {
        UserDetails user = userDetailsRepository.findByPublicId(publicId).orElseThrow(() -> new EntityNotFoundException("User with public id: " + publicId + " not found"));

        if (user.getUser().getUserState().equals(UserState.ACTIVE)) {
            throw new InvalidRequestException("You cannot unblock user who is already active");
        }

        user.getUser().setUserState(UserState.ACTIVE);
        notificationService.createInternalNotification(user.getId(), "Cuenta desbloqueada", reason, Instant.now());

        userDetailsRepository.save(user);
    }

    @Override
    public EntityUpdatedResponseDTO editBasicInfo(UUID publicId, EditBasicInfoRequestDTO request) {
        UserDetails user = userDetailsRepository.findByPublicId(publicId).orElseThrow(() -> new EntityNotFoundException("User with public id: " + publicId + " not found"));
        User u = user.getUser();

        u.setEmail(request.getEmail());
        u.setPhoneNumber(request.getPhoneNumber());

        userDetailsRepository.save(user);

        return new EntityUpdatedResponseDTO(user.getId(), "Basic info updated", Instant.now());
    }


}
