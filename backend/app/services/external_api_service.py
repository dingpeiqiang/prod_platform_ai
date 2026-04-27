from typing import Dict, Any, List, Optional, Callable
import httpx
import json
from app.core.config_loader import config_loader
import logging

logger = logging.getLogger(__name__)


class APIConfig:
    def __init__(self, config: Dict[str, Any]):
        self.url = config.get("url")
        self.method = config.get("method", "GET").upper()
        self.headers = config.get("headers", {})
        self.timeout = config.get("timeout", 30)
        self.data_path = config.get("dataPath")
        self.cache_ttl = config.get("cacheTTL", 300)
        self.retry_count = config.get("retryCount", 3)
        self.fallback = config.get("fallback")


class CachedResponse:
    def __init__(self, data: Any, timestamp: float):
        self.data = data
        self.timestamp = timestamp


class ExternalAPIService:
    _cache: Dict[str, CachedResponse] = {}
    _custom_processors: Dict[str, Callable] = {}
    
    @classmethod
    def register_processor(cls, name: str, processor: Callable):
        cls._custom_processors[name] = processor
    
    @classmethod
    async def call_api(cls, api_config: Dict[str, Any], context: Dict[str, Any] = None) -> Dict[str, Any]:
        config = APIConfig(api_config)
        
        if not config.url:
            return cls._handle_fallback(config, "API URL not configured")
        
        cache_key = cls._build_cache_key(config, context)
        
        if config.cache_ttl > 0:
            cached = cls._cache.get(cache_key)
            if cached:
                import time
                if time.time() - cached.timestamp < config.cache_ttl:
                    return {"success": True, "data": cached.data, "cached": True}
        
        for attempt in range(config.retry_count):
            try:
                result = await cls._execute_request(config, context)
                if result.get("success"):
                    if config.cache_ttl > 0:
                        import time
                        cls._cache[cache_key] = CachedResponse(result["data"], time.time())
                    return result
            except Exception as e:
                logger.warning(f"API call attempt {attempt + 1} failed: {e}")
                if attempt == config.retry_count - 1:
                    return cls._handle_fallback(config, str(e))
        
        return cls._handle_fallback(config, "Max retries exceeded")
    
    @classmethod
    async def _execute_request(cls, config: APIConfig, context: Dict[str, Any] = None) -> Dict[str, Any]:
        url = cls._render_template(config.url, context)
        headers = cls._render_headers(config.headers, context)
        
        timeout = httpx.Timeout(config.timeout)
        
        async with httpx.AsyncClient(timeout=timeout) as client:
            request_data = cls._prepare_request_data(config, context)
            
            if config.method == "GET":
                response = await client.get(url, headers=headers, params=request_data)
            elif config.method == "POST":
                response = await client.post(url, headers=headers, json=request_data)
            elif config.method == "PUT":
                response = await client.put(url, headers=headers, json=request_data)
            elif config.method == "DELETE":
                response = await client.delete(url, headers=headers)
            else:
                raise ValueError(f"Unsupported HTTP method: {config.method}")
            
            response.raise_for_status()
            result = response.json()
            
            processed_data = cls._process_response(result, config.data_path)
            
            return {"success": True, "data": processed_data}
    
    @classmethod
    def _prepare_request_data(cls, config: APIConfig, context: Dict[str, Any] = None) -> Dict[str, Any]:
        if not context:
            return {}
        
        data = {}
        for key, value in context.items():
            if isinstance(value, (str, int, float, bool, list, dict)):
                data[key] = value
        
        return data
    
    @classmethod
    def _render_template(cls, template: str, context: Dict[str, Any] = None) -> str:
        if not context:
            return template
        
        result = template
        for key, value in context.items():
            if isinstance(value, str):
                result = result.replace(f"${{{key}}}", value)
        
        return result
    
    @classmethod
    def _render_headers(cls, headers: Dict[str, str], context: Dict[str, Any] = None) -> Dict[str, str]:
        if not context:
            return headers.copy()
        
        rendered = {}
        for key, value in headers.items():
            if isinstance(value, str):
                rendered[key] = cls._render_template(value, context)
            else:
                rendered[key] = value
        
        return rendered
    
    @classmethod
    def _process_response(cls, response: Any, data_path: Optional[str] = None) -> Any:
        if not data_path:
            return response
        
        result = response
        for part in data_path.split("."):
            if isinstance(result, dict) and part in result:
                result = result[part]
            elif isinstance(result, list) and part.isdigit():
                idx = int(part)
                if 0 <= idx < len(result):
                    result = result[idx]
                else:
                    return None
            else:
                return None
        
        return result
    
    @classmethod
    def _handle_fallback(cls, config: APIConfig, error: str) -> Dict[str, Any]:
        if config.fallback is not None:
            return {"success": True, "data": config.fallback, "fallback": True}
        return {"success": False, "error": error}
    
    @classmethod
    def _build_cache_key(cls, config: APIConfig, context: Dict[str, Any] = None) -> str:
        key_parts = [config.url, config.method]
        if context:
            key_parts.append(json.dumps(context, sort_keys=True))
        return "|".join(key_parts)
    
    @classmethod
    def clear_cache(cls):
        cls._cache.clear()
    
    @classmethod
    async def get_enum_options(cls, enum_config: Dict[str, Any], context: Dict[str, Any] = None) -> List[str]:
        if enum_config.get("type") == "static":
            return enum_config.get("options", [])
        
        if enum_config.get("type") == "api":
            api_result = await cls.call_api(enum_config.get("api", {}), context)
            if api_result.get("success"):
                data = api_result.get("data", [])
                if isinstance(data, list):
                    return data
                return []
        
        return []
    
    @classmethod
    async def validate_with_external(cls, validate_config: Dict[str, Any], field_value: Any, context: Dict[str, Any] = None) -> Dict[str, Any]:
        if not validate_config or validate_config.get("type") != "api":
            return {"success": True, "valid": True}
        
        api_config = validate_config.get("api", {})
        
        full_context = context or {}
        full_context["fieldValue"] = field_value
        
        api_result = await cls.call_api(api_config, full_context)
        
        if not api_result.get("success"):
            fallback_valid = validate_config.get("fallbackValid", True)
            return {"success": True, "valid": fallback_valid, "fallback": True}
        
        data = api_result.get("data", {})
        
        if isinstance(data, dict):
            return {
                "success": True,
                "valid": data.get("valid", True),
                "message": data.get("message")
            }
        
        return {"success": True, "valid": bool(data)}
    
    @classmethod
    async def submit_with_external(cls, submit_config: Dict[str, Any], form_data: Dict[str, Any], context: Dict[str, Any] = None) -> Dict[str, Any]:
        if not submit_config or submit_config.get("type") != "api":
            return {"success": True}
        
        api_config = submit_config.get("api", {})
        
        full_context = context or {}
        full_context["formData"] = form_data
        
        return await cls.call_api(api_config, full_context)


external_api_service = ExternalAPIService()
