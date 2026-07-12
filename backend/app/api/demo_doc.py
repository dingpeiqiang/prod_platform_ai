"""
演示场景二 API - 智读・批量生成(方案文档批量开品)
前缀: /api/v1/demo/doc
"""
import os
import json
import uuid
import asyncio
from typing import Dict, Any, List, Optional
from pathlib import Path
from datetime import datetime
from fastapi import APIRouter, HTTPException, UploadFile, File, Query
from pydantic import BaseModel, Field

from app.core.logger import get_logger
from app.services.demo_data_service import demo_data_service
from app.services.llm_service import llm_service
from app.services.llm.base import extract_json

logger = get_logger(__name__)
router = APIRouter(prefix="/api/v1/demo/doc", tags=["演示-智读批量生成"])

UPLOAD_DIR = Path("uploads/demo")
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

PROMPT_PATH = Path(__file__).parent.parent / "prompts" / "demo_doc_parse.txt"


class ParseRequest(BaseModel):
    file_path: Optional[str] = Field(default=None, description="上传的文件路径")
    use_builtin: bool = Field(default=True, description="使用内置方案文档")


class BatchGenerateRequest(BaseModel):
    items: List[Dict[str, Any]] = Field(..., description="解析出的套餐列表")


class BatchSubmitRequest(BaseModel):
    configs: List[Dict[str, Any]] = Field(..., description="批量配置列表")


@router.post("/upload", summary="上传方案文档")
async def upload_document(
    file: UploadFile = File(...),
    session_id: Optional[str] = Query(None)
) -> Dict[str, Any]:
    """上传方案文档(支持 .txt, .docx, .pdf)"""
    logger.info(f"[DemoDoc] 上传文档: {file.filename}")

    file_ext = os.path.splitext(file.filename)[1].lower()
    if file_ext not in [".txt", ".docx", ".pdf", ".md"]:
        raise HTTPException(status_code=400, detail="仅支持 .txt, .docx, .pdf, .md 格式")

    new_filename = f"demo_{uuid.uuid4().hex}{file_ext}"
    file_path = UPLOAD_DIR / new_filename

    content = await file.read()
    with open(file_path, "wb") as f:
        f.write(content)

    logger.info(f"[DemoDoc] 文档保存: {file_path}, 大小: {len(content)} bytes")

    return {
        "success": True,
        "filename": file.filename,
        "file_path": str(file_path),
        "size": len(content),
        "message": "文档上传成功"
    }


@router.post("/parse", summary="AI解析文档提取套餐")
async def parse_document(request: ParseRequest) -> Dict[str, Any]:
    """AI解析方案文档,提取所有套餐信息"""
    logger.info(f"[DemoDoc] 解析文档: use_builtin={request.use_builtin}")

    if request.use_builtin or not request.file_path:
        document_content = demo_data_service.get_enterprise_proposal()
        source = "内置方案文档"
    else:
        file_path = Path(request.file_path)
        if not file_path.exists():
            raise HTTPException(status_code=404, detail="文件不存在")
        document_content = file_path.read_text(encoding="utf-8")
        source = file_path.name

    if not document_content:
        raise HTTPException(status_code=400, detail="文档内容为空")

    parsed_items = []
    ai_used = False

    if llm_service.enabled:
        try:
            prompt_template = PROMPT_PATH.read_text(encoding="utf-8")
            prompt = prompt_template.replace("{document_content}", document_content)
            response = llm_service._call_llm_sync(prompt)

            if response:
                parsed_data = extract_json(response)
                if isinstance(parsed_data, list):
                    parsed_items = parsed_items
                    ai_used = True
                    logger.info(f"[DemoDoc] AI解析成功,提取 {len(parsed_items)} 个套餐")
        except Exception as e:
            logger.warning(f"[DemoDoc] AI解析失败,使用内置数据: {e}")

    if not parsed_items:
        from app.data.demo.enterprise_lines import get_all_enterprise_packages
        packages = get_all_enterprise_packages()
        parsed_items = [
            {
                "package_name": p["package_name"],
                "target_audience": p["target_audience"],
                "bandwidth": p["bandwidth"],
                "upload_bandwidth": p.get("upload_bandwidth", ""),
                "monthly_fee": p["monthly_fee"],
                "contract_period": p["contract_period"],
                "static_ip_count": p.get("static_ip_count", 0),
                "sla_level": p.get("sla_level", ""),
                "acceptance_restrictions": p["acceptance_restrictions"],
                "promotional_activities": p["promotional_activities"]
            }
            for p in packages
        ]
        logger.info(f"[DemoDoc] 使用内置数据,共 {len(parsed_items)} 个套餐")

    return {
        "success": True,
        "source": source,
        "ai_used": ai_used,
        "items": parsed_items,
        "total": len(parsed_items),
        "message": f"成功解析出 {len(parsed_items)} 套套餐配置"
    }


@router.post("/batch-generate", summary="批量生成配置草稿")
async def batch_generate(request: BatchGenerateRequest) -> Dict[str, Any]:
    """根据解析结果批量生成结构化配置草稿"""
    logger.info(f"[DemoDoc] 批量生成配置: {len(request.items)} 个套餐")

    configs = []
    for idx, item in enumerate(request.items):
        config = {
            "package_id": f"PKG_ENT_DRAFT_{idx + 1:03d}",
            "package_name": item.get("package_name", f"政企专线套餐{idx + 1}"),
            "scene_type": "政企专线",
            "target_audience": item.get("target_audience", ""),
            "status": "草稿",
            "bandwidth": item.get("bandwidth", ""),
            "upload_bandwidth": item.get("upload_bandwidth", ""),
            "monthly_fee": item.get("monthly_fee", 0),
            "contract_period": item.get("contract_period", 12),
            "static_ip_count": item.get("static_ip_count", 0),
            "sla_level": item.get("sla_level", ""),
            "acceptance_restrictions": item.get("acceptance_restrictions", ""),
            "promotional_activities": item.get("promotional_activities", ""),
            "effective_rules": {
                "effective_type": "立即生效",
                "effective_delay": 0,
                "expiry_type": "合约到期自动失效"
            },
            "tariff_rules": [
                {"name": "基础月租", "amount": item.get("monthly_fee", 0), "cycle": "月"}
            ],
            "constraints": [
                {"type": "target_audience", "value": item.get("target_audience", ""), "description": f"仅限{item.get('target_audience', '')}办理"},
                {"type": "contract", "value": f"{item.get('contract_period', 12)}个月", "description": f"需签订{item.get('contract_period', 12)}个月合约"}
            ],
            "created_at": datetime.now().isoformat(),
            "created_by": "文档智读",
            "remark": ""
        }
        configs.append(config)

    return {
        "success": True,
        "configs": configs,
        "total": len(configs),
        "message": f"已批量生成 {len(configs)} 套配置草稿"
    }


@router.post("/batch-submit", summary="批量提交稽核")
async def batch_submit(request: BatchSubmitRequest) -> Dict[str, Any]:
    """批量提交配置进行稽核校验"""
    logger.info(f"[DemoDoc] 批量提交稽核: {len(request.configs)} 个套餐")

    results = []
    passed_count = 0

    for config in request.configs:
        package_id = config.get("package_id", "")
        package_name = config.get("package_name", "")

        errors = []
        warnings = []

        if not config.get("bandwidth"):
            errors.append({"field": "bandwidth", "message": "带宽不能为空"})
        if not config.get("monthly_fee") or config.get("monthly_fee") <= 0:
            errors.append({"field": "monthly_fee", "message": "月费必须大于0"})
        if not config.get("contract_period"):
            errors.append({"field": "contract_period", "message": "合约期不能为空"})

        if config.get("monthly_fee", 0) > 2000 and config.get("contract_period", 0) < 24:
            warnings.append({"field": "contract_period", "message": "高价位套餐建议合约期24个月以上"})

        is_valid = len(errors) == 0
        if is_valid:
            passed_count += 1

        results.append({
            "package_id": package_id,
            "package_name": package_name,
            "valid": is_valid,
            "errors": errors,
            "warnings": warnings,
            "record_id": f"REC_{package_id}_{uuid.uuid4().hex[:8].upper()}" if is_valid else None
        })

    return {
        "success": True,
        "results": results,
        "total": len(results),
        "passed": passed_count,
        "failed": len(results) - passed_count,
        "message": f"批量稽核完成: {passed_count} 通过, {len(results) - passed_count} 失败"
    }
