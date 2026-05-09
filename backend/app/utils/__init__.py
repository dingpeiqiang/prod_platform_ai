"""
通用工具函数模块

提供项目中常用的工具函数，消除重复代码：
- 日期处理
- JSON 处理
- 字符串处理
- 验证工具
"""

from typing import Any, Optional, Dict
from datetime import datetime, timedelta
import json
import re


# ── 日期处理工具 ────────────────────────────────────────────────────────────────

def parse_date(date_str: str, formats: Optional[list] = None) -> Optional[datetime]:
    """
    解析日期字符串为 datetime 对象

    Args:
        date_str: 日期字符串
        formats: 可选的日期格式列表，默认为常用格式

    Returns:
        datetime 对象，如果解析失败返回 None
    """
    if not date_str or not isinstance(date_str, str):
        return None

    default_formats = [
        '%Y-%m-%d',
        '%Y/%m/%d',
        '%Y%m%d',
        '%Y-%m-%d %H:%M:%S',
        '%Y-%m-%dT%H:%M:%S',
        '%Y-%m-%d %H:%M',
    ]
    date_formats = formats or default_formats

    for fmt in date_formats:
        try:
            return datetime.strptime(date_str.strip(), fmt)
        except ValueError:
            continue

    return None


def format_date(dt: datetime, fmt: str = '%Y-%m-%d') -> str:
    """
    将 datetime 对象格式化为字符串

    Args:
        dt: datetime 对象
        fmt: 输出格式，默认为 '%Y-%m-%d'

    Returns:
        格式化后的日期字符串
    """
    if not isinstance(dt, datetime):
        return str(dt) if dt else ""
    return dt.strftime(fmt)


def convert_date_string(date_str: str) -> str:
    """
    将自然语言日期转换为标准日期格式 (YYYY-MM-DD)
    支持：今天、明天、后天、昨天、前天等

    Args:
        date_str: 日期字符串

    Returns:
        标准日期字符串 (YYYY-MM-DD)
    """
    if not date_str or not isinstance(date_str, str):
        return date_str

    today = datetime.now()
    date_str_lower = date_str.strip().lower()

    if date_str_lower in ['今天', '今日']:
        return today.strftime('%Y-%m-%d')
    elif date_str_lower in ['明天', '明日']:
        return (today + timedelta(days=1)).strftime('%Y-%m-%d')
    elif date_str_lower in ['后天']:
        return (today + timedelta(days=2)).strftime('%Y-%m-%d')
    elif date_str_lower in ['昨天', '昨日']:
        return (today - timedelta(days=1)).strftime('%Y-%m-%d')
    elif date_str_lower in ['前天']:
        return (today - timedelta(days=2)).strftime('%Y-%m-%d')

    parsed = parse_date(date_str)
    if parsed:
        return parsed.strftime('%Y-%m-%d')

    return date_str


def get_date_range(start_date: Optional[str], end_date: Optional[str]) -> Dict[str, datetime]:
    """
    获取日期范围（用于查询）

    Args:
        start_date: 开始日期字符串
        end_date: 结束日期字符串

    Returns:
        包含 start_dt 和 end_dt 的字典
    """
    result = {}

    if start_date:
        parsed = parse_date(start_date)
        if parsed:
            result['start_dt'] = parsed

    if end_date:
        parsed = parse_date(end_date)
        if parsed:
            result['end_dt'] = parsed.replace(hour=23, minute=59, second=59)

    return result


# ── JSON 处理工具 ───────────────────────────────────────────────────────────────

def safe_parse_json(text: str) -> Optional[Any]:
    """
    安全解析 JSON，支持各种格式问题

    Args:
        text: JSON 字符串

    Returns:
        解析后的对象，如果解析失败返回 None
    """
    if not text:
        return None

    text = text.strip()

    if text.startswith('```'):
        first_nl = text.find('\n')
        if first_nl != -1:
            text = text[first_nl + 1:]
        if text.endswith('```'):
            text = text[:-3]
        text = text.strip()

    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass

    start = text.find('{')
    end = text.rfind('}')
    if start != -1 and end != -1 and end > start:
        try:
            return json.loads(text[start:end + 1])
        except json.JSONDecodeError:
            pass

    start = text.find('[')
    end = text.rfind(']')
    if start != -1 and end != -1 and end > start:
        try:
            return json.loads(text[start:end + 1])
        except json.JSONDecodeError:
            pass

    return None


def fix_json_newlines(json_str: str) -> str:
    """
    修复 JSON 字符串值中的裸换行符

    模型在 JSON 字段值中写入真实换行而非 \n 转义，导致 json.loads 失败。
    此函数将字符串值内的裸换行替换为 \n 转义序列。

    Args:
        json_str: JSON 字符串

    Returns:
        修复后的 JSON 字符串
    """
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


def strip_json_comments(text: str) -> str:
    """
    移除 JSON 字符串中的注释和代码块标记

    Args:
        text: 原始文本

    Returns:
        清理后的文本
    """
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


# ── 字符串处理工具 ─────────────────────────────────────────────────────────────

def truncate(text: str, max_len: int = 200, suffix: str = "...") -> str:
    """
    截断文本到指定长度

    Args:
        text: 原始文本
        max_len: 最大长度
        suffix: 截断后的后缀

    Returns:
        截断后的文本
    """
    if not text:
        return ""
    if len(text) <= max_len:
        return text
    return text[:max_len] + suffix


def normalize_string(text: str) -> str:
    """
    规范化字符串：去除首尾空格，转换为小写

    Args:
        text: 原始文本

    Returns:
        规范化后的文本
    """
    if not text:
        return ""
    return str(text).strip().lower()


def extract_numbers(text: str) -> list:
    """
    从文本中提取数字

    Args:
        text: 原始文本

    Returns:
        数字列表
    """
    if not text:
        return []
    pattern = r'(\d+(?:\.\d+)?)'
    return re.findall(pattern, text)


# ── 验证工具 ─────────────────────────────────────────────────────────────────

def is_email(email: str) -> bool:
    """
    验证邮箱格式

    Args:
        email: 邮箱字符串

    Returns:
        是否为有效邮箱
    """
    if not email or not isinstance(email, str):
        return False
    pattern = r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'
    return re.match(pattern, email) is not None


def is_phone(phone: str) -> bool:
    """
    验证手机号格式

    Args:
        phone: 手机号字符串

    Returns:
        是否为有效手机号
    """
    if not phone or not isinstance(phone, str):
        return False
    pattern = r'^1[3-9]\d{9}$'
    return re.match(pattern, phone.strip()) is not None


def is_valid_date(date_str: str) -> bool:
    """
    验证日期格式是否有效

    Args:
        date_str: 日期字符串

    Returns:
        是否为有效日期
    """
    return parse_date(date_str) is not None


# ── 字典/对象工具 ────────────────────────────────────────────────────────────

def deep_get(dictionary: Dict, keys: str, default: Any = None) -> Any:
    """
    安全获取嵌套字典中的值

    Args:
        dictionary: 字典
        keys: 用点分隔的键路径，如 'a.b.c'
        default: 默认值

    Returns:
        获取到的值或默认值
    """
    if not dictionary or not isinstance(dictionary, dict):
        return default

    key_list = keys.split('.')
    result = dictionary

    for key in key_list:
        if isinstance(result, dict) and key in result:
            result = result[key]
        else:
            return default

    return result


def merge_dicts(*dicts) -> Dict:
    """
    合并多个字典，后面的字典会覆盖前面的

    Args:
        dicts: 多个字典

    Returns:
        合并后的字典
    """
    result = {}
    for d in dicts:
        if isinstance(d, dict):
            result.update(d)
    return result


def remove_none_values(dictionary: Dict) -> Dict:
    """
    移除字典中值为 None 的键

    Args:
        dictionary: 原始字典

    Returns:
        清理后的字典
    """
    if not isinstance(dictionary, dict):
        return dictionary
    return {k: v for k, v in dictionary.items() if v is not None}


# ── 列表工具 ─────────────────────────────────────────────────────────────────

def dedupe_list(items: list, key=None) -> list:
    """
    去除列表中的重复项

    Args:
        items: 原始列表
        key: 可选的去重键函数

    Returns:
        去重后的列表
    """
    if not items:
        return []

    seen = set()
    result = []
    for item in items:
        if key:
            item_key = key(item)
        else:
            item_key = item

        if item_key not in seen:
            seen.add(item_key)
            result.append(item)

    return result