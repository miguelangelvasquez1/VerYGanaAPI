package com.verygana2.models.userDetails;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "game_designer_details")
@Data
@EqualsAndHashCode(callSuper = false)
public class GameDesignerDetails extends UserDetails {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "designer_code", nullable = false, unique = true, length = 20)
    private String designerCode;

    @Column(length = 500)
    private String bio;

    @Column(name = "campaigns_designed", nullable = false)
    private int campaignsDesigned = 0;

    @Column(name = "can_publish_directly", nullable = false)
    private boolean canPublishDirectly = false;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "joined_at", nullable = false)
    private LocalDate joinedAt;
}
