package com.sriven.tfnkmsservice.controller;

import org.springframework.web.bind.annotation.*;
import com.sriven.tfnkmsservice.service.KeyService;

@RestController
@RequestMapping("/key")
public class KeyController {

    private final KeyService keyService;

    public KeyController(KeyService keyService) {
        this.keyService = keyService;
    }

    @GetMapping("/decrypt")
    public String decrypt(@RequestParam String keyId) {
        return keyService.decryptKey(keyId);
    }
}