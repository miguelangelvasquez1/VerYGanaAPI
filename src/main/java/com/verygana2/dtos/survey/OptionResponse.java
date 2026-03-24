package com.verygana2.dtos.survey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor 
@AllArgsConstructor 
@Builder
@Data
public class OptionResponse {
    private Long id;
    private String text;
    private Integer orderIndex;
}