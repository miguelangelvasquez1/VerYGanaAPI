package com.verygana2.dtos.ad.requests;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import com.verygana2.models.enums.AdStatus;

import lombok.Data;

@Data
public class AdFilterDTO {

    private String searchTerm; // -> Title
    private AdStatus status;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private ZonedDateTime startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private ZonedDateTime endDate;

    private List<Long> categoryIds;
}