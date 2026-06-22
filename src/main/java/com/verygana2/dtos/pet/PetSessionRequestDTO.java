package com.verygana2.dtos.pet;


import com.fasterxml.jackson.annotation.JsonProperty;

public record PetSessionRequestDTO(
        @JsonProperty("session_token") String sessionToken,
        @JsonProperty("user_hash") String userHash
) {}