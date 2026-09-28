package com.sitech.prodai.service.appstore;

import com.sitech.prodai.config.AppStoreInterfaceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * B1 上传前文件落盘测试：稽核 Excel 应写入 upload-dir，文件名附加时间戳 {@code _yyyyMMddHHmmss}（保留扩展名）。
 */
@DisplayName("B1 上传前落盘")
class EopUploadStagingTest {

    private EopInterfaceAdapter adapterWith(String uploadDir) {
        AppStoreInterfaceProperties props = new AppStoreInterfaceProperties();
        props.getEop().setUploadDir(uploadDir);
        return new EopInterfaceAdapter(props, new ObjectMapper(), null, null);
    }

    @Test
    @DisplayName("落盘后返回带时间戳的文件名，且文件真实写入目录")
    void stagesFileWithTimestamp(@TempDir Path dir) throws Exception {
        EopInterfaceAdapter adapter = adapterWith(dir.toString());
        String staged = (String) invoke(adapter, "stageFile", new Class<?>[]{String.class, byte[].class},
                "稽核需求.xlsx", "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Assertions.assertTrue(staged.startsWith("稽核需求_"), "应原名+时间戳：" + staged);
        Assertions.assertTrue(staged.endsWith(".xlsx"), "应保留扩展名：" + staged);
        Assertions.assertTrue(Pattern.matches("稽核需求_\\d{14}\\.xlsx", staged),
                "时间戳应为 yyyyMMddHHmmss：" + staged);

        Path written = dir.resolve(staged);
        Assertions.assertTrue(Files.exists(written), "文件应已落盘：" + written);
        Assertions.assertEquals("hello", Files.readString(written), "落盘内容应与入参一致");
    }

    @Test
    @DisplayName("无扩展名文件名也能附加时间戳")
    void stampsNameWithoutExtension(@TempDir Path dir) throws Exception {
        EopInterfaceAdapter adapter = adapterWith(dir.toString());
        String staged = (String) invoke(adapter, "withTimestamp", new Class<?>[]{String.class}, "plainfile");
        Assertions.assertTrue(Pattern.matches("plainfile_\\d{14}", staged), "应为 原名_yyyyMMddHHmmss：" + staged);
        Assertions.assertEquals("plainfile", staged.substring(0, staged.indexOf('_')));
    }

    private Object invoke(EopInterfaceAdapter adapter, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = EopInterfaceAdapter.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(adapter, args);
    }
}
