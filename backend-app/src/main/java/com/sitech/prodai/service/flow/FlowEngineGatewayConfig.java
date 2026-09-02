package com.sitech.prodai.service.flow;

import com.sitech.prodai.domain.entity.WorkflowExecution;
import com.sitech.prodai.mapper.WorkflowExecutionMapper;
import com.sitech.prodai.service.LlmService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 引擎网关注册（P2-5）：
 * - {@link #flowLlmGateway}：llm 节点的 LlmService 适配（completePrompt，LLM 只进节点——铁律二）
 * - {@link #flowHttpGateway}：http 节点的 RestClient 适配（节点级超时 connect/read 5s，重试语义由节点 retry 声明驱动）
 */
@Configuration
@EnableScheduling
public class FlowEngineGatewayConfig {

    @Bean
    public FlowEngineService.LlmGateway flowLlmGateway(LlmService llmService) {
        return params -> {
            try {
                String prompt = String.valueOf(params.get("prompt"));
                return llmService.completePrompt(prompt);
            } catch (Exception e) {
                return null;
            }
        };
    }

    @Bean
    public FlowEngineService.HttpGateway flowHttpGateway() {
        RestClient client = RestClient.builder()
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(java.time.Duration.ofSeconds(5));
                    setReadTimeout(java.time.Duration.ofSeconds(30));
                }})
                .build();
        return (url, method, body) -> {
            HttpMethod httpMethod = "GET".equalsIgnoreCase(method) ? HttpMethod.GET : HttpMethod.POST;
            ResponseEntity<String> resp;
            if (httpMethod == HttpMethod.GET) {
                resp = client.get().uri(url).retrieve().toEntity(String.class);
            } else {
                resp = client.post().uri(url).body(body == null ? Map.of() : body).retrieve().toEntity(String.class);
            }
            return resp;
        };
    }

    /**
     * 挂起 TTL 清理（设计文档 §4.4）：waiting_human 超过 72h 的执行实例标 failed，
     * 可经 resume 端点人工续跑。每小时扫描一次。
     */
    @Component
    public static class HumanTtlSweeper {

        private final WorkflowExecutionMapper executionMapper;

        public HumanTtlSweeper(WorkflowExecutionMapper executionMapper) {
            this.executionMapper = executionMapper;
        }

        @Scheduled(fixedDelay = 3600_000, initialDelay = 600_000)
        public void sweepExpiredSuspensions() {
            List<WorkflowExecution> suspended = executionMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkflowExecution>()
                            .eq(WorkflowExecution::getStatus, "waiting_human"));
            java.time.LocalDateTime threshold = java.time.LocalDateTime.now().minus(java.time.Duration.ofHours(72));
            for (WorkflowExecution execution : suspended) {
                if (execution.getUpdatedAt() != null && execution.getUpdatedAt().isBefore(threshold)) {
                    execution.setStatus("failed");
                    execution.setErrorMessage("人工节点挂起超过 72 小时未恢复，自动失败（可经 resume 续跑）");
                    execution.setEndTime(java.time.LocalDateTime.now());
                    executionMapper.updateById(execution);
                }
            }
        }
    }
}
