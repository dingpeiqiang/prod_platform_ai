from fastapi import APIRouter, HTTPException, UploadFile, File
from pydantic import BaseModel
from typing import Dict, Any, List, Optional
import logging
import tempfile
import os
from pathlib import Path
from app.core.config_loader import config_loader
from app.services.ontology_service import OntologyService
from app.scripts.import_history import discover_forms, import_form_data

# 项目根目录
PROJECT_ROOT = Path(__file__).parent.parent.parent

logger = logging.getLogger("config_api")

router = APIRouter(prefix="/api/v1/config", tags=["config"])


class ReloadConfigRequest(BaseModel):
    configType: Optional[str] = None


class ConfigReloadResponse(BaseModel):
    success: bool
    message: str


class OntologyListResponse(BaseModel):
    success: bool
    ontologies: List[Dict[str, Any]]


class AppConfigResponse(BaseModel):
    success: bool
    config: Dict[str, Any]


class ImportableForm(BaseModel):
    formCode: str
    formName: str
    dataType: str
    dataFile: str
    hasSchema: bool
    description: str = ""


class ImportListResponse(BaseModel):
    success: bool
    forms: List[ImportableForm]


class ImportRequest(BaseModel):
    formCode: str
    limit: Optional[int] = None


class ImportFieldStat(BaseModel):
    fieldCode: str
    distinctValues: int
    topValues: List[Dict[str, Any]]


class ImportResponse(BaseModel):
    success: bool
    message: str
    formCode: str
    totalImported: int
    totalSource: int
    totalSkipped: int
    totalErrors: int
    fieldStats: List[ImportFieldStat] = []
    nestedFields: List[str] = []


@router.post("/reload", response_model=ConfigReloadResponse)
async def reload_config(request: ReloadConfigRequest):
    logger.info("[config/reload] 触发配置重载 configType=%s", request.configType or "all")
    try:
        config_loader.reload_config(request.configType)
        logger.info("[config/reload] 配置重载成功 configType=%s", request.configType or "all")
        return ConfigReloadResponse(
            success=True,
            message=f"配置已重新加载: {request.configType or 'all'}"
        )
    except Exception as e:
        logger.exception("[config/reload] 配置重载失败: %s", e)
        raise HTTPException(status_code=500, detail=f"配置重新加载失败: {str(e)}")


@router.get("/app", response_model=AppConfigResponse)
async def get_app_config():
    logger.debug("[config/app] 获取应用配置")
    try:
        config = config_loader.get_app_config()
        return AppConfigResponse(
            success=True,
            config=config
        )
    except Exception as e:
        logger.exception("[config/app] 获取配置失败: %s", e)
        raise HTTPException(status_code=500, detail=f"获取配置失败: {str(e)}")


@router.get("/ontologies", response_model=OntologyListResponse)
async def list_ontologies():
    logger.debug("[config/ontologies] 获取本体列表")
    try:
        result = OntologyService.get_all_ontologies()
        logger.debug("[config/ontologies] 返回 %d 个本体", len(result.get("ontologies", [])))
        return OntologyListResponse(
            success=result["success"],
            ontologies=result.get("ontologies", [])
        )
    except Exception as e:
        logger.exception("[config/ontologies] 获取本体列表失败: %s", e)
        raise HTTPException(status_code=500, detail=f"获取本体列表失败: {str(e)}")


@router.get("/import/list", response_model=ImportListResponse)
async def list_importable_forms():
    """列出所有可导入的历史数据表单"""
    logger.info("[config/import/list] 获取可导入表单列表")
    try:
        forms = discover_forms()
        importable = [ImportableForm(**f) for f in forms]
        logger.info("[config/import/list] 找到 %d 个可导入表单", len(importable))
        return ImportListResponse(
            success=True,
            forms=importable
        )
    except Exception as e:
        logger.exception("[config/import/list] 获取失败: %s", e)
        raise HTTPException(status_code=500, detail=f"获取可导入表单失败: {str(e)}")


@router.get("/import/template/{formCode}")
async def download_template(formCode: str):
    """
    下载导入模板文件

    基于本体定义动态生成模板，实时生成，不读取本地文件或缓存
    """
    from fastapi.responses import StreamingResponse
    import json

    # 加载本体定义
    ontologies_dir = PROJECT_ROOT / "config" / "ontologies"
    ontology_file = ontologies_dir / f"{formCode}.json"

    if not ontology_file.exists():
        logger.warning("[config/import/template] 未找到本体文件 formCode=%s", formCode)
        content = '# 历史数据模板\n# 未找到本体定义\n{"error": "本体不存在"}\n'
        return StreamingResponse(
            iter([content]),
            media_type='text/plain',
            headers={"Content-Disposition": f"attachment; filename={formCode}_template.jsonl"}
        )

    try:
        # 读取本体定义
        with open(ontology_file, 'r', encoding='utf-8') as f:
            ontology = json.load(f)

        form_name = ontology.get('formName', formCode)
        entities = ontology.get('entities', [])

        # 生成字段注释说明
        field_comments = []
        sample_data = {}

        for entity in entities:
            entity_name = entity.get('entityName', '')
            fields = entity.get('fields', [])
            for field in fields:
                field_code = field.get('fieldCode', '')
                field_name = field.get('fieldName', '')
                field_type = field.get('fieldType', '')
                required = field.get('required', False)
                options = field.get('options', [])

                # 添加字段说明
                required_mark = '(必填)' if required else '(选填)'
                field_comments.append(f"# {field_code}: {field_name} {required_mark} [{field_type}]")

                # 生成示例值
                sample_value = _generate_sample_value(field_type, options)
                sample_data[field_code] = sample_value

        # 构建模板内容
        template_lines = [
            f"# {form_name} 导入模板",
            "# 基于本体定义实时生成",
            f"# 共 {len(sample_data)} 个字段",
            "",
        ]
        template_lines.extend(field_comments)
        template_lines.append("")
        template_lines.append(json.dumps(sample_data, ensure_ascii=False))

        template_content = '\n'.join(template_lines) + '\n'
        logger.info("[config/import/template] 生成模板 formCode=%s, 字段数=%d", formCode, len(sample_data))

        return StreamingResponse(
            iter([template_content]),
            media_type='application/jsonl',
            headers={"Content-Disposition": f"attachment; filename={formCode}_template.jsonl"}
        )

    except Exception as e:
        logger.exception("[config/import/template] 生成模板失败: %s", e)
        content = f'# 历史数据模板\n# 生成失败: {str(e)}\n'
        return StreamingResponse(
            iter([content]),
            media_type='text/plain',
            headers={"Content-Disposition": f"attachment; filename={formCode}_template.jsonl"}
        )


def _generate_sample_value(field_type: str, options: list) -> str:
    """根据字段类型和选项生成示例值"""
    if options:
        # options 统一为对象格式 {"value":"xxx", "label":"xxx"}
        return options[0].get('value', 'option_value')

    # 根据字段类型生成示例值
    field_type_lower = field_type.lower()
    if field_type_lower == 'input':
        return '示例文本'
    elif field_type_lower == 'textarea':
        return '这是示例文本内容'
    elif field_type_lower in ('date', 'datetime'):
        return '2024-01-15'
    elif field_type_lower == 'number':
        return 100
    elif field_type_lower == 'integer':
        return 10
    elif field_type_lower == 'boolean':
        return True
    elif field_type_lower == 'email':
        return 'example@company.com'
    elif field_type_lower == 'phone':
        return '13800138000'
    else:
        return 'sample_value'


@router.post("/import/execute", response_model=ImportResponse)
async def execute_import(request: ImportRequest):
    """执行历史数据导入（从 import_data 目录）"""
    logger.info("[config/import/execute] 开始导入 formCode=%s limit=%s",
                request.formCode, request.limit)
    try:
        result = import_form_data(
            form_code=request.formCode,
            dry_run=False,
            limit=request.limit
        )

        if result.get('error'):
            logger.error("[config/import/execute] 导入失败: %s", result['error'])
            return ImportResponse(
                success=False,
                message=f"导入失败: {result['error']}",
                formCode=request.formCode,
                totalImported=0,
                totalSource=result.get('totalSource', 0),
                totalSkipped=result.get('totalSkipped', 0),
                totalErrors=result.get('totalErrors', 0)
            )

        # 转换字段统计信息
        field_stats = []
        for fc, values in result.get('fieldStats', {}).items():
            top_values = sorted(values.items(), key=lambda x: x[1], reverse=True)[:5]
            field_stats.append(ImportFieldStat(
                fieldCode=fc,
                distinctValues=len(values),
                topValues=[{"value": v, "count": c} for v, c in top_values]
            ))

        message = f"成功导入 {result['totalImported']} 条「{request.formCode}」历史数据"
        logger.info("[config/import/execute] %s", message)

        return ImportResponse(
            success=True,
            message=message,
            formCode=request.formCode,
            totalImported=result['totalImported'],
            totalSource=result['totalSource'],
            totalSkipped=result['totalSkipped'],
            totalErrors=result['totalErrors'],
            fieldStats=field_stats,
            nestedFields=result.get('nestedFields', [])
        )
    except Exception as e:
        logger.exception("[config/import/execute] 导入异常: %s", e)
        raise HTTPException(status_code=500, detail=f"导入失败: {str(e)}")


@router.post("/import/upload", response_model=ImportResponse)
async def upload_and_import(
    formCode: str,
    file: UploadFile = File(...),
    limit: Optional[int] = None
):
    """
    用户上传数据文件并立即导入
    
    流程：
    1. 接收用户上传的JSONL文件
    2. 保存到临时目录
    3. 执行导入
    4. 返回导入报告
    """
    logger.info("[config/import/upload] 收到上传 formCode=%s filename=%s",
                formCode, file.filename)
    
    # 验证文件类型
    if not file.filename.endswith('.jsonl'):
        raise HTTPException(status_code=400, detail="只支持 .jsonl 格式文件")
    
    # 创建临时目录
    temp_dir = Path(tempfile.mkdtemp())
    temp_file = temp_dir / f"{formCode}.data.jsonl"
    
    try:
        # 保存上传的文件
        content = await file.read()
        with open(temp_file, 'wb') as f:
            f.write(content)
        
        logger.info("[config/import/upload] 文件已保存: %s", temp_file)
        
        # 临时修改 DATA_DIR 指向临时目录
        from app.scripts import import_history as ih
        original_data_dir = ih.DATA_DIR
        ih.DATA_DIR = temp_dir
        
        try:
            # 执行导入
            result = import_form_data(
                form_code=formCode,
                dry_run=False,
                limit=limit
            )
        finally:
            # 恢复原始 DATA_DIR
            ih.DATA_DIR = original_data_dir
        
        if result.get('error'):
            logger.error("[config/import/upload] 导入失败: %s", result['error'])
            return ImportResponse(
                success=False,
                message=f"导入失败: {result['error']}",
                formCode=formCode,
                totalImported=0,
                totalSource=result.get('totalSource', 0),
                totalSkipped=result.get('totalSkipped', 0),
                totalErrors=result.get('totalErrors', 0)
            )
        
        # 转换字段统计信息
        field_stats = []
        for fc, values in result.get('fieldStats', {}).items():
            top_values = sorted(values.items(), key=lambda x: x[1], reverse=True)[:5]
            field_stats.append(ImportFieldStat(
                fieldCode=fc,
                distinctValues=len(values),
                topValues=[{"value": v, "count": c} for v, c in top_values]
            ))
        
        message = f"成功导入 {result['totalImported']} 条「{formCode}」历史数据"
        logger.info("[config/import/upload] %s", message)
        
        return ImportResponse(
            success=True,
            message=message,
            formCode=formCode,
            totalImported=result['totalImported'],
            totalSource=result['totalSource'],
            totalSkipped=result['totalSkipped'],
            totalErrors=result['totalErrors'],
            fieldStats=field_stats,
            nestedFields=result.get('nestedFields', [])
        )
        
    except Exception as e:
        logger.exception("[config/import/upload] 导入异常: %s", e)
        raise HTTPException(status_code=500, detail=f"导入失败: {str(e)}")
    finally:
        # 清理临时文件
        try:
            import shutil
            shutil.rmtree(temp_dir, ignore_errors=True)
            logger.info("[config/import/upload] 临时文件已清理")
        except Exception as e:
            logger.warning("[config/import/upload] 清理临时文件失败: %s", e)
