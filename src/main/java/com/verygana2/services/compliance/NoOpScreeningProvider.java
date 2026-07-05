package com.verygana2.services.compliance;

import java.util.Arrays;
import java.util.List;

import com.verygana2.services.interfaces.compliance.ScreeningPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.verygana2.models.enums.ScreeningList;
import com.verygana2.models.enums.ScreeningStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * No-op implementation active when screening.provider=noop (development environments).
 * In production, replace with TruoraScreeningProvider or another adapter.
 */
@Component
@ConditionalOnProperty(name = "screening.provider", havingValue = "noop", matchIfMissing = true)
@Slf4j
public class NoOpScreeningProvider implements ScreeningPort {

    @Override
    public List<ScreeningOutcome> screen(String name, String queriedDocument) {
        log.debug("NoOp screening for '{}' — returning NO_HIT on all lists", name);
        return Arrays.stream(ScreeningList.values())
                .map(list -> new ScreeningOutcome(list, ScreeningStatus.NO_HIT, null, null))
                .toList();
    }
}