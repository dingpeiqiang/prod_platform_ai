# A2UI 表单组件模板参考（24 种组件全覆盖）

**唯一模板库** — Agent 必须调用 `render_a2ui` 工具（非 `<a2ui-json>` 标签）。
模板中 `<...>` 标记的值为动态占位符，运行时从 MCP 查询结果填入。

---

## render_a2ui 工具调用约定

```python
render_a2ui(
    components=[...],       # 组件数组（必填）
    title="标题",            # 卡片标题
    description="描述",      # 卡片副标题
    data_model={},          # 表单初始数据（可选）
    surface_id=""           # 留空自动生成，更新已有 Surface 时传入
)
```

---

## 24 种组件速查

| # | 组件 | 分类 | 用途 | 模板编号 |
|---|------|------|------|---------|
| 1 | Text | 基础展示 | 文本/Markdown/HTML | 1~15 |
| 2 | Button | 基础展示 | 按钮 | 4, 11 |
| 3 | Card | 基础展示 | 卡片容器 | 4,5,6,7,8,9,15 |
| 4 | Divider | 基础展示 | 分割线 | 5, 15 |
| 5 | Link | 基础展示 | 超链接 | 4, 15 |
| 6 | Image | 基础展示 | 图片展示 | 9, 15 |
| 7 | List | 基础展示 | 键值列表 | 5, 7, 15 |
| 8 | Form | 表单输入 | 表单容器 | 1,2,3,10,13 |
| 9 | TextField | 表单输入 | 文本输入 | 1, 3, 10 |
| 10 | Select | 表单输入 | 下拉单选 | 1,2,3,10 |
| 11 | MultiSelect | 表单输入 | 下拉多选 | 8, 13 |
| 12 | Checkbox | 表单输入 | 复选框 | 2,6,8,13 |
| 13 | Switch | 表单输入 | 开关 | 2, 6, 13 |
| 14 | NumberInput | 表单输入 | 数字输入 | 1, 6 |
| 15 | Slider | 表单输入 | 滑动条 | 8, 13 |
| 16 | DatePicker | 表单输入 | 日期选择 | 10 |
| 17 | TimePicker | 表单输入 | 时间选择 | 10 |
| 18 | FileUpload | 文件上传 | 单文件上传 | 14 |
| 19 | MultiFileUpload | 文件上传 | 多文件上传 | 14 |
| 20 | ImageUpload | 文件上传 | 图片上传 | 3, 14 |
| 21 | Container | 布局 | 弹性布局 | 9, 15 |
| 22 | DataGrid | 数据展示 | 数据表格 | 11 |
| 23 | Chart | 数据展示 | 图表 | 12 |
| 24 | Progress | 数据展示 | 进度条 | 7 |

---

## 模板 1：宽带新装申请表

**涉及组件**：Form, TextField, Select, NumberInput, Button

```python
render_a2ui(
    components=[{
        "type": "Form",
        "fields": [
            {"type": "TextField", "name": "customer_name", "label": "客户姓名", "defaultValue": "<crm.name>"},
            {"type": "TextField", "name": "phone_no", "label": "手机号", "defaultValue": "<crm.phone_no>"},
            {"type": "TextField", "name": "id_no", "label": "身份证号", "defaultValue": "<crm.id_no>"},
            {"type": "TextField", "name": "install_address", "label": "安装地址", "defaultValue": "<crm.address>"},
            {"type": "Select", "name": "speed", "label": "宽带速率", "options": [
                {"label": "100M - 59元/月", "value": "100M"},
                {"label": "300M - 89元/月", "value": "300M"},
                {"label": "500M - 129元/月", "value": "500M"},
                {"label": "1000M - 199元/月", "value": "1000M"}
            ]},
            {"type": "NumberInput", "name": "contract_months", "label": "合约期限(月)", "defaultValue": 24, "min": 6, "max": 36, "step": 6},
        ],
        "submitLabel": "提交申请",
        "validation": {
            "customer_name": {"required": True},
            "phone_no": {"required": True, "pattern": r"^1[3-9]\d{9}$"},
            "id_no": {"required": True},
            "install_address": {"required": True},
            "contract_months": {"min": 6, "max": 36},
        }
    }],
    title="宽带新装申请",
    description="请确认以下信息无误后提交，预计3个工作日内上门安装。"
)
```

表单提交后调用：
```python
crm_broadband_apply(
    phone_no=form_data["phone_no"],
    customer_name=form_data["customer_name"],
    id_no=form_data["id_no"],
    install_address=form_data["install_address"],
    speed=form_data["speed"],
    contract_months=form_data["contract_months"]
)
```

---

## 模板 2：套餐升级确认表单

**涉及组件**：Form, Select, Checkbox, Switch

```python
render_a2ui(
    components=[{
        "type": "Form",
        "fields": [
            {"type": "TextField", "name": "phone_no", "label": "手机号", "defaultValue": "<crm.phone_no>"},
            {"type": "Select", "name": "current_package", "label": "当前套餐", "options": [
                {"label": f"{c['product_name']} ({c['monthly_fee']}元/月)", "value": c['product_id']}
                for c in current_contracts
            ]},
            {"type": "Select", "name": "target_package", "label": "升级到", "options": [
                {"label": f"{p['name']} - {p['monthly_fee']}元/月", "value": p['id']}
                for p in available_packages
            ]},
            {"type": "Switch", "name": "auto_renew", "label": "到期自动续约", "defaultValue": True, "activeText": "是", "inactiveText": "否"},
            {"type": "Checkbox", "name": "confirm_change", "label": "我已了解套餐变更后将立即生效，原套餐剩余时长自动折算"},
        ],
        "submitLabel": "确认升级",
        "validation": {
            "phone_no": {"required": True},
            "target_package": {"required": True},
            "confirm_change": {"required": True},
        }
    }],
    title="套餐变更确认",
    description="升级套餐后立即生效，当月按天折算费用。"
)
```

---

## 模板 3：故障报修表单

**涉及组件**：Form, TextField, Select, ImageUpload

```python
render_a2ui(
    components=[{
        "type": "Form",
        "fields": [
            {"type": "TextField", "name": "phone_no", "label": "手机号", "defaultValue": "<crm.phone_no>"},
            {"type": "Text", "content": "**故障地址**：<crm.address>", "format": "markdown"},
            {"type": "Select", "name": "issue_type", "label": "故障类型", "options": [
                {"label": "宽带掉线/频繁断网", "value": "宽带掉线"},
                {"label": "网速慢", "value": "网速慢"},
                {"label": "无法连接", "value": "无法连接"},
                {"label": "光猫/路由器故障", "value": "设备故障"},
                {"label": "WiFi信号弱", "value": "信号弱"},
                {"label": "其他", "value": "其他"}
            ]},
            {"type": "TextField", "name": "description", "label": "故障描述", "placeholder": "请详细描述故障现象（如：光猫指示灯状态、故障发生时间等）...", "multiline": True},
            {"type": "ImageUpload", "name": "fault_photo", "label": "现场照片（可选）", "maxCount": 3, "maxSize": 10485760},
        ],
        "submitLabel": "提交报修",
        "validation": {
            "phone_no": {"required": True},
            "issue_type": {"required": True},
            "description": {"required": True},
        }
    }],
    title="宽带故障报修",
    description="工程师将在24小时内联系您，请保持电话畅通。上传现场照片有助于更快定位问题。"
)
```

---

## 模板 4：受理结果确认卡片

**涉及组件**：Card, Text, Button, Link

```python
render_a2ui(
    components=[{
        "type": "Card",
        "title": "受理成功 ✅",
        "variant": "elevated",
        "body": [
            {"type": "Text", "content": f"您的**{business_type}**申请已成功提交！", "format": "markdown"},
            {"type": "Container", "direction": "column", "gap": 6, "children": [
                {"type": "Text", "content": f"**订单号**：{order_id}", "format": "markdown"},
                {"type": "Text", "content": f"**客户姓名**：{customer_name}", "format": "markdown"},
                {"type": "Text", "content": f"**办理业务**：{business_type}", "format": "markdown"},
                {"type": "Text", "content": f"**预计完工**：{estimated_date}", "format": "markdown"},
                {"type": "Text", "content": f"**月费**：{monthly_fee}元 | **一次性费用**：{one_time_fee}元", "format": "markdown"},
            ]},
            {"type": "Divider"},
            {"type": "Container", "direction": "row", "gap": 8, "justify": "center", "children": [
                {"type": "Button", "label": "查询订单进度", "variant": "primary", "action": "check_order"},
                {"type": "Link", "href": f"https://shop.example.com/orders/{order_id}", "text": "查看详情 →", "target": "_blank"},
            ]},
        ]
    }],
    title="办理结果",
    description="如安装过程中遇到问题，可随时联系我们。"
)
```

---

## 模板 5：客户合约信息展示

**涉及组件**：Card, Text, List, Divider

```python
render_a2ui(
    components=[{
        "type": "Card",
        "title": f"{customer_name} 的账户信息",
        "subtitle": f"手机号：{phone_no}",
        "variant": "outlined",
        "body": [
            {"type": "List", "items": [
                {"label": "账户状态", "value": status_map.get(user_status, user_status), "tag": user_status},
                {"label": "信誉等级", "value": credit_rating},
                {"label": "当前总月费", "value": f"{total_monthly_fee}元"},
                {"label": "账户余额", "value": f"{balance}元"},
            ], "showTag": True},
            {"type": "Divider", "text": "当前合约"},
            *[{"type": "List", "items": [
                {"label": "产品名称", "value": c['product_name']},
                {"label": "月费", "value": f"{c['monthly_fee']}元"},
                {"label": "有效期", "value": f"{c['start']} ~ {c['end']}"},
                {"label": "状态", "value": c['status'], "tag": c['status']},
            ], "showTag": True} for c in contracts],
        ]
    }],
    title="客户合约信息",
    description=f"查询时间：{datetime.now().strftime('%Y-%m-%d %H:%M')}"
)
```

---

## 模板 6：增值服务选择表单

**涉及组件**：Form, Text, Divider, Checkbox, Switch, NumberInput, Card, Container

```python
render_a2ui(
    components=[{
        "type": "Form",
        "fields": [
            {"type": "Text", "content": "### 📺 IPTV 高清电视\n120+ 高清频道 | 回看7天 | **30元/月**", "format": "markdown"},
            {"type": "Checkbox", "name": "addon_iptv", "label": "开通 IPTV（30元/月）"},
            {"type": "Divider", "dashed": True},
            {"type": "Text", "content": "### 📡 全屋 WiFi6 覆盖\nMesh 组网 | 千兆全覆盖 | **20元/月**", "format": "markdown"},
            {"type": "Checkbox", "name": "addon_wifi6", "label": "开通全屋WiFi（20元/月）"},
            {"type": "Divider", "dashed": True},
            {"type": "Text", "content": "### 🏠 智能家居套装\n智能门锁+摄像头+传感器 | **49元/月**", "format": "markdown"},
            {"type": "Checkbox", "name": "addon_smarthome", "label": "开通智能家居（49元/月）"},
            {"type": "NumberInput", "name": "addon_smarthome_count", "label": "设备数量", "defaultValue": 1, "min": 1, "max": 5, "step": 1},
            {"type": "Divider", "dashed": True},
            {"type": "Text", "content": "### 🛡️ 网络安全保障\n防病毒+防钓鱼+家长控制 | **10元/月**", "format": "markdown"},
            {"type": "Switch", "name": "addon_security", "label": "开通安全防护（10元/月）", "defaultValue": False, "activeText": "开通", "inactiveText": "不开通"},
        ],
        "submitLabel": "确认增值服务，继续办理"
    }],
    title="增值服务选择",
    description="以下服务可随时退订。确认后进入下一步。"
)
```

---

## 模板 7：工单进度跟踪卡片

**涉及组件**：Card, Progress, List, Text

```python
render_a2ui(
    components=[{
        "type": "Card",
        "title": f"工单 #{fault_id} — 处理进度",
        "variant": "elevated",
        "body": [
            {"type": "Progress", "percent": progress_percent,
             "status": progress_status, "showLabel": True,
             "indicatorText": f"{progress_percent}% — {status_text}"},
            {"type": "Divider"},
            {"type": "List", "items": [
                {"label": "报修时间", "value": created_at},
                {"label": "故障类型", "value": issue_type},
                {"label": "当前状态", "value": status_text, "tag": progress_status},
                {"label": "负责工程师", "value": engineer_name or "待分配"},
                {"label": "预计到达", "value": estimated_arrival or "待确认"},
                {"label": "联系电话", "value": engineer_phone or "-"},
            ], "showTag": True},
            {"type": "Divider"},
            {"type": "Text", "content": "工程师正在路上，请耐心等待。如需改约，请回复**改约**。", "format": "markdown"},
        ]
    }],
    title="故障处理进度",
    description=f"更新时间：{updated_at}"
)
```

进度状态映射：
```python
PROGRESS_MAP = {
    "created":    {"percent": 10, "status": "default", "text": "已创建"},
    "dispatched": {"percent": 30, "status": "warning", "text": "已派单"},
    "en_route":   {"percent": 60, "status": "warning", "text": "工程师在路上"},
    "repairing":  {"percent": 80, "status": "success", "text": "维修中"},
    "resolved":   {"percent": 100, "status": "success", "text": "已修复"},
    "cancelled":  {"percent": 0, "status": "error", "text": "已取消"},
}
```

---

## 模板 8：合约续约产品选择

**涉及组件**：Form, MultiSelect, Slider, Checkbox, Text

```python
render_a2ui(
    components=[{
        "type": "Form",
        "fields": [
            {"type": "Text", "content": f"### {customer_name} 的合约续约\n当前总月费：**{total_monthly_fee}元**", "format": "markdown"},
            {"type": "MultiSelect", "name": "renew_products", "label": "续约产品",
             "options": [{"label": f"{c['product_name']} ({c['monthly_fee']}元/月)", "value": c['product_id']} for c in expiring_contracts],
             "defaultValue": [c['product_id'] for c in expiring_contracts],
             "placeholder": "请选择要续约的产品"},
            {"type": "Slider", "name": "renew_months", "label": "续约月数",
             "defaultValue": 12, "min": 6, "max": 36, "step": 6, "showInput": True, "showStops": True},
            {"type": "Text", "content": "### 续约优惠\n- 12个月：免1个月月费\n- 24个月：免3个月月费\n- 36个月：免6个月月费", "format": "markdown"},
            {"type": "Checkbox", "name": "accept_renew_terms", "label": "我已阅读并同意《续约服务协议》"},
        ],
        "submitLabel": "确认续约",
        "validation": {
            "renew_products": {"required": True},
            "accept_renew_terms": {"required": True},
        }
    }],
    title="合约续约",
    description="选择续约产品和时长，点击「确认续约」提交。"
)
```

---

## 模板 9：套餐选择交互卡片

**涉及组件**：Container, Card (带 action), Text, Image

```python
render_a2ui(
    components=[
        {"type": "Text", "content": "### 📡 请点击卡片选择套餐\n您的地址支持以下速率：", "format": "markdown"},
        {"type": "Container", "direction": "column", "gap": 8, "children": [
            {
                "type": "Card",
                "id": "card_100M",
                "title": "🌐 100M — 百兆光纤",
                "subtitle": "59元/月 | 24个月合约",
                "action": "select_package_100M",
                "variant": "outlined",
                "body": [
                    {"type": "Image", "src": "https://cdn.example.com/icons/speed-100.png", "alt": "100M", "width": 48, "height": 48, "fit": "contain"},
                    {"type": "Text", "content": "适合日常上网、看视频 | 赠送基础路由器", "format": "markdown"},
                ]
            },
            {
                "type": "Card",
                "id": "card_300M",
                "title": "🚀 300M — 三百兆畅享",
                "subtitle": "89元/月 | 24个月合约",
                "action": "select_package_300M",
                "variant": "outlined",
                "body": [
                    {"type": "Image", "src": "https://cdn.example.com/icons/speed-300.png", "alt": "300M", "width": 48, "height": 48, "fit": "contain"},
                    {"type": "Text", "content": "高清视频、在线游戏无忧 | 赠送千兆路由器（价值299元）", "format": "markdown"},
                ]
            },
            {
                "type": "Card",
                "id": "card_500M",
                "title": "⚡ 500M — 五百兆极速",
                "subtitle": "129元/月 | 24个月合约",
                "action": "select_package_500M",
                "variant": "elevated",
                "body": [
                    {"type": "Image", "src": "https://cdn.example.com/icons/speed-500.png", "alt": "500M", "width": 48, "height": 48, "fit": "contain"},
                    {"type": "Text", "content": "多设备同时在线不卡顿 | 赠送 WiFi6 路由器（价值499元）+ IPTV 首年免费", "format": "markdown"},
                ]
            },
            {
                "type": "Card",
                "id": "card_1000M",
                "title": "💎 1000M — 千兆旗舰",
                "subtitle": "199元/月 | 24个月合约",
                "action": "select_package_1000M",
                "variant": "outlined",
                "body": [
                    {"type": "Image", "src": "https://cdn.example.com/icons/speed-1000.png", "alt": "1000M", "width": 48, "height": 48, "fit": "contain"},
                    {"type": "Text", "content": "全屋光纤覆盖 + 企业级体验 | 赠送 WiFi6 Mesh 套装（价值899元）+ IPTV 永久免费 + 智能家居套装首年免费", "format": "markdown"},
                ]
            },
        ]},
    ],
    title="宽带套餐选择",
    description="★ 点击卡片即可选择套餐，无需回复文字。"
)
```

> ★ 每个 Card 必须设置 `action` 属性使其可点击。用户点击后对应 action 值作为用户消息回传。
> action 命名规则：`select_package_{速率}`

---

## 模板 10：上门预约表单

**涉及组件**：Form, DatePicker, TimePicker, TextField, Select

```python
render_a2ui(
    components=[{
        "type": "Form",
        "fields": [
            {"type": "TextField", "name": "phone_no", "label": "手机号", "defaultValue": "<crm.phone_no>"},
            {"type": "TextField", "name": "address", "label": "上门地址", "defaultValue": "<crm.address>"},
            {"type": "Select", "name": "visit_type", "label": "上门类型", "options": [
                {"label": "新装宽带", "value": "install"},
                {"label": "移机", "value": "relocate"},
                {"label": "维修", "value": "repair"},
                {"label": "退网拆机", "value": "cancel"},
            ]},
            {"type": "DatePicker", "name": "visit_date", "label": "预约日期",
             "defaultValue": "<today+1>", "pickerType": "date",
             "placeholder": "请选择上门日期"},
            {"type": "TimePicker", "name": "visit_time", "label": "预约时段",
             "isRange": False,
             "placeholder": "请选择上门时间"},
            {"type": "TextField", "name": "remark", "label": "备注", "placeholder": "如有特殊需求请注明...", "multiline": True},
        ],
        "submitLabel": "确认预约",
        "validation": {
            "phone_no": {"required": True},
            "address": {"required": True},
            "visit_type": {"required": True},
            "visit_date": {"required": True},
            "visit_time": {"required": True},
        }
    }],
    title="上门服务预约",
    description="工作日 9:00-18:00 可预约，周末及节假日仅支持上午时段。"
)
```

---

## 模板 11：套餐对比 DataGrid

**涉及组件**：DataGrid, Button

```python
render_a2ui(
    components=[{
        "type": "DataGrid",
        "columns": [
            {"key": "name", "label": "套餐名称", "width": 150},
            {"key": "speed", "label": "速率"},
            {"key": "monthly_fee", "label": "月费", "sortable": True},
            {"key": "contract_months", "label": "合约期"},
            {"key": "install_fee", "label": "安装费"},
            {"key": "gifts", "label": "赠送", "width": 200},
            {"key": "action", "label": "操作", "width": 80},
        ],
        "rows": [
            {"name": "百兆光纤", "speed": "100M", "monthly_fee": "59元", "contract_months": "24月", "install_fee": "100元", "gifts": "基础路由器", "action": "办理"},
            {"name": "三百兆畅享", "speed": "300M", "monthly_fee": "89元", "contract_months": "24月", "install_fee": "0元", "gifts": "千兆路由器(价值299元)", "action": "办理"},
            {"name": "五百兆极速", "speed": "500M", "monthly_fee": "129元", "contract_months": "24月", "install_fee": "0元", "gifts": "WiFi6路由器(价值499元)+IPTV首年免费", "action": "办理"},
            {"name": "千兆旗舰", "speed": "1000M", "monthly_fee": "199元", "contract_months": "24月", "install_fee": "0元", "gifts": "WiFi6 Mesh套装(价值899元)+IPTV永久+智能家居首年", "action": "办理"},
        ],
        "pageSize": 10,
        "rowActions": [{"label": "办理", "action": "select_from_grid"}],
    }],
    title="宽带套餐对比",
    description="★ 点击「办理」按钮选择套餐。可按月费排序。"
)
```

> DataGrid 的 `rows` 数据应从 `crm_query_available_packages("all")` MCP 返回结果动态填充。

---

## 模板 12：套餐价格趋势 Chart

**涉及组件**：Chart

```python
render_a2ui(
    components=[{
        "type": "Chart",
        "chartType": "bar",
        "xAxis": "speed",
        "series": [
            {"name": "月费(元)", "dataKey": "monthly_fee", "type": "bar", "color": "#409EFF"},
            {"name": "安装费(元)", "dataKey": "install_fee", "type": "bar", "color": "#E6A23C"},
        ],
        "data": [
            {"speed": "100M", "monthly_fee": 59, "install_fee": 100},
            {"speed": "300M", "monthly_fee": 89, "install_fee": 0},
            {"speed": "500M", "monthly_fee": 129, "install_fee": 0},
            {"speed": "1000M", "monthly_fee": 199, "install_fee": 0},
        ],
    }],
    title="套餐费用对比",
    description="柱状图直观展示各速率套餐的月费与安装费。"
)
```

饼图示例（用户合约分布）：
```python
render_a2ui(
    components=[{
        "type": "Chart",
        "chartType": "pie",
        "series": [
            {"name": "月费占比", "dataKey": "fee", "color": "#409EFF"},
        ],
        "data": [
            {"name": "宽带300M", "fee": 89},
            {"name": "IPTV", "fee": 30},
            {"name": "全屋WiFi", "fee": 20},
        ],
    }],
    title="您当前的月费构成",
    description="各产品月费占比"
)
```

---

## 模板 13：服务评价表单

**涉及组件**：Form, Slider, Switch, Checkbox, MultiSelect

```python
render_a2ui(
    components=[{
        "type": "Form",
        "fields": [
            {"type": "Slider", "name": "rating", "label": "总体评分",
             "defaultValue": 5, "min": 1, "max": 5, "step": 1,
             "showInput": True, "showStops": True},
            {"type": "Text", "content": "1=非常不满意  2=不满意  3=一般  4=满意  5=非常满意", "format": "plain"},
            {"type": "Divider"},
            {"type": "MultiSelect", "name": "tags", "label": "好评标签（可多选）",
             "options": [
                 {"label": "服务态度好", "value": "service_good"},
                 {"label": "上门及时", "value": "on_time"},
                 {"label": "技术专业", "value": "professional"},
                 {"label": "安装快速", "value": "fast_install"},
                 {"label": "讲解清楚", "value": "clear_explain"},
             ],
             "placeholder": "请选择满意的方面"},
            {"type": "TextField", "name": "comment", "label": "评价留言",
             "placeholder": "欢迎分享您的建议...", "multiline": True},
            {"type": "Switch", "name": "subscribe_newsletter", "label": "订阅优惠通知",
             "defaultValue": False, "activeText": "订阅", "inactiveText": "不订阅"},
            {"type": "Checkbox", "name": "public_comment", "label": "允许匿名展示我的评价"},
        ],
        "submitLabel": "提交评价",
        "validation": {
            "rating": {"required": True, "min": 1, "max": 5},
        }
    }],
    title="服务评价",
    description="您的反馈将帮助我们不断提升服务质量。"
)
```

---

## 模板 14：文件上传组件

**涉及组件**：FileUpload, MultiFileUpload, ImageUpload

```python
render_a2ui(
    components=[
        {"type": "Text", "content": "### 上传材料\n请上传办理宽带所需的证件材料：", "format": "markdown"},
        {"type": "Card", "title": "必备材料", "variant": "outlined", "body": [
            {"type": "ImageUpload", "name": "id_card_front", "label": "身份证正面照片", "maxCount": 1, "maxSize": 10485760},
            {"type": "ImageUpload", "name": "id_card_back", "label": "身份证反面照片", "maxCount": 1, "maxSize": 10485760},
        ]},
        {"type": "Card", "title": "补充材料（可选）", "variant": "outlined", "body": [
            {"type": "FileUpload", "name": "property_cert", "label": "房产证/租赁合同", "accept": ".pdf,.jpg,.png", "drag": True, "maxSize": 20971520},
            {"type": "MultiFileUpload", "name": "extra_docs", "label": "其他证明材料", "accept": ".pdf,.jpg,.png,.doc,.docx", "maxCount": 5, "maxSize": 10485760},
        ]},
        {"type": "Button", "label": "材料已上传完毕，提交审核", "variant": "primary", "action": "submit_materials"},
    ],
    title="证件材料上传",
    description="支持 JPG/PNG/PDF 格式，单文件不超过 10MB。"
)
```

---

## 模板 15：宽带办理指南页

**涉及组件**：Card, Image, Link, List, Divider, Container

```python
render_a2ui(
    components=[{
        "type": "Card",
        "title": "🏢 电信营业厅 — 宽带办理指南",
        "variant": "elevated",
        "body": [
            {"type": "Image", "src": "https://cdn.example.com/banners/broadband-guide.png",
             "alt": "宽带办理指南", "fit": "cover", "height": 160},
            {"type": "Divider"},
            {"type": "Text", "content": "### 📋 办理流程\n", "format": "markdown"},
            {"type": "List", "items": [
                {"label": "1. 选择套餐", "value": "根据您的需求选择合适的速率和合约"},
                {"label": "2. 提交资料", "value": "身份证 + 安装地址"},
                {"label": "3. 上门安装", "value": "工程师48小时内上门调试"},
                {"label": "4. 开通使用", "value": "安装完毕后即可使用"},
            ], "ordered": True},
            {"type": "Divider"},
            {"type": "Text", "content": "### 🎁 本月优惠活动\n", "format": "markdown"},
            {"type": "List", "items": [
                {"label": "新用户首年9折", "value": "宽带新装用户首年月费9折优惠", "tag": "热门"},
                {"label": "老用户续约送3个月", "value": "续约24个月送3个月免费", "tag": "推荐"},
                {"label": "推荐有礼", "value": "推荐新用户办理送100元话费", "tag": "新"},
            ], "showTag": True},
            {"type": "Divider"},
            {"type": "Container", "direction": "row", "gap": 12, "justify": "center", "children": [
                {"type": "Link", "href": "https://shop.example.com/plans", "text": "查看全部套餐", "target": "_blank"},
                {"type": "Link", "href": "https://shop.example.com/faq", "text": "常见问题", "target": "_blank"},
                {"type": "Link", "href": "https://shop.example.com/contact", "text": "联系客服", "target": "_blank"},
            ]},
        ]
    }],
    title="宽带办理",
    description="回复「办宽带」开始办理，或直接告诉我您的手机号。"
)
```

---

## 模板 16：24 种组件最小示例

以下为每种组件的**最小可用示例**，用于快速参考：

```python
# 1. Text — 纯文本/Markdown/HTML
{"type": "Text", "content": "Hello **World**", "format": "markdown"}

# 2. Button — 可点击按钮
{"type": "Button", "label": "点击办理", "variant": "primary", "action": "do_action"}

# 3. Card — 卡片容器
{"type": "Card", "title": "标题", "subtitle": "副标题", "variant": "outlined",
 "body": [{"type": "Text", "content": "内容", "format": "plain"}]}

# 4. Divider — 分割线
{"type": "Divider", "dashed": False, "text": "分割文字"}

# 5. Link — 超链接
{"type": "Link", "href": "https://example.com", "text": "点击跳转", "target": "_blank"}

# 6. Image — 图片展示
{"type": "Image", "src": "https://example.com/img.png", "alt": "示例图片", "fit": "cover"}

# 7. List — 键值列表
{"type": "List", "items": [{"label": "项目1", "value": "值1", "tag": "标签"}], "showTag": True}

# 8. Form — 表单容器
{"type": "Form", "fields": [{"type": "TextField", "name": "name", "label": "姓名"}], "submitLabel": "提交"}

# 9. TextField — 单行/多行文本输入
{"type": "TextField", "name": "name", "label": "姓名", "placeholder": "请输入", "defaultValue": ""}

# 10. Select — 下拉单选
{"type": "Select", "name": "city", "label": "城市", "options": [{"label": "北京", "value": "beijing"}]}

# 11. MultiSelect — 下拉多选
{"type": "MultiSelect", "name": "tags", "label": "标签", "options": [{"label": "A", "value": "a"}, {"label": "B", "value": "b"}]}

# 12. Checkbox — 复选框
{"type": "Checkbox", "name": "agree", "label": "我同意协议", "checked": False}

# 13. Switch — 开关
{"type": "Switch", "name": "enabled", "label": "启用", "defaultValue": True, "activeText": "开", "inactiveText": "关"}

# 14. NumberInput — 数字输入
{"type": "NumberInput", "name": "count", "label": "数量", "defaultValue": 1, "min": 1, "max": 100, "step": 1}

# 15. Slider — 滑动输入条
{"type": "Slider", "name": "volume", "label": "音量", "defaultValue": 50, "min": 0, "max": 100, "step": 10, "showInput": True}

# 16. DatePicker — 日期选择
{"type": "DatePicker", "name": "date", "label": "日期", "pickerType": "date"}

# 17. TimePicker — 时间选择
{"type": "TimePicker", "name": "time", "label": "时间", "isRange": False}

# 18. FileUpload — 单文件上传
{"type": "FileUpload", "name": "file", "label": "上传文件", "drag": True, "maxSize": 10485760}

# 19. MultiFileUpload — 多文件上传
{"type": "MultiFileUpload", "name": "files", "label": "多文件上传", "maxCount": 5, "maxSize": 10485760}

# 20. ImageUpload — 图片上传
{"type": "ImageUpload", "name": "photo", "label": "上传照片", "maxCount": 1, "maxSize": 10485760}

# 21. Container — 弹性布局 (Row/Column)
{"type": "Container", "direction": "row", "gap": 8, "children": [
    {"type": "Button", "label": "确认", "variant": "primary", "action": "confirm"},
    {"type": "Button", "label": "取消", "variant": "secondary", "action": "cancel"}
]}

# 22. DataGrid — 数据表格
{"type": "DataGrid", "columns": [{"key": "id", "label": "编号"}, {"key": "name", "label": "名称"}],
 "rows": [{"id": 1, "name": "项目A"}, {"id": 2, "name": "项目B"}], "pageSize": 10}

# 23. Chart — 图表
{"type": "Chart", "chartType": "bar", "xAxis": "name",
 "series": [{"name": "数值", "dataKey": "value", "color": "#409EFF"}],
 "data": [{"name": "A", "value": 30}, {"name": "B", "value": 50}]}

# 24. Progress — 进度条
{"type": "Progress", "percent": 65, "status": "success", "showLabel": True, "indicatorText": "65% 已完成"}
```

---

## 设计原则

1. **模板优先**：直接复制上方模板的 JSON 结构，只替换 `<...>` 动态数据
2. **Form 用于收集信息**：需要用户填写/确认时使用
3. **Card 用于展示信息**：展示查询结果、状态时使用
4. **Container 用于布局**：多组件排列时用 Container 嵌套（默认 direction="column"）
5. **Card + action 用于选择**：让用户从多个选项中点击选择
6. **DataGrid 用于表格**：超过 3 行对比数据时优先用表格
7. **Chart 用于趋势**：费用对比、趋势展示时用图表
8. **Progress 用于进度**：工单状态、办理进度等流程追踪
9. **不要在一个 render_a2ui 调用中混用多个独立 Surface**
10. **更新已有 Surface**：传入上次返回的 surface_id 可替换旧 UI
11. **组件选择优先级**：Card > Container+Card > DataGrid > Chart > 纯文本
