// ConfirmRaffleCreationRequestDTO.java
package com.verygana2.dtos.raffle.requests;

import java.util.List;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConfirmRaffleCreationRequestDTO {

    @NotNull(message = "Raffle asset ID is required")
    private Long raffleAssetId;

    // Lista de assetIds de prizes, en el mismo orden de los prizes originales
    @NotNull
    @Size(min = 1)
    private List<Long> prizeAssetIds;

    @NotNull(message = "Raffle data is required")
    private CreateRaffleRequestDTO raffleData;
}