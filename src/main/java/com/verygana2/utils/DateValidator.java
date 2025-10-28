package com.verygana2.utils;

import java.time.ZonedDateTime;

import com.verygana2.exceptions.adsExceptions.InvalidAdStateException;

public final class DateValidator {

    private DateValidator() {} // Evita instanciación

    public static void validateStartBeforeEnd(ZonedDateTime start, ZonedDateTime end, String message) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new InvalidAdStateException(message != null ? message : 
                "La fecha de fin debe ser posterior a la de inicio");
        }
    }
}