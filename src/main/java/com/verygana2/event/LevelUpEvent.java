package com.verygana2.event;


import org.springframework.context.ApplicationEvent;
import com.verygana2.models.enums.UserLevel;
import lombok.Getter;

@Getter
public class LevelUpEvent extends ApplicationEvent {

    private final Long consumerId;
    private final UserLevel previousLevel;
    private final UserLevel newLevel;

    public LevelUpEvent(Object source, Long consumerId,
                        UserLevel previousLevel, UserLevel newLevel) {
        super(source);
        this.consumerId = consumerId;
        this.previousLevel = previousLevel;
        this.newLevel = newLevel;
    }
}