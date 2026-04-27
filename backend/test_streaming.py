#!/usr/bin/env python3
"""
测试流式输出优化的脚本
"""
import asyncio
import sys
import os

# 添加 app 目录到 Python 路径
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'app'))

from services.llm_service import llm_service, StreamStats
from core.config_loader import config_loader


async def test_stream_buffer():
    """测试 StreamBuffer 类"""
    print("=" * 60)
    print("测试 StreamBuffer 类")
    print("=" * 60)
    
    # 导入 StreamBuffer（注意：它在 llm_service.py 中）
    from services.llm_service import StreamBuffer
    
    buffer = StreamBuffer(buffer_size=10, flush_interval=0.01)
    
    test_text = "这是一个测试文本，用于验证缓冲区功能是否正常工作。"
    
    print(f"\n输入文本: {test_text}")
    print(f"文本长度: {len(test_text)}")
    
    output = []
    for char in test_text:
        flushed = buffer.add(char)
        if flushed:
            output.append(flushed)
            print(f"  刷新: '{flushed}'")
    
    # 刷新剩余内容
    remaining = buffer.flush()
    if remaining:
        output.append(remaining)
        print(f"  最终刷新: '{remaining}'")
    
    reconstructed = ''.join(output)
    print(f"\n重建文本: {reconstructed}")
    print(f"重建成功: {reconstructed == test_text}")
    print(f"总块数: {buffer.total_chunks}")
    print(f"总字符: {buffer.total_chars}")
    print()


async def test_stream_stats():
    """测试 StreamStats 类"""
    print("=" * 60)
    print("测试 StreamStats 类")
    print("=" * 60)
    
    stats = StreamStats(
        start_time=0,
        end_time=10,
        token_count=500,
        char_count=2500,
        chunk_count=125
    )
    
    print(f"耗时: {stats.elapsed}s")
    print(f"Token 数: {stats.token_count}")
    print(f"字符数: {stats.char_count}")
    print(f"TPS: {stats.tokens_per_second:.1f}")
    print(f"CPS: {stats.chars_per_second:.1f}")
    print()


def test_config():
    """测试配置加载"""
    print("=" * 60)
    print("测试配置加载")
    print("=" * 60)
    
    app_config = config_loader.get_app_config()
    llm_config = app_config.get('llm', {})
    
    print(f"LLM Provider: {llm_config.get('provider')}")
    print(f"LLM Model: {llm_config.get('model')}")
    print(f"Buffer Size: {llm_config.get('bufferSize')}")
    print(f"Flush Interval: {llm_config.get('flushInterval')}")
    print()


def main():
    """主函数"""
    print("\n")
    print("╔" + "═" * 58 + "╗")
    print("║" + " " * 10 + "AI 驱动动态表单 - 流式输出优化测试" + " " * 10 + "║")
    print("╚" + "═" * 58 + "╝")
    print()
    
    # 测试配置
    test_config()
    
    # 运行异步测试
    loop = asyncio.get_event_loop()
    loop.run_until_complete(asyncio.gather(
        test_stream_buffer(),
        test_stream_stats()
    ))
    
    print("=" * 60)
    print("✅ 所有测试完成！")
    print("=" * 60)
    print("\n优化内容总结：")
    print("1. 添加了 StreamBuffer 类：批量发送 Token，减少网络开销")
    print("2. 添加了 StreamStats 类：统计流式输出性能")
    print("3. 优化了 SSE 输出：从逐字符改为块级输出")
    print("4. 添加了性能统计信息输出")
    print("5. 更新了配置文件，支持 bufferSize 和 flushInterval 参数")
    print()


if __name__ == "__main__":
    main()
