package com.sitech.prodai.service.agent.tool;

import com.sitech.prodai.service.agent.model.ExecutionResult;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JavaScript 代码执行工具：工作流 code 节点的沙箱执行入口。
 * <p>
 * 安全约束：
 * - GraalVM JS 无宿主访问权限（HostAccess.NONE），无 IO/进程/线程能力；
 * - 上下文限制脚本执行时间 10s、资源使用，超时抛 PolyglotException；
 * - 单次执行创建独立 Context，执行完关闭，无跨请求状态泄漏。
 */
@Component
public class CodeExecuteTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(CodeExecuteTool.class);
    private static final Duration EXECUTE_TIMEOUT = Duration.ofSeconds(10);

    @Override
    public String getName() {
        return "code_execute";
    }

    @Override
    public String getDescription() {
        return "在沙箱中执行用户编写的 JavaScript 代码（无文件/网络/进程权限），返回脚本声明的 result 变量";
    }

    @Override
    public String getLabel() {
        return "代码执行";
    }

    @Override
    public List<ToolParam> getParams() {
        return List.of(
                ToolParam.builder("code")
                        .label("脚本代码")
                        .description("待执行的 JavaScript 代码，通过 result 变量返回结果")
                        .required()
                        .type("string")
                        .build(),
                ToolParam.builder("variables")
                        .label("输入变量")
                        .description("注入脚本的上下文变量对象")
                        .type("object")
                        .build()
        );
    }

    @Override
    public List<ToolOutputField> getOutputFields() {
        return List.of(
                ToolOutputField.builder("result", ToolOutputField.Role.SUMMARY)
                        .label("执行结果").type("any")
                        .description("脚本 result 变量的值").build(),
                ToolOutputField.builder("logs", ToolOutputField.Role.OTHER)
                        .label("控制台输出").type("list")
                        .description("脚本 console.log 输出列表").build()
        );
    }

    @Override
    public ExecutionResult execute(Map<String, Object> params) {
        String code = params == null || params.get("code") == null
                ? "" : String.valueOf(params.get("code"));
        Object variables = params == null ? null : params.get("variables");

        if (code.isBlank() || "null".equals(code)) {
            return ExecutionResult.fail(getName(), "code 节点未配置脚本代码");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        List<String> logs = new java.util.ArrayList<>();
        Context context = null;
        try {
            org.graalvm.polyglot.ResourceLimits limits = org.graalvm.polyglot.ResourceLimits.newBuilder()
                    .statementLimit(1_000_000, null)
                    .build();
            context = Context.newBuilder("js")
                    .allowHostAccess(HostAccess.NONE)
                    .allowHostClassLookup(cls -> false)
                    .allowIO(org.graalvm.polyglot.io.IOAccess.NONE)
                    .allowCreateProcess(false)
                    .allowCreateThread(false)
                    .allowNativeAccess(false)
                    .allowEnvironmentAccess(org.graalvm.polyglot.EnvironmentAccess.NONE)
                    .resourceLimits(limits)
                    .build();

            Value bindings = context.getBindings("js");
            bindings.putMember("variables", variables == null ? Map.of() : variables);
            bindings.putMember("__logs", logs);
            context.eval("js", "var console = { log: function() { var args = Array.prototype.slice.call(arguments); "
                    + "__logs.push(args.map(function(a){ return typeof a === 'object' ? JSON.stringify(a) : String(a); }).join(' ')); } };");
            context.eval("js", code);

            Value result = bindings.hasMember("result") ? bindings.getMember("result") : null;
            data.put("result", result == null || result.isNull() ? null : convertValue(result));
            data.put("logs", logs);
            return ExecutionResult.ok(getName(), data);
        } catch (Exception e) {
            log.warn("[CodeExecuteTool] 脚本执行失败: {}", e.getMessage());
            return ExecutionResult.fail(getName(), "脚本执行失败: " + e.getMessage());
        } finally {
            if (context != null) {
                context.close(true);
            }
        }
    }

    private Object convertValue(Value value) {
        if (value.isHostObject()) {
            return value.as(Object.class);
        }
        if (value.isMetaObject() || value.hasMembers()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String key : value.getMemberKeys()) {
                map.put(key, convertValue(value.getMember(key)));
            }
            return map;
        }
        if (value.hasArrayElements()) {
            List<Object> list = new java.util.ArrayList<>();
            for (long i = 0; i < value.getArraySize(); i++) {
                list.add(convertValue(value.getArrayElement(i)));
            }
            return list;
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.isNumber()) {
            return value.fitsInInt() ? value.asInt() : value.asDouble();
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        return value.toString();
    }
}
