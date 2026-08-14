package com.verygana2.controllers.admin;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.user.admin.AdminResponseDTO;
import com.verygana2.dtos.user.admin.AdminSummaryResponseDTO;
import com.verygana2.dtos.user.admin.EditBasicInfoRequestDTO;
import com.verygana2.dtos.user.admin.SendNotificationsRequestDTO;
import com.verygana2.dtos.user.admin.UserSummaryResponseDTO;
import com.verygana2.dtos.user.admin.commercials.CommercialResponseDTO;
import com.verygana2.dtos.user.admin.commercials.CommercialSummaryResponseDTO;
import com.verygana2.dtos.user.admin.complianceOfficers.ComplianceOfficerResponseDTO;
import com.verygana2.dtos.user.admin.complianceOfficers.ComplianceOfficerSummaryResponseDTO;
import com.verygana2.dtos.user.admin.consumers.ConsumerResponseDTO;
import com.verygana2.dtos.user.admin.consumers.ConsumerSummaryResponseDTO;
import com.verygana2.dtos.user.admin.gameDesigners.GameDesignerResponseDTO;
import com.verygana2.dtos.user.admin.gameDesigners.GameDesignerSummaryResponseDTO;
import com.verygana2.models.enums.Gender;
import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.UserLevel;
import com.verygana2.models.enums.UserState;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.services.interfaces.details.AdminDetailsService;
import com.verygana2.services.interfaces.details.CommercialDetailsService;
import com.verygana2.services.interfaces.details.ComplianceOfficerDetailsService;
import com.verygana2.services.interfaces.details.ConsumerDetailsService;
import com.verygana2.services.interfaces.details.GameDesignerDetailsService;
import com.verygana2.services.interfaces.details.UserDetailsService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");

    private final UserDetailsService userDetailsService;
    private final ConsumerDetailsService consumerDetailsService;
    private final AdminDetailsService adminDetailsService;
    private final CommercialDetailsService commercialDetailsService;
    private final GameDesignerDetailsService gameDesignerDetailsService;
    private final ComplianceOfficerDetailsService complianceOfficerDetailsService;

    @GetMapping("/stats")
    public ResponseEntity<Integer> countActiveUsersByRole (@RequestParam(required = true) Role role){
        return ResponseEntity.ok(userDetailsService.countActiveUsersByRole(role));
    }

    @GetMapping("/news")
    public ResponseEntity<PagedResponse<UserSummaryResponseDTO>> getNewUsers (@RequestParam LocalDate startDate, @RequestParam LocalDate endDate, @RequestParam(required = false) String search, Pageable pageable){

        ZonedDateTime start = ZonedDateTime.of(startDate, LocalTime.of(0, 0, 0), BOGOTA_ZONE);
        ZonedDateTime end = ZonedDateTime.of(endDate.plusDays(1), LocalTime.of(0, 0, 0), BOGOTA_ZONE);

        return ResponseEntity.ok(userDetailsService.getNewUsers(start, end, search, pageable));
    }

    @GetMapping("/consumers")
    public ResponseEntity<PagedResponse<ConsumerSummaryResponseDTO>> getConsumers (@RequestParam (required = false) UserLevel level, @RequestParam (required = false) String search, @RequestParam (required = false) UserState userState,
        @RequestParam (required = false) Integer maxAge, @RequestParam (required = false) Integer minAge, @RequestParam (required = false) Gender gender, @RequestParam (required = false) String departmentCode, @RequestParam (required = false) String municipalityCode,
        @RequestParam (required = false) LocalDate startDate, @RequestParam (required = false) LocalDate endDate, Pageable pageable){

            ZonedDateTime start = startDate == null ? null : ZonedDateTime.of(startDate, LocalTime.of(0, 0, 0), BOGOTA_ZONE);
            ZonedDateTime end = endDate == null ? null : ZonedDateTime.of(endDate.plusDays(1), LocalTime.of(0, 0, 0), BOGOTA_ZONE);

        return ResponseEntity.ok(consumerDetailsService.getConsumers(level, search, userState, maxAge, minAge, gender, departmentCode, municipalityCode, start, end, pageable));

    }

    @GetMapping("/consumers/{publicId}")
    public ResponseEntity<ConsumerResponseDTO> getConsumer (@PathVariable UUID publicId){
        return ResponseEntity.ok(consumerDetailsService.getConsumer(publicId));
    }

    @GetMapping("/admins")
    public ResponseEntity<PagedResponse<AdminSummaryResponseDTO>> getAdmins (@RequestParam(required = false) String search, @RequestParam(required = false) UserState userState, Pageable pageable){
        return ResponseEntity.ok(adminDetailsService.getAdmins(search, userState, pageable));
    }

    @GetMapping("/admins/{publicId}")
    public ResponseEntity<AdminResponseDTO> getAdmin (@PathVariable UUID publicId){
        return ResponseEntity.ok(adminDetailsService.getAdmin(publicId));
    }

    @GetMapping("/commercials")
    public ResponseEntity<PagedResponse<CommercialSummaryResponseDTO>> getCommercials (@RequestParam(required = false) String search, @RequestParam(required = false) UserState userState,
    @RequestParam(required = false) PlanCode currentPlan, Pageable pageable) {

        return ResponseEntity.ok(commercialDetailsService.getCommercials(search, userState, currentPlan, pageable));
    }

    @GetMapping("/commercials/{publicId}")
    public ResponseEntity<CommercialResponseDTO> getCommercial(@PathVariable UUID publicId) {
        return ResponseEntity.ok(commercialDetailsService.getCommercial(publicId));
    }

    @GetMapping("/game-designers")
    public ResponseEntity<PagedResponse<GameDesignerSummaryResponseDTO>> getGameDesigners (@RequestParam(required = false) String search, @RequestParam(required = false) UserState userState, Pageable pageable) {
        return ResponseEntity.ok(gameDesignerDetailsService.getGameDesigners(search, userState, pageable));
    }

    @GetMapping("/game-designers/{publicId}")
    public ResponseEntity<GameDesignerResponseDTO> getGameDesigner (@PathVariable UUID publicId) {
        return ResponseEntity.ok(gameDesignerDetailsService.getGameDesigner(publicId));
    }

    @GetMapping("/compliance-officers")
    public ResponseEntity<PagedResponse<ComplianceOfficerSummaryResponseDTO>> getComplianceOfficers(@RequestParam(required = false) String search, @RequestParam(required = false) UserState userState, Pageable pageable) {
        return ResponseEntity.ok(complianceOfficerDetailsService.getComplianceOfficers(search, userState, pageable));
    }

    @GetMapping("/compliance-officers/{publicId}")
    public ResponseEntity<ComplianceOfficerResponseDTO> getComplianceOfficer (@PathVariable UUID publicId) {
        return ResponseEntity.ok(complianceOfficerDetailsService.getComplianceOfficer(publicId));
    }

    @PatchMapping("/{publicId}/block")
    public ResponseEntity<Void> blockUser (@PathVariable UUID publicId, @RequestParam String reason){
        userDetailsService.blockUser(publicId, reason);
        return ResponseEntity.noContent().build();
    } 

    @PatchMapping("/{publicId}/unblock")
    public ResponseEntity<Void> unblockUser (@PathVariable UUID publicId, @RequestParam String reason){
        userDetailsService.unblockUser(publicId, reason);
        return ResponseEntity.noContent().build();
    } 

    @PutMapping("/{publicId}")
    public ResponseEntity<EntityUpdatedResponseDTO> editBasicInfo (@PathVariable UUID publicId, @Valid @RequestBody EditBasicInfoRequestDTO request){
        return ResponseEntity.ok(userDetailsService.editBasicInfo(publicId, request));
    }

    @PostMapping("/{publicId}/send-notification")
    public ResponseEntity<Void> sendNotification (@PathVariable UUID publicId, @RequestParam String message) {
        adminDetailsService.sendNotification(publicId, message);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/send-notifications")
    public ResponseEntity<Void> sendNotifications (@Valid @RequestBody SendNotificationsRequestDTO request) {
        adminDetailsService.sendNotifications(request.getPublicIds(), request.getMessage());
        return ResponseEntity.noContent().build();
    }
}
