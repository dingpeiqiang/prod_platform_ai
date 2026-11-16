import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { stockProducts } from '../config/stockProductData.js'

/**
 * 工单中心 store
 *
 * 职责：
 * 1. 维护"已完成归档"的历史工单列表（多套餐并存，统一管理）
 * 2. 完成 上架销售 流程后，将套餐归档为一条工单记录
 * 3. 提供工单详情只读快照、产品运营指标、下架等操作位
 *
 * 归档工单结构：
 *   {
 *     id, workOrderId, productName, templateLabel,
 *     channelList: [渠道...], salesAt: 上架时间,
 *     snapshot: 配置快照（只读档案）,
 *     values: 全量表单值快照,
 *     testReport: 测试报告历史快照,
 *     filing: 备案公示历史快照,
 *     channel: 销售渠道历史快照,
 *     status: 'listed' | 'offline',   // 已上架 / 已下架
 *     metrics: [ { key, label, value, delta, trend } ... ],  // 产品运营指标
 *   }
 */
export const useWorkOrderStore = defineStore('workOrder', () => {
  const archived = ref([])

  const total = computed(() => archived.value.length)
  const listedCount = computed(() => archived.value.filter((w) => w.status !== 'offline').length)

  // 按上架时间倒序
  const sortedArchived = computed(() =>
    [...archived.value].sort((a, b) => (b.salesAt || 0) - (a.salesAt || 0))
  )

  /**
   * 归档一条已完成上架销售的工单（幂等：同 workOrderId 不重复归档）
   * payload：
   *   { workOrderId, productName, templateLabel, channelList, salesAt,
   *     snapshot: 概要行[{key,label,value}], values: 全量表单值{k:v},
   *     testReport/filing/channel: 三份历史快照,
   *     status, metrics }
   */
  function addArchived(order) {
    if (!order || !order.workOrderId) return null
    const exist = archived.value.find((w) => w.workOrderId === order.workOrderId)
    if (exist) return exist
    const entry = {
      id: 'wo_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
      workOrderId: order.workOrderId,
      productName: order.productName || '未命名产品',
      templateLabel: order.templateLabel || '个人主资费',
      channelList: (order.channelList || []).slice(),
      salesAt: order.salesAt || Date.now(),
      snapshot: order.snapshot || [],
      // 全量表单值快照：用于"查看完整工作台档案"只读回看
      values: order.values || null,
      // 测试报告 / 备案公示 / 销售渠道 三份历史快照：归档工单的"配置档案"按该工单自身数据展示
      testReport: order.testReport || null,
      filing: order.filing || null,
      channel: order.channel || null,
      productInfo: {
        workOrderId: order.workOrderId,
        prodPrcName: order.productName || '未命名产品',
        templateLabel: order.templateLabel || '个人主资费',
        status: order.status || 'listed',
      },
      // seeded=true 表示该工单为演示用种子数据（非用户本次走完上架流程的真实产品），
      // 用于在「产品运营视图下钻」等真实业务场景中区分排除
      seeded: order.seeded === true,
      status: order.status || 'listed',
      metrics: (order.metrics && order.metrics.length) ? order.metrics.slice() : defaultMetrics(order.productName),
    }
    archived.value = [entry, ...archived.value]
    return entry
  }

  /**
   * 下架 / 恢复上架
   */
  function setStatus(workOrderId, status) {
    const w = archived.value.find((x) => x.workOrderId === workOrderId)
    if (w) w.status = status
  }

  function getByWorkOrderId(woid) {
    return archived.value.find((w) => w.workOrderId === woid)
  }

  /**
   * 演示用：注入已完成上架的存量产品（stockProductData 4 档）+ 示例工单，
   * 使「我的产品」抽屉里能看到智享99元5G套餐等在架存量记录（格式与用户归档的记录一致）。
   */
  function seedIfEmpty() {
    // 存量产品：始终注入（幂等），不受用户本地归档影响——它们是"在架产品"而非演示归档
    seedStockProducts()
    if (archived.value.length > 0) return
    addArchived({
      workOrderId: 'WO20260801001',
      productName: '畅享59元5G套餐',
      templateLabel: '个人主资费',
      channelList: ['营业厅', '线上商城', '渠道代理'],
      salesAt: Date.now() - 1000 * 60 * 60 * 24 * 30,
      status: 'listed',
      seeded: true,
      snapshot: [
        { key: 'prodName', label: '产品名称', value: '畅享59元5G套餐' },
        { key: 'prodCode', label: '产品编码', value: 'SP-2024-0590' },
        { key: 'flow', label: '通用流量', value: '30GB' },
        { key: 'voice', label: '本地语音', value: '100分钟' },
        { key: 'fee', label: '月租', value: '59.00元/月' },
      ],
      values: {
        prodName: '畅享59元5G套餐',
        prodCode: 'SP-2024-0590',
        flowAmount: 30,
        voiceMinutes: 100,
        monthlyFee: 59.00,
      },
      testReport: {
        uploaded: true,
        uploadedAt: '2026-07-02 16:20',
        reportId: 'TR20260702002',
        workOrderId: 'WO20260801001',
        productName: '畅享59元5G套餐',
        tester: '王强',
        testDate: '2026-07-02',
        status: 'passed',
        testMethod: '新业务测试（含固费核查、计费核查、提醒核查、工单核查）',
        acceptNumber: '13920260702',
        sections: [
          {
            key: 'fixedFee',
            title: '固费核查',
            status: 'passed',
            items: [
              { label: '月租固定费', expected: '59.00元', actual: '59.00元', result: 'pass' },
              { label: '固定费生效时间', expected: '订购次月1日生效', actual: '次月1日生效', result: 'pass' },
            ],
          },
          {
            key: 'billing',
            title: '计费核查',
            status: 'passed',
            items: [
              { label: '流量计费', expected: '30GB内不额外计费，超出后1元/GB自动叠加', actual: '30GB内正常，超出按1元/GB叠加', result: 'pass' },
              { label: '语音计费', expected: '100分钟内免费，超出0.15元/分钟', actual: '100分钟内免费，超出0.15元/分钟', result: 'pass' },
            ],
          },
          {
            key: 'reminder',
            title: '提醒核查',
            status: 'passed',
            items: [
              { label: '流量使用提醒', expected: '使用达80%、95%时下发短信提醒', actual: '80%和95%均正常下发', result: 'pass' },
            ],
          },
        ],
        conclusion: '通过',
        conclusionDesc: '该资费方案各项核查均符合预期，测试通过，可进入备案公示环节。',
      },
      filing: {
        started: true,
        submitted: true,
        approved: true,
        approvedAt: '2026-07-20 10:00',
        filingId: 'FL20260720001',
        workOrderId: 'WO20260801001',
        productName: '畅享59元5G套餐',
        template: '个人主资费',
        productCode: 'SP20240590G',
        applicant: '产品配置专员',
        applyDate: '2026-07-10',
        info: {
          filingOrg: '产品研发中心',
          filingOfficer: '李芳',
          filingDate: '2026-07-10',
          effectiveDate: '2026-08-01',
          expireDate: '长期有效',
        },
        checks: [
          { label: '资费合规性', status: 'passed', desc: '符合《资费管理办法》相关规定，无违规项' },
          { label: '公示内容完整性', status: 'passed', desc: '资费名称、资费标准、生效时间等公示信息完整' },
          { label: '测试报告核销', status: 'passed', desc: '测试报告 TR20260702002 已通过，允许备案' },
          { label: '审批链完整性', status: 'passed', desc: '预览审批、测试审核审批链完整' },
        ],
        notice: {
          title: '关于"畅享59元5G套餐"资费方案备案公示',
          target: '全体用户',
          content: '现将"畅享59元5G套餐"资费方案（资费代码：SP20240590G）予以备案公示。公示期满且无异议后，该资费方案将正式上架销售。',
          notifyChannels: '营业厅公示栏、网上营业厅、掌上营业厅',
          startDate: '2026-07-11',
          endDate: '2026-07-25',
          duration: '15天',
        },
        sections: [
          {
            title: '备案基本信息',
            previewOnly: true,
            fields: [
              { label: '备案公示流程', value: '先备案再公示', category: 'filing' },
              { label: '公示系列ID', value: '资费ID', category: 'filing' },
              { label: '公示系列名称', value: '畅享59元5G套餐', category: 'filing' },
              { label: '方案编号', value: 'XNBP0002', category: 'filing' },
            ],
          },
          {
            title: '套餐基本信息',
            previewOnly: false,
            fields: [
              { label: 'BOSSID', value: 'SP-2024-0590', category: 'filing' },
              { label: '一级分类', value: '公众', category: 'filing' },
              { label: '二级分类', value: '套餐', category: 'filing' },
              { label: '名称', value: '畅享59元5G套餐', category: 'filing' },
              { label: '资费属性', value: '省内', category: 'filing' },
              { label: '资费单位', value: '元/月', category: 'filing' },
            ],
          },
          {
            title: '基础信息',
            previewOnly: false,
            fields: [
              { label: '资费标准', value: '59元/月', category: 'common' },
              { label: '资费类型', value: '套餐', category: 'public' },
              { label: '上线日期', value: '20260801', category: 'common' },
              { label: '有效期限', value: '36个月', category: 'common' },
              { label: '适用地区', value: '全省（安徽）', category: 'common' },
              { label: '销售渠道', value: '自有营业厅/网上营业厅', category: 'common' },
            ],
          },
          {
            title: '资源信息',
            previewOnly: false,
            fields: [
              { label: '国内通话', value: '100分钟', category: 'common' },
              { label: '国内通用流量', value: '30GB', category: 'common' },
              { label: '定向流量', value: '100GB', category: 'common' },
              { label: '短信', value: '100条', category: 'common' },
              { label: '权益', value: '腾讯视频+爱奇艺月卡', category: 'common' },
            ],
          },
        ],
      },
      channel: {
        available: true,
        submitted: true,
        list: [
          { key: 'own_offline', label: '自有营业厅', desc: '实体营业厅线下受理' },
          { key: 'own_online', label: '网上营业厅', desc: '官方网上营业厅在线订购' },
          { key: 'app', label: '掌上营业厅APP', desc: '手机营业厅客户端' },
          { key: 'customer_service', label: '10086客服热线', desc: '电话客服人员受理' },
          { key: 'channel_coop', label: '社会合作渠道', desc: '第三方合作代理商' },
        ],
        selected: ['own_offline', 'own_online', 'channel_coop'],
        defaultSelected: ['own_online', 'app'],
      },
    })
    addArchived({
      workOrderId: 'WO20260720002',
      productName: '畅享39元青春版',
      templateLabel: '个人主资费',
      channelList: ['线上商城', '抖音渠道'],
      salesAt: Date.now() - 1000 * 60 * 60 * 24 * 62,
      status: 'listed',
      seeded: true,
      snapshot: [
        { key: 'prodName', label: '产品名称', value: '畅享39元青春版' },
        { key: 'prodCode', label: '产品编码', value: 'SP-2024-0589' },
        { key: 'flow', label: '通用流量', value: '20GB' },
        { key: 'voice', label: '本地语音', value: '80分钟' },
        { key: 'fee', label: '月租', value: '39.00元/月' },
      ],
      values: {
        prodName: '畅享39元青春版',
        prodCode: 'SP-2024-0589',
        flowAmount: 20,
        voiceMinutes: 80,
        monthlyFee: 39.00,
      },
      testReport: {
        uploaded: true,
        uploadedAt: '2026-06-10 15:00',
        reportId: 'TR20260610003',
        workOrderId: 'WO20260720002',
        productName: '畅享39元青春版',
        tester: '陈晓',
        testDate: '2026-06-10',
        status: 'passed',
        testMethod: '新业务测试（含固费核查、计费核查、提醒核查、工单核查）',
        acceptNumber: '13920260610',
        sections: [
          {
            key: 'fixedFee',
            title: '固费核查',
            status: 'passed',
            items: [
              { label: '月租固定费', expected: '39.00元', actual: '39.00元', result: 'pass' },
              { label: '首月优惠费', expected: '19.50元（新用户首月半价）', actual: '19.50元', result: 'pass' },
            ],
          },
          {
            key: 'billing',
            title: '计费核查',
            status: 'passed',
            items: [
              { label: '流量计费', expected: '20GB内不额外计费，超出后1元/GB自动叠加', actual: '20GB内正常，超出按1元/GB叠加', result: 'pass' },
              { label: '语音计费', expected: '80分钟内免费，超出0.15元/分钟', actual: '80分钟内免费，超出0.15元/分钟', result: 'pass' },
            ],
          },
          {
            key: 'reminder',
            title: '提醒核查',
            status: 'passed',
            items: [
              { label: '流量使用提醒', expected: '使用达80%、95%时下发短信提醒', actual: '80%和95%均正常下发', result: 'pass' },
            ],
          },
        ],
        conclusion: '通过',
        conclusionDesc: '该资费方案各项核查均符合预期，测试通过，可进入备案公示环节。',
      },
      filing: {
        started: true,
        submitted: true,
        approved: true,
        approvedAt: '2026-06-28 09:30',
        filingId: 'FL20260628002',
        workOrderId: 'WO20260720002',
        productName: '畅享39元青春版',
        template: '个人主资费',
        productCode: 'SP20240589Q',
        applicant: '产品配置专员',
        applyDate: '2026-06-18',
        info: {
          filingOrg: '产品研发中心',
          filingOfficer: '李芳',
          filingDate: '2026-06-18',
          effectiveDate: '2026-07-01',
          expireDate: '长期有效',
        },
        checks: [
          { label: '资费合规性', status: 'passed', desc: '符合《资费管理办法》相关规定，无违规项' },
          { label: '公示内容完整性', status: 'passed', desc: '资费名称、资费标准、生效时间等公示信息完整' },
          { label: '测试报告核销', status: 'passed', desc: '测试报告 TR20260610003 已通过，允许备案' },
          { label: '审批链完整性', status: 'passed', desc: '预览审批、测试审核审批链完整' },
        ],
        notice: {
          title: '关于"畅享39元青春版"资费方案备案公示',
          target: '全体用户',
          content: '现将"畅享39元青春版"资费方案（资费代码：SP20240589Q）予以备案公示。公示期满且无异议后，该资费方案将正式上架销售。',
          notifyChannels: '营业厅公示栏、网上营业厅、掌上营业厅',
          startDate: '2026-06-19',
          endDate: '2026-07-03',
          duration: '15天',
        },
        sections: [
          {
            title: '备案基本信息',
            previewOnly: true,
            fields: [
              { label: '备案公示流程', value: '先备案再公示', category: 'filing' },
              { label: '公示系列ID', value: '资费ID', category: 'filing' },
              { label: '公示系列名称', value: '畅享39元青春版', category: 'filing' },
              { label: '方案编号', value: 'XNBP0003', category: 'filing' },
            ],
          },
          {
            title: '套餐基本信息',
            previewOnly: false,
            fields: [
              { label: 'BOSSID', value: 'SP-2024-0589', category: 'filing' },
              { label: '一级分类', value: '公众', category: 'filing' },
              { label: '二级分类', value: '套餐', category: 'filing' },
              { label: '名称', value: '畅享39元青春版', category: 'filing' },
              { label: '资费属性', value: '省内', category: 'filing' },
              { label: '资费单位', value: '元/月', category: 'filing' },
            ],
          },
          {
            title: '基础信息',
            previewOnly: false,
            fields: [
              { label: '资费标准', value: '39元/月', category: 'common' },
              { label: '资费类型', value: '套餐', category: 'public' },
              { label: '上线日期', value: '20260701', category: 'common' },
              { label: '有效期限', value: '36个月', category: 'common' },
              { label: '适用地区', value: '全省（安徽）', category: 'common' },
              { label: '销售渠道', value: '网上营业厅/掌上营业厅', category: 'common' },
            ],
          },
          {
            title: '资源信息',
            previewOnly: false,
            fields: [
              { label: '国内通话', value: '80分钟', category: 'common' },
              { label: '国内通用流量', value: '20GB', category: 'common' },
              { label: '定向流量', value: '80GB', category: 'common' },
              { label: '短信', value: '80条', category: 'common' },
              { label: '权益', value: '腾讯视频月卡', category: 'common' },
            ],
          },
        ],
      },
      channel: {
        available: true,
        submitted: true,
        list: [
          { key: 'own_offline', label: '自有营业厅', desc: '实体营业厅线下受理' },
          { key: 'own_online', label: '网上营业厅', desc: '官方网上营业厅在线订购' },
          { key: 'app', label: '掌上营业厅APP', desc: '手机营业厅客户端' },
          { key: 'customer_service', label: '10086客服热线', desc: '电话客服人员受理' },
          { key: 'channel_coop', label: '社会合作渠道', desc: '第三方合作代理商' },
        ],
        selected: ['own_online', 'app'],
        defaultSelected: ['own_online', 'app'],
      },
    })
  }

  function defaultMetrics(productName) {
    return [
      { key: 'newOrder', label: '今日新增订购', value: '380 单', delta: '+12%', trend: 'up' },
      { key: 'active',    label: '在网用户',     value: '12.6 万', delta: '+3.2%', trend: 'up' },
      { key: 'monthRev',  label: '月收入',       value: '743 万元', delta: '+8.4%', trend: 'up' },
      { key: 'churn',     label: '退订率',       value: '1.8%', delta: '-0.3%', trend: 'down' },
    ]
  }

  /**
   * 注入存量在架产品（stockProductData）：转成与归档工单同构的记录，
   * 使「我的产品」抽屉展示 智享99元5G套餐 等 4 档存量产品，
   * 展开/配置档案浮层与普通归档记录格式完全一致。
   */
  function seedStockProducts() {
    stockProducts.forEach((p) => {
      const v = p.snapshot?.values || {}
      addArchived({
        workOrderId: p.code,
        productName: p.name,
        templateLabel: p.templateLabel,
        channelList: ['自有营业厅', '网上营业厅', '掌上营业厅APP'],
        salesAt: Date.now() - 1000 * 60 * 60 * 24 * 15,
        status: 'listed',
        seeded: true,
        snapshot: [
          { key: 'prodName', label: '产品名称', value: p.name },
          { key: 'prodCode', label: '产品编码', value: v.prodCode || p.code },
          { key: 'flow', label: '通用流量', value: v.flowAmount ? `${v.flowAmount}${v.flowUnit || 'GB'}` : '—' },
          { key: 'voice', label: '语音', value: v.voiceAmount ? `${v.voiceAmount}分钟` : '—' },
          { key: 'fee', label: '月租', value: v.monthlyFee ? `${v.monthlyFee}元/月` : '—' },
        ],
        values: v,
        testReport: p.snapshot?.testReport || null,
        filing: p.snapshot?.filing || null,
        channel: p.snapshot?.channel || null,
      })
    })
  }

  return {
    archived,
    total,
    listedCount,
    sortedArchived,
    addArchived,
    setStatus,
    getByWorkOrderId,
    defaultMetrics,
    seedIfEmpty,
    seedStockProducts,
  }
})
