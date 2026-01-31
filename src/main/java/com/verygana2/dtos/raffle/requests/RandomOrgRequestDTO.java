package com.verygana2.dtos.raffle.requests;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RandomOrgRequestDTO {

    private String jsonrpc = "2.0";
    private String method = "generateIntegers";
    private RandomOrgParams params;
    private String id;
}
