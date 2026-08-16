package com.verygana2.services.details;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.user.admin.AdminResponseDTO;
import com.verygana2.dtos.user.admin.AdminSummaryResponseDTO;
import com.verygana2.mappers.UserMapper;
import com.verygana2.models.enums.UserState;
import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.models.userDetails.UserDetails;
import com.verygana2.repositories.details.AdminDetailsRepository;
import com.verygana2.repositories.details.UserDetailsRepository;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.interfaces.details.AdminDetailsService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminDetailsServiceImpl implements AdminDetailsService{

    private final AdminDetailsRepository adminDetailsRepository;
    private final UserDetailsRepository userDetailsRepository;
    private final NotificationService notificationService;
    private final UserMapper userMapper;
    
    @Override
    public AdminDetails getById(Long adminId) {
        return adminDetailsRepository.findById(Objects.requireNonNull(adminId)).orElseThrow(() -> new EntityNotFoundException("Admin with id: " + adminId + " not found "));
    }

    @Override
    public boolean existById(Long adminId) {
        return adminDetailsRepository.existsById(Objects.requireNonNull(adminId));
    }

    @Override
    public PagedResponse<AdminSummaryResponseDTO> getAdmins(String search, UserState userState, Pageable pageable) {
        return PagedResponse.from(adminDetailsRepository.findAdmins(search, userState, pageable)).map(userMapper::toAdminSummaryResponseDTO);
    }

    @Override
    public AdminResponseDTO getAdmin(UUID publicId) {
        return userMapper.toAdminResponseDTO(adminDetailsRepository.findByPublicId(publicId).orElseThrow(() -> new EntityNotFoundException("Admin with public id: " + publicId + " not found")));
    }

    @Override
    public void sendNotification(UUID publicId, String message) {
        UserDetails u = userDetailsRepository.findByPublicId(publicId).orElseThrow(() -> new EntityNotFoundException("user with public id: " + publicId + " not found"));
        notificationService.createInternalNotification(u.getId(), "Mensaje de VERyGANA", message, Instant.now());
    }

    @Override
    public void sendNotifications(List<UUID> publicIds, String message) {
        publicIds.forEach(publicId -> sendNotification(publicId, message));
    }
    
}
