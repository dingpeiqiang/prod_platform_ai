# TOOLS.md - 工具与安全规范

> 本文档定义工具使用规范、错误处理机制和护栏系统。

## 工具使用规范

### 工具分类

| 分类           | 说明   | 示例工具                              |
| ------------ | ---- | --------------------------------- |
| `form`       | 表单相关 | recognize_scene, extract_fields |
| `validation` | 验证相关 | validate_field, validate_form   |
| `system`     | 系统相关 | get_status, health_check        |
| `data`       | 数据相关 | query_database                   |
| `file`       | 文件相关 | upload_file                      |
| `external`   | 外部调用 | call_api                         |

### 权限级别

```
PUBLIC (公开) → AUTHENTICATED (登录) → ADMIN (管理员) → RESTRICTED (受限)
```

- **PUBLIC**：无需认证，所有用户可用
- **AUTHENTICATED**：需要登录认证
- **ADMIN**：需要管理员权限
- **RESTRICTED**：受限使用，需要特殊授权

### 工具调用规则

1. **先识别意图**：确定需要调用的工具
2. **检查权限**：确认用户有权限调用
3. **验证参数**：检查参数是否符合 Schema
4. **执行并记录**：执行工具并记录日志
5. **返回结果**：返回结构化结果

## 错误处理

### 错误码定义

| 错误码    | 类型         | 说明        |
| ------ | ---------- | --------- |
| `E001` | 输入校验失败     | 输入包含不安全内容 |
| `E002` | Schema 不匹配 | 输出不符合定义   |
| `E003` | 必填字段缺失     | 缺少必填字段    |
| `E004` | 类型错误       | 字段类型不正确   |
| `E005` | 范围超限       | 数值超出允许范围  |
| `E006` | 权限不足       | 用户权限不够    |
| `E007` | 工具不存在      | 请求的工具未注册  |
| `E008` | 执行失败       | 工具执行出错    |

### 错误响应格式

```json
{
  "success": false,
  "error": {
    "code": "E003",
    "message": "必填字段缺失",
    "details": [
      {"field": "required_field", "message": "必填字段不能为空"}
    ]
  }
}
```

## 护栏系统

### 输入护栏检测

| 威胁类型               | 描述              | 严重级别 |
| ------------------ | --------------- | ---- |
| `xss_script`       | XSS 脚本注入        | 高    |
| `xss_onerror`      | HTML 事件注入       | 中    |
| `xss_javascript`   | JavaScript 协议注入 | 高    |
| `sql_union`        | SQL UNION 注入    | 高    |
| `sql_drop`         | SQL DROP 注入     | 严重   |
| `sql_exec`         | SQL 执行命令        | 严重   |
| `cmd_shell`        | Shell 命令注入      | 严重   |
| `cmd_pipe`         | 管道命令注入          | 高    |
| `cmd_semicolon`    | 分号命令注入          | 高    |
| `prompt_ignore`    | Prompt 忽略攻击     | 中    |
| `prompt_override`  | Prompt 覆盖攻击     | 中    |
| `prompt_jailbreak` | Prompt 越狱攻击     | 高    |
| `malicious_url`    | 恶意协议 URL        | 中    |
| `path_traversal`   | 路径遍历攻击          | 高    |

### 敏感信息检测

- API Key
- Password
- Private Key
- Bearer Token

---

**相关文档**：
- [架构设计规范](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/ARCHITECTURE.md)
- [表单系统规范](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/FORMS.md)
- [编码质量规范](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/AGENTS.md)
- [开发指南](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/DEVELOPMENT.md)