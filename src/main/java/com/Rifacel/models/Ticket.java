package com.Rifacel.models;

import com.Rifacel.models.Enums.TicketState;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Ticket {
    private String id;
    private String userId;
    private String number;
    private String raffleId;
    private TicketState state; // En revisión.
}
