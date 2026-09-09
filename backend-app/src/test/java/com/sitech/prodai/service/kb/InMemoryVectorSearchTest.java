package com.sitech.prodai.service.kb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内嵌向量检索（方案 §6-B6）单元测试：
 * 中文 bigram 相关性排序、Top-K 截断、确定性复现、增删索引一致性、空查询/未命中语义。
 */
class InMemoryVectorSearchTest {

    private InMemoryVectorSearch search;

    @BeforeEach
    void setUp() {
        search = new InMemoryVectorSearch();
    }

    @Test
    void ranksRelevantDocFirst() {
        search.upsert("a", "备案规范", "资费类商品备案须提交资费方案说明，上架前 5 个工作日提交备案");
        search.upsert("b", "资费说明", "融合套餐档位按月费划分，轻量档畅享档旗舰档带宽基准");

        List<Map<String, Object>> hits = search.topK("备案 时限 工作日", 2);

        assertFalse(hits.isEmpty());
        assertEquals("a", hits.get(0).get("id"), "备案相关查询应命中备案文档优先");
    }

    @Test
    void topKTruncatesAndSortsDescending() {
        for (int i = 0; i < 5; i++) {
            search.upsert("d" + i, "商品文档" + i, "商品配置知识第" + i + "篇，含商品与配置要素");
        }

        List<Map<String, Object>> hits = search.topK("商品配置知识", 3);

        assertEquals(3, hits.size(), "Top-K 截断");
        for (int i = 1; i < hits.size(); i++) {
            double prev = (double) hits.get(i - 1).get("similarity");
            double cur = (double) hits.get(i).get("similarity");
            assertTrue(prev >= cur, "相似度降序");
        }
    }

    @Test
    void deterministicForSameQuery() {
        search.upsert("a", "备案规范", "促销活动须在开始前备案并标注适用地市");
        search.upsert("b", "资费说明", "合约期分 12 24 36 个月三档");

        List<Map<String, Object>> first = search.topK("促销备案", 2);
        List<Map<String, Object>> second = search.topK("促销备案", 2);

        assertEquals(first, second, "同输入同输出（确定性，可复现）");
    }

    @Test
    void removeDropsDocFromIndex() {
        search.upsert("a", "备案规范", "资费备案说明");
        search.upsert("b", "资费说明", "资费档位说明");

        search.remove("a");
        List<Map<String, Object>> hits = search.topK("资费", 2);

        assertTrue(hits.stream().noneMatch(h -> "a".equals(h.get("id"))), "注销后不再命中");
        assertFalse(hits.isEmpty(), "其余文档仍可检索");
    }

    @Test
    void upsertOverwritesSameId() {
        search.upsert("a", "旧标题", "旧内容关于宽带");
        search.upsert("a", "新标题", "新内容关于合约");

        List<Map<String, Object>> hits = search.topK("合约", 2);

        assertFalse(hits.isEmpty());
        assertEquals("a", hits.get(0).get("id"), "同 id 覆盖后命中新内容");
        // 旧内容不再主导
        List<Map<String, Object>> oldHits = search.topK("宽带", 2);
        assertTrue(oldHits.isEmpty() || !"a".equals(oldHits.get(0).get("id")) || oldHits.size() > 1,
                "覆盖后旧内容不再主导（覆盖语义）");
    }

    @Test
    void emptyOrMissQueryReturnsEmpty() {
        search.upsert("a", "备案规范", "资费备案说明");

        assertTrue(search.topK("", 3).isEmpty(), "空查询返回空集");
        assertTrue(search.topK("   ", 3).isEmpty(), "空白查询返回空集");
        assertTrue(search.topK("完全不相关词汇xyz", 3).isEmpty(), "未命中返回空集（不冒充低分结果）");
    }

    @Test
    void emptyIndexReturnsEmpty() {
        assertTrue(search.topK("任意查询", 3).isEmpty(), "空索引返回空集");
    }
}
