package com.sitech.prodai.controller;

import com.sitech.prodai.service.FormMockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    private final FormMockService formMockService;

    public ConfigController(FormMockService formMockService) {
        this.formMockService = formMockService;
    }

    @GetMapping("/ontologies")
    public Map<String, Object> listOntologies() {
        return formMockService.listOntologies();
    }
}
