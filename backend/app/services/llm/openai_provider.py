import json
import logging
import requests
import asyncio
import time
from typing import Optional, Dict, Any, AsyncGenerator, Tuple
from app.services.llm.provider import BaseProvider
from app.services.llm.factory import ProviderFactory
from app.services.llm.base import StreamStats, extract_json
from app.core.config import get_settings

logger = logging.getLogger("llm.openai")


class LLMAPIError(Exception):
    """LLM API 返回的业务错误（如认证失败、权限不足等）"""
    def __init__(self, result_code: str, message: str):
        self.result_code = result_code
        self.message = message
        super().__init__(f"LLM API 错误 [{result_code}]: {message}")


@ProviderFactory.register('openai')
@ProviderFactory.register('custom')
class OpenAIProvider(BaseProvider):
    """OpenAI 兼容 API Provider"""
    
    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        settings = get_settings()
        
        # 优先使用 config 中的值，兼容驼峰和下划线两种命名方式
        self.api_key = (
            config.get('apiKey') or config.get('api_key') or ''
        ) or (settings.LLM_API_KEY.strip() if settings.LLM_API_KEY else '')
        
        self.base_url = (
            config.get('baseUrl') or config.get('base_url') or ''
        ) or (settings.LLM_BASE_URL.strip() if settings.LLM_BASE_URL else '')


        
        # 如果 config 中没有提供 model，使用 .env 的默认值
        if not self.model and settings.LLM_MODEL:
            self.model = settings.LLM_MODEL.strip()
        
        # 检查配置完整性，给出友好提示
        if not self.api_key or not self.base_url:
            logger.warning(
                "LLM 配置不完整！请通过前端界面配置模型参数。\n"
                "访问 http://localhost:5173，在侧边栏找到'模型配置'面板进行设置。"
            )
        
        # 兼容两种命名方式
        self.temperature = config.get('temperature', 0.3)
        self.max_tokens = config.get('maxTokens') or config.get('max_tokens') or 2048
        self.max_input_tokens = config.get('maxInputTokens') or config.get('max_input_tokens') or 180000
    
    def _get_headers(self) -> Dict[str, str]:
        """获取请求头"""
        headers = {"Content-Type": "application/json"}
        
        # 根据配置的认证类型添加认证头
        auth_type = self.config.get('authType') or self.config.get('auth_type') or 'bearer'
        auth_header = self.config.get('authHeader') or self.config.get('auth_header')
        
        if auth_type == 'custom' and auth_header:
            # 自定义认证头
            headers[auth_header] = self.api_key
        elif auth_type == 'token':
            headers["token"] = self.api_key
        elif auth_type == 'api_key':
            headers["api-key"] = self.api_key
        else:
            # 默认使用 Bearer 认证
            headers["Authorization"] = f"Bearer {self.api_key}"
        
        return headers
    
    def _build_payload(self, prompt: str, system_prompt: Optional[str] = None, 
                       max_tokens: Optional[int] = None, stream: bool = False) -> Dict[str, Any]:
        """构建请求体"""
        messages = self._build_messages(prompt, system_prompt)
        
        # 去掉 custom- 前缀，因为实际 API 需要的是不带前缀的模型名称
        model_name = self.model
        if model_name.startswith('custom-'):
            model_name = model_name[7:]
        
        payload = {
            "model": model_name,
            "messages": messages,
            "temperature": self.temperature,
            "max_tokens": max_tokens or self.max_tokens,
            "stream": stream
        }
        
        if self.thinking_enabled:
            payload['thinking'] = True
        
        return payload
    
    def call_sync(self, prompt: str, system_prompt: Optional[str] = None, 
                  max_tokens: Optional[int] = None) -> Optional[str]:
        """同步调用"""
        start_time = time.time()
        
        if not self.base_url or not self.api_key:
            logger.error(f"[OpenAIProvider] ❌ 配置不完整")
            logger.error(f"[OpenAIProvider]   - base_url: {'已设置' if self.base_url else '未设置'}")
            logger.error(f"[OpenAIProvider]   - api_key: {'已设置' if self.api_key else '未设置'}")
            return None
        
        # 判断是否使用完整 URL
        is_full_url = self.config.get('isFullUrl') or self.config.get('is_full_url') or False
        
        if is_full_url:
            url = self.base_url
        else:
            url = f"{self.base_url}/chat/completions"
        payload = self._build_payload(prompt, system_prompt, max_tokens, stream=False)
        headers = self._get_headers()
        
        # 记录请求信息（INFO级别）
        logger.info(f"[OpenAIProvider] 开始同步调用 ==================")
        logger.info(f"[OpenAIProvider] URL: {url}")
        logger.info(f"[OpenAIProvider] Model: {self.model}")
        logger.info(f"[OpenAIProvider] Temperature: {self.temperature}")
        logger.info(f"[OpenAIProvider] Max Tokens: {max_tokens or self.max_tokens}")
        logger.info(f"[OpenAIProvider] Prompt 长度: {len(prompt)} 字符")
        logger.debug(f"[OpenAIProvider] 请求体: {json.dumps(payload, ensure_ascii=False)[:500]}...")
        
        try:
            logger.info(f"[OpenAIProvider] 发起 POST 请求")
            resp = requests.post(url, json=payload, headers=headers, timeout=180)
            elapsed = time.time() - start_time
            
            logger.info(f"[OpenAIProvider] 响应状态码: {resp.status_code}")
            logger.info(f"[OpenAIProvider] 响应耗时: {elapsed:.2f}s")
            
            # 记录完整响应（DEBUG级别，但错误时记录INFO级别）
            if resp.content:
                logger.debug(f"[OpenAIProvider] 响应体: {resp.text[:2000]}")
            
            if resp.status_code != 200:
                error_msg = f"HTTP {resp.status_code}"
                try:
                    error_detail = resp.json()
                    logger.error(f"[OpenAIProvider] ❌ 调用失败")
                    logger.error(f"[OpenAIProvider]   - 状态码: {resp.status_code}")
                    logger.error(f"[OpenAIProvider]   - 错误详情: {json.dumps(error_detail, ensure_ascii=False)}")
                    if 'error' in error_detail:
                        error_msg += f": {error_detail['error'].get('message', str(error_detail))}"
                except:
                    logger.error(f"[OpenAIProvider] ❌ 调用失败 - HTTP {resp.status_code}")
                    if resp.content:
                        logger.error(f"[OpenAIProvider]   - 原始响应: {resp.text[:500]}")
                    error_msg += f": {resp.text[:200]}"
                raise Exception(error_msg)
            
            result = resp.json()
            
            # 检查自定义错误格式（公司内部部署的 MiniMax）
            if result.get('flag') is False or result.get('resultCode'):
                err_msg = result.get('message', 'Unknown error')
                result_code = result.get('resultCode', 'unknown')
                logger.error(f"[OpenAIProvider] ❌ API 返回错误")
                logger.error(f"[OpenAIProvider]   - resultCode: {result_code}")
                logger.error(f"[OpenAIProvider]   - message: {err_msg}")
                raise LLMAPIError(str(result_code), err_msg)
            
            # 检查格式2: {"code": 401, "message": "..."}
            if result.get('code'):
                err_msg = result.get('message', 'Unknown error')
                result_code = result.get('code', 'unknown')
                logger.error(f"[OpenAIProvider] ❌ API 返回错误")
                logger.error(f"[OpenAIProvider]   - code: {result_code}")
                logger.error(f"[OpenAIProvider]   - message: {err_msg}")
                raise LLMAPIError(str(result_code), err_msg)

            content = result.get('choices', [{}])[0].get('message', {}).get('content', '')

            if not content:
                logger.warning(f"[OpenAIProvider] ⚠️ 响应内容为空")
                logger.debug(f"[OpenAIProvider]   - 完整响应: {json.dumps(result, ensure_ascii=False)}")
                return None

            logger.info(f"[OpenAIProvider] ✅ 调用成功 ==================")
            logger.info(f"[OpenAIProvider]   - 内容长度: {len(content)} 字符")
            logger.info(f"[OpenAIProvider]   - 内容预览: {content[:200] if content else '(empty)'}")
            
            return content

        except LLMAPIError:
            raise
        except requests.exceptions.Timeout:
            elapsed = time.time() - start_time
            error_msg = f"请求超时 ({elapsed:.0f}秒)"
            logger.error(f"[OpenAIProvider] ❌ {error_msg}")
            raise Exception(error_msg)
        except requests.exceptions.ConnectionError as e:
            elapsed = time.time() - start_time
            error_msg = f"连接失败: {str(e)}"
            logger.error(f"[OpenAIProvider] ❌ {error_msg} (耗时: {elapsed:.2f}s)")
            raise Exception(error_msg)
        except requests.exceptions.RequestException as e:
            elapsed = time.time() - start_time
            error_msg = f"网络请求异常: {str(e)}"
            logger.error(f"[OpenAIProvider] ❌ {error_msg} (耗时: {elapsed:.2f}s)")
            raise Exception(error_msg)
        except Exception as e:
            elapsed = time.time() - start_time
            error_msg = f"未知错误: {str(e)}"
            logger.error(f"[OpenAIProvider] ❌ {error_msg} (耗时: {elapsed:.2f}s)", exc_info=True)
            raise
    
    def call_with_reasoning(self, prompt: str, system_prompt: Optional[str] = None,
                           max_tokens: Optional[int] = None) -> Tuple[Optional[str], Optional[str]]:
        """同步调用，返回 (content, reasoning)"""
        import logging
        _logger = logging.getLogger("llm.openai")
        _logger.info(f"[OpenAIProvider] call_with_reasoning - base_url='{self.base_url}', api_key_set={bool(self.api_key)}, model={self.model}")
        if not self.base_url or not self.api_key:
            _logger.error("[OpenAIProvider] ====== 配置错误 ======")
            _logger.error("[OpenAIProvider] base_url: %s", "已设置" if self.base_url else "❌ 未设置")
            _logger.error("[OpenAIProvider] api_key: %s", "已设置" if self.api_key else "❌ 未设置")
            _logger.error("[OpenAIProvider] model: %s", self.model)
            _logger.error("[OpenAIProvider] ====== 请通过前端界面配置模型参数 ======")
            _logger.error("[OpenAIProvider] 访问 http://localhost:5173，在侧边栏找到'模型配置'面板进行设置")
            return None, None
        
        url = f"{self.base_url}/chat/completions"
        payload = self._build_payload(prompt, system_prompt, max_tokens, stream=False)
        headers = self._get_headers()
        
        try:
            resp = requests.post(url, json=payload, headers=headers, timeout=180)
            
            if resp.status_code != 200:
                error_msg = resp.json().get('error', {}).get('message', f"HTTP {resp.status_code}")
                raise Exception(f"LLM 服务调用失败: {error_msg}")
            
            result = resp.json()

            # 【新增】检测 teamshub 自定义错误格式
            if result.get('flag') is False or result.get('resultCode'):
                err_msg = result.get('message', 'Unknown error')
                result_code = result.get('resultCode', 'unknown')
                import logging
                logger = logging.getLogger("llm.openai")
                logger.error(f"[OpenAIProvider] API 返回错误 - resultCode: {result_code}, message: {err_msg}")
                raise LLMAPIError(str(result_code), err_msg)

            # 提取 content
            content = result.get('choices', [{}])[0].get('message', {}).get('content', '')
            
            # 【修复】支持多种 reasoning 字段名（适配不同 API）
            choice = result.get('choices', [{}])[0] if result.get('choices') else {}
            message = choice.get('message', {})
            
            # 尝试从多个可能的位置提取 reasoning
            reasoning = (
                message.get('thinking', '') or  # DashScope thinking 模式
                message.get('reasoning', '') or  # 标准 reasoning 字段
                message.get('reasoning_content', '') or  # 备用字段名
                message.get('thought', '') or  # 其他可能的字段名
                choice.get('thinking', '') or  # 可能在 choice 层级
                choice.get('reasoning', '')    # 可能在 choice 层级
            )
            
            # 记录日志以便调试
            if reasoning:
                import logging
                logger = logging.getLogger("llm.openai")
                logger.info(f"[OpenAIProvider] 成功提取 reasoning（长度: {len(reasoning)}）")
            
            return content, reasoning
        
        except requests.exceptions.RequestException as e:
            raise Exception(f"网络连接失败: {str(e)}")
        except Exception as e:
            raise
    
    async def call_stream(self, prompt: str, system_prompt: Optional[str] = None, 
                          max_tokens: Optional[int] = None) -> AsyncGenerator[Tuple[str, Optional[StreamStats]], None]:
        """异步流式调用"""
        if not self.base_url or not self.api_key:
            logger.error(f"[OpenAIProvider] call_stream 失败 - base_url 或 api_key 为空")
            return
        
        url = f"{self.base_url}/chat/completions"
        payload = self._build_payload(prompt, system_prompt, max_tokens, stream=True)
        headers = self._get_headers()
        
        # 记录请求信息（INFO级别，确保能看到）
        logger.info(f"[OpenAIProvider] 开始流式调用 ==================")
        logger.info(f"[OpenAIProvider] URL: {url}")
        logger.info(f"[OpenAIProvider] Model: {self.model}")
        logger.info(f"[OpenAIProvider] Temperature: {self.temperature}")
        logger.info(f"[OpenAIProvider] Max Tokens: {max_tokens or self.max_tokens}")
        logger.info(f"[OpenAIProvider] Prompt 长度: {len(prompt)} 字符")
        logger.debug(f"[OpenAIProvider] 请求体: {json.dumps(payload, ensure_ascii=False)[:500]}...")
        
        loop = asyncio.get_event_loop()
        stats = StreamStats(start_time=loop.time())
        token_queue = asyncio.Queue()
        received_chunks = 0
        total_content_length = 0
        raw_response_lines = []
        
        def _worker(event_loop):
            nonlocal received_chunks, total_content_length
            try:
                logger.info(f"[OpenAIProvider] 发起 POST 请求")
                resp = requests.post(url, json=payload, headers=headers, stream=True, timeout=300)
                logger.info(f"[OpenAIProvider] 响应状态码: {resp.status_code}")
                
                if resp.status_code != 200:
                    try:
                        error_detail = resp.json() if resp.content else {}
                        logger.error(f"[OpenAIProvider] ❌ 调用失败 - HTTP {resp.status_code}")
                        logger.error(f"[OpenAIProvider] ❌ 错误详情: {json.dumps(error_detail, ensure_ascii=False)}")
                    except:
                        logger.error(f"[OpenAIProvider] ❌ 调用失败 - HTTP {resp.status_code}")
                        if resp.content:
                            logger.error(f"[OpenAIProvider] ❌ 原始响应: {resp.text[:500]}")
                    return
                
                logger.info(f"[OpenAIProvider] ✅ 连接成功，开始读取响应")
                
                for raw_line in resp.iter_lines():
                    if not raw_line:
                        continue
                    
                    line_text = raw_line.decode('utf-8')
                    
                    # 记录原始响应行（用于调试）
                    if len(raw_response_lines) < 50:
                        raw_response_lines.append(line_text[:200])
                    
                    if line_text.startswith('data: [DONE]') or line_text.startswith('data:[DONE]'):
                        logger.debug(f"[OpenAIProvider] 收到结束标记")
                        break
                    
                    if not line_text.startswith('data:'):
                        logger.debug(f"[OpenAIProvider] 非 data 行: {line_text[:100]}")
                        continue
                    
                    try:
                        data = json.loads(line_text[5:])
                        
                        # 检查错误响应格式
                        if 'code' in data or 'error' in data:
                            error_code = data.get('code') or data.get('error', {}).get('code')
                            error_msg = data.get('message') or data.get('error', {}).get('message', 'Unknown error')
                            logger.error(f"[OpenAIProvider] ❌ 流式调用收到错误响应")
                            logger.error(f"[OpenAIProvider]   - 错误码: {error_code}")
                            logger.error(f"[OpenAIProvider]   - 错误信息: {error_msg}")
                            # 设置错误标记，在调用方处理
                            event_loop.call_soon_threadsafe(
                                token_queue.put_nowait, ('error', f"错误 {error_code}: {error_msg}")
                            )
                            continue
                        
                        choices = data.get('choices', [])
                        if not choices:
                            logger.debug(f"[OpenAIProvider] 空 choices 列表")
                            continue
                        
                        delta = choices[0].get('delta', {})
                        content = delta.get('content', '')
                        reasoning = delta.get('reasoning', '')
                        
                        if content:
                            received_chunks += 1
                            total_content_length += len(content)
                            event_loop.call_soon_threadsafe(
                                token_queue.put_nowait, ('content', content)
                            )
                        if reasoning:
                            event_loop.call_soon_threadsafe(
                                token_queue.put_nowait, ('reasoning', reasoning)
                            )
                    except json.JSONDecodeError as e:
                        logger.warning(f"[OpenAIProvider] ⚠️ JSON 解析失败: {e}")
                        logger.debug(f"[OpenAIProvider] 原始行: {line_text[:200]}")
                        continue
                    
                logger.info(f"[OpenAIProvider] 响应读取完成，共 {received_chunks} 个块，总长度: {total_content_length} 字符")
                
                if received_chunks == 0:
                    logger.warning(f"[OpenAIProvider] ⚠️ 未收到任何内容块")
                    if raw_response_lines:
                        logger.warning(f"[OpenAIProvider] ⚠️ 原始响应行预览:")
                        for i, line in enumerate(raw_response_lines[:10]):
                            logger.warning(f"[OpenAIProvider]   [{i}] {line}")
                
            except requests.exceptions.Timeout:
                logger.error(f"[OpenAIProvider] ❌ 请求超时 (300秒)")
            except requests.exceptions.ConnectionError as e:
                logger.error(f"[OpenAIProvider] ❌ 连接失败: {e}")
            except requests.exceptions.RequestException as e:
                logger.error(f"[OpenAIProvider] ❌ 请求异常: {e}")
            except Exception as e:
                logger.error(f"[OpenAIProvider] ❌ 未知异常: {e}", exc_info=True)
            finally:
                event_loop.call_soon_threadsafe(token_queue.put_nowait, None)
        
        loop.run_in_executor(None, _worker, loop)
        
        while True:
            item = await token_queue.get()
            if item is None:
                elapsed = time.time() - stats.start_time
                if received_chunks == 0:
                    logger.warning(f"[OpenAIProvider] ⚠️ 流式调用返回空响应 - URL: {url}, Model: {self.model}")
                    logger.warning(f"[OpenAIProvider] ⚠️ 耗时: {elapsed:.2f}s")
                else:
                    logger.info(f"[OpenAIProvider] 流式调用完成 ==================")
                    logger.info(f"[OpenAIProvider] 耗时: {elapsed:.2f}s")
                    logger.info(f"[OpenAIProvider] 收到块数: {received_chunks}")
                    logger.info(f"[OpenAIProvider] 总字符数: {total_content_length}")
                break
            
            item_type, text = item
            
            # 处理错误消息
            if item_type == 'error':
                logger.error(f"[OpenAIProvider] ❌ 流式调用错误: {text}")
                # 抛出异常让调用方处理
                raise Exception(text)
            
            stats.char_count += len(text)
            
            if item_type == 'content':
                yield text, None
            else:
                yield text, None