package com.verygana2.security;

/**
 * CodeEncryptor dedicado a códigos de reclamo de premios (ver CodeEncryptionConfig).
 * Un tipo propio permite que Spring resuelva la inyección por tipo, sin
 * depender de que Lombok copie un @Qualifier al constructor generado.
 */
public class ClaimCodeEncryptor extends CodeEncryptor {

    public ClaimCodeEncryptor(String aesHexKey, String hmacHexKey) {
        super(aesHexKey, hmacHexKey);
    }
}
