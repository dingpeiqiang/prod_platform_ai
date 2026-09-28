package com.sitech.prodai.service.appstore;

import com.sitech.prodai.config.AppStoreInterfaceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * A1/B4 信封报文测试：tcpCont 的 svcCode 等字段须按配置填充（svcCode 为空时 EOP 报「请求报文svcCode为空」）。
 */
@DisplayName("A1/B4 信封报文")
class EopEnvelopeRequestTest {

    @SuppressWarnings("unchecked")
    private Map<String, Object> envelope(AppStoreInterfaceProperties props) throws Exception {
        EopInterfaceAdapter adapter = new EopInterfaceAdapter(props, new ObjectMapper(), null, null);
        Method m = EopInterfaceAdapter.class.getDeclaredMethod("envelopeRequest", Map.class);
        m.setAccessible(true);
        return (Map<String, Object>) m.invoke(adapter, Map.of("sqlId", "20260626001"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tcpCont(Map<String, Object> payload) {
        Map<String, Object> contractRoot = (Map<String, Object>) payload.get("contractRoot");
        return (Map<String, Object>) contractRoot.get("tcpCont");
    }

    @Test
    @DisplayName("默认配置下 tcpCont.svcCode 等字段非空")
    void envelopeFieldsFromConfig() throws Exception {
        Map<String, Object> payload = envelope(new AppStoreInterfaceProperties());
        Map<String, Object> tcp = tcpCont(payload);

        Assertions.assertEquals("5012010056", tcp.get("svcCode"), "svcCode 应非空且取自配置");
        Assertions.assertEquals("eOrder1", tcp.get("appKey"), "appKey 应取自配置");
        Assertions.assertEquals("OrderCenter", tcp.get("dstSysId"), "dstSysId 应取自配置");
        Assertions.assertNotEquals("", tcp.get("transactionId"), "transactionId 应生成");
        Assertions.assertNotNull(tcp.get("reqTime"));

        Map<String, Object> svcCont = (Map<String, Object>) ((Map<String, Object>) payload.get("contractRoot"))
                .get("svcCont");
        Assertions.assertNotNull(svcCont.get("requestObject"), "应含 svcCont.requestObject");
    }

    @Test
    @DisplayName("配置覆盖生效：svcCode 自定义值透传")
    void envelopeRespectsOverride() throws Exception {
        AppStoreInterfaceProperties props = new AppStoreInterfaceProperties();
        props.getEop().setSvcCode("CUSTOM_SVC");
        props.getEop().setDstSysId("CUSTOM_SYS");

        Map<String, Object> tcp = tcpCont(envelope(props));
        Assertions.assertEquals("CUSTOM_SVC", tcp.get("svcCode"));
        Assertions.assertEquals("CUSTOM_SYS", tcp.get("dstSysId"));
    }
}
