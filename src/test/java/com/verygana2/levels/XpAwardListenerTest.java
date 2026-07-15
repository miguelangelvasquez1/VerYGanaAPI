package com.verygana2.levels;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.event.XpAwardListener;
import com.verygana2.event.XpAwardRequestedEvent;
import com.verygana2.models.enums.ActivityType;
import com.verygana2.services.interfaces.levels.LevelService;

@ExtendWith(MockitoExtension.class)
@DisplayName("XpAwardListener")
class XpAwardListenerTest {

    @Mock LevelService levelService;

    @InjectMocks XpAwardListener listener;

    @Test
    @DisplayName("delega en LevelService.awardActivity con los datos del evento")
    void delegatesToLevelService() {
        listener.onXpAwardRequested(
                new XpAwardRequestedEvent(this, 42L, ActivityType.SURVEY_COMPLETED));

        verify(levelService).awardActivity(42L, ActivityType.SURVEY_COMPLETED);
    }

    @Test
    @DisplayName("una excepción del servicio se loguea y NO se propaga")
    void swallowsServiceExceptions() {
        when(levelService.awardActivity(42L, ActivityType.VIDEO_WATCHED))
                .thenThrow(new RuntimeException("DB down"));

        assertThatCode(() -> listener.onXpAwardRequested(
                new XpAwardRequestedEvent(this, 42L, ActivityType.VIDEO_WATCHED)))
                .doesNotThrowAnyException();
    }
}