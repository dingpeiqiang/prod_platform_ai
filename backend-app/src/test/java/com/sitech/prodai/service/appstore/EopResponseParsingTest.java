package com.sitech.prodai.service.appstore;

import com.sitech.prodai.config.AppStoreInterfaceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * EOP 响应解析容错测试：EOP 的 {@code errmsg}/{@code errdetail} 会把下游原始返回（含 HTML 错误页、
 * 未转义换行）原样透传，标准 JSON 解析会失败并把真实业务错误掩盖成「响应非 JSON」。
 * 断言宽松解析器能正确还原 errcode/errmsg/errdetail。
 */
@DisplayName("EOP 响应解析容错")
class EopResponseParsingTest {

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String resp) throws Exception {
        EopInterfaceAdapter adapter = new EopInterfaceAdapter(new AppStoreInterfaceProperties(), new ObjectMapper());
        Method m = EopInterfaceAdapter.class.getDeclaredMethod("parse", String.class);
        m.setAccessible(true);
        return (Map<String, Object>) m.invoke(adapter, resp);
    }

    @Test
    @DisplayName("errdetail 含未转义换行/HTML 片段时仍可解析出 errcode/errmsg")
    void parsesUnescapedControlChars() throws Exception {
        String resp = "{\"errcode\":\"310\",\"errmsg\":\"服务端内部异常\","
                + "\"errdetail\":\"请求失败：该地址返回码：405，原始报文片段：'<!doctype html>\n"
                + "<html lang=en>\n<title>405 Method Not Allowed</title>\n', 请联系接口负责人。\"}";

        Map<String, Object> body = parse(resp);
        Assertions.assertEquals("310", body.get("errcode"));
        Assertions.assertEquals("服务端内部异常", body.get("errmsg"));
        Assertions.assertTrue(String.valueOf(body.get("errdetail")).contains("405 Method Not Allowed"),
                "errdetail 应被完整保留：" + body.get("errdetail"));
    }

    @Test
    @DisplayName("EOP 业务错误透出 errdetail（含下游原始返回）")
    void eopErrorSurfacesErrDetail() throws Exception {
        EopInterfaceAdapter adapter = new EopInterfaceAdapter(new AppStoreInterfaceProperties(), new ObjectMapper());
        Method m = EopInterfaceAdapter.class.getDeclaredMethod("assertNoEopError", String.class, Map.class);
        m.setAccessible(true);

        Map<String, Object> body = Map.of(
                "errcode", "310",
                "errmsg", "服务端内部异常",
                "errdetail", "该地址返回码：405，报文片段：'405 Method Not Allowed'");

        Exception ex = Assertions.assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> m.invoke(adapter, "notify", body));
        String msg = ex.getCause().getMessage();
        Assertions.assertTrue(msg.contains("errcode=310"), "应含 errcode：" + msg);
        Assertions.assertTrue(msg.contains("errmsg=服务端内部异常"), "应含 errmsg：" + msg);
        Assertions.assertTrue(msg.contains("errdetail=该地址返回码：405"), "应含 errdetail：" + msg);
    }
}
