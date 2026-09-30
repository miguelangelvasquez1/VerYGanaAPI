package com.verygana2.dtos.branding;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameDesignerSummaryDTO {

    private UUID publicId;
    private String name;
    private String lastName;
    private String designerCode;
    private int campaignsDesigned;
}
