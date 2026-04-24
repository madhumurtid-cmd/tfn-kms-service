package com.sriven.tfnkmsservice.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Service
public class KeyService {

    private final JdbcTemplate jdbcTemplate;

    private static final String MASTER_KEY = "MySuperSecretKey"; // 16 chars

    public KeyService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String decryptKey(String keyId) {

        String encryptedKey = jdbcTemplate.queryForObject(
            "SELECT KMS_KEY_REF FROM KEY_REGISTRY WHERE KEY_ID = ?",
            String.class,
            keyId
        );

        return decryptWithMasterKey(encryptedKey);
    }

    public String encryptWithMasterKey(String data) {
        try {
            SecretKeySpec key = new SecretKeySpec(MASTER_KEY.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return Base64.getEncoder().encodeToString(cipher.doFinal(data.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String decryptWithMasterKey(String encrypted) {
        try {
            SecretKeySpec key = new SecretKeySpec(MASTER_KEY.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return new String(cipher.doFinal(Base64.getDecoder().decode(encrypted)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}