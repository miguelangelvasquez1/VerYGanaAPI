package com.verygana2.models.companions;

import java.time.ZonedDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper=false)
public class PetEvent {
    
    private Long id;
    private Pet pet;
    private String type;
    private ZonedDateTime timeStamp;
    private Object payload;
}
