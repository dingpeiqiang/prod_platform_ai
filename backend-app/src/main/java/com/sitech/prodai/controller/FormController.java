package com.sitech.prodai.controller;

import com.sitech.prodai.service.FormMockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class FormController {

    private final FormMockService formMockService;

    public FormController(FormMockService formMockService) {
        this.formMockService = formMockService;
    }

    @GetMapping("/form/schema/{formCode}")
    public Map<String, Object> getFormSchema(@PathVariable String formCode) {
        return formMockService.getFormSchema(formCode);
    }
}
