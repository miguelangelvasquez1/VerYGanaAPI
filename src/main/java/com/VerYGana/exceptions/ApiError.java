package com.VerYGana.exceptions;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class ApiError {
    private int status;
    private String error;
    private String message;
    private String timeStamp;
    private String path;
}
