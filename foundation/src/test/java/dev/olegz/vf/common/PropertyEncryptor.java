package dev.olegz.vf.common;

import java.security.SecureRandom;
import java.util.Base64;

import dev.olegz.vf.common.props.PropertyStore;

public class PropertyEncryptor {
    public static void main(String[] args) {
        try {
            VigiloEnvironment.setKek("");
            byte[] kek = VigiloEnvironment.getKek();
            if (kek == null) System.out.println("KEK not set!");
            else System.out.println("KEK: " + Base64.getEncoder().encodeToString(kek));
            System.out.println(PropertyStore.encrypt("itsasecret"));
            //System.out.println(PropertyStore.decrypt("jdbc.password"));
            //System.out.println("New KEK: " + generateAesKey());
        } catch (Throwable e) {
            e.printStackTrace();
        }

        System.exit(0);
    }

    private static String generateAesKey() {
        byte[] secureRandomKeyBytes = new byte[16]; // 128 bit
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(secureRandomKeyBytes);
        return Base64.getEncoder().encodeToString(secureRandomKeyBytes);
    }
}
