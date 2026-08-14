package com.verygana2.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Value;

import com.verygana2.dtos.product.responses.CommercialProfileResponseDTO;
import com.verygana2.dtos.user.CommercialRegisterDTO;
import com.verygana2.dtos.user.ComplianceOfficerRegisterDTO;
import com.verygana2.dtos.user.ConsumerRegisterDTO;
import com.verygana2.dtos.user.GameDesignerRegisterDTO;
import com.verygana2.dtos.user.admin.AdminResponseDTO;
import com.verygana2.dtos.user.admin.AdminSummaryResponseDTO;
import com.verygana2.dtos.user.admin.UserSummaryResponseDTO;
import com.verygana2.dtos.user.admin.commercials.CommercialResponseDTO;
import com.verygana2.dtos.user.admin.commercials.CommercialSummaryResponseDTO;
import com.verygana2.dtos.user.admin.complianceOfficers.ComplianceOfficerResponseDTO;
import com.verygana2.dtos.user.admin.complianceOfficers.ComplianceOfficerSummaryResponseDTO;
import com.verygana2.dtos.user.admin.consumers.ConsumerResponseDTO;
import com.verygana2.dtos.user.admin.consumers.ConsumerSummaryResponseDTO;
import com.verygana2.dtos.user.admin.gameDesigners.GameDesignerResponseDTO;
import com.verygana2.dtos.user.admin.gameDesigners.GameDesignerSummaryResponseDTO;
import com.verygana2.dtos.user.commercial.CommercialInitialDataResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.PlanSummaryResponseDTO;
import com.verygana2.dtos.user.consumer.requests.ConsumerUpdateProfileRequestDTO;
import com.verygana2.dtos.user.consumer.responses.ConsumerInitialDataResponseDTO;
import com.verygana2.dtos.user.consumer.responses.ConsumerProfileResponseDTO;
import com.verygana2.models.User;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ComplianceOfficerDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.models.userDetails.GameDesignerDetails;

@Mapper(componentModel = "spring")
public abstract class UserMapper {

    @Value("${financial.key-value-cents:1000}")
    protected long keyValueCents;

    @Named("centsToKeys")
    protected Long centsToKeys(Long cents) {
        if (cents == null) return 0L;
        return cents / keyValueCents;
    }

    // ---- CONSUMER ----
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "accountLockedAt", ignore = true)
    @Mapping(target = "failedLoginAttempts", ignore = true)
    @Mapping(target = "role", constant = "CONSUMER")
    @Mapping(target = "userDetails", ignore = true)
    @Mapping(target = "userState", constant = "PENDING_EMAIL")
    @Mapping(target = "registeredDate", expression = "java(java.time.ZonedDateTime.now())")
    @Mapping(target = "verification", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "passwordConfigured", ignore = true)
    public abstract User toUser(ConsumerRegisterDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "adsWatched", ignore = true)
    @Mapping(target = "dailyAdCount", ignore = true)
    @Mapping(target = "referralCode", ignore = true)
    @Mapping(target = "favoriteProducts", ignore = true)
    @Mapping(target = "raffleTickets", ignore = true)
    @Mapping(target = "age", ignore = true)
    @Mapping(target = "gender", ignore = true)
    @Mapping(target = "userHash", ignore = true)
    @Mapping(target = "hasPet", ignore = true)
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "userName", ignore = true)
    @Mapping(target = "notifications", ignore = true)
    @Mapping(target = "municipality", ignore = true)
    @Mapping(target = "referrals", ignore = true)
    @Mapping(target = "referredBy", ignore = true)
    @Mapping(target = "keyWallet", ignore = true)
    @Mapping(target = "departmentName", ignore = true)
    @Mapping(target = "municipalityName", ignore = true)
    @Mapping(target = "lastDailyLoginDate", ignore = true)
    @Mapping(target = "monthlyIncomeRange", ignore = true)
    @Mapping(target = "occupation", ignore = true)
    @Mapping(target = "pep", ignore = true)
    public abstract ConsumerDetails toConsumerDetails(ConsumerRegisterDTO dto);

    @Mapping(target = "publicId", source = "user.publicId")
    @Mapping(target = "role", source = "user.role")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "userState", source = "user.userState")
    public abstract ConsumerSummaryResponseDTO toConsumerSummaryResponseDTO (ConsumerDetails consumerDetails);

    @Mapping(target = "publicId", source = "user.publicId")
    @Mapping(target = "role", source = "user.role")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "userState", source = "user.userState")
    @Mapping(target = "phoneNumber", source = "user.phoneNumber")
    @Mapping(target = "registeredDate", source = "user.registeredDate")
    @Mapping(target = "failedLoginAttempts", source = "user.failedLoginAttempts")
    @Mapping(target = "accountLockedAt", source = "user.accountLockedAt")
    @Mapping(target = "avatarUrl", source = "avatar.imageUrl")
    @Mapping(target = "referredBy", source = "referredBy.user.id")
    public abstract ConsumerResponseDTO toConsumerResponseDTO (ConsumerDetails consumerDetails);

    @Mapping(target = "totalAvailableKeys", source = "keyWallet.availableKeysCents", qualifiedByName = "centsToKeys")
    @Mapping(target = "purchaseKeys", source = "keyWallet.purchaseKeysCents", qualifiedByName = "centsToKeys")
    @Mapping(target = "connectivityKeys", source = "keyWallet.connectivityKeysCents", qualifiedByName = "centsToKeys")
    @Mapping(target = "blockedPurchaseKeys", source = "keyWallet.blockedPurchaseKeysCents", qualifiedByName = "centsToKeys")
    @Mapping(target = "blockedConnectivityKeys", source = "keyWallet.blockedConnectivityKeysCents", qualifiedByName = "centsToKeys")
    @Mapping(target = "avatarUrl", source = "avatar.imageUrl")
    public abstract ConsumerInitialDataResponseDTO toConsumerInitialDataResponseDTO(ConsumerDetails consumer);

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "email", source = "consumer.user.email")
    @Mapping(target = "phoneNumber", source = "consumer.user.phoneNumber")
    @Mapping(target = "role", source = "consumer.user.role")
    @Mapping(target = "userState", source = "consumer.user.userState")
    @Mapping(target = "department", ignore = true)
    public abstract ConsumerProfileResponseDTO toConsumerProfileResponseDTO(ConsumerDetails consumer);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "lastName", ignore = true)
    @Mapping(target = "adsWatched", ignore = true)
    @Mapping(target = "dailyAdCount", ignore = true)
    @Mapping(target = "referralCode", ignore = true)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "favoriteProducts", ignore = true)
    @Mapping(target = "raffleTickets", ignore = true)
    @Mapping(target = "age", ignore = true)
    @Mapping(target = "gender", ignore = true)
    @Mapping(target = "userHash", ignore = true)
    @Mapping(target = "hasPet", ignore = true)
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "userName", ignore = true)
    @Mapping(target = "notifications", ignore = true)
    @Mapping(target = "user.email", source = "email")
    @Mapping(target = "user.phoneNumber", source = "phoneNumber")
    @Mapping(target = "municipality", ignore = true)
    @Mapping(target = "referredBy", ignore = true)
    @Mapping(target = "referrals", ignore = true)
    @Mapping(target = "keyWallet", ignore = true)
    @Mapping(target = "departmentName", ignore = true)
    @Mapping(target = "lastDailyLoginDate", ignore = true)
    @Mapping(target = "documentType", ignore = true)
    @Mapping(target = "documentNumber", ignore = true)
    @Mapping(target = "occupation", ignore = true)
    @Mapping(target = "monthlyIncomeRange", ignore = true)
    @Mapping(target = "pep", ignore = true)
    public abstract void updateConsumerFromDto(ConsumerUpdateProfileRequestDTO dto, @MappingTarget ConsumerDetails entity);

    // ---- COMMERCIAL ----
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", constant = "COMMERCIAL")
    @Mapping(target = "userDetails", ignore = true)
    @Mapping(target = "userState", constant = "PENDING_EMAIL")
    @Mapping(target = "registeredDate", expression = "java(java.time.ZonedDateTime.now())")
    @Mapping(target = "verification", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "passwordConfigured", ignore = true)
    @Mapping(target = "accountLockedAt", ignore = true)
    @Mapping(target = "failedLoginAttempts", ignore = true)
    public abstract User toUser(CommercialRegisterDTO dto);

    // El registro básico (paso 1) solo trae email/password/phoneNumber:
    // toda la identificación jurídica (companyName, nit, ciiuCode, municipio, etc.) se completa
    // después en el paso 3 (submitLegalIdentification), por eso se ignora aquí.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "notifications", ignore = true)
    @Mapping(target = "payoutMethods", ignore = true)
    @Mapping(target = "defaultPayoutMethod", ignore = true)
    @Mapping(target = "wallet", ignore = true)
    @Mapping(target = "currentPlan", ignore = true)
    @Mapping(target = "subscriptions", ignore = true)
    @Mapping(target = "municipality", ignore = true)
    @Mapping(target = "municipalityName", ignore = true)
    @Mapping(target = "departmentName", ignore = true)
    @Mapping(target = "companyName", ignore = true)
    @Mapping(target = "nit", ignore = true)
    @Mapping(target = "ciiuCode", ignore = true)
    @Mapping(target = "mercantileRegistration", ignore = true)
    @Mapping(target = "legalRepDocType", ignore = true)
    @Mapping(target = "legalRepDocNumber", ignore = true)
    @Mapping(target = "pep", ignore = true)
    @Mapping(target = "annualIncomeRange", ignore = true)
    @Mapping(target = "onboarding", ignore = true)
    public abstract CommercialDetails toCommercialDetails(CommercialRegisterDTO dto);

    @Mapping(target = "email", source = "details.user.email")
    @Mapping(target = "onboardingStatus", source = "details.onboarding.currentStep")
    public abstract CommercialInitialDataResponseDTO toCommercialInitialDataResponseDTO(CommercialDetails details);

    @Mapping(target = "registeredDate", source = "commercial.user.registeredDate")
    @Mapping(target = "averageRate", ignore = true)
    @Mapping(target = "reviewCount", ignore = true)
    @Mapping(target = "productCategories", ignore = true)
    @Mapping(target = "totalActiveProducts", ignore = true)
    @Mapping(target = "activeProducts", ignore = true)
    public abstract CommercialProfileResponseDTO toCommercialProfileResponseDTO(CommercialDetails commercial);

    @Mapping(target = "publicId", source = "user.publicId")
    @Mapping(target = "role", source = "user.role")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "userState", source = "user.userState")
    public abstract CommercialSummaryResponseDTO toCommercialSummaryResponseDTO (CommercialDetails commercial);

    @Mapping(target = "planCode", source = "code")
    @Mapping(target = "planName", source = "name")
    @Mapping(target = "monthlyFeeCents", source = "monthlyPriceCents")
    @Mapping(target = "investmentAmountCents", ignore = true)
    @Mapping(target = "contractDurationMonths", ignore = true)
    @Mapping(target = "requiresSpecialNegotiation", ignore = true)
    @Mapping(target = "specialNegotiationResolvedAt", ignore = true)
    @Mapping(target = "specialNegotiationDetails", ignore = true)
    @Mapping(target = "accepted", ignore = true)
    @Mapping(target = "acceptedAt", ignore = true)
    public abstract PlanSummaryResponseDTO toPlanSummaryResponseDTO (Plan plan);

    @Mapping(target = "publicId", source = "user.publicId")
    @Mapping(target = "role", source = "user.role")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "userState", source = "user.userState")
    @Mapping(target = "phoneNumber", source = "user.phoneNumber")
    @Mapping(target = "registeredDate", source = "user.registeredDate")
    @Mapping(target = "failedLoginAttempts", source = "user.failedLoginAttempts")
    @Mapping(target = "accountLockedAt", source = "user.accountLockedAt")
    public abstract CommercialResponseDTO toCommercialResponseDTO (CommercialDetails commercial);

    // ---- GAME DESIGNER ----
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", constant = "GAME_DESIGNER")
    @Mapping(target = "userDetails", ignore = true)
    @Mapping(target = "userState", constant = "PENDING_EMAIL")
    @Mapping(target = "registeredDate", expression = "java(java.time.ZonedDateTime.now())")
    @Mapping(target = "verification", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "passwordConfigured", ignore = true)
    @Mapping(target = "accountLockedAt", ignore = true)
    @Mapping(target = "failedLoginAttempts", ignore = true)
    public abstract User toUser(GameDesignerRegisterDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "notifications", ignore = true)
    @Mapping(target = "designerCode", ignore = true)
    @Mapping(target = "campaignsDesigned", ignore = true)
    @Mapping(target = "joinedAt", ignore = true)
    @Mapping(target = "active", expression = "java(true)")
    public abstract GameDesignerDetails toGameDesignerDetails(GameDesignerRegisterDTO dto);

    @Mapping(target = "publicId", source = "user.publicId")
    @Mapping(target = "role", source = "user.role")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "userState", source = "user.userState")
    public abstract GameDesignerSummaryResponseDTO toGameDesignerSummaryResponseDTO (GameDesignerDetails gameDesigner);

    @Mapping(target = "publicId", source = "user.publicId")
    @Mapping(target = "role", source = "user.role")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "userState", source = "user.userState")
    @Mapping(target = "phoneNumber", source = "user.phoneNumber")
    @Mapping(target = "registeredDate", source = "user.registeredDate")
    @Mapping(target = "failedLoginAttempts", source = "user.failedLoginAttempts")
    @Mapping(target = "accountLockedAt", source = "user.accountLockedAt")
    public abstract GameDesignerResponseDTO toGameDesignerResponseDTO (GameDesignerDetails gameDesigner);

    // ---- COMPLIANCE OFFICER ----
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", constant = "COMPLIANCE_OFFICER")
    @Mapping(target = "userDetails", ignore = true)
    @Mapping(target = "userState", constant = "ACTIVE")
    @Mapping(target = "registeredDate", expression = "java(java.time.ZonedDateTime.now())")
    @Mapping(target = "verification", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "passwordConfigured", ignore = true)
    @Mapping(target = "accountLockedAt", ignore = true)
    @Mapping(target = "failedLoginAttempts", ignore = true)
    public abstract User toUser(ComplianceOfficerRegisterDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "notifications", ignore = true)
    @Mapping(target = "badgeNumber", ignore = true)
    public abstract ComplianceOfficerDetails toComplianceOfficerDetails(ComplianceOfficerRegisterDTO dto);

    @Mapping(target = "publicId", source = "user.publicId")
    @Mapping(target = "role", source = "user.role")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "userState", source = "user.userState")
    public abstract ComplianceOfficerSummaryResponseDTO toComplianceOfficerSummaryResponseDTO (ComplianceOfficerDetails complianceOfficer);

    @Mapping(target = "publicId", source = "user.publicId")
    @Mapping(target = "role", source = "user.role")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "userState", source = "user.userState")
    @Mapping(target = "phoneNumber", source = "user.phoneNumber")
    @Mapping(target = "registeredDate", source = "user.registeredDate")
    @Mapping(target = "failedLoginAttempts", source = "user.failedLoginAttempts")
    @Mapping(target = "accountLockedAt", source = "user.accountLockedAt")
    public abstract ComplianceOfficerResponseDTO toComplianceOfficerResponseDTO (ComplianceOfficerDetails complianceOfficer);

    // ---- ADMIN ----
    @Mapping(target = "publicId", source = "user.publicId")
    @Mapping(target = "role", source = "user.role")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "userState", source = "user.userState")
    public abstract AdminSummaryResponseDTO toAdminSummaryResponseDTO (AdminDetails adminDetails);

    @Mapping(target = "publicId", source = "user.publicId")
    @Mapping(target = "role", source = "user.role")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "userState", source = "user.userState")
    @Mapping(target = "phoneNumber", source = "user.phoneNumber")
    @Mapping(target = "registeredDate", source = "user.registeredDate")
    @Mapping(target = "failedLoginAttempts", source = "user.failedLoginAttempts")
    @Mapping(target = "accountLockedAt", source = "user.accountLockedAt")
    public abstract AdminResponseDTO toAdminResponseDTO (AdminDetails adminDetails);

    // ---- USER (genérico, sin subtipo) ----
    public abstract UserSummaryResponseDTO toUserSummaryResponseDTO (User user);
}
