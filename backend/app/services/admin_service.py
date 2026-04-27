"""
管理模块 Service 层
负责：表单本体 CRUD、场景关键词管理、配置热重载、AI 配置生成
"""
import json
import logging
import os
from pathlib import Path
from typing import Dict, Any, List, Optional

from app.core.config_loader import config_loader

logger = logging.getLogger("admin_service")

# 配置根路径
_BASE_DIR = Path(__file__).parent.parent.parent / "config"


class AdminService:

    # ────────────────────────────────────────────────────────────────────────
    # 表单本体（Ontology）管理
    # ────────────────────────────────────────────────────────────────────────

    @classmethod
    def list_ontologies(cls) -> Dict[str, Any]:
        """获取所有表单配置列表"""
        try:
            all_ontologies = config_loader.get_all_ontologies()
            ontologies = list(all_ontologies.values())
            # 注入字段数量统计
            for o in ontologies:
                total_fields = sum(
                    len(e.get("fields", [])) for e in o.get("entities", [])
                )
                o["_fieldCount"] = total_fields
            logger.info("[AdminService] 获取表单列表 count=%d", len(ontologies))
            return {"success": True, "total": len(ontologies), "data": ontologies}
        except Exception as e:
            logger.exception("[AdminService] 获取表单列表失败: %s", e)
            return {"success": False, "total": 0, "data": [], "message": str(e)}

    @classmethod
    def get_ontology(cls, form_code: str) -> Dict[str, Any]:
        """获取单个表单配置"""
        data = config_loader.get_ontology(form_code)
        if data is None:
            return {"success": False, "data": None, "message": f"表单 {form_code} 不存在"}
        return {"success": True, "data": data}

    @classmethod
    def create_ontology(cls, payload: Dict[str, Any]) -> Dict[str, Any]:
        """新建表单本体，写入 config/ontologies/{formCode}.json"""
        form_code = payload.get("formCode", "").strip()
        if not form_code:
            return {"success": False, "message": "formCode 不能为空"}

        # 检查是否已存在
        if config_loader.get_ontology(form_code) is not None:
            return {"success": False, "message": f"表单编码 '{form_code}' 已存在，请使用不同的编码"}

        return cls._write_ontology_file(form_code, payload)

    @classmethod
    def update_ontology(cls, form_code: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        """更新表单本体"""
        existing = config_loader.get_ontology(form_code)
        if existing is None:
            return {"success": False, "message": f"表单 {form_code} 不存在"}

        # 合并更新（保留未提交字段）
        merged = {**existing}
        if "formName" in payload and payload["formName"] is not None:
            merged["formName"] = payload["formName"]
        if "description" in payload and payload["description"] is not None:
            merged["description"] = payload["description"]
        if "entities" in payload and payload["entities"] is not None:
            merged["entities"] = payload["entities"]

        return cls._write_ontology_file(form_code, merged)

    @classmethod
    def delete_ontology(cls, form_code: str) -> Dict[str, Any]:
        """删除表单本体"""
        existing = config_loader.get_ontology(form_code)
        if existing is None:
            return {"success": False, "message": f"表单 {form_code} 不存在"}

        file_path = _BASE_DIR / "ontologies" / f"{form_code}.json"
        try:
            file_path.unlink()
            config_loader.reload_config("ontologies")
            logger.info("[AdminService] 删除表单 form_code=%s", form_code)
            return {"success": True, "message": f"表单 {form_code} 已删除"}
        except Exception as e:
            logger.exception("[AdminService] 删除表单失败 form_code=%s: %s", form_code, e)
            return {"success": False, "message": str(e)}

    @classmethod
    def _write_ontology_file(cls, form_code: str, data: Dict[str, Any]) -> Dict[str, Any]:
        """将表单本体写入 JSON 文件并热重载"""
        ontologies_dir = _BASE_DIR / "ontologies"
        ontologies_dir.mkdir(parents=True, exist_ok=True)
        file_path = ontologies_dir / f"{form_code}.json"
        try:
            with open(file_path, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            # 热重载配置
            config_loader.reload_config("ontologies")
            logger.info("[AdminService] 写入表单配置 form_code=%s path=%s", form_code, file_path)
            return {"success": True, "data": data, "message": "操作成功"}
        except Exception as e:
            logger.exception("[AdminService] 写入表单配置失败 form_code=%s: %s", form_code, e)
            return {"success": False, "message": str(e)}

    # ────────────────────────────────────────────────────────────────────────
    # 场景关键词（Scene Mapping）管理
    # ────────────────────────────────────────────────────────────────────────

    @classmethod
    def get_scene_mappings(cls) -> Dict[str, Any]:
        """获取当前场景关键词配置"""
        try:
            scenes_dir = _BASE_DIR / "scenes"
            file_path = scenes_dir / "scene_mapping.json"
            if not file_path.exists():
                return {"success": True, "data": {"sceneMappings": [], "defaultScene": "generic"}}
            with open(file_path, "r", encoding="utf-8") as f:
                data = json.load(f)
            return {"success": True, "data": data}
        except Exception as e:
            logger.exception("[AdminService] 获取场景配置失败: %s", e)
            return {"success": False, "data": None, "message": str(e)}

    @classmethod
    def update_scene_mappings(cls, payload: Dict[str, Any]) -> Dict[str, Any]:
        """更新场景关键词配置"""
        scenes_dir = _BASE_DIR / "scenes"
        scenes_dir.mkdir(parents=True, exist_ok=True)
        file_path = scenes_dir / "scene_mapping.json"
        try:
            with open(file_path, "w", encoding="utf-8") as f:
                json.dump(payload, f, ensure_ascii=False, indent=2)
            config_loader.reload_config("scene_mappings")
            logger.info("[AdminService] 更新场景配置 mappings=%d", len(payload.get("sceneMappings", [])))
            return {"success": True, "data": payload, "message": "场景配置已更新"}
        except Exception as e:
            logger.exception("[AdminService] 更新场景配置失败: %s", e)
            return {"success": False, "message": str(e)}

    # ────────────────────────────────────────────────────────────────────────
    # 统计信息
    # ────────────────────────────────────────────────────────────────────────

    @classmethod
    def get_stats(cls) -> Dict[str, Any]:
        """获取系统统计信息"""
        try:
            all_ontologies = config_loader.get_all_ontologies()
            scene_mappings = config_loader.get_scene_mappings()

            total_fields = 0
            for o in all_ontologies.values():
                for e in o.get("entities", []):
                    total_fields += len(e.get("fields", []))

            return {
                "success": True,
                "data": {
                    "formCount": len(all_ontologies),
                    "sceneCount": len(scene_mappings),
                    "totalFields": total_fields,
                    "formList": [
                        {
                            "formCode": code,
                            "formName": o.get("formName", ""),
                            "fieldCount": sum(len(e.get("fields", [])) for e in o.get("entities", []))
                        }
                        for code, o in all_ontologies.items()
                    ]
                }
            }
        except Exception as e:
            logger.exception("[AdminService] 获取统计信息失败: %s", e)
            return {"success": False, "data": None, "message": str(e)}

    # ────────────────────────────────────────────────────────────────────────
    # AI 配置生成（原 AdminAiService 功能，合并到此处）
    # ────────────────────────────────────────────────────────────────────────

    # ── 配置生成 Prompt ──────────────────────────────────────────────────────

    _CONFIG_SYSTEM_PROMPT = """你是一个专业的业务表单设计助手。用户会描述他们想要的业务表单，你需要根据描述生成完整的表单本体配置。

## 输出要求

请严格以 JSON 格式返回，不要包含其他文本。返回结构如下：

```json
{
  "hasConfig": true,
  "config": {
    "formCode": "表单编码（snake_case格式，如 contract_review）",
    "formName": "表单中文名称",
    "description": "业务场景描述",
    "entities": [
      {
        "entityCode": "实体编码（snake_case）",
        "entityName": "实体中文名",
        "fields": [
          {
            "fieldCode": "字段编码（snake_case）",
            "fieldName": "字段中文名",
            "fieldType": "字段类型",
            "required": true/false,
            "options": ["选项1", "选项2"]  // 仅 select/radio/checkbox 类型需要
          }
        ]
      }
    ]
  },
  "validationErrors": ["校验问题列表，没有则为空数组"],
  "reply": "对用户的简短回复，说明已生成配置"
}
```

## 字段类型说明
- input: 单行文本
- textarea: 多行文本
- select: 下拉选择（必须提供 options）
- radio: 单选按钮（必须提供 options）
- checkbox: 复选框（必须提供 options）
- date: 日期选择器
- number: 数字输入
- email: 邮箱
- phone: 手机号

## 设计原则
1. 实体分组要合理：如"申请人信息"、"审批信息"、"业务详情"等
2. 字段命名要语义清晰，使用中文 fieldName
3. 必填字段要审慎设置，通常核心业务字段设为必填
4. select/radio/checkbox 必须提供合理的选项列表
5. 根据业务常识设计合理的字段，用户描述不清的地方可以自行补充
6. formCode 必须是 snake_case 格式，不能有空格和特殊字符

## 如果用户的描述不足以生成完整配置
返回：
```json
{
  "hasConfig": false,
  "reply": "追问用户以获取更多信息"
}
```
"""

    _KEYWORDS_PROMPT_TEMPLATE = """请为以下业务表单生成 3-8 个场景关键词，用于智能匹配用户意图。

表单编码：{form_code}
表单名称：{form_name}
业务描述：{description}

要求：
1. 关键词应该是用户在对话中可能使用的自然语言表达
2. 包含表单名称本身和常见的同义词/简称
3. 涵盖该业务场景的核心操作动词

请严格以 JSON 格式返回：
{{"keywords": ["关键词1", "关键词2", "关键词3"]}}"""

    @classmethod
    def chat(cls, messages: List[Dict[str, str]]) -> Dict[str, Any]:
        """
        与 AI 对话，生成新表单配置。

        Args:
            messages: 对话消息列表 [{"role": "user/assistant", "content": "..."}]

        Returns:
            {"success": True, "hasConfig": bool, "config": {...}, "reply": "...", "validationErrors": [...]}
        """
        try:
            from app.services.llm_service import llm_service

            if not llm_service.enabled:
                return {"success": False, "reply": "LLM 服务未启用，无法生成配置"}

            # 构建对话 prompt
            conversation_text = ""
            for msg in messages:
                role = msg.get("role", "user")
                content = msg.get("content", "")
                if role == "user":
                    conversation_text += f"用户：{content}\n"
                else:
                    conversation_text += f"助手：{content}\n"

            prompt = f"以下是用户与助手的对话记录，请根据最后一条用户消息生成表单配置：\n\n{conversation_text}"

            result_text = llm_service._call_llm(prompt, system_prompt=cls._CONFIG_SYSTEM_PROMPT)

            if not result_text:
                return {"success": False, "reply": "AI 未返回结果，请重试"}

            # 解析 JSON
            parsed = llm_service._extract_json(result_text)
            if not parsed:
                logger.warning("[AdminService] AI 返回无法解析: %s", result_text[:200])
                return {"success": False, "reply": "配置生成格式异常，请重新描述需求"}

            has_config = parsed.get("hasConfig", False)
            if has_config:
                config_data = parsed.get("config", {})
                validation_errors = parsed.get("validationErrors", [])
                reply = parsed.get("reply", "")

                # 基本校验
                if not config_data.get("formCode"):
                    validation_errors.append("formCode 不能为空")
                if not config_data.get("formName"):
                    validation_errors.append("formName 不能为空")
                if not config_data.get("entities"):
                    validation_errors.append("entities 不能为空")

                logger.info("[AdminService] 配置生成成功 formCode=%s", config_data.get("formCode"))
                return {
                    "success": True,
                    "hasConfig": True,
                    "config": config_data,
                    "validationErrors": validation_errors,
                    "reply": reply
                }
            else:
                reply = parsed.get("reply", "请描述您想创建的表单类型。")
                return {"success": True, "hasConfig": False, "reply": reply}

        except Exception as e:
            logger.exception("[AdminService] AI 配置生成失败: %s", e)
            return {"success": False, "reply": f"配置生成失败: {str(e)}"}

    @classmethod
    def generate_scene_keywords(cls, form_code: str, form_name: str, description: str) -> Dict[str, Any]:
        """
        用 AI 为指定表单生成场景关键词。

        Args:
            form_code: 表单编码
            form_name: 表单名称
            description: 业务描述

        Returns:
            {"success": True, "keywords": ["关键词1", "关键词2", ...]}
        """
        try:
            from app.services.llm_service import llm_service

            if not llm_service.enabled:
                # LLM 不可用时，返回基础关键词
                return {"success": True, "keywords": [form_name, form_code]}

            prompt = cls._KEYWORDS_PROMPT_TEMPLATE.format(
                form_code=form_code,
                form_name=form_name,
                description=description or form_name
            )

            result_text = llm_service._call_llm(prompt)
            if not result_text:
                return {"success": True, "keywords": [form_name, form_code]}

            parsed = llm_service._extract_json(result_text)
            if parsed and "keywords" in parsed:
                keywords = parsed["keywords"]
                if isinstance(keywords, list) and len(keywords) > 0:
                    logger.info("[AdminService] 生成关键词 formCode=%s count=%d", form_code, len(keywords))
                    return {"success": True, "keywords": keywords}

            # 解析失败，返回基础关键词
            return {"success": True, "keywords": [form_name, form_code]}

        except Exception as e:
            logger.exception("[AdminService] 关键词生成失败: %s", e)
            return {"success": True, "keywords": [form_name, form_code]}
