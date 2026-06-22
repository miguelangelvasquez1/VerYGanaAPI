package com.verygana2.services;

import com.verygana2.models.Avatar;
import com.verygana2.models.Municipality;
import com.verygana2.services.interfaces.*;
import com.verygana2.services.interfaces.levels.LevelService;
import org.hibernate.ObjectNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.user.CommercialRegisterDTO;
import com.verygana2.dtos.user.ConsumerRegisterDTO;
import com.verygana2.dtos.user.GameDesignerRegisterDTO;
import com.verygana2.mappers.UserMapper;
import com.verygana2.models.User;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.models.userDetails.GameDesignerDetails;
import com.verygana2.repositories.UserRepository;
import com.verygana2.services.interfaces.finance.KeyWalletService;
import com.verygana2.utils.generators.UserHashGenerator;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.Period;

@Slf4j
@Transactional
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final ReferralService referralService;
    private final AvatarService avatarService;
    private final UserHashGenerator userHashGenerator;
    private final UserRepository userRepository;
    private final KeyWalletService keyWalletService;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final OutboxService outboxService;
    private final LocationService locationService;
    private final LevelService levelService;

    @Override
    public User registerGameDesigner(GameDesignerRegisterDTO dto) {
        validateEmailAndPhoneNumber(dto.getEmail(), dto.getPhoneNumber());

        User user = userMapper.toUser(dto);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));

        GameDesignerDetails details = userMapper.toGameDesignerDetails(dto);
        details.setDesignerCode("GD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        details.setJoinedAt(LocalDate.now());
        details.setUser(user);
        user.setUserDetails(details);

        return userRepository.save(user);
    }

    public User registerCommercial(CommercialRegisterDTO dto) {
        validateEmailAndPhoneNumber(dto.getEmail(), dto.getPhoneNumber());

        User user = userMapper.toUser(dto);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));

        CommercialDetails details = userMapper.toCommercialDetails(dto);
        details.setUser(user);
        user.setUserDetails(details);

        User savedUser = userRepository.save(user);

        return savedUser;
    }

    @Override
    public User registerConsumer(ConsumerRegisterDTO dto) {
        validateEmailAndPhoneNumber(dto.getEmail(), dto.getPhoneNumber());

        User user = userMapper.toUser(dto);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));

        ConsumerDetails details = userMapper.toConsumerDetails(dto);
        details.setUser(user);
        user.setUserDetails(details);

        // === ASIGNACIÓN DEL MUNICIPIO ===
        if (dto.getMunicipalityCode() != null) {
            Municipality municipality = locationService.getMunicipalityEntityByCode(dto.getMunicipalityCode());

            details.setMunicipality(municipality);
            details.setMunicipalityName(municipality.getName());   // por redundancia y consultas rápidas
            details.setDepartmentName(municipality.getDepartment().getName()); // por redundancia y consultas rápidas
        }

        Avatar avatar = avatarService.getActiveAvatarOrThrow(dto.getAvatarId());
        details.setAvatar(avatar);
        details.setUserName(normalizeUsername(dto.getUserName()));
        details.setGender(dto.getGender());
        details.setAge(calculateAge(dto.getBirthDate()));

        referralService.prepareNewConsumer(user, details, dto.getReferredByCode());

        // userHash NOT NULL+UNIQUE requiere un valor único antes del INSERT (IDENTITY);
        // se reemplaza con el hash real tras obtener el ID generado por la BD.
        details.setUserHash(UUID.randomUUID().toString());
        userRepository.saveAndFlush(user);
        details.setUserHash(userHashGenerator.generate(user.getId()));

        keyWalletService.createFor(user.getId());
        levelService.initializeProfile(user.getId());

        if (details.getReferredBy() != null) {
            outboxService.saveReferralEvent(
                    details.getReferredBy().getId(), // referrerId → quien refirió, recibe el ticket
                    details.getId()                  // referralId → el nuevo usuario, sourceId del ticket
            );
        }
        return user;
    }

    private String normalizeUsername(String u) {
        return u == null ? null : u.trim();
    }
    private int calculateAge(LocalDate birthDate) {
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    private void validateEmailAndPhoneNumber(String email, String phoneNumber) {

        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException("Email already registered");
        }

        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new IllegalStateException("Phone number already registered");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("UserId cannot be null or minor than 1");
        }
        return userRepository.findById(id)
                .orElseThrow(() -> new ObjectNotFoundException("User not found for userId: " + id, User.class));
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ObjectNotFoundException("User", User.class));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email");
        }
        return userRepository.existsByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean phoneExists(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank() || phoneNumber.length() != 10) {
            throw new IllegalArgumentException("Invalid phone number");
        }
        return userRepository.existsByPhoneNumber(phoneNumber);
    }

    @Override
    public void deleteById(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("UserId cannot be null or minor than 1");
        }
        userRepository.deleteById(id);
    }
}
