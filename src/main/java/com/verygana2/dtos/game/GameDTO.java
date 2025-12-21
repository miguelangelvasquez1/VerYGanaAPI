package com.verygana2.dtos.game;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
    
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameDTO {
 
    private Long id;
    private String title;
    private String description;
    private String frontPageUrl;
}
