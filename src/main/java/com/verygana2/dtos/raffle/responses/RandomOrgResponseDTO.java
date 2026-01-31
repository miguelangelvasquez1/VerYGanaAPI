package com.verygana2.dtos.raffle.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RandomOrgResponseDTO {
    private String jsonrpc;
    private RandomOrgResult result;
    private RandomOrgError error;
    private String id;
}
