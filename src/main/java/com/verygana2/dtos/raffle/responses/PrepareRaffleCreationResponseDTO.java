// PrepareRaffleCreationResponseDTO.java
package com.verygana2.dtos.raffle.responses;

import java.util.List;
import com.verygana2.dtos.FileUploadPermissionDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PrepareRaffleCreationResponseDTO {

    // Token que identifica esta sesión de creación (se usa en el Step 2)
    private Long raffleAssetId;

    // Pre-signed URL para subir la imagen de la raffle
    private FileUploadPermissionDTO raffleImagePermission;

    // Pre-signed URLs para cada prize, en el mismo orden que se enviaron
    private List<PrizeUploadSlotDTO> prizeUploadSlots;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PrizeUploadSlotDTO {
        // Índice que corresponde al prize en la lista original (0-based)
        private Integer prizeIndex;
        private Long prizeAssetId;
        private FileUploadPermissionDTO permission;
    }
}