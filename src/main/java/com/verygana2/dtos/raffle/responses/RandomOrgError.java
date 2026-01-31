package com.verygana2.dtos.raffle.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RandomOrgError {
    private int code;
    private String message;
}
