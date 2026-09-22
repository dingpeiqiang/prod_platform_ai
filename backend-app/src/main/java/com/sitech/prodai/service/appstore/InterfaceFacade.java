package com.sitech.prodai.service.appstore;

import java.util.Map;

/**
 * 产销品场景接口适配门面（统一方案 §4.5.4）——9 个现场接口的统一端口。
 * <p>
 * 由 {@code AppStoreV16Controller} 注入，运行期按配置在 {@code MockInterfaceAdapter}（开发）
 * 与 {@code EopInterfaceAdapter}（现场 EOP）间切换；两类实现出参结构同构（§4.5.5 第 4 点），
 * 工作流/插件/Controller 签名与出参不变。
 * <p>
 * 接口键：similar(A1)/upload(B1)/notify(B2)/insert(B3)/sql(B4)/testStart(C1)/testScenes(C2)/
 * testProgress(C3)/testResult(C4)。A1 与 C1~C4 兼具 mock/real；B1~B4 为现场新增能力。
 */
public interface InterfaceFacade {

    /** A1 相似度分析：{business_desc} → {resultCode, similar_offer_list[]}。 */
    Map<String, Object> similarOfferQuery(Map<String, Object> req);

    /** B1 智能稽核文件上传（multipart）：{fileName, bytes} → {file_id, sales_id{}}。 */
    Map<String, Object> auditFileUpload(Map<String, Object> req);

    /** B2 异步报告生成并通知：{sales_id, location, sheet_name, file_id} → {success, request_id, sales_id}。 */
    Map<String, Object> auditAsyncNotify(Map<String, Object> req);

    /** B3 插入 AI 稽核结果：{records[]} → {resultCode, resultMsg}。 */
    Map<String, Object> auditResultInsert(Map<String, Object> req);

    /** B4 通用 SQL 查询：{sql_id, params} → {resultCode, execute_sql_resp[]}（轮询完成状态 / 查稽核结果）。 */
    Map<String, Object> auditSqlQuery(Map<String, Object> req);

    /** C1 销售品测试发起：{offer_id} → {resultCode, global_id}。 */
    Map<String, Object> testOfferStart(Map<String, Object> req);

    /** C2 查询测试场景：{global_id} → {resultCode, test_scenes[]}。 */
    Map<String, Object> testScenes(Map<String, Object> req);

    /** C3 查询测试进度：{global_id} → {resultCode, total_steps, active_index, done, failed, ...}。 */
    Map<String, Object> testProgress(Map<String, Object> req);

    /** C4 查询测试结果：{global_id} → {resultCode, test_scenes[]（含逐场景测点/obj_test_scene_rel）, offer_id, offer_name, global_id}。 */
    Map<String, Object> testResult(Map<String, Object> req);

    /** 当前生效模式（mock|real|mixed），供 meta/健康检查显影。 */
    String mode();
}
