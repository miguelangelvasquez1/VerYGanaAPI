package com.verygana2.utils.referral;

import java.security.SecureRandom;

public class ReferralCodeGenerator {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RNG = new SecureRandom();

    private ReferralCodeGenerator () {}

    public static String codeGenerator(int len){
        StringBuilder stringBuilder = new StringBuilder(len);
        for (int i = 0; i < len; i++){
            stringBuilder.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
        }
        return stringBuilder.toString();
    }
}
