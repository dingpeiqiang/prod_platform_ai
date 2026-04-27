# LLM 服务 MCP 工具封装
# 将 LLM 调用封装为标准化 MCP 工具

from typing import Dict, Any, List, Optional
from .tool_hub import mcptool


# ============================================================
# 通用对话工具
# ============================================================

@mcptool(
    name="llm_chat",
    description="使用 LLM 进行通用对话。当用户询问需要推理、分析、创意写作等问题时使用。",
    category="llm"
)
def llm_chat(
    prompt: str,
    system_prompt: str = None,
    temperature: float = 0.7,
    max_tokens: int = 2000
) -> Dict[str, Any]:
    """
    LLM 对话
    
    Args:
        prompt: 用户 prompt
        system_prompt: 可选的系统提示
        temperature: 温度参数（创造性）
        max_tokens: 最大 token 数
        
    Returns:
        LLM 回复
    """
    from ..services.llm_service import llm_service
    
    messages = []
    if system_prompt:
        messages.append({"role": "system", "content": system_prompt})
    messages.append({"role": "user", "content": prompt})
    
    result = llm_service._call_llm_sync(
        messages=messages,
        temperature=temperature,
        max_tokens=max_tokens
    )
    
    return {
        "success": result is not None,
        "result": result,
        "error": None if result else "LLM 调用失败"
    }


# ============================================================
# 结构化 JSON 生成工具
# ============================================================

@mcptool(
    name="llm_json",
    description="使用 LLM 生成结构化 JSON 数据。适用于需要提取、分类、结构化场景。",
    category="llm"
)
def llm_generate_json(
    prompt: str,
    json_schema: Dict = None,
    temperature: float = 0.3
) -> Dict[str, Any]:
    """
    LLM 生成 JSON
    
    Args:
        prompt: 用户 prompt
        json_schema: 可选的 JSON Schema 定义输出格式
        temperature: 温度参数（通常较低）
        
    Returns:
        解析后的 JSON 数据
    """
    import json
    from ..services.llm_service import llm_service
    
    if json_schema:
        schema_str = json.dumps(json_schema, ensure_ascii=False)
        full_prompt = f"{prompt}\n\n请严格按照以下 JSON Schema 返回：\n{schema_str}"
    else:
        full_prompt = f"{prompt}\n\n请以 JSON 格式返回结果。"
    
    result = llm_service._call_llm_sync(
        prompt=full_prompt,
        temperature=temperature
    )
    
    if not result:
        return {
            "success": False,
            "error": "LLM 调用失败"
        }
    
    try:
        # 尝试解析 JSON
        cleaned = result.strip()
        if "```json" in cleaned:
            cleaned = cleaned.split("```json")[1].split("```")[0].strip()
        elif "```" in cleaned:
            cleaned = cleaned.split("```")[1].split("```")[0].strip()
        
        parsed = json.loads(cleaned)
        return {
            "success": True,
            "result": parsed
        }
    except json.JSONDecodeError:
        return {
            "success": False,
            "error": "JSON 解析失败",
            "raw": result
        }


# ============================================================
# 批量处理工具
# ============================================================

@mcptool(
    name="llm_batch",
    description="批量使用 LLM 处理多个输入。适用于需要对多个项目进行分类、提取等操作。",
    category="llm"
)
def llm_batch_process(
    items: List[str],
    prompt_template: str,
    temperature: float = 0.3
) -> Dict[str, Any]:
    """
    LLM 批量处理
    
    Args:
        items: 输入列表
        prompt_template: prompt 模板，使用 {item} 占位
        temperature: 温度参数
        
    Returns:
        批量处理结果
    """
    from ..services.llm_service import llm_service
    import json
    
    results = []
    for item in items:
        prompt = prompt_template.format(item=item)
        result = llm_service._call_llm_sync(prompt, temperature=temperature)
        results.append({
            "input": item,
            "output": result,
            "success": result is not None
        })
    
    success_count = sum(1 for r in results if r["success"])
    return {
        "success": True,
        "total": len(items),
        "success_count": success_count,
        "results": results
    }
