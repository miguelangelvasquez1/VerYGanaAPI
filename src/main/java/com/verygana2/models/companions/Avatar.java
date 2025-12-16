package com.verygana2.models.companions;

import jakarta.persistence.Column;
//import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

//@Entity
@Table(name = "avatars")
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper=false)
public class Avatar extends Companion{
    @Column(name = "skin_tone", nullable = false)
    private String skinTone;
    @Column(name = "hair_style", nullable = false)
    private String hairStyle;
    @Column(name = "clothing_style", nullable = false)
    private String clothingStyle;
}
