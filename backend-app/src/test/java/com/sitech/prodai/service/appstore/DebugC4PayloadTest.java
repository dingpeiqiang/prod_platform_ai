package com.sitech.prodai.service.appstore;

import com.sitech.prodai.service.common.MapOps;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

@SpringBootTest
class DebugC4PayloadTest {

    @Autowired
    private InterfaceFacade interfaceFacade;

    @Test
    void dumpC4Payload() {
        String lostGlobalId = "50202609282105416868245365";
        Map<String, Object> result = interfaceFacade.testResult(Map.of("global_id", lostGlobalId));
        Object ro = result.get("resultObject");
        System.out.println("=== C4 resultCode=" + result.get("resultCode") + " msg=" + result.get("resultMsg"));
        if (ro instanceof Map<?, ?> m) {
            System.out.println("=== resultObject keys=" + m.keySet());
            System.out.println("=== offerId=" + m.get("offerId"));
            System.out.println("=== offerName=" + m.get("offerName"));
            System.out.println("=== testRequestName=" + m.get("testRequestName"));
        }
        List<Map<String, Object>> scenes = MapOps.castListOfMaps(ro instanceof Map ? ((Map<?, ?>) ro).get("testScenes") : null);
        System.out.println("=== scene count=" + scenes.size());
        if (!scenes.isEmpty()) {
            System.out.println("=== scene0 keys=" + scenes.get(0).keySet());
        }
    }
}
