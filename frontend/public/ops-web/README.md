# 单品运营管理 · 视图组件 (ops-web)

> 由 `场景设计/ah_cti_poc/方案/运营视图2/运营视图2.html` 拆分重构的**独立静态页面组件**，
> 托管于 `frontend/public/ops-web/`。Vite 会把 `public/` 原样拷贝到 `dist/`，
> 因此这些页面无需登录、无 SPA 壳，可直接被外部 AI 应用平台以 **iframe** 加载。

## 为什么放在 `public/` 而非 Vue 路由
外部平台通过 **iframe 地址** 把页面嵌入消息窗口。若做成 Vue 路由会走统一登录守卫与 SPA 壳，
外部 iframe 需协同登录态、受限较多。静态页面零依赖、免登录，URL 唯一即可直嵌，最契合 iframe 场景。

## 目录结构（组件化拆分）
```
frontend/public/ops-web/
├── index.html               # 组件导航/入口（列出各子页面）
├── product-detail.html      # ⭐ 单品监控运营详情页（下钻后内容，本次核心）
├── metrics-dictionary.html  # 指标口径与预警规则（view-single 拆出）
├── assets/
│   ├── css/common.css       # 共享：设计令牌/卡片/按钮/表格/胶囊/提醒/骨架屏
│   ├── css/detail.css       # 单品详情：健康度主卡/四维度/生命周期/流量图
│   └── js/
│       ├── mock-data.js     # 数据层：按 product_id 归档每商品监控指标（POC mock）
│       └── detail.js        # 渲染与图表：数据注入 + echarts 四维度 + 标签切换
└── lib/echarts/echarts.min.js  # 本地 echarts（自 node_modules 拷贝，免 CDN）
```

## 单品详情页 URL 契约（SKILL 监控运营出口）
```
/ops-web/product-detail.html?product_id={offer_id}[&name={商品名}&type={单品|融合}]
```
- `product_id` 兼容别名：`product_id / productId / offer_id / offerId`
- 命中 `OPS_MOCK[product_id]` 渲染该商品画像；未命中回退默认画像（可附 name/type）。
- 页面 **仅展示下钻后的单品详情内容**（健康度 + 四维度图表 + 异动预警），不含大盘列表。

## 部署访问地址
- **前端云部署基址**：`http://10.86.13.201:31280`（nginx 容器内 listen 6173，网关对外映射 31280；`dist/ops-web/*` 由 `location /` 静态托管于 `/ops-web/`）。

### 外部 AI 平台 iframe 示例（按部署地址绝对路径）
```html
<iframe src="http://10.86.13.201:31280/ops-web/product-detail.html?product_id=900102308"
        style="width:100%;height:720px;border:0;"></iframe>
```
跨源 iframe 沙箱限制时，同源引用可省略协议主机用相对路径 `/ops-web/product-detail.html?...`。

## 数据接入
POC 阶段数据在 `assets/js/mock-data.js` 中按商品归档（现收录 900102308 健康画像、900102310 亚健康画像）。
正式接入后，将该数据层替换为「监控运营」后端出参（`query_monitor` 的 order/error/fee 与告警）的映射即可，
页面渲染逻辑（`detail.js`）不变——保持「页面确定性渲染、数据来源可替换」。
