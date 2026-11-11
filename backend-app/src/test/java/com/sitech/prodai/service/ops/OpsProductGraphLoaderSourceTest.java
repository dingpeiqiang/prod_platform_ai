package com.sitech.prodai.service.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitech.prodai.config.ProdAiProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * OpsProductGraphLoader 数据源分支行为：验证 data-source 是图谱来源的唯一开关。
 * <p>纯构造调用，不启动 Spring 容器：classpath / http 源用真实实现（不触发网络），jdbc 源传 null
 * （模拟 bean 未装配）。
 */
class OpsProductGraphLoaderSourceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OpsProductGraphLoader loader(String dataSource) {
        ProdAiProperties properties = new ProdAiProperties();
        properties.getOntology().setDataSource(dataSource);
        properties.getOntology().setGraphPath("classpath:ontology/mock_graph.json");
        properties.getOntology().setDemoEnabled(true);
        ClasspathOpsProductDataSource classpath = new ClasspathOpsProductDataSource(
                objectMapper, new DefaultResourceLoader(), properties);
        HttpOpsProductDataSource http = new HttpOpsProductDataSource(objectMapper, properties);
        return new OpsProductGraphLoader(properties, classpath, http);
    }

    @Test
    @DisplayName("classpath：图谱来自 classpath 源，未走 jdbc 分支")
    void classpathModeDoesNotUseJdbc() {
        OpsProductGraphLoader.LoadedGraph loaded = loader("classpath").load();
        Assertions.assertNotEquals("jdbc", loaded.sourceId());
    }

    @Test
    @DisplayName("data-source=jdbc 但 JDBC bean 未装配：快速失败并给出可操作指引")
    void failsFastWhenJdbcBeanAbsent() {
        IllegalStateException ex = Assertions.assertThrows(IllegalStateException.class,
                () -> loader("jdbc").load());
        Assertions.assertTrue(ex.getMessage().contains("JdbcOpsProductDataSource"),
                "错误信息应指出缺失的 bean，实际：" + ex.getMessage());
        Assertions.assertTrue(ex.getMessage().contains("ABoxDataSourceConfig"),
                "错误信息应指出装配来源，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("empty：返回空图")
    void emptyModeReturnsEmptyGraph() {
        OpsProductGraphLoader.LoadedGraph loaded = loader("empty").load();
        Assertions.assertEquals("empty", loaded.sourceId());
        Assertions.assertTrue(loaded.graph().get("shelfOfferings") instanceof java.util.List<?>);
    }
}
