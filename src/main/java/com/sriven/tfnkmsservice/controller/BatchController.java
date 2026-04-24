package com.sriven.tfnkmsservice.controller;

import org.springframework.web.bind.annotation.*;
import java.util.*;
import com.sriven.tfnkmsservice.service.BatchService;

@RestController
@RequestMapping("/batch")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @PostMapping("/start")
    public Map<String, String> start(@RequestParam String batchId) {
        return batchService.start(batchId);
    }

    @PostMapping("/end")
    public void end(@RequestParam String runId) {
        batchService.end(runId);
    }
}