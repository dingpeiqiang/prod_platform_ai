# 外部API调用配置化方案

## 概述

支持通过**本体配置**调用外部API，包括：
- **枚举API**：从外部API获取下拉选项
- **校验API**：调用外部API进行字段校验
- **提交API**：表单提交时调用外部API

---

## 核心架构

```
本体配置（JSON）
    ↓
ExternalAPIService（异步HTTP客户端）
    ↓
外部API服务
    ↓
结果返回（含缓存和降级）
```

---

## 通用API配置参数

| 参数 | 说明 | 必填 | 默认值 |
|------|------|------|---------|
| `url` | API地址 | ✅ | - |
| `method` | HTTP方法 | ❌ | GET |
| `headers` | 请求头 | ❌ | {} |
| `timeout` | 超时时间（秒） | ❌ | 30 |
| `dataPath` | 响应数据路径 | ❌ | - |
| `cacheTTL` | 缓存时间（秒） | ❌ | 300 |
| `retryCount` | 重试次数 | ❌ | 3 |
| `fallback` | 降级数据 | ❌ | - |

### URL模板变量
支持在URL和headers中使用 `${变量名}` 模板：

```json
{
  "url": "https://api.example.com/users/${userId}",
  "headers": {
    "Authorization": "Bearer ${token}"
  }
}
```

### dataPath说明
用于从响应中提取特定数据，支持 `.` 分隔的路径：

```json
{
  "dataPath": "data.cities"
}
```

示例响应：
```json
{
  "code": 200,
  "data": {
    "cities": ["北京", "上海", "广州"]
  }
}
```

提取结果：`["北京", "上海", "广州"]`

---

## 1. 枚举API配置

### 配置结构

```json
{
  "fieldCode": "city",
  "fieldName": "城市",
  "fieldType": "select",
  "enumConfig": {
    "type": "api",
    "api": {
      "url": "https://api.example.com/cities",
      "method": "GET",
      "timeout": 10,
      "dataPath": "data.cities",
      "cacheTTL": 3600,
      "fallback": ["北京", "上海", "广州"]
    }
  }
}
```

### 完整示例

```json
{
  "fieldCode": "department",
  "fieldName": "部门",
  "fieldType": "select",
  "required": true,
  "enumConfig": {
    "type": "api",
    "api": {
      "url": "https://api.company.com/departments",
      "method": "GET",
      "headers": {
        "X-API-Key": "your-api-key"
      },
      "timeout": 5,
      "dataPath": "result",
      "cacheTTL": 1800,
      "retryCount": 2,
      "fallback": ["技术部", "产品部", "运营部"]
    }
  }
}
```

### 静态枚举（不调用API）

```json
{
  "enumConfig": {
    "type": "static",
    "options": ["技术部", "产品部", "运营部"]
  }
}
```

---

## 2. 校验API配置

### 配置结构

```json
{
  "fieldCode": "username",
  "fieldName": "用户名",
  "validateConfig": {
    "type": "api",
    "api": {
      "url": "https://api.example.com/validate/username",
      "method": "POST",
      "timeout": 5,
      "cacheTTL": 0
    },
    "fallbackValid": true
  }
}
```

### API响应格式

外部校验API应返回以下格式之一：

**格式1：布尔值**
```json
true
```

**格式2：带message的对象**
```json
{
  "valid": false,
  "message": "用户名已存在"
}
```

### 完整示例

```json
{
  "fieldCode": "idCard",
  "fieldName": "身份证号",
  "fieldType": "input",
  "required": true,
  "validateConfig": {
    "type": "api",
    "api": {
      "url": "https://api.example.com/validate/idcard",
      "method": "POST",
      "headers": {
        "Content-Type": "application/json"
      },
      "timeout": 3,
      "dataPath": "isValid",
      "cacheTTL": 0
    },
    "fallbackValid": false
  }
}
```

### 内置校验+外部校验混合

可以同时使用内置规则和外部API：

```json
{
  "fieldCode": "username",
  "fieldName": "用户名",
  "rules": [
    {
      "rule_type": "minLength",
      "rule_value": 3,
      "message": "用户名至少3个字符"
    }
  ],
  "validateConfig": {
    "type": "api",
    "api": {
      "url": "https://api.example.com/check/username",
      "method": "POST"
    },
    "fallbackValid": true
  }
}
```

---

## 3. 提交API配置

### 配置结构

在表单根级别配置：

```json
{
  "formCode": "order",
  "formName": "订单",
  "entities": [...],
  "submitConfig": {
    "type": "api",
    "api": {
      "url": "https://api.example.com/submit/order",
      "method": "POST",
      "headers": {
        "Content-Type": "application/json"
      },
      "timeout": 30
    }
  }
}
```

### 完整示例

```json
{
  "formCode": "sales_order",
  "formName": "销售订单",
  "entities": [...],
  "submitConfig": {
    "type": "api",
    "api": {
      "url": "https://api.company.com/orders",
      "method": "POST",
      "headers": {
        "Content-Type": "application/json",
        "X-API-Key": "your-api-key",
        "Authorization": "Bearer ${token}"
      },
      "timeout": 60,
      "dataPath": ""
    }
  }
}
```

---

## 完整演示本体

查看 `config/ontologies/external_api_demo.json`，包含：
- 静态枚举和API枚举
- 校验API
- 提交API

**使用方法：** 在聊天窗口输入"外部API"或"API演示"

---

## 服务API

### 获取枚举选项
```python
from app.services.external_api_service import external_api_service

options = await external_api_service.get_enum_options(enum_config, context)
```

### 外部校验
```python
result = await external_api_service.validate_with_external(
    validate_config, 
    field_value, 
    context
)
```

### 外部提交
```python
result = await external_api_service.submit_with_external(
    submit_config, 
    form_data, 
    context
)
```

---

## 核心特性

| 特性 | 说明 |
|------|------|
| ✅ **异步调用** | 使用httpx异步HTTP客户端 |
| ✅ **自动重试** | 失败自动重试，可配置次数 |
| ✅ **智能缓存** | 可配置缓存时间，避免重复调用 |
| ✅ **优雅降级** | API失败时使用fallback数据 |
| ✅ **模板变量** | URL和Header支持 `${变量}` |
| ✅ **数据路径** | 支持 `data.path` 提取嵌套数据 |
| ✅ **超时控制** | 可配置超时时间 |

---

## 依赖

在 `requirements.txt` 中已添加：
```
httpx==0.27.2
```

---

## 使用示例

### 场景：从ERP获取部门列表

**本体配置：**
```json
{
  "fieldCode": "department",
  "fieldName": "部门",
  "fieldType": "select",
  "enumConfig": {
    "type": "api",
    "api": {
      "url": "https://erp.company.com/api/departments",
      "method": "GET",
      "headers": {
        "Authorization": "Bearer ${erpToken}"
      },
      "timeout": 10,
      "dataPath": "data.list",
      "cacheTTL": 3600,
      "fallback": ["技术部", "产品部"]
    }
  }
}
```

### 场景：调用第三方身份校验

**本体配置：**
```json
{
  "fieldCode": "idCard",
  "fieldName": "身份证号",
  "validateConfig": {
    "type": "api",
    "api": {
      "url": "https://id-check.example.com/verify",
      "method": "POST",
      "timeout": 5,
      "cacheTTL": 0
    },
    "fallbackValid": false
  }
}
```

### 场景：表单提交到业务系统

**本体配置：**
```json
{
  "submitConfig": {
    "type": "api",
    "api": {
      "url": "https://biz.company.com/submit",
      "method": "POST",
      "headers": {
        "Content-Type": "application/json"
      },
      "timeout": 30
    }
  }
}
```
