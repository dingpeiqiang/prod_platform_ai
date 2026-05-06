"""
管理模块 Service 层
负责：表单本体 CRUD、场景关键词管理、配置热重载、AI 配置生成、版本备份与回退
"""
import json
import logging
import os
import shutil
from datetime import datetime
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
    def delete_ontology(cls, form_code: str, auto_backup: bool = True) -> Dict[str, Any]:
        """删除表单本体（自动备份到版本历史）"""
        existing = config_loader.get_ontology(form_code)
        if existing is None:
            return {"success": False, "message": f"表单 {form_code} 不存在"}

        # 自动备份当前版本
        backup_info = None
        if auto_backup:
            backup_result = cls.create_version(form_code, action="delete")
            if backup_result.get("success"):
                backup_info = backup_result.get("version")
                logger.info("[AdminService] 删除前自动备份 form_code=%s version_id=%s", form_code, backup_info.get("id") if backup_info else "N/A")

        # 删除本体文件
        file_path = _BASE_DIR / "ontologies" / f"{form_code}.json"
        try:
            file_path.unlink()

            # 从场景映射中移除该表单
            cls._remove_from_scene_mappings(form_code)

            config_loader.reload_config("ontologies")
            config_loader.reload_config("scene_mappings")

            logger.info("[AdminService] 删除表单 form_code=%s (已备份)", form_code)
            return {
                "success": True,
                "message": f"表单 {form_code} 已删除",
                "backup": backup_info
            }
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
    # 版本管理（备份、历史、回退）
    # ────────────────────────────────────────────────────────────────────────

    @classmethod
    def create_version(cls, form_code: str, action: str = "update") -> Dict[str, Any]:
        """
        为指定表单创建版本备份。

        Args:
            form_code: 表单编码
            action: 触发版本的操作类型（update/delete/create）

        Returns:
            {"success": True, "version": {"id": "...", "timestamp": "...", "action": "..."}}
        """
        existing = config_loader.get_ontology(form_code)
        if existing is None:
            return {"success": False, "message": f"表单 {form_code} 不存在，无法备份"}

        versions_dir = _BASE_DIR / "versions" / form_code
        versions_dir.mkdir(parents=True, exist_ok=True)

        # 生成版本 ID：时间戳 + 操作类型
        now = datetime.now()
        version_id = now.strftime("%Y%m%d_%H%M%S")
        version_file = versions_dir / f"{version_id}.json"

        # 版本元数据
        version_meta = {
            "id": version_id,
            "formCode": form_code,
            "formName": existing.get("formName", ""),
            "action": action,
            "timestamp": now.isoformat(),
            "data": existing
        }

        try:
            with open(version_file, "w", encoding="utf-8") as f:
                json.dump(version_meta, f, ensure_ascii=False, indent=2)
            logger.info("[AdminService] 创建版本 form_code=%s version_id=%s action=%s", form_code, version_id, action)
            return {"success": True, "version": version_meta}
        except Exception as e:
            logger.exception("[AdminService] 创建版本失败 form_code=%s: %s", form_code, e)
            return {"success": False, "message": str(e)}

    @classmethod
    def list_versions(cls, form_code: str) -> Dict[str, Any]:
        """
        获取指定表单的版本历史列表。

        Returns:
            {"success": True, "versions": [{"id": "...", "timestamp": "...", "action": "..."}, ...]}
        """
        versions_dir = _BASE_DIR / "versions" / form_code
        if not versions_dir.exists():
            return {"success": True, "versions": [], "formCode": form_code}

        versions = []
        for vf in sorted(versions_dir.glob("*.json"), reverse=True):
            try:
                with open(vf, "r", encoding="utf-8") as f:
                    meta = json.load(f)
                versions.append({
                    "id": meta.get("id", vf.stem),
                    "formCode": meta.get("formCode", form_code),
                    "formName": meta.get("formName", ""),
                    "action": meta.get("action", "unknown"),
                    "timestamp": meta.get("timestamp", ""),
                })
            except Exception:
                logger.warning("[AdminService] 版本文件损坏: %s", vf)

        logger.info("[AdminService] 获取版本列表 form_code=%s count=%d", form_code, len(versions))
        return {"success": True, "versions": versions, "formCode": form_code}

    @classmethod
    def get_version(cls, form_code: str, version_id: str) -> Dict[str, Any]:
        """获取指定版本的完整数据"""
        version_file = _BASE_DIR / "versions" / form_code / f"{version_id}.json"
        if not version_file.exists():
            return {"success": False, "message": f"版本 {version_id} 不存在"}

        try:
            with open(version_file, "r", encoding="utf-8") as f:
                meta = json.load(f)
            return {"success": True, "version": meta}
        except Exception as e:
            logger.exception("[AdminService] 读取版本失败: %s", e)
            return {"success": False, "message": str(e)}

    @classmethod
    def rollback_version(cls, form_code: str, version_id: str) -> Dict[str, Any]:
        """
        回退到指定版本。

        流程：
        1. 备份当前版本（如果存在）
        2. 从版本文件恢复数据
        3. 写入本体文件并热重载
        """
        # 读取目标版本
        version_file = _BASE_DIR / "versions" / form_code / f"{version_id}.json"
        if not version_file.exists():
            return {"success": False, "message": f"版本 {version_id} 不存在"}

        try:
            with open(version_file, "r", encoding="utf-8") as f:
                meta = json.load(f)
            target_data = meta.get("data")
            if not target_data:
                return {"success": False, "message": f"版本 {version_id} 数据为空"}

            # 备份当前版本（如果还存在）
            current = config_loader.get_ontology(form_code)
            if current is not None:
                cls.create_version(form_code, action="rollback")

            # 恢复目标版本
            result = cls._write_ontology_file(form_code, target_data)
            if result.get("success"):
                logger.info("[AdminService] 回退成功 form_code=%s → version_id=%s", form_code, version_id)
                return {
                    "success": True,
                    "message": f"已回退到版本 {version_id}",
                    "data": target_data
                }
            else:
                return result

        except Exception as e:
            logger.exception("[AdminService] 回退失败 form_code=%s: %s", form_code, e)
            return {"success": False, "message": str(e)}

    @classmethod
    def _remove_from_scene_mappings(cls, form_code: str):
        """从场景映射中移除指定表单的关键词条目"""
        scene_result = cls.get_scene_mappings()
        if not scene_result.get("success"):
            return

        mappings_data = scene_result.get("data", {})
        scene_mappings = mappings_data.get("sceneMappings", [])

        # 过滤掉该 formCode 的条目
        original_count = len(scene_mappings)
        scene_mappings = [m for m in scene_mappings if m.get("formCode") != form_code]

        if len(scene_mappings) < original_count:
            mappings_data["sceneMappings"] = scene_mappings
            cls.update_scene_mappings(mappings_data)
            logger.info("[AdminService] 从场景映射移除 form_code=%s (删除 %d 条)", form_code, original_count - len(scene_mappings))

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

## 日期规范（重要）
- 日期格式：必须使用 **YYYY-MM-DD** 标准格式，如 2026-05-06
- 日期默认值：基于用户 prompt 中隐含或明确的日期语义生成合理默认值
  - "今天" → 当前日期（用户 prompt 中会提供）
  - 报销、订单等业务日期 → 建议默认"今天"或近期日期
  - 生效日期、开始日期 → 通常为今天或明天
  - 结束日期、截止日期 → 通常为开始日期之后
- 绝对日期示例：2024-01-15、2026-03-20

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
            "fieldType": "字段类型（见下方枚举）",
            "required": true/false,
            "defaultValue": "默认值（可选）",
            "ruleDescription": "用自然语言描述该字段的填写规则和约束（可选）",
            "options": ["选项1", "选项2"],
            "enumConfig": { "type": "static", "options": ["选项1", "选项2"] }
          }
        ]
      }
    ],
    "submitConfig": {
      "type": "api",
      "api": { "url": "", "method": "POST", "timeout": 30, "dataPath": "" }
    }
  },
  "validationErrors": ["校验问题列表，没有则为空数组"],
  "reply": "对用户的简短回复，说明已生成配置"
}
```

## 字段类型枚举（fieldType）

| fieldType | 说明 | 必须配合属性 |
|-----------|------|-------------|
| `input` | 单行文本 | - |
| `textarea` | 多行文本 | - |
| `number` | 数字输入 | - |
| `select` | 下拉选择 | `options` 或 `enumConfig` |
| `radio` | 单选按钮 | `options` 或 `enumConfig` |
| `checkbox` | 复选框 | `options` 或 `enumConfig` |
| `date` | 日期选择器 | - |
| `datetime` | 日期时间选择器 | - |
| `email` | 邮箱输入 | - |
| `phone` | 手机号输入 | - |
| `file` | 文件上传 | - |

## 枚举配置（enumConfig）

当选项来源需要灵活配置时，使用 `enumConfig` 代替 `options`。二者互斥，有 enumConfig 时优先使用。

### 静态枚举
```json
"enumConfig": {
  "type": "static",
  "options": ["选项1", "选项2", "选项3"]
}
```

### API 动态枚举
```json
"enumConfig": {
  "type": "api",
  "api": {
    "url": "https://api.example.com/options",
    "method": "GET",
    "headers": { "Authorization": "Bearer ${token}" },
    "timeout": 10,
    "dataPath": "data.list",
    "cacheTTL": 3600,
    "retryCount": 2,
    "fallback": ["默认选项1", "默认选项2"]
  }
}
```

**使用原则**：
- 简单固定选项 → 用 `options` 即可
- 选项可能动态变化或需要从接口获取 → 用 `enumConfig`
- API 枚举必须提供 `fallback` 兜底选项

## 校验规则（ruleDescription）说明

每个字段可添加 `ruleDescription` 字段，用**自然语言描述**该字段的填写规则和约束。
AI 会在用户提交表单时读取这些描述来判断填写是否符合规则。

**规则描述应清晰、具体、可判断**，例如：

| 字段类型 | ruleDescription 示例 |
|---------|---------------------|
| 营业执照号 | "15-18位数字和大写字母，统一社会信用代码为18位" |
| 手机号 | "11位中国大陆手机号，以1开头" |
| 邮箱 | "标准邮箱格式，如 user@example.com" |
| 金额 | "正数，最小0.01，最大999999.99" |
| 日期 | "不早于2024-01-01，不晚于当天" |
| 身份证号 | "18位身份证号，最后一位可以是X" |
| 网址 | "以http://或https://开头的合法URL" |
| 密码 | "至少12位，包含大小写字母和数字" |

**规则描述设计原则**：
1. 描述应该**具体可量化**，包含明确的数值范围、格式要求
2. 如果字段没有特殊约束，可以省略 ruleDescription
3. 通用格式（如手机号、邮箱）可使用 fieldType 代替，无需重复在 ruleDescription 中描述
4. 业务特有的约束（如"金额不超过合同总额"）务必写入 ruleDescription

## 提交配置（submitConfig）

表单提交目标可配置，默认不设置（系统内部处理）。如需提交到外部 API：

```json
"submitConfig": {
  "type": "api",
  "api": {
    "url": "https://api.example.com/submit",
    "method": "POST",
    "headers": { "Content-Type": "application/json", "X-API-Key": "your-key" },
    "timeout": 30,
    "dataPath": ""
  }
}
```

## 设计原则
1. 实体分组要合理：如"申请人信息"、"审批信息"、"业务详情"等
2. 字段命名要语义清晰，使用中文 fieldName
3. 必填字段要审慎设置，通常核心业务字段设为必填
4. **select/radio/checkbox 必须提供 options 或 enumConfig，否则前端无法渲染**
5. 根据业务常识设计合理的字段，用户描述不清的地方可以自行补充
6. formCode 必须是 snake_case 格式，不能有空格和特殊字符
7. **为有业务约束的字段添加 ruleDescription**，用自然语言清晰描述规则
8. 复杂的选项配置用 enumConfig，简单固定选项用 options
9. **枚举选项必须基于业务常识设计，至少3个选项，不要用占位符**

### ⚠️ 枚举字段完整性检查（必须遵守）

对于 fieldType 为 `select`、`radio`、`checkbox` 的字段，**必须**满足以下条件之一：
- 提供 `options` 数组（至少包含3个有业务含义的选项）
- 提供 `enumConfig` 对象（包含 type 和对应的选项配置）

**正确示例**：
```json
{
  "fieldCode": "contract_type",
  "fieldName": "合同类型",
  "fieldType": "select",
  "required": true,
  "options": ["固定期限合同", "无固定期限合同", "以完成一定工作任务为期限的合同"]
}
```

**错误示例（禁止）**：
```json
{
  "fieldCode": "contract_type",
  "fieldName": "合同类型",
  "fieldType": "select",
  "required": true
}
```
↑ 缺少 options，前端无法渲染下拉框，**这是严重错误**！

**错误示例（禁止）**：
```json
{
  "fieldCode": "priority",
  "fieldName": "优先级",
  "fieldType": "select",
  "required": true,
  "options": ["选项1", "选项2"]
}
```
↑ 选项没有业务含义，只是占位符，**这也是错误**！

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

            # 注入当前日期，帮助 LLM 正确生成日期默认值
            current_date = datetime.now().strftime('%Y-%m-%d')
            current_weekday = datetime.now().strftime('%A')  # 如 Monday, Tuesday 等

            prompt = f"""[系统上下文]
当前日期：{current_date}（{current_weekday}）
请以当前日期为基准，生成符合业务逻辑的日期默认值。

[对话记录]
以下是用户与助手的对话记录，请根据最后一条用户消息生成表单配置：

{conversation_text}"""

            # 配置生成的 JSON 输出可能较长（5000-8000 tokens）
            # 使用 _call_llm_sync_with_reasoning 同时获取模型思考过程
            result_text, config_reasoning = llm_service._call_llm_sync_with_reasoning(
                prompt, system_prompt=cls._CONFIG_SYSTEM_PROMPT, max_tokens=8192
            )

            if not result_text:
                logger.error("[AdminService] chat() 调用 LLM 返回为空")
                return {"success": False, "reply": "AI 未返回结果，请重试", "reasoning": config_reasoning}

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

                # 枚举字段完整性校验：select/radio/checkbox 必须有 options 或 enumConfig
                enum_field_types = {"select", "radio", "checkbox"}
                for entity in (config_data.get("entities") or []):
                    entity_name = entity.get("entityName", entity.get("entityCode", ""))
                    for field in (entity.get("fields") or []):
                        ft = field.get("fieldType", "")
                        fc = field.get("fieldCode", "")
                        fn = field.get("fieldName", fc)
                        if ft in enum_field_types:
                            has_options = bool(field.get("options"))
                            has_enum_config = bool(field.get("enumConfig"))
                            if not has_options and not has_enum_config:
                                validation_errors.append(
                                    f"字段 {fn}({fc}) 类型为 {ft}，但缺少 options 或 enumConfig，前端无法渲染"
                                )

                logger.info("[AdminService] 配置生成成功 formCode=%s", config_data.get("formCode"))
                return {
                    "success": True,
                    "hasConfig": True,
                    "config": config_data,
                    "validationErrors": validation_errors,
                    "reply": reply,
                    "reasoning": config_reasoning
                }
            else:
                reply = parsed.get("reply", "请描述您想创建的表单类型。")
                return {"success": True, "hasConfig": False, "reply": reply, "reasoning": config_reasoning}

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

            result_text, keywords_reasoning = llm_service._call_llm_sync_with_reasoning(prompt)
            if not result_text:
                return {"success": True, "keywords": [form_name, form_code], "reasoning": keywords_reasoning}

            parsed = llm_service._extract_json(result_text)
            if parsed and "keywords" in parsed:
                keywords = parsed["keywords"]
                if isinstance(keywords, list) and len(keywords) > 0:
                    logger.info("[AdminService] 生成关键词 formCode=%s count=%d", form_code, len(keywords))
                    return {"success": True, "keywords": keywords, "reasoning": keywords_reasoning}

            # 解析失败，返回基础关键词
            return {"success": True, "keywords": [form_name, form_code], "reasoning": keywords_reasoning}

        except Exception as e:
            logger.exception("[AdminService] 关键词生成失败: %s", e)
            return {"success": True, "keywords": [form_name, form_code]}
