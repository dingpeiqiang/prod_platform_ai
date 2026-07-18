package com.sitech.prodai.controller;

import com.sitech.prodai.service.AdminMockService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AdminController {

    private final AdminMockService adminMockService;

    public AdminController(AdminMockService adminMockService) {
        this.adminMockService = adminMockService;
    }

    @GetMapping("/scenes/tree")
    public Map<String, Object> scenesTree(@RequestParam(required = false) Boolean isActive) {
        return adminMockService.listScenesTree(isActive);
    }

    @GetMapping("/scenes")
    public Map<String, Object> scenes(@RequestParam(required = false) Boolean isActive) {
        return adminMockService.listScenes(isActive);
    }

    @GetMapping("/scenes/stats/summary")
    public Map<String, Object> sceneStats() {
        return adminMockService.getSceneStats();
    }

    @GetMapping("/scenes/{sceneCode}")
    public Map<String, Object> getScene(@PathVariable String sceneCode) {
        return adminMockService.getScene(sceneCode);
    }

    @PostMapping("/scenes")
    public Map<String, Object> createScene(@RequestBody Map<String, Object> body) {
        return adminMockService.unsupportedWrite("创建场景");
    }

    @PutMapping("/scenes/{sceneCode}")
    public Map<String, Object> updateScene(@PathVariable String sceneCode, @RequestBody Map<String, Object> body) {
        return adminMockService.unsupportedWrite("更新场景");
    }

    @DeleteMapping("/scenes/{sceneCode}")
    public Map<String, Object> deleteScene(@PathVariable String sceneCode) {
        return adminMockService.unsupportedWrite("删除场景");
    }

    @PatchMapping("/scenes/{sceneCode}/toggle")
    public Map<String, Object> toggleScene(@PathVariable String sceneCode) {
        return adminMockService.unsupportedWrite("切换场景状态");
    }

    @PostMapping("/scenes/test")
    public Map<String, Object> testScene(@RequestBody Map<String, Object> body) {
        return adminMockService.testSceneRecognition(body == null ? null : String.valueOf(body.get("userInput")));
    }

    @GetMapping("/prompts/categories")
    public Map<String, Object> promptCategories() {
        return adminMockService.promptCategories();
    }

    @GetMapping("/prompts")
    public Map<String, Object> listPrompts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean isActive) {
        return adminMockService.listPrompts(category, isActive);
    }

    @GetMapping("/prompts/{code}")
    public Map<String, Object> getPrompt(@PathVariable String code) {
        return adminMockService.getPrompt(code);
    }

    @PostMapping("/prompts")
    public Map<String, Object> createPrompt(@RequestBody Map<String, Object> body) {
        return adminMockService.createPrompt(body == null ? Map.of() : body);
    }

    @PutMapping("/prompts/{code}")
    public Map<String, Object> updatePrompt(@PathVariable String code, @RequestBody Map<String, Object> body) {
        return adminMockService.updatePrompt(code, body == null ? Map.of() : body);
    }

    @DeleteMapping("/prompts/{code}")
    public Map<String, Object> deletePrompt(@PathVariable String code) {
        return adminMockService.deletePrompt(code);
    }

    @GetMapping("/prompts/{code}/versions")
    public Map<String, Object> promptVersions(@PathVariable String code) {
        return adminMockService.promptVersions(code);
    }

    @PostMapping("/prompts/{code}/preview")
    public Map<String, Object> previewPrompt(@PathVariable String code, @RequestBody(required = false) Map<String, Object> body) {
        Object vars = body == null ? null : body.get("variables");
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = vars instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        return adminMockService.previewPrompt(code, variables);
    }

    @PostMapping("/prompts/generate")
    public Map<String, Object> generatePrompt(@RequestBody Map<String, Object> body) {
        return adminMockService.aiStub("# AI 生成提示词（Mock）\n\n" + String.valueOf(body == null ? "" : body.getOrDefault("prompt", "")));
    }

    @PostMapping("/prompts/optimize")
    public Map<String, Object> optimizePrompt(@RequestBody Map<String, Object> body) {
        return adminMockService.aiStub(String.valueOf(body == null ? "" : body.getOrDefault("content", "")));
    }

    @GetMapping("/ontologies/categories")
    public Map<String, Object> ontologyCategories() {
        return adminMockService.ontologyCategories();
    }

    @GetMapping("/ontologies")
    public Map<String, Object> listOntologies(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean isActive) {
        return adminMockService.listOntologies(category, isActive);
    }

    @GetMapping("/ontologies/{ontologyCode}")
    public Map<String, Object> getOntology(@PathVariable String ontologyCode) {
        return adminMockService.getOntology(ontologyCode);
    }

    @PostMapping("/ontologies")
    public Map<String, Object> createOntology(@RequestBody Map<String, Object> body) {
        return adminMockService.unsupportedWrite("创建本体");
    }

    @PutMapping("/ontologies/{ontologyCode}")
    public Map<String, Object> updateOntology(@PathVariable String ontologyCode, @RequestBody Map<String, Object> body) {
        return adminMockService.unsupportedWrite("更新本体");
    }

    @DeleteMapping("/ontologies/{ontologyCode}")
    public Map<String, Object> deleteOntology(@PathVariable String ontologyCode) {
        return adminMockService.unsupportedWrite("删除本体");
    }

    @PatchMapping("/ontologies/{ontologyCode}/toggle")
    public Map<String, Object> toggleOntology(@PathVariable String ontologyCode) {
        return adminMockService.unsupportedWrite("切换本体状态");
    }
}