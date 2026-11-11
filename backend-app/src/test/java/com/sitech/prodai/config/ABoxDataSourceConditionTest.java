package com.sitech.prodai.config;

import com.sitech.prodai.service.ABoxSyncScheduler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ABox 数据源装配条件验证：data-source 是唯一开关，abox-sync-enabled 仅在 jdbc 前提下有意义。
 */
class ABoxDataSourceConditionTest {

    @Test
    @DisplayName("data-source=jdbc：定时同步前提校验通过")
    void jdbcSourcePassesGuard() {
        Assertions.assertDoesNotThrow(() -> ABoxSyncScheduler.requireJdbcDataSource("jdbc"));
        Assertions.assertDoesNotThrow(() -> ABoxSyncScheduler.requireJdbcDataSource("JDBC"));
    }

    @Test
    @DisplayName("data-source=classpath：开启定时同步时快速失败，暴露配置矛盾")
    void nonJdbcSourceFailsGuard() {
        IllegalStateException ex = Assertions.assertThrows(IllegalStateException.class,
                () -> ABoxSyncScheduler.requireJdbcDataSource("classpath"));
        Assertions.assertTrue(ex.getMessage().contains("data-source=jdbc"),
                "错误信息应指出需要 data-source=jdbc，实际：" + ex.getMessage());
        Assertions.assertTrue(ex.getMessage().contains("abox-sync-enabled"),
                "错误信息应提示可关闭 abox-sync-enabled，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("data-source=empty / http：同样被拒绝")
    void otherModesFailsGuard() {
        Assertions.assertThrows(IllegalStateException.class,
                () -> ABoxSyncScheduler.requireJdbcDataSource("empty"));
        Assertions.assertThrows(IllegalStateException.class,
                () -> ABoxSyncScheduler.requireJdbcDataSource("http"));
    }
}
