package com.sriven.tfnkmsservice.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

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

        if (batchId == null || batchId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "batchId is required");
        }

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
                "LOCAL_KMS",
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
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Batch start failed: " + e.getMessage()
            );
        }
    }

    // =========================
    // END BATCH
    // =========================
    @Transactional
    public Map<String, Object> end(String runId) {

        if (runId == null || runId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "runId is required");
        }

        // 🔍 Validate run exists
        Integer exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM BATCH_RUN WHERE RUN_ID = ?",
            Integer.class,
            runId
        );

        if (exists == null || exists == 0) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "RunId not found: " + runId
            );
        }

        // =========================
        // 1. Get stats (NULL SAFE)
        // =========================
        Map<String, Object> stats = jdbcTemplate.queryForMap(
            "SELECT " +
            "COUNT(*) AS total, " +
            "NVL(SUM(CASE WHEN STATUS = 'SUCCESS' THEN 1 ELSE 0 END),0) AS success, " +
            "NVL(SUM(CASE WHEN STATUS = 'FAIL' AND RETRY_COUNT >= 3 THEN 1 ELSE 0 END),0) AS failed, " +
            "NVL(SUM(CASE WHEN STATUS = 'FAIL' AND RETRY_COUNT < 3 THEN 1 ELSE 0 END),0) AS retryable " +
            "FROM STG_TFN_PROCESS " +
            "WHERE RUN_ID = ?",
            runId
        );

        int total = ((Number) stats.get("TOTAL")).intValue();
        int success = ((Number) stats.get("SUCCESS")).intValue();
        int failed = ((Number) stats.get("FAILED")).intValue();
        int retryable = ((Number) stats.get("RETRYABLE")).intValue();

        // =========================
        // 2. Decide batch status
        // =========================
        String status;

        if (total == 0) {
            status = "FAILED";
        } else if (retryable > 0) {
            status = "IN_PROGRESS";
        } else if (failed == 0 && success == total) {
            status = "COMPLETED";
        } else if (success == 0) {
            status = "FAILED";
        } else {
            status = "PARTIAL_SUCCESS";
        }

        // =========================
        // 3. Update batch_run
        // =========================
        jdbcTemplate.update(
            "UPDATE BATCH_RUN " +
            "SET END_TIME = SYSTIMESTAMP, " +
            "DURATION_MS = (SYSTIMESTAMP - START_TIME) * 86400000, " +
            "STATUS = ?, " +
            "RECORDS_PROCESSED = ? " +
            "WHERE RUN_ID = ?",
            status,
            total,
            runId
        );

        // =========================
        // 4. Return summary
        // =========================
        return Map.of(
            "runId", runId,
            "status", status,
            "total", total,
            "success", success,
            "failed", failed,
            "retryable", retryable
        );
    }
}