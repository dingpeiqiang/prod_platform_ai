import json
import uuid
from typing import Dict, Any, Optional, List

from app.core.config_loader import config_loader


def truncate(text: str, max_len: int = 200) -> str:
    """截断文本"""
    if not text:
        return ""
    if len(text) <= max_len:
        return text
    return text[:max_len] + "..."


def merge_field_recommendations(
    llm_recs: Dict[str, Any],
    engine_recs: Dict[str, Any]
) -> Dict[str, Any]:
    """
    合并两路 fieldRecommendations：
    - llm_recs: LLM 意图识别输出，格式 {"field": [{"value","reason"},...], ...}
      来源 source 标记为 "llm_rule"
    - engine_recs: 推荐引擎输出，格式 {"field": {"items":[...],"strategyUsed":...}, ...}

    合并策略：LLM 推荐优先保留（source=llm_rule），引擎推荐作为补充追加
    同一 value 去重，按 value 去重取 LLM 的 reason
    """
    merged = {}
    max_per_field = config_loader.get_system_config().get("smartRecommend", {}).get("maxRecommendationsPerField", 5)

    for field_code, rec_data in llm_recs.items():
        if isinstance(rec_data, list):
            merged[field_code] = {
                "items": [
                    {**item, "source": item.get("source", "llm_rule")}
                    for item in rec_data
                    if isinstance(item, dict) and item.get("value")
                ],
                "strategyUsed": ["llm_rule_inference"],
                "_has_llm": True
            }
        elif isinstance(rec_data, dict) and "items" in rec_data:
            items = [
                {**item, "source": item.get("source", "llm_rule")}
                for item in (rec_data.get("items") or [])
                if isinstance(item, dict)
            ]
            merged[field_code] = {
                "items": items,
                "strategyUsed": rec_data.get("strategyUsed", ["llm_rule_inference"]),
                "_has_llm": True
            }

    for field_code, rec_data in engine_recs.items():
        engine_items = []
        if isinstance(rec_data, dict):
            engine_items = rec_data.get("items", [])
        elif isinstance(rec_data, list):
            engine_items = rec_data

        if not engine_items:
            continue

        existing_values = set()
        existing_items = merged.get(field_code, {}).get("items", [])
        for item in existing_items:
            v = item.get("value") if isinstance(item, dict) else str(item)
            if v:
                existing_values.add(v)

        new_items = []
        for item in engine_items:
            if not isinstance(item, dict):
                item = {"value": str(item), "source": "history"}
            val = item.get("value", "")
            if val and val not in existing_values:
                new_items.append(item)
                existing_values.add(val)

        if field_code in merged:
            merged[field_code]["items"].extend(new_items)
            strategies = merged[field_code].get("strategyUsed", [])
            if "engine_history" not in strategies:
                strategies.append("engine_history")
        else:
            merged[field_code] = {
                "items": engine_items[:max_per_field] if not new_items else (engine_items[:max_per_field - len(new_items)] + new_items),
                "strategyUsed": rec_data.get("strategyUsed", ["engine"]) if isinstance(rec_data, dict) else ["engine"],
                "_has_llm": False
            }

    for field_code in list(merged.keys()):
        items = merged[field_code].get("items", [])
        if len(items) > max_per_field:
            merged[field_code]["items"] = items[:max_per_field]
        merged[field_code].pop("_has_llm", None)

    return merged


def strip_json_comments(text: str) -> str:
    result = []
    for line in text.split('\n'):
        stripped = line.strip()
        if stripped.startswith('//'):
            continue
        if stripped.startswith('```'):
            continue
        result.append(line)
    text = '\n'.join(result)
    if text.startswith("```json"):
        text = text[7:]
    if text.startswith("```"):
        text = text[3:]
    if text.endswith("```"):
        text = text[:-3]
    return text.strip()


def extract_json_from_text(text: str) -> str:
    """从包含额外文本的响应中提取 JSON 部分"""
    import re
    
    # 尝试匹配 ```json ... ``` 格式
    json_block_match = re.search(r'```(?:json)?\s*(\{.*?\})\s*```', text, re.DOTALL)
    if json_block_match:
        return json_block_match.group(1).strip()
    
    # 尝试匹配 {} 包裹的内容
    brace_start = text.find('{')
    brace_end = text.rfind('}')
    if brace_start != -1 and brace_end != -1 and brace_start < brace_end:
        return text[brace_start:brace_end + 1].strip()
    
    # 尝试匹配 [] 包裹的内容（数组）
    bracket_start = text.find('[')
    bracket_end = text.rfind(']')
    if bracket_start != -1 and bracket_end != -1 and bracket_start < bracket_end:
        return text[bracket_start:bracket_end + 1].strip()
    
    return text.strip()


def fix_json_newlines(json_str: str) -> str:
    """修复 JSON 字符串值中的裸换行符（MiniMax 等模型常见问题）。
    模型在 JSON 字段值中写入真实换行而非 \\n 转义，导致 json.loads 失败。
    此函数将字符串值内的裸换行替换为 \\n 转义序列。"""
    import re
    result = []
    in_string = False
    escape_next = False
    for ch in json_str:
        if escape_next:
            result.append(ch)
            escape_next = False
            continue
        if ch == '\\':
            result.append(ch)
            escape_next = True
            continue
        if ch == '"' and not escape_next:
            in_string = not in_string
            result.append(ch)
            continue
        if in_string and ch in '\n\r':
            result.append('\\n')
        else:
            result.append(ch)
    return ''.join(result)


def fix_incomplete_json(json_str: str) -> str:
    """修复不完整的 JSON（模型返回结果被截断的情况）。
    
    处理以下情况：
    1. 未闭合的字符串（添加结束引号）
    2. 缺少闭合的花括号/方括号
    """
    # 统计引号数量，如果是奇数，说明字符串未闭合
    quote_count = json_str.count('"')
    if quote_count % 2 != 0:
        # 找到最后一个未转义的引号位置
        last_quote_idx = -1
        escape_next = False
        for i, ch in enumerate(json_str):
            if escape_next:
                escape_next = False
                continue
            if ch == '\\':
                escape_next = True
                continue
            if ch == '"':
                last_quote_idx = i
        
        if last_quote_idx != -1:
            # 在字符串末尾添加闭合引号
            json_str = json_str[:last_quote_idx + 1] + '"' + json_str[last_quote_idx + 1:]
    
    # 统计花括号和方括号是否匹配
    open_braces = json_str.count('{')
    close_braces = json_str.count('}')
    open_brackets = json_str.count('[')
    close_brackets = json_str.count(']')
    
    # 添加缺少的闭合括号
    json_str += '}' * (open_braces - close_braces)
    json_str += ']' * (open_brackets - close_brackets)
    
    return json_str


def build_ontologies_info() -> str:
    ontologies = config_loader.get_all_ontologies()
    info_lines = []
    for form_code, ontology in ontologies.items():
        form_name = ontology.get('formName', form_code)
        description = ontology.get('description', '')

        info_lines.append(f"### {form_code} ({form_name})")
        if description:
            info_lines.append(f"描述：{description}")

        entities = ontology.get('entities', [])
        for entity in entities:
            entity_name = entity.get('entityName', '')
            fields = entity.get('fields', [])
            if fields:
                field_list = []
                for field in fields:
                    field_info = f"{field.get('fieldName')} ({field.get('fieldCode')})"
                    field_type = field.get('fieldType', '')
                    if field_type:
                        field_info += f" - {field_type}"
                    if field.get('required'):
                        field_info += " [必填]"
                    field_list.append(field_info)

                if entity_name:
                    info_lines.append(f"{entity_name}字段：")
                info_lines.extend([f"  - {f}" for f in field_list])

        info_lines.append("")

    return "\n".join(info_lines)


def build_scene_keywords() -> str:
    scene_mappings = config_loader.get_scene_mappings()
    keyword_lines = []
    for mapping in scene_mappings:
        scene_code = mapping.get('sceneCode', '')
        keywords = mapping.get('keywords', [])
        if keywords:
            keyword_lines.append(f"{scene_code}: {', '.join(keywords)}")
    return "\n".join(keyword_lines)


def build_scene_hierarchy() -> str:
    from app.models.scene import Scene
    from app.core.database import get_db
    
    try:
        db_gen = get_db()
        db = next(db_gen)
        scene_mappings = db.query(Scene).filter(Scene.is_active == True).all()
        scene_mappings = [scene.to_dict() for scene in scene_mappings]
    except Exception as e:
        return "场景加载失败"
    
    if not scene_mappings:
        return "暂无场景配置"
    
    scenes_by_parent = {}
    root_scenes = []
    
    for mapping in scene_mappings:
        parent_id = mapping.get('parentId')
        if parent_id is None:
            root_scenes.append(mapping)
        else:
            if parent_id not in scenes_by_parent:
                scenes_by_parent[parent_id] = []
            scenes_by_parent[parent_id].append(mapping)
    
    hierarchy_lines = []
    hierarchy_lines.append("```")
    hierarchy_lines.append("【场景层级筛选规则】")
    hierarchy_lines.append("")
    hierarchy_lines.append("┌─────────────────────────────────────────────────────────────┐")
    hierarchy_lines.append("│  层级结构用于帮助您筛选和定位正确的【三级】具体场景         │")
    hierarchy_lines.append("├─────────────────────────────────────────────────────────────┤")
    hierarchy_lines.append("│  【一级】部门分类 → 用于缩小业务范围                        │")
    hierarchy_lines.append("│  【二级】业务类型 → 进一步定位业务方向                      │")
    hierarchy_lines.append("│  【三级】具体场景 → 最终意图，唯一可作为意图的层级          │")
    hierarchy_lines.append("└─────────────────────────────────────────────────────────────┘")
    hierarchy_lines.append("")
    hierarchy_lines.append("【筛选流程】")
    hierarchy_lines.append("1. 分析用户输入，先匹配【一级】部门关键词")
    hierarchy_lines.append("2. 根据一级结果，缩小到对应的【二级】业务类型")
    hierarchy_lines.append("3. 在二级业务下，找到最匹配的【三级】具体场景")
    hierarchy_lines.append("4. 最终意图必须是【三级】场景，不能是一级或二级")
    hierarchy_lines.append("")
    hierarchy_lines.append("【可用场景树】")
    
    for root in sorted(root_scenes, key=lambda x: x.get('priority', 0), reverse=True):
        root_id = root.get('id')
        root_code = root.get('sceneCode', '')
        root_name = root.get('sceneName', '')
        root_keywords = root.get('keywords', [])
        root_keywords_str = ", ".join(root_keywords) if root_keywords else "无"
        hierarchy_lines.append(f"┌ 【一级】{root_code} ({root_name})")
        hierarchy_lines.append(f"│  关键词: {root_keywords_str}")
        
        children = scenes_by_parent.get(root_id)
        if children:
            for child in sorted(children, key=lambda x: x.get('priority', 0), reverse=True):
                child_id = child.get('id')
                child_code = child.get('sceneCode', '')
                child_name = child.get('sceneName', '')
                child_keywords = child.get('keywords', [])
                child_keywords_str = ", ".join(child_keywords) if child_keywords else "无"
                hierarchy_lines.append(f"├─┬ 【二级】{child_code} ({child_name})")
                hierarchy_lines.append(f"│ │  关键词: {child_keywords_str}")
                
                grandchildren = scenes_by_parent.get(child_id)
                if grandchildren:
                    for i, grandchild in enumerate(sorted(grandchildren, key=lambda x: x.get('priority', 0), reverse=True)):
                        grandchild_code = grandchild.get('sceneCode', '')
                        grandchild_name = grandchild.get('sceneName', '')
                        grandchild_keywords = grandchild.get('keywords', [])
                        grandchild_prompt_code = grandchild.get('promptCode', '')
                        keywords_str = ", ".join(grandchild_keywords) if grandchild_keywords else "无"
                        prefix = "│ └─" if i == len(grandchildren) - 1 else "│ ├─"
                        prompt_info = f" │ 提示词: {grandchild_prompt_code}" if grandchild_prompt_code else ""
                        hierarchy_lines.append(f"{prefix} 【三级】{grandchild_code} ({grandchild_name}) ✅{prompt_info}")
                        hierarchy_lines.append(f"│     关键词: {keywords_str}")
                else:
                    hierarchy_lines.append(f"│ └─ （无三级场景）")
        else:
            hierarchy_lines.append(f"└─ （无二级业务）")
    
    hierarchy_lines.append("```")
    
    return "\n".join(hierarchy_lines)


def build_separators() -> str:
    fe_config = config_loader.get_field_extraction_config()
    separators = fe_config.get('separators', ['是', '为', '：', ':', ' '])
    return ", ".join([f"'{s}'" for s in separators])


def sse(data: dict) -> str:
    """格式化 SSE 帧"""
    return f"data: {json.dumps(data, ensure_ascii=False)}\n\n"


def thinking(content: str, result: Any = None, assistant_message_id: str = None) -> str:
    """系统步骤日志（type=thinking），支持结构化结果详情"""
    data = {
        "type": "thinking",
        "content": content,
        "message_id": str(uuid.uuid4()),
        "assistant_message_id": assistant_message_id
    }
    if result is not None:
        data["result"] = result
    return sse(data)


def reasoning(content: str) -> str:
    """大模型推理过程（type=reasoning），与系统步骤区分"""
    return sse({"type": "reasoning", "content": content})


FALLBACK_RESPONSES = {
    '你好': '你好！我是AI智能助手。我可以帮你填写各种表单（销售订单、请假申请、费用报销等），也可以和你聊天。有什么我可以帮你的吗？',
    '你能做什么': '我可以帮你：\n1. 生成和填写表单（销售订单、请假申请、费用报销等）\n2. 回答你的问题\n3. 和你聊天\n\n你可以直接告诉我需要什么帮助，比如："帮我填一个请假申请"',
    '帮助': '使用指南：\n1. 告诉我需要什么表单，如"帮我填一个销售订单"\n2. 我会自动生成表单\n3. 填写后点击提交\n\n快捷操作可以点击下方按钮！',
    '默认': '我是AI智能助手！我可以帮你填写各种表单。你可以告诉我需要填写什么，比如：\n- "帮我填一个销售订单"\n- "帮我填一个请假申请"\n- "帮我填一个费用报销"'
}