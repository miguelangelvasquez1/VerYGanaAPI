package com.verygana2.scheduled.compliance;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.verygana2.exceptions.compliance.ScreeningHitException;
import com.verygana2.models.User;
import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.UserState;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.UserRepository;
import com.verygana2.services.interfaces.compliance.ScreeningService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PeriodicRescreeningJob {

    private final UserRepository userRepository;
    private final ScreeningService screeningService;

    // Primer día de cada mes a las 2:00 AM
    @Scheduled(cron = "0 0 2 1 * ?")
    public void runMonthlyRescreening() {
        log.info("Iniciando re-screening mensual de usuarios activos");

        List<User> usersToScreen = userRepository.findByUserStateAndRoleIn(
                UserState.ACTIVE, List.of(Role.CONSUMER, Role.COMMERCIAL));

        int total = usersToScreen.size();
        int hits = 0;
        int errors = 0;

        for (User user : usersToScreen) {
            try {
                screenUser(user);
            } catch (ScreeningHitException e) {
                hits++;
                log.warn("Re-screening HIT para userId={}: {} — cuenta bloqueada", user.getId(), e.getMessage());
                user.setUserState(UserState.BLOCKED);
                userRepository.save(user);
            } catch (Exception e) {
                errors++;
                log.error("Error en re-screening para userId={}: {}", user.getId(), e.getMessage());
            }
        }

        log.info("Re-screening mensual completado: {} usuarios procesados, {} HITs, {} errores", total, hits, errors);
    }

    private void screenUser(User user) {
        if (user.getRole() == Role.CONSUMER && user.getUserDetails() instanceof ConsumerDetails d) {
            screeningService.screenOrThrow(
                    user.getId(),
                    d.getName() + " " + d.getLastName(),
                    d.getDocumentNumber());
        } else if (user.getRole() == Role.COMMERCIAL && user.getUserDetails() instanceof CommercialDetails d) {
            screeningService.screenOrThrow(user.getId(), d.getCompanyName(), d.getNit());
            screeningService.screenOrThrow(
                    user.getId(),
                    d.getRepresentanteDocNumero(),
                    d.getRepresentanteDocNumero());
        }
    }
}