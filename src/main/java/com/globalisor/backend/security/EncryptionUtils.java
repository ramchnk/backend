package com.globalisor.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

@Component
public class EncryptionUtils {

    @Value("${globalisor.app.encryptionSecret:mySuperSecretKey123}")
    private String secret;

    @Value("${globalisor.app.encryptionSalt:5c0744940b5c369b}")
    private String salt; // Hex-encoded salt

    private TextEncryptor strongEncryptor;
    private SecretKeySpec secretKey;

    @PostConstruct
    public void init() throws Exception {
        this.strongEncryptor = Encryptors.text(secret, salt);

        // Deterministic encryptor setup
        byte[] key = secret.getBytes("UTF-8");
        MessageDigest sha = MessageDigest.getInstance("SHA-1");
        key = sha.digest(key);
        key = Arrays.copyOf(key, 16); // use only first 128 bit
        this.secretKey = new SecretKeySpec(key, "AES");
    }

    public String encryptQueryable(String text) {
        if (text == null) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(text.getBytes("UTF-8")));
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting", e);
        }
    }

    public String decryptQueryable(String encryptedText) {
        if (encryptedText == null) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedText)));
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting", e);
        }
    }

    public String encryptStrong(String text) {
        if (text == null) return null;
        return strongEncryptor.encrypt(text);
    }

    public String decryptStrong(String encryptedText) {
        if (encryptedText == null) return null;
        return strongEncryptor.decrypt(encryptedText);
    }
}
