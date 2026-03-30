package com.verygana2.dtos.wompi;

import lombok.Data;

@Data
public class WompiWebhookEventDTO {
    private String event;
    private Long timestamp;
    private EventDataDTO data;
}
