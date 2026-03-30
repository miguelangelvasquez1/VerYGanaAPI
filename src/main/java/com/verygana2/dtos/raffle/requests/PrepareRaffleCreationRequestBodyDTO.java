// PrepareRaffleCreationRequestDTO.java
package com.verygana2.dtos.raffle.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import com.verygana2.dtos.FileUploadRequestDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrepareRaffleCreationRequestBodyDTO {

    @NotNull @Valid
    private CreateRaffleRequestDTO raffleData;

    @NotNull @Valid
    private FileUploadRequestDTO raffleImageMetadata;

    // En el mismo orden que raffleData.prizes
    @NotNull
    @Size(min = 1)
    private List<@Valid FileUploadRequestDTO> prizeImageMetadataList;
}