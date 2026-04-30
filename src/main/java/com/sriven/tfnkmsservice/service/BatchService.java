package com.sriven.tfnkmsservice.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class BatchService {

    private final JdbcTemplate jdbcTemplate;
    private final KeyService keyService;

    public BatchService(JdbcTemplate jdbcTemplate, KeyService keyService) {
        this.jdbcTemplate = jdbcTemplate;
        this.keyService = keyService;
    }

    // =========================
    // START BATCH
    // =========================
    @Transactional
    public Map<String, String> start(String batchId) {

        String runId = UUID.randomUUID().toString();
        String keyId = UUID.randomUUID().toString();

        try {
            // 🔐 Generate data key
            String dataKey = UUID.randomUUID().toString();
            String encryptedKey = keyService.encryptWithMasterKey(dataKey);

            // =========================
            // INSERT KEY_REGISTRY
            // =========================
            jdbcTemplate.update(
                "INSERT INTO KEY_REGISTRY " +
                "(KEY_ID, KMS_PROVIDER, KMS_KEY_REF, KEY_VERSION, STATUS, CREATED_AT, CREATED_BY) " +
                "VALUES (?, ?, ?, ?, ?, SYSTIMESTAMP, ?)",
                keyId,
                "LOCAL_KMS",        // or AWS later
                encryptedKey,
                1,
                "ACTIVE",
                "BATCH_PROCESS"
            );

            // =========================
            // INSERT BATCH_RUN
            // =========================
            jdbcTemplate.update(
                "INSERT INTO BATCH_RUN " +
                "(RUN_ID, BATCH_ID, START_TIME, STATUS, KEY_ID, CREATED_AT) " +
                "VALUES (?, ?, SYSTIMESTAMP, ?, ?, SYSTIMESTAMP)",
                runId,
                batchId,
                "STARTED",
                keyId
            );

            return Map.of(
                "runId", runId,
                "keyId", keyId
            );

        } catch (Exception e) {
            throw new RuntimeException("Batch start failed", e);
        }
    }

    // =========================
    // END BATCH
    // =========================
    @Transactional
    public void end(String runId) {

        int updated = jdbcTemplate.update(
            "UPDATE BATCH_RUN " +
            "SET END_TIME = SYSTIMESTAMP, STATUS = ? " +
            "WHERE RUN_ID = ?",
            "COMPLETED",
            runId
        );

        if (updated == 0) {
            throw new RuntimeException("RunId not found: " + runId);
        }
    }
}