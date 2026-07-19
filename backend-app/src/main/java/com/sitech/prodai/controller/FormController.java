package com.sitech.prodai.controller;

import com.sitech.prodai.service.FormService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class FormController {

    private static final Logger log = LoggerFactory.getLogger(FormController.class);

    private final FormService formService;

    public FormController(FormService formService) {
        this.formService = formService;
        log.info("[FormController] initialized");
    }

    @GetMapping("/form/schema/{formCode}")
    public Map<String, Object> getFormSchema(@PathVariable String formCode) {
        log.info("[FormController] getFormSchema called, formCode={}", formCode);
        try {
            return formService.getFormSchema(formCode);
        } catch (Exception e) {
            log.error("[FormController] getFormSchema failed, formCode={}", formCode, e);
            throw e;
        }
    }

    @PostMapping("/form/generate")
    public Map<String, Object> generateForm(@RequestBody(required = false) Map<String, Object> request) {
        log.info("[FormController] generateForm called, request_size={}",
                request != null ? request.size() : 0);
        try {
            return formService.generateForm(request == null ? Map.of() : request);
        } catch (Exception e) {
            log.error("[FormController] generateForm failed", e);
            throw e;
        }
    }

    @PostMapping("/form/submit")
    public Map<String, Object> submitForm(@RequestBody Map<String, Object> request) {
        log.info("[FormController] submitForm called, formId={}, formCode={}",
                request.get("formId"), request.get("formCode"));
        try {
            return formService.submitForm(request);
        } catch (Exception e) {
            log.error("[FormController] submitForm failed", e);
            throw e;
        }
    }
}
