package com.sitech.prodai.service.ops;

import java.util.Map;

/**
 * 产商品运营事实图数据源。统一返回与 mock_graph 同构的 raw graph：
 * shelfOfferings / opsGraph / bizScenarios / templates / ...
 */
public interface OpsProductDataSource {

    /** 数据源标识，写入 API 的 dataSource 字段。 */
    String sourceId();

    /**
     * 加载原始事实图（不做演示造数扩容）。
     * @return 非 null；无数据时返回空结构
     */
    Map<String, Object> loadRawGraph();
}
