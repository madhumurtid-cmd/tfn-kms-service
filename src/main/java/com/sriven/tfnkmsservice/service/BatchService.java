package com.sriven.tfnkmsservice.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BatchService {

    private final JdbcTemplate jdbcTemplate;
    private final KeyService keyService;

    public BatchService(JdbcTemplate jdbcTemplate, KeyService keyService) {
        this.jdbcTemplate = jdbcTemplate;
        this.keyService = keyService;
    }

    public Map<String, String> start(String batchId) {

        String runId = UUID.randomUUID().toString();
        String keyId = UUID.randomUUID().toString();

        String dataKey = UUID.randomUUID().toString();
        String encryptedKey = keyService.encryptWithMasterKey(dataKey);

        // Insert KEY
        jdbcTemplate.update(
            "INSERT INTO KEY_REGISTRY (KEY_ID, KMS_KEY_REF, CREATED_AT) VALUES (?, ?, SYSTIMESTAMP)",
            keyId, encryptedKey
        );

        // Insert BATCH_RUN
        jdbcTemplate.update(
            "INSERT INTO BATCH_RUN (RUN_ID, BATCH_ID, START_TIME, STATUS, KEY_ID, CREATED_AT) " +
            "VALUES (?, ?, SYSTIMESTAMP, 'STARTED', ?, SYSTIMESTAMP)",
            runId, batchId, keyId
        );

        return Map.of("runId", runId, "keyId", keyId);
    }

    public void end(String runId) {
        jdbcTemplate.update(
            "UPDATE BATCH_RUN SET END_TIME = SYSTIMESTAMP, STATUS = 'COMPLETED' WHERE RUN_ID = ?",
            runId
        );
    }
}