package com.verygana2.services;

import java.time.Clock;
import java.time.ZonedDateTime;

import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.exceptions.InvalidStatusException;
import com.verygana2.models.AccountStatusHistory;
import com.verygana2.models.User;
import com.verygana2.models.enums.AccountStatus;
import com.verygana2.repositories.AccountStatusHistoryRepository;
import com.verygana2.repositories.UserRepository;
import com.verygana2.services.interfaces.AccountStatusService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountStatusServiceImpl implements AccountStatusService {

    private final UserRepository userRepository;
    private final AccountStatusHistoryRepository accountStatusHistoryRepository;
    private final Clock clock;

    @Override
    @Transactional
    public void transition(Long userId, AccountStatus target, String reason, String actor) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ObjectNotFoundException("User not found for userId: " + userId, User.class));

        AccountStatus current = user.getAccountStatus();

        if (!current.canTransitionTo(target)) {
            throw new InvalidStatusException(
                    "Cannot transition user " + userId + " from " + current + " to " + target);
        }

        user.setAccountStatus(target);
        userRepository.save(user);

        accountStatusHistoryRepository.save(AccountStatusHistory.builder()
                .userId(userId)
                .fromStatus(current)
                .toStatus(target)
                .reason(reason)
                .actor(actor)
                .occurredAt(ZonedDateTime.now(clock))
                .build());

        log.info("Account {} transitioned {} -> {} by {} ({})", userId, current, target, actor, reason);
    }
}
