package com.verygana2.dtos.raffle.responses;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RandomOrgResult {

    private RandomOrgRandom random;
    private int bitsUsed;
    private int bitsLeft;
    private int requestsLeft;
    private Map<String, Object> advisories;
}
