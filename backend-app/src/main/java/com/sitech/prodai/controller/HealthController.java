package com.sitech.prodai.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    @RequestMapping(value = {"/health", "/api/v1/health"}, method = {RequestMethod.GET, RequestMethod.HEAD})
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("service", "prod-platform-ai");
        body.put("runtime", "spring-boot");
        return body;
    }

    @GetMapping("/")
    public Map<String, Object> root() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "prod-platform-ai");
        body.put("runtime", "spring-boot");
        body.put("docs", "See backend-app/README.md");
        return body;
    }
}
