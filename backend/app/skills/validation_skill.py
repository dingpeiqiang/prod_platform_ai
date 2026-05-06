# ValidationSkill - 表单校验 Skill
# 提供独立的 AI 业务规则校验能力，支持流式输出 reasoning

import logging
import json
from typing import Dict, Any, List, Optional, AsyncGenerator, Tuple

from app.core.config_loader import config_loader
from app.services.llm_service import llm_service

logger = logging.getLogger("validation_skill")


class ValidationSkill:

    @classmethod
    async def validate(
        cls,
        form_code: str,
        form_data: Dict[str, Any],
        form_schema: Optional[Dict] = None,
        yield_reasoning: bool = True
    ) -> AsyncGenerator[Dict[str, Any], None]:
        """
        AI 业务规则校验（流式版本）。

        每 yield 一次代表一个 reasoning 片段完成，最终返回校验结果。
        调用方通过 last yield（type == "done"）获取 {passed, errors, warnings}。

        Args:
            form_code: 表单编码
            form_data: 填写的数据 {fieldCode: value}
            form_schema: 可选，传入 schema 以获取字段名和规则描述
            yield_reasoning: 是否 yield reasoning 片段

        Yields:
            {"type": "reasoning", "content": str}       - reasoning 片段
            {"type": "done", "passed": bool, "errors": [], "warnings": []}  - 最终结果
            {"type": "error", "message": str}           - 异常信息
        """
        # 获取本体（优先用传入的 schema）
        if not form_schema:
            ontology = config_loader.get_ontology(form_code)
            if not ontology:
                yield {"type": "error", "message": f"表单 {form_code} 不存在"}
                yield {"type": "done", "passed": True, "errors": [], "warnings": []}
                return
        else:
            ontology = form_schema

        # 收集所有字段及其规则描述
        fields_with_rules: List[Dict] = []
        entities = ontology.get("entities", [])
        if entities:
            for entity in entities:
                for field in entity.get("fields", []):
                    fields_with_rules.append(field)
        else:
            fields_with_rules = ontology.get("fields", [])

        # 构建校验项
        check_items = []
        for field in fields_with_rules:
            field_code = field.get("fieldCode", "")
            field_name = field.get("fieldName", "")
            rule_desc = field.get("ruleDescription", "")
            field_type = field.get("fieldType", "")
            value = form_data.get(field_code)

            if not rule_desc:
                if value is not None and value != "":
                    check_items.append({
                        "fieldCode": field_code,
                        "fieldName": field_name,
                        "fieldType": field_type,
                        "value": str(value),
                        "ruleDescription": "无特殊规则"
                    })
                continue

            check_items.append({
                "fieldCode": field_code,
                "fieldName": field_name,
                "fieldType": field_type,
                "value": str(value) if value is not None else "",
                "ruleDescription": rule_desc
            })

        if not check_items:
            yield {"type": "done", "passed": True, "errors": [], "warnings": []}
            return

        # 构建 prompt
        items_text = "\n".join([
            f"- 字段: {item['fieldName']}({item['fieldCode']}), 类型: {item['fieldType']}, "
            f"当前值: \"{item['value']}\", 规则: {item['ruleDescription']}"
            for item in check_items
        ])

        user_prompt = f"""你是一个严格的数据校验助手。请仔细检查以下表单字段是否填写正确、是否符合业务规则。

表单编码: {form_code}
表单名称: {ontology.get('formName', form_code)}

字段详情：
{items_text}

请逐条判断，对每个字段分析：
1. 值是否合法（类型、格式）
2. 是否符合业务规则描述
3. 是否有遗漏的必填信息

然后返回 JSON 格式的校验结果（必须以```json代码块包裹）：
```json
{{
  "passed": true或false,
  "errors": [
    {{"fieldName": "字段名", "fieldCode": "字段编码", "reason": "具体问题描述"}}
  ],
  "warnings": [
    {{"fieldName": "字段名", "fieldCode": "字段编码", "reason": "提示信息"}}
  ]
}}
```"""

        if not llm_service.enabled:
            yield {"type": "error", "message": "LLM 服务未启用"}
            yield {"type": "done", "passed": True, "errors": [], "warnings": []}
            return

        if not yield_reasoning:
            # 同步版本：直接返回结果
            result_text = llm_service._call_llm(user_prompt)
            if result_text:
                parsed = llm_service._extract_json(result_text)
                if parsed:
                    yield {"type": "done", "passed": parsed.get("passed", True),
                           "errors": parsed.get("errors", []),
                           "warnings": parsed.get("warnings", [])}
                    return
            yield {"type": "done", "passed": True, "errors": [], "warnings": []}
            return

        # 流式版本：通过 HTTP stream 边收边推 reasoning
        import requests
        from app.core.config import get_settings

        messages = [{"role": "user", "content": user_prompt}]
        base_url = llm_service._get_base_url()
        model = llm_service._get_model()
        api_key = get_settings().LLM_API_KEY or llm_service.llm_config.get('apiKey', '')
        url = f"{base_url}/chat/completions"
        headers = {"Content-Type": "application/json", "token": api_key}
        payload = {
            "stream": True,
            "messages": messages,
            "model": model,
            "temperature": llm_service.llm_config.get('temperature', 0.3),
            "max_tokens": 2048
        }

        import asyncio
        reasoning_queue: asyncio.Queue = asyncio.Queue()

        class SharedResult:
            content = ""

        def stream_task():
            try:
                resp = requests.post(url, json=payload, headers=headers, stream=True, timeout=180)
                if resp.status_code != 200:
                    reasoning_queue.put_nowait(None)
                    return
                content_parts = []
                for raw_line in resp.iter_lines():
                    if not raw_line:
                        continue
                    try:
                        line_text = raw_line.decode('utf-8')
                        if not line_text.startswith('data:'):
                            continue
                        data_str = line_text[5:].strip()
                        if data_str == '[DONE]':
                            break
                        chunk = json.loads(data_str)
                        delta = chunk.get('choices', [{}])[0].get('delta', {})
                        reasoning_chunk = delta.get('reasoning_content', '') or delta.get('reasoning', '')
                        if reasoning_chunk:
                            reasoning_queue.put_nowait(reasoning_chunk)
                        content_chunk = delta.get('content', '')
                        if content_chunk:
                            content_parts.append(content_chunk)
                    except (json.JSONDecodeError, UnicodeDecodeError):
                        continue
                SharedResult.content = ''.join(content_parts)
            except Exception as e:
                logger.error("[ValidationSkill] 流式异常: %s", e)
            finally:
                reasoning_queue.put_nowait(None)

        loop = asyncio.get_event_loop()
        loop.run_in_executor(None, stream_task)

        reasonings = []
        while True:
            try:
                chunk = await asyncio.wait_for(reasoning_queue.get(), timeout=90)
            except asyncio.TimeoutError:
                logger.warning("[ValidationSkill] 等待 reasoning 超时")
                break
            if chunk is None:
                break
            reasonings.append(chunk)
            yield {"type": "reasoning", "content": chunk}
            await asyncio.sleep(0)

        # 解析最终结果
        result_text = SharedResult.content
        if result_text:
            parsed = llm_service._extract_json(result_text)
            if parsed:
                yield {"type": "done",
                       "passed": parsed.get("passed", True),
                       "errors": parsed.get("errors", []),
                       "warnings": parsed.get("warnings", [])}
                return

        # 无法解析时从 reasoning 推断
        full_reasoning = ''.join(reasonings)
        has_error = any(kw in full_reasoning for kw in ['错误', '不符合', '失败', '❌', '不对', '有问题'])
        has_warning = any(kw in full_reasoning for kw in ['警告', '提示', '注意', '⚠', '建议'])
        yield {"type": "done",
               "passed": not has_error,
               "errors": [{"fieldName": "校验", "reason": full_reasoning[:200]}] if has_error else [],
               "warnings": [{"fieldName": "提示", "reason": full_reasoning[:200]}] if has_warning else []}


# 同步版本（供其他服务调用）
def validate_sync(
    form_code: str,
    form_data: Dict[str, Any],
    form_schema: Optional[Dict] = None
) -> Dict[str, Any]:
    """同步校验，立即返回结果（不流式输出 reasoning）"""
    # 复用流式逻辑但用同步方式
    import asyncio

    async def _run():
        result = None
        async for event in ValidationSkill.validate(form_code, form_data, form_schema, yield_reasoning=False):
            if event["type"] == "done":
                result = event
        return result

    loop = asyncio.new_event_loop()
    try:
        result = loop.run_until_complete(_run())
        if result:
            return {"passed": result.get("passed", True),
                    "errors": result.get("errors", []),
                    "warnings": result.get("warnings", [])}
    finally:
        loop.close()

    return {"passed": True, "errors": [], "warnings": []}