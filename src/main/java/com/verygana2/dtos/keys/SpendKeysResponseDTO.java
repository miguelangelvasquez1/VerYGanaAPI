package com.verygana2.dtos.keys;

public record SpendKeysResponseDTO(boolean success, Long newBalance, String error) {

    public static SpendKeysResponseDTO ok(Long newBalance) {
        return new SpendKeysResponseDTO(true, newBalance, null);
    }

    public static SpendKeysResponseDTO fail(String error) {
        return new SpendKeysResponseDTO(false, null, error);
    }
}