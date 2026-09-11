package com.sitech.prodai.controller;

import com.sitech.prodai.service.IntentPromptManager;
import com.sitech.prodai.service.OntologyService;
import com.sitech.prodai.service.PromptService;
import com.sitech.prodai.service.SceneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 管理后台 API。
 *
 * <p>场景管理（只读）/ 提示词管理 / 本体管理。
 * 场景数据完全由文件管理，不支持数据库 CRUD。
 */
@Tag(name = "场景与提示词管理", description = "后台管理 API：场景查询（只读）、提示词 CRUD、本体管理")
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

    @Operation(summary = "场景树", description = "返回场景层级树，可按启用状态过滤")
    @GetMapping("/scenes/tree")
    public Map<String, Object> scenesTree(@RequestParam(required = false) Boolean isActive) {
        return sceneService.listScenesTree(isActive);
    }

    @Operation(summary = "场景列表", description = "返回场景清单，可按启用状态过滤")
    @GetMapping("/scenes")
    public Map<String, Object> scenes(@RequestParam(required = false) Boolean isActive) {
        return sceneService.listScenes(isActive);
    }

    @Operation(summary = "场景统计", description = "返回场景数量/启用状态等汇总统计")
    @GetMapping("/scenes/stats/summary")
    public Map<String, Object> sceneStats() {
        return sceneService.getSceneStats();
    }

    @Operation(summary = "场景详情", description = "按场景编码查询场景详情")
    @GetMapping("/scenes/{sceneCode}")
    public Map<String, Object> getScene(@PathVariable String sceneCode) {
        return sceneService.getScene(sceneCode);
    }

    @Operation(summary = "场景测试", description = "对指定场景执行测试请求")
    @PostMapping("/scenes/test")
    public Map<String, Object> testScene(@RequestBody Map<String, Object> body) {
        return sceneService.testSceneRecognition(body == null ? null : String.valueOf(body.get("userInput")));
    }

    // ==================== 提示词管理 ====================

    @Operation(summary = "提示词分类", description = "返回提示词分类清单")
    @GetMapping("/prompts/categories")
    public Map<String, Object> promptCategories() {
        return promptService.getCategories();
    }

    @Operation(summary = "提示词列表", description = "分页/分类查询提示词清单")
    @GetMapping("/prompts")
    public Map<String, Object> listPrompts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean isActive) {
        return promptService.listPrompts(category, isActive);
    }

    @Operation(summary = "提示词详情", description = "按编码查询提示词详情")
    @GetMapping("/prompts/{code}")
    public Map<String, Object> getPrompt(@PathVariable String code) {
        return promptService.getPrompt(code);
    }

    @Operation(summary = "创建提示词", description = "新增提示词模板")
    @PostMapping("/prompts")
    public Map<String, Object> createPrompt(@RequestBody Map<String, Object> body) {
        return promptService.createPrompt(body == null ? Map.of() : body, "admin");
    }

    @Operation(summary = "更新提示词", description = "按编码更新提示词内容")
    @PutMapping("/prompts/{code}")
    public Map<String, Object> updatePrompt(@PathVariable String code, @RequestBody Map<String, Object> body) {
        return promptService.updatePrompt(code, body == null ? Map.of() : body, "admin");
    }

    @Operation(summary = "删除提示词", description = "按编码删除提示词")
    @DeleteMapping("/prompts/{code}")
    public Map<String, Object> deletePrompt(@PathVariable String code) {
        return promptService.deletePrompt(code);
    }

    @Operation(summary = "提示词版本历史", description = "返回提示词历史版本列表")
    @GetMapping("/prompts/{code}/versions")
    public Map<String, Object> promptVersions(@PathVariable String code) {
        return promptService.getVersions(code);
    }

    @Operation(summary = "提示词预览", description = "按变量渲染提示词并返回预览结果")
    @PostMapping("/prompts/{code}/preview")
    public Map<String, Object> previewPrompt(@PathVariable String code, @RequestBody(required = false) Map<String, Object> body) {
        Object vars = body == null ? null : body.get("variables");
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = vars instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        return promptService.previewPrompt(code, variables);
    }

    @Operation(summary = "AI 生成提示词", description = "由 LLM 依据需求生成提示词")
    @PostMapping("/prompts/generate")
    public Map<String, Object> generatePrompt(@RequestBody Map<String, Object> body) {
        return promptService.generateWithAi(body == null ? Map.of() : body);
    }

    @Operation(summary = "AI 优化提示词", description = "由 LLM 优化既有提示词")
    @PostMapping("/prompts/optimize")
    public Map<String, Object> optimizePrompt(@RequestBody Map<String, Object> body) {
        return promptService.optimizePrompt(body == null ? Map.of() : body);
    }

    /**
     * 热加载意图识别 prompt 模板（修改 classpath:prompts/intent_recognition_prompt.txt 后调用）。
     */
    @Operation(summary = "重载提示词", description = "热重载提示词配置")
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

    @Operation(summary = "本体分类", description = "返回本体分类清单")
    @GetMapping("/ontologies/categories")
    public Map<String, Object> ontologyCategories() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", ontologyService.getCategories());
        return body;
    }

    @Operation(summary = "本体列表", description = "分页/分类查询本体清单")
    @GetMapping("/ontologies")
    public Map<String, Object> listOntologies(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean isActive) {
        return ontologyService.listOntologies(category, isActive);
    }

    @Operation(summary = "本体详情", description = "按编码查询本体详情")
    @GetMapping("/ontologies/{ontologyCode}")
    public Map<String, Object> getOntology(@PathVariable String ontologyCode) {
        return ontologyService.getOntology(ontologyCode);
    }

    @Operation(summary = "创建本体", description = "新增本体")
    @PostMapping("/ontologies")
    public Map<String, Object> createOntology(@RequestBody Map<String, Object> body) {
        return ontologyService.createOntology(body, "admin");
    }

    @Operation(summary = "更新本体", description = "按编码更新本体")
    @PutMapping("/ontologies/{ontologyCode}")
    public Map<String, Object> updateOntology(@PathVariable String ontologyCode, @RequestBody Map<String, Object> body) {
        return ontologyService.updateOntology(ontologyCode, body, "admin");
    }

    @Operation(summary = "删除本体", description = "按编码删除本体")
    @DeleteMapping("/ontologies/{ontologyCode}")
    public Map<String, Object> deleteOntology(@PathVariable String ontologyCode) {
        return ontologyService.deleteOntology(ontologyCode);
    }

    @Operation(summary = "启停本体", description = "切换本体启用/停用状态")
    @PatchMapping("/ontologies/{ontologyCode}/toggle")
    public Map<String, Object> toggleOntology(@PathVariable String ontologyCode) {
        return ontologyService.toggleActive(ontologyCode);
    }
}
