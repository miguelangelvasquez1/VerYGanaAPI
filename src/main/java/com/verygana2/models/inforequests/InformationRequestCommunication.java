package com.verygana2.models.inforequests;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.inforequests.CommunicationChannel;
import com.verygana2.models.enums.inforequests.DeliveryStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Envío del requerimiento por un canal. No registra lectura ni conocimiento sin evidencia. */
@Entity
@Table(name = "information_request_communications")
@Getter
@Setter
public class InformationRequestCommunication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private InformationRequest request;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CommunicationChannel channel;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(name = "sent_at", nullable = false)
    private ZonedDateTime sentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 10)
    private DeliveryStatus deliveryStatus;

    @Column(name = "error_detail", length = 500)
    private String errorDetail;
}
