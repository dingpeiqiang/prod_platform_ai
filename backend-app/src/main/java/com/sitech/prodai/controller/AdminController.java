package com.sitech.prodai.controller;

import com.sitech.prodai.service.IntentPromptManager;
import com.sitech.prodai.service.OntologyService;
import com.sitech.prodai.service.PromptService;
import com.sitech.prodai.service.SceneService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 管理后台 API。
 *
 * <p>场景管理（只读）/ 提示词管理 / 本体管理。
 * 场景数据完全由文件管理，不支持数据库 CRUD。
 */
@RestController
@RequestMapping("/api/v1")
public class AdminController {

    private final SceneService sceneService;
    private final PromptService promptService;
    private final OntologyService ontologyService;
    private final Optional<IntentPromptManager> intentPromptManager;

    public AdminController(SceneService sceneService,
                           PromptService promptService,
                           OntologyService ontologyService,
                           Optional<IntentPromptManager> intentPromptManager) {
        this.sceneService = sceneService;
        this.promptService = promptService;
        this.ontologyService = ontologyService;
        this.intentPromptManager = intentPromptManager;
    }

    // ==================== 场景查询（文件数据源，只读）====================

    @GetMapping("/scenes/tree")
    public Map<String, Object> scenesTree(@RequestParam(required = false) Boolean isActive) {
        return sceneService.listScenesTree(isActive);
    }

    @GetMapping("/scenes")
    public Map<String, Object> scenes(@RequestParam(required = false) Boolean isActive) {
        return sceneService.listScenes(isActive);
    }

    @GetMapping("/scenes/stats/summary")
    public Map<String, Object> sceneStats() {
        return sceneService.getSceneStats();
    }

    @GetMapping("/scenes/{sceneCode}")
    public Map<String, Object> getScene(@PathVariable String sceneCode) {
        return sceneService.getScene(sceneCode);
    }

    @PostMapping("/scenes/test")
    public Map<String, Object> testScene(@RequestBody Map<String, Object> body) {
        return sceneService.testSceneRecognition(body == null ? null : String.valueOf(body.get("userInput")));
    }

    // ==================== 提示词管理 ====================

    @GetMapping("/prompts/categories")
    public Map<String, Object> promptCategories() {
        return promptService.getCategories();
    }

    @GetMapping("/prompts")
    public Map<String, Object> listPrompts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean isActive) {
        return promptService.listPrompts(category, isActive);
    }

    @GetMapping("/prompts/{code}")
    public Map<String, Object> getPrompt(@PathVariable String code) {
        return promptService.getPrompt(code);
    }

    @PostMapping("/prompts")
    public Map<String, Object> createPrompt(@RequestBody Map<String, Object> body) {
        return promptService.createPrompt(body == null ? Map.of() : body, "admin");
    }

    @PutMapping("/prompts/{code}")
    public Map<String, Object> updatePrompt(@PathVariable String code, @RequestBody Map<String, Object> body) {
        return promptService.updatePrompt(code, body == null ? Map.of() : body, "admin");
    }

    @DeleteMapping("/prompts/{code}")
    public Map<String, Object> deletePrompt(@PathVariable String code) {
        return promptService.deletePrompt(code);
    }

    @GetMapping("/prompts/{code}/versions")
    public Map<String, Object> promptVersions(@PathVariable String code) {
        return promptService.getVersions(code);
    }

    @PostMapping("/prompts/{code}/preview")
    public Map<String, Object> previewPrompt(@PathVariable String code, @RequestBody(required = false) Map<String, Object> body) {
        Object vars = body == null ? null : body.get("variables");
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = vars instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        return promptService.previewPrompt(code, variables);
    }

    @PostMapping("/prompts/generate")
    public Map<String, Object> generatePrompt(@RequestBody Map<String, Object> body) {
        return promptService.generateWithAi(body == null ? Map.of() : body);
    }

    @PostMapping("/prompts/optimize")
    public Map<String, Object> optimizePrompt(@RequestBody Map<String, Object> body) {
        return promptService.optimizePrompt(body == null ? Map.of() : body);
    }

    /**
     * 热加载意图识别 prompt 模板（修改 classpath:prompts/intent_recognition_prompt.txt 后调用）。
     */
    @PostMapping("/prompts/reload")
    public Map<String, Object> reloadPrompts() {
        Map<String, Object> body = new LinkedHashMap<>();
        if (intentPromptManager.isPresent()) {
            intentPromptManager.get().reload();
            body.put("success", true);
            body.put("message", "Prompt 模板已重新加载");
        } else {
            body.put("success", false);
            body.put("message", "IntentPromptManager 未启用");
        }
        return body;
    }

    // ==================== 本体管理 ====================

    @GetMapping("/ontologies/categories")
    public Map<String, Object> ontologyCategories() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", ontologyService.getCategories());
        return body;
    }

    @GetMapping("/ontologies")
    public Map<String, Object> listOntologies(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean isActive) {
        return ontologyService.listOntologies(category, isActive);
    }

    @GetMapping("/ontologies/{ontologyCode}")
    public Map<String, Object> getOntology(@PathVariable String ontologyCode) {
        return ontologyService.getOntology(ontologyCode);
    }

    @PostMapping("/ontologies")
    public Map<String, Object> createOntology(@RequestBody Map<String, Object> body) {
        return ontologyService.createOntology(body, "admin");
    }

    @PutMapping("/ontologies/{ontologyCode}")
    public Map<String, Object> updateOntology(@PathVariable String ontologyCode, @RequestBody Map<String, Object> body) {
        return ontologyService.updateOntology(ontologyCode, body, "admin");
    }

    @DeleteMapping("/ontologies/{ontologyCode}")
    public Map<String, Object> deleteOntology(@PathVariable String ontologyCode) {
        return ontologyService.deleteOntology(ontologyCode);
    }

    @PatchMapping("/ontologies/{ontologyCode}/toggle")
    public Map<String, Object> toggleOntology(@PathVariable String ontologyCode) {
        return ontologyService.toggleActive(ontologyCode);
    }
}
