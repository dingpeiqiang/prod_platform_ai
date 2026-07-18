"""本体 MVP 推理服务单元测试"""
from app.services.ontology_mvp_service import OntologyMvpService as S


def test_chat_configure_family_fusion_infer_and_missing_name():
    result = S.chat_configure("给家庭用户做一个融合套餐，月费158，带500M宽带，全渠道销售")
    assert result["draft"]["bizScenario"] == "家庭融合"
    assert result["draft"]["includeVoice"] == "500分钟"
    assert result["draft"]["includeData"] == "40GB"
    assert result["draft"]["includeBroadband"] == "500M"
    assert result["compliancePass"] is False
    assert any(i["ruleId"] == "R-C06" for i in result["issues"])


def test_chat_configure_name_then_mutex_block():
    step1 = S.chat_configure("给家庭用户做一个融合套餐，月费158，带500M宽带，全渠道销售")
    step2 = S.chat_configure("就叫家庭融合畅享158", step1["draft"])
    assert step2["draft"]["offeringName"] == "家庭融合畅享158"
    assert step2["compliancePass"] is True

    step3 = S.chat_configure("再绑一个畅享128主套餐一起卖", step2["draft"])
    assert any(i["ruleId"] == "R-C03" for i in step3["issues"])
    assert step3["compliancePass"] is False

    step4 = S.chat_configure("那不加128了，就单独上158", step3["draft"])
    assert not step4["draft"].get("bindExistingMainPkg")
    assert step4["compliancePass"] is True
    assert not any(i["ruleId"] == "R-C03" for i in step4["issues"])


def test_batch_campus_document():
    batch = S.batch_from_document("校园迎新产商品方案")
    assert batch["total"] == 3
    assert batch["passedCount"] >= 1
    assert batch["pendingCount"] >= 1
    statuses = {item["status"] for item in batch["items"]}
    assert "通过" in statuses
    assert "待修正" in statuses
    pkg_c = next(i for i in batch["items"] if "加装" in (i["draft"].get("offeringName") or ""))
    assert any(i["ruleId"] == "R-C06" for i in pkg_c["issues"])
    assert any(i["ruleId"] == "R-C04" for i in pkg_c["issues"])
    pkg_b = next(i for i in batch["items"] if "0元" in (i["draft"].get("offeringName") or ""))
    assert any(i["ruleId"] == "R-C05" for i in pkg_b["issues"])


def test_root_cause_top3_matches_demo_script():
    """验收：Top3 根因权重与演示话术一致（营业厅0.42 / 加装礼0.31 / 友商0.18）"""
    root = S.analyze_root_cause("OF-HF-128")
    assert root["success"] is True
    assert len(root["paths"]) == 3
    assert root["paths"][0]["name"] == "营业厅"
    assert root["paths"][0]["weight"] == 0.42
    assert root["paths"][0]["ruleId"] == "R-A02"
    assert root["paths"][1]["name"] == "家庭融合加装礼"
    assert root["paths"][1]["weight"] == 0.31
    assert root["paths"][2]["name"] == "友商融合120"
    assert root["paths"][2]["weight"] == 0.18
    assert root["workOrder"]["offeringId"] == "OF-HF-128"
    assert root["evidenceTriples"]
    assert root["reportEvidence"]["anomaly"]["delta"] == -0.18
    assert "R-A01" in root["appliedRules"]


def test_risk_audit_demo_acceptance():
    """验收：演示口径 高风险13/中风险7/建议下架7；0元100%命中；白名单不误报"""
    S.reset_risk_rules()
    risk = S.audit_risks()
    assert risk["scannedCount"] == 80
    assert risk["highCount"] == 13
    assert risk["mediumCount"] == 7
    assert risk["suggestDelistCount"] == 7
    assert risk["ruleVersion"] == "RiskRules-v1.2"

    # 白名单权益赠送不误报
    assert not any(i["offeringId"] == "OF-GIFT-WL" for i in risk["items"])

    # 所有零元高危均命中
    zero_ids = [i["offeringId"] for i in risk["items"] if any(r["feature"] == "零元资费" for r in i["risks"])]
    assert "OF-RISK-001" in zero_ids
    assert len([oid for oid in zero_ids if oid.startswith("OF-RISK-")]) == 8

    r001 = next(i for i in risk["items"] if i["offeringId"] == "OF-RISK-001")
    assert r001["riskLevel"] == "HIGH"
    assert r001["urgent"] is True
    assert r001["suggestDelist"] is False
    assert any(r["ruleId"] == "R-B05" for r in r001["risks"])
    assert r001["evidenceTriples"]

    low = next(i for i in risk["items"] if i["offeringId"] == "OF-LOW-019")
    assert low["shelfDays"] == 287
    assert low["salesCnt30d"] == 0
    assert any(r["feature"] == "长期零销" for r in low["risks"])
    assert low["suggestDelist"] is True
    assert low["riskLevel"] == "MEDIUM"


def test_risk_rule_threshold_reconfigurable():
    """验收：零销阈值 180→90 清单变化（规则可配置）"""
    S.reset_risk_rules()
    before = S.audit_risks()
    before_b03 = sum(1 for i in before["items"] if any(r["ruleId"] == "R-B03" for r in i["risks"]))
    S.update_risk_rules({"zeroSalesShelfDays": 90})
    after = S.audit_risks()
    after_b03 = sum(1 for i in after["items"] if any(r["ruleId"] == "R-B03" for r in i["risks"]))
    assert after["riskRules"]["zeroSalesShelfDays"] == 90
    assert after_b03 > before_b03
    # 阈值演示样本：在架约 112 天，180 不命中、90 命中
    demo = next(i for i in after["items"] if i["offeringId"] == "OF-LOW-T01")
    assert any(r["ruleId"] == "R-B03" for r in demo["risks"])
    S.reset_risk_rules()


def test_ops_dashboard():
    dash = S.get_ops_dashboard()
    assert dash["anomalyOfferingCount"] == 1
    assert dash["highRiskCount"] >= 1
    assert dash["alerts"]
    assert "OF-HF-128" in dash["alerts"][0]["text"]
