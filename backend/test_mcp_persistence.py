"""测试 MCP 调用日志持久化功能"""
import sys
import time
from datetime import datetime, timedelta
from sqlalchemy.orm import Session
from app.core.database import SessionLocal, engine
from app.models.mcp_call_log import MCPCallLog, MCPToolStats
from app.mcp_tools import get_toolhub

def test_insert_log():
    """测试插入调用日志"""
    print("\n" + "="*60)
    print("测试 1: 插入调用日志")
    print("="*60)
    
    db = SessionLocal()
    try:
        # 创建测试日志
        log = MCPCallLog(
            tool_name="test_tool",
            tool_category="general",
            success=True,
            execution_time_ms=123.45,
            error_message=None,
            request_args='{"param1": "value1"}',
            response_data='{"result": "success"}'
        )
        
        db.add(log)
        db.commit()
        
        print(f"✓ 日志插入成功，ID: {log.id}")
        
        # 查询验证
        queried_log = db.query(MCPCallLog).filter(MCPCallLog.id == log.id).first()
        if queried_log:
            print(f"✓ 查询成功:")
            print(f"  - 工具名称: {queried_log.tool_name}")
            print(f"  - 成功状态: {queried_log.success}")
            print(f"  - 执行时间: {queried_log.execution_time_ms}ms")
            print(f"  - 时间戳: {queried_log.timestamp}")
        else:
            print("✗ 查询失败!")
            
    except Exception as e:
        db.rollback()
        print(f"✗ 插入失败: {e}")
        return False
    finally:
        db.close()
    
    return True


def test_query_stats():
    """测试查询统计数据"""
    print("\n" + "="*60)
    print("测试 2: 查询统计数据")
    print("="*60)
    
    db = SessionLocal()
    try:
        # 插入几条测试数据
        for i in range(5):
            log = MCPCallLog(
                tool_name=f"tool_{i % 2}",  # tool_0 和 tool_1
                tool_category="test",
                success=(i % 2 == 0),  # 交替成功/失败
                execution_time_ms=100 + i * 10,
                timestamp=datetime.now() - timedelta(hours=i)
            )
            db.add(log)
        db.commit()
        
        # 查询每个工具的统计
        seven_days_ago = datetime.now() - timedelta(days=7)
        
        for tool_name in ["tool_0", "tool_1"]:
            logs = db.query(MCPCallLog).filter(
                MCPCallLog.tool_name == tool_name,
                MCPCallLog.timestamp >= seven_days_ago
            ).all()
            
            total = len(logs)
            success = sum(1 for l in logs if l.success)
            avg_time = sum(l.execution_time_ms for l in logs) / total if total > 0 else 0
            
            print(f"\n工具: {tool_name}")
            print(f"  - 总调用: {total}")
            print(f"  - 成功: {success}")
            print(f"  - 失败: {total - success}")
            print(f"  - 平均响应时间: {avg_time:.2f}ms")
        
        print("\n✓ 统计查询成功")
        
    except Exception as e:
        db.rollback()
        print(f"✗ 查询失败: {e}")
        return False
    finally:
        db.close()
    
    return True


def test_real_tool_call():
    """测试真实工具调用并记录"""
    print("\n" + "="*60)
    print("测试 3: 真实工具调用记录")
    print("="*60)
    
    hub = get_toolhub()
    tools = hub.list_tools()
    
    if not tools:
        print("⚠ 没有可用的工具，跳过测试")
        return True
    
    # 选择第一个工具进行测试
    test_tool = tools[0]
    tool_name = test_tool["name"]
    
    print(f"测试工具: {tool_name}")
    print(f"描述: {test_tool.get('description', 'N/A')}")
    
    db = SessionLocal()
    try:
        start_time = time.time()
        
        # 执行工具（使用空参数）
        result = hub.execute_sync(tool_name, {})
        
        elapsed_ms = (time.time() - start_time) * 1000
        
        # 记录到数据库
        log = MCPCallLog(
            tool_name=tool_name,
            tool_category=test_tool.get("metadata", {}).get("category"),
            success=result.get("success", False),
            execution_time_ms=round(elapsed_ms, 2),
            error_message=result.get("error"),
            request_args='{}',
            response_data=str(result)[:1000]  # 限制长度
        )
        
        db.add(log)
        db.commit()
        
        print(f"\n✓ 工具执行完成")
        print(f"  - 成功: {result.get('success', False)}")
        print(f"  - 耗时: {elapsed_ms:.2f}ms")
        print(f"  - 已记录到数据库，ID: {log.id}")
        
    except Exception as e:
        db.rollback()
        print(f"✗ 工具执行失败: {e}")
        return False
    finally:
        db.close()
    
    return True


def cleanup_test_data():
    """清理测试数据"""
    print("\n" + "="*60)
    print("清理测试数据")
    print("="*60)
    
    db = SessionLocal()
    try:
        # 删除测试工具的数据
        deleted = db.query(MCPCallLog).filter(
            MCPCallLog.tool_name.like("test_%")
        ).delete()
        
        db.commit()
        print(f"✓ 已删除 {deleted} 条测试记录")
        
    except Exception as e:
        db.rollback()
        print(f"✗ 清理失败: {e}")
    finally:
        db.close()


if __name__ == "__main__":
    print("\n" + "="*60)
    print("MCP 调用日志持久化功能测试")
    print("="*60)
    
    results = []
    
    # 运行测试
    results.append(("插入日志", test_insert_log()))
    results.append(("查询统计", test_query_stats()))
    results.append(("真实调用", test_real_tool_call()))
    
    # 清理
    cleanup_test_data()
    
    # 总结
    print("\n" + "="*60)
    print("测试结果总结")
    print("="*60)
    
    all_passed = True
    for name, passed in results:
        status = "✓ 通过" if passed else "✗ 失败"
        print(f"{name:20s} {status}")
        if not passed:
            all_passed = False
    
    print("\n" + "="*60)
    if all_passed:
        print("✓ 所有测试通过！数据持久化功能正常")
    else:
        print("✗ 部分测试失败，请检查错误信息")
    print("="*60 + "\n")
    
    sys.exit(0 if all_passed else 1)
