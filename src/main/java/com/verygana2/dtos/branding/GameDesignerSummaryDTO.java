package com.verygana2.dtos.branding;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameDesignerSummaryDTO {

    private Long id;
    private Long userId;
    private String name;
    private String lastName;
    private String designerCode;
    private int campaignsDesigned;
}
