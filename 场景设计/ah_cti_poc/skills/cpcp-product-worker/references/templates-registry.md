# 模板注册表（templates-registry）V1.0

> 数据源=scripts/templates/_registry.json（聚合统计；**产品类型权威字段=各 schema 顶层 `x-product-type`**，
> identify_products 运行时枚举即扫描该集合，新增配置场景投 schema 即可路由，无需改代码）。
> 原 _index.json 已并入本注册表（唯一事实源）。本表供：第①步 product_type→templateId 路由、第④步提取提示词模板注入、derive_flat24 映射、第⑤步价格字段禁照搬判定。

## 模板总表

| templateId | 中文名 | 适用产品类型 | 叶子数 | 必填 | number | enum 项 | show-when | 价格字段 |
| :--- | :--- | :--- | ---: | ---: | ---: | ---: | ---: | ---: |
| personMainPrc | 个人主资费 | 个人主套餐 | 85 | 44 | 16 | 97 | 9 | 6 |
| broadBandMainPrc | 宽带主资费 | 宽带主套餐 | 43 | 29 | 9 | 33 | 8 | 5 |
| personAddPrc | 个人附加资费 | 个人附加资费 | 99 | 57 | 19 | 111 | 14 | 6 |
| broadBandOptSpeedPrc | 宽带加速包 | 宽带附加资费 | 45 | 29 | 10 | 31 | 9 | 5 |
| familyBasePrc | 家庭基础套餐 | 家庭基础套餐 | 93 | 64 | 15 | 73 | 11 | 7 |
| familyAddPrc | 家庭附加业务 | 家庭附加资费 | 75 | 47 | 15 | 57 | 9 | 5 |

## 价格字段路径表（第⑤步禁照搬判定依据）

### personMainPrc（个人主资费）

| JSONPath | 字段名称 | 类型 |
| :--- | :--- | :--- |
| optionalInfo.printContent.prcMonthFee | 套餐月费 | number |
| optionalInfo.acctMonth.fixFee | 套餐固定费（元） | number |
| optionalInfo.acctMonth.fixValidity | 月租有效期 | string |
| optionalInfo.acctMonth.fixValidityVAlue | 月租有效期值 | number |
| optionalInfo.acctFav.favFee | 优惠费用（元） | number |
| optionalInfo.dlowFavFee.fav1Fee | 保底费用（元） | number |

### broadBandMainPrc（宽带主资费）

| JSONPath | 字段名称 | 类型 |
| :--- | :--- | :--- |
| optionalInfo.printContent.prcMonthFee | 套餐月费 | number |
| optionalInfo.acctMonth.fixFee | 套餐固定费（元） | number |
| optionalInfo.acctMonth.fixValidity | 月租有效期 | string |
| optionalInfo.acctMonth.fixValidityVAlue | 月租有效期值 | number |
| optionalInfo.acctFav.favFee | 优惠费用（元） | number |

### personAddPrc（个人附加资费）

| JSONPath | 字段名称 | 类型 |
| :--- | :--- | :--- |
| optionalInfo.printContent.prcMonthFee | 套餐月费 | number |
| optionalInfo.acctMonth.fixFee | 套餐固定费（元） | number |
| optionalInfo.acctMonth.fixValidity | 月租有效期 | string |
| optionalInfo.acctMonth.fixValidityVAlue | 月租有效期值 | number |
| optionalInfo.acctFav.favFee | 优惠费用（元） | number |
| optionalInfo.dlowFavFee.fav1Fee | 保底费用（元） | number |

### broadBandOptSpeedPrc（宽带加速包）

| JSONPath | 字段名称 | 类型 |
| :--- | :--- | :--- |
| optionalInfo.printContent.prcMonthFee | 套餐月费 | number |
| optionalInfo.acctMonth.fixFee | 套餐固定费（元） | number |
| optionalInfo.acctMonth.fixValidity | 月租有效期 | string |
| optionalInfo.acctMonth.fixValidityVAlue | 月租有效期值 | number |
| optionalInfo.acctFav.favFee | 优惠费用（元） | number |

### familyBasePrc（家庭基础套餐）

| JSONPath | 字段名称 | 类型 |
| :--- | :--- | :--- |
| phoneMbrInfo.phoneMbrFee | 超出成员月租（元/月） | number |
| optionalInfo.printContent.prcMonthFee | 套餐月费 | number |
| optionalInfo.acctMonth.fixFee | 套餐固定费（元） | number |
| optionalInfo.acctMonth.fixValidity | 月租有效期 | string |
| optionalInfo.acctMonth.fixValidityVAlue | 月租有效期值 | number |
| optionalInfo.acctFav.favFee | 优惠费用（元） | number |
| optionalInfo.dlowFavFee.fav1Fee | 保底费用（元） | number |

### familyAddPrc（家庭附加业务）

| JSONPath | 字段名称 | 类型 |
| :--- | :--- | :--- |
| optionalInfo.printContent.prcMonthFee | 套餐月费 | number |
| optionalInfo.acctMonth.fixFee | 套餐固定费（元） | number |
| optionalInfo.acctMonth.fixValidity | 月租有效期 | string |
| optionalInfo.acctMonth.fixValidityVAlue | 月租有效期值 | number |
| optionalInfo.acctFav.favFee | 优惠费用（元） | number |

