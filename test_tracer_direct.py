# 直接测试 tracer 功能
import sys
sys.path.insert(0, 'backend')

from app.harness.observability.tracer import get_tracer, SpanStatus

# 获取 tracer
tracer = get_tracer()

# 添加一些测试追踪
span1 = tracer.start_span("test_span_1", trace_id="test_trace_1", component="test")
span1.add_tag("status", "success")
tracer.finish_span(span1, SpanStatus.OK)

span2 = tracer.start_span("test_span_2", trace_id="test_trace_2", component="test")
span2.add_tag("status", "success")
tracer.finish_span(span2, SpanStatus.OK)

# 导出追踪
traces = tracer.export_traces()
print(f"Number of traces: {len(traces)}")
print(f"Traces: {traces}")

# 获取统计
stats = tracer.get_stats()
print(f"Stats: {stats}")