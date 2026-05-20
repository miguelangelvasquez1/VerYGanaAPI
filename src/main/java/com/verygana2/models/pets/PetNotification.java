package com.verygana2.models.pets;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "pet_notifications")
@Data
@NoArgsConstructor
public class PetNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String externalId;
    private String title;

    @Column(length = 1000)
    private String message;

    private String imageUrl;
    private String buttonLabel;
    private String buttonUrl;
    private LocalDate date;
    private Boolean active = true;
    @Column(name = "is_read")
    private Boolean read = false;
}