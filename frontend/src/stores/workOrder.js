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
      templateLabel: order.templateLabel || (order.productType === 'addon' ? '可选包' : '个人主资费'),
      productType: order.productType || 'main',
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
        templateLabel: order.templateLabel || (order.productType === 'addon' ? '可选包' : '个人主资费'),
        productType: order.productType || 'main',
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
   * 演示用：注入已完成上架的存量产品（stockProductData 各档）+ 示例工单，
   * 使「我的产品」抽屉里能看到 WeWalk 香港.台湾数据卡等在架存量记录（格式与用户归档的记录一致）。
   */
  function seedIfEmpty() {
    // 存量产品：始终注入（幂等），不受用户本地归档影响——它们是"在架产品"而非演示归档
    seedStockProducts()
    if (archived.value.length > 0) return
    addArchived({
      workOrderId: 'WO20260801001',
      productName: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)',
      templateLabel: '个人主资费·数据卡',
      channelList: ['营业厅', '线上商城', '渠道代理'],
      salesAt: Date.now() - 1000 * 60 * 60 * 24 * 30,
      status: 'listed',
      seeded: true,
      snapshot: [
        { key: 'prodName', label: '产品名称', value: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)' },
        { key: 'prodCode', label: '产品编码', value: 'SP-HK-TW-30D' },
        { key: 'data', label: '高速配额', value: '30GB（总量高速）' },
        { key: 'roam', label: '漫游地', value: '香港.台湾' },
        { key: 'valid', label: '有效期', value: '30天' },
        { key: 'charge', label: '资费', value: '一次性' },
      ],
      values: {
        prodName: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)',
        prodCode: 'SP-HK-TW-30D',
        dataAmount: 30,
        dataAmountUnit: 'GB',
        validity: '30d',
        validityLabel: '30天',
        dataRoamArea: ['hk-tw'],
        chargeType: 'once',
        cardType: 'data',
      },
      testReport: {
        uploaded: true,
        uploadedAt: '2026-07-02 16:20',
        reportId: 'TR20260702002',
        workOrderId: 'WO20260801001',
        productName: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)',
        tester: '王强',
        testDate: '2026-07-02',
        status: 'passed',
        testMethod: '新业务测试（含资费核查、计费核查、提醒核查、工单核查）',
        acceptNumber: '13920260702',
        sections: [
          {
            key: 'fixedFee',
            title: '资费核查',
            status: 'passed',
            items: [
              { label: '资费类型', expected: '一次性资费', actual: '一次性资费', result: 'pass' },
              { label: '有效期', expected: '30天（自然天）', actual: '30天（自然天）', result: 'pass' },
            ],
          },
          {
            key: 'billing',
            title: '计费核查',
            status: 'passed',
            items: [
              { label: '数据计费', expected: '30GB高速，用尽后不限量限速1Mbps', actual: '30GB内正常，超出后限速不限量', result: 'pass' },
              { label: '漫游计费', expected: '香港.台湾共用漫游同价，不加收漫游费', actual: '香港.台湾漫游计费正确', result: 'pass' },
            ],
          },
          {
            key: 'reminder',
            title: '提醒核查',
            status: 'passed',
            items: [
              { label: '流量使用提醒', expected: '高速用量达80%、95%时下发短信提醒', actual: '80%和95%均正常下发', result: 'pass' },
              { label: '订购提醒', expected: '订购成功后下发欢迎短信', actual: '欢迎短信正常下发', result: 'pass' },
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
        productName: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)',
        template: '个人主资费·数据卡',
        productCode: 'SP-HK-TW-30D',
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
          title: '关于"WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)"资费方案备案公示',
          target: '全体用户',
          content: '现将"WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)"资费方案（资费代码：SP-HK-TW-30D）予以备案公示。公示期满且无异议后，该资费方案将正式上架销售。',
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
              { label: '公示系列名称', value: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)', category: 'filing' },
              { label: '方案编号', value: 'XNBP0002', category: 'filing' },
            ],
          },
          {
            title: '套餐基本信息',
            previewOnly: false,
            fields: [
              { label: 'BOSSID', value: 'SP-HK-TW-30D', category: 'filing' },
              { label: '一级分类', value: '国际漫游', category: 'filing' },
              { label: '二级分类', value: '数据卡', category: 'filing' },
              { label: '名称', value: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)', category: 'filing' },
              { label: '资费属性', value: '国际漫游', category: 'filing' },
              { label: '资费单位', value: '一次性', category: 'filing' },
            ],
          },
          {
            title: '基础信息',
            previewOnly: false,
            fields: [
              { label: '资费标准', value: '一次性资费', category: 'common' },
              { label: '资费类型', value: '主套餐·数据卡', category: 'public' },
              { label: '上线日期', value: '20260801', category: 'common' },
              { label: '有效期限', value: '30天（自然天）', category: 'common' },
              { label: '适用地区', value: '香港.台湾（共用）', category: 'common' },
              { label: '销售渠道', value: '自有营业厅/网上营业厅', category: 'common' },
            ],
          },
          {
            title: '资源信息',
            previewOnly: false,
            fields: [
              { label: '高速配额', value: '30GB（总量）', category: 'common' },
              { label: '漫游地', value: '香港.台湾', category: 'common' },
              { label: '限速策略', value: 'PCRF（用尽后不限量限速）', category: 'common' },
              { label: '卡类型/网络', value: '5G数据卡（Data Only）', category: 'common' },
              { label: '语音/短信', value: '无（纯数据产品）', category: 'common' },
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
      workOrderId: 'WO20260718006',
      productName: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)·可选包',
      templateLabel: '可选包',
      productType: 'addon',
      channelList: ['线上商城', '掌上营业厅APP'],
      salesAt: Date.now() - 1000 * 60 * 60 * 24 * 50,
      status: 'listed',
      seeded: true,
      snapshot: [
        { key: 'prodName', label: '产品名称', value: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)·可选包' },
        { key: 'prodCode', label: '产品编码', value: 'W23062000001' },
        { key: 'data', label: '高速配额', value: '800MB/日高速' },
        { key: 'roam', label: '漫游地', value: '香港.台湾' },
        { key: 'valid', label: '有效期', value: '1天' },
        { key: 'charge', label: '资费', value: '一次性' },
      ],
      values: {
        prodName: 'WeWalk-Data Only-澳门.香港.台湾共用1日不限量(800MB/日高速)·可选包',
        prodCode: 'W23062000001',
        dataAmount: 800,
        dataAmountUnit: 'MB/日',
        validity: '1d',
        validityLabel: '1天',
        dataRoamArea: ['hk-tw'],
        chargeType: 'once',
        cardType: 'data',
      },
    })
    addArchived({
      workOrderId: 'WO20260720002',
      productName: 'WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速)',
      templateLabel: '个人主资费·数据卡',
      channelList: ['线上商城', '抖音渠道'],
      salesAt: Date.now() - 1000 * 60 * 60 * 24 * 62,
      status: 'listed',
      seeded: true,
      snapshot: [
        { key: 'prodName', label: '产品名称', value: 'WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速)' },
        { key: 'prodCode', label: '产品编码', value: 'SP-HK-TW-15D' },
        { key: 'data', label: '高速配额', value: '15GB（总量高速）' },
        { key: 'roam', label: '漫游地', value: '香港.台湾' },
        { key: 'valid', label: '有效期', value: '15天' },
        { key: 'charge', label: '资费', value: '一次性' },
      ],
      values: {
        prodName: 'WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速)',
        prodCode: 'SP-HK-TW-15D',
        dataAmount: 15,
        dataAmountUnit: 'GB',
        validity: '15d',
        validityLabel: '15天',
        dataRoamArea: ['hk-tw'],
        chargeType: 'once',
        cardType: 'data',
      },
      testReport: {
        uploaded: true,
        uploadedAt: '2026-06-10 15:00',
        reportId: 'TR20260610003',
        workOrderId: 'WO20260720002',
        productName: 'WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速)',
        tester: '陈晓',
        testDate: '2026-06-10',
        status: 'passed',
        testMethod: '新业务测试（含资费核查、计费核查、提醒核查、工单核查）',
        acceptNumber: '13920260610',
        sections: [
          {
            key: 'fixedFee',
            title: '资费核查',
            status: 'passed',
            items: [
              { label: '资费类型', expected: '一次性资费', actual: '一次性资费', result: 'pass' },
              { label: '有效期', expected: '15天（自然天）', actual: '15天（自然天）', result: 'pass' },
            ],
          },
          {
            key: 'billing',
            title: '计费核查',
            status: 'passed',
            items: [
              { label: '数据计费', expected: '15GB高速，用尽后不限量限速1Mbps', actual: '15GB内正常，超出后限速不限量', result: 'pass' },
              { label: '漫游计费', expected: '香港.台湾共用漫游同价，不加收漫游费', actual: '香港.台湾漫游计费正确', result: 'pass' },
            ],
          },
          {
            key: 'reminder',
            title: '提醒核查',
            status: 'passed',
            items: [
              { label: '流量使用提醒', expected: '高速用量达80%、95%时下发短信提醒', actual: '80%和95%均正常下发', result: 'pass' },
              { label: '订购提醒', expected: '订购成功后下发欢迎短信', actual: '欢迎短信正常下发', result: 'pass' },
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
        productName: 'WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速)',
        template: '个人主资费·数据卡',
        productCode: 'SP-HK-TW-15D',
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
          title: '关于"WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速)"资费方案备案公示',
          target: '全体用户',
          content: '现将"WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速)"资费方案（资费代码：SP-HK-TW-15D）予以备案公示。公示期满且无异议后，该资费方案将正式上架销售。',
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
              { label: '公示系列名称', value: 'WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速)', category: 'filing' },
              { label: '方案编号', value: 'XNBP0003', category: 'filing' },
            ],
          },
          {
            title: '套餐基本信息',
            previewOnly: false,
            fields: [
              { label: 'BOSSID', value: 'SP-HK-TW-15D', category: 'filing' },
              { label: '一级分类', value: '国际漫游', category: 'filing' },
              { label: '二级分类', value: '数据卡', category: 'filing' },
              { label: '名称', value: 'WeWalk-Data Only-澳门.香港.台湾共用15日不限量(15GB高速)', category: 'filing' },
              { label: '资费属性', value: '国际漫游', category: 'filing' },
              { label: '资费单位', value: '一次性', category: 'filing' },
            ],
          },
          {
            title: '基础信息',
            previewOnly: false,
            fields: [
              { label: '资费标准', value: '一次性资费', category: 'common' },
              { label: '资费类型', value: '主套餐·数据卡', category: 'public' },
              { label: '上线日期', value: '20260701', category: 'common' },
              { label: '有效期限', value: '15天（自然天）', category: 'common' },
              { label: '适用地区', value: '香港.台湾（共用）', category: 'common' },
              { label: '销售渠道', value: '网上营业厅/掌上营业厅', category: 'common' },
            ],
          },
          {
            title: '资源信息',
            previewOnly: false,
            fields: [
              { label: '高速配额', value: '15GB（总量）', category: 'common' },
              { label: '漫游地', value: '香港.台湾', category: 'common' },
              { label: '限速策略', value: 'PCRF（用尽后不限量限速）', category: 'common' },
              { label: '卡类型/网络', value: '5G数据卡（Data Only）', category: 'common' },
              { label: '语音/短信', value: '无（纯数据产品）', category: 'common' },
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
   * 使「我的产品」抽屉展示 WeWalk 香港.台湾数据卡 等各档存量产品，
   * 展开/配置档案浮层与普通归档记录格式完全一致。
   */
  function seedStockProducts() {
    stockProducts.forEach((p, idx) => {
      const v = p.snapshot?.values || {}
      addArchived({
        // 存量在架产品分配正式工单号（WO+日期+序号），与其他归档工单格式一致；产品编码保留在快照「产品编码」栏展示
        workOrderId: `WO2026080100${idx + 1}`,
        productName: p.name,
        templateLabel: p.templateLabel,
        channelList: ['自有营业厅', '网上营业厅', '掌上营业厅APP'],
        salesAt: Date.now() - 1000 * 60 * 60 * 24 * 15,
        status: 'listed',
        seeded: true,
        snapshot: [
          { key: 'prodName', label: '产品名称', value: p.name },
          { key: 'prodCode', label: '产品编码', value: v.prodCode || p.code },
          { key: 'data', label: '高速配额', value: v.dataAmount ? `${v.dataAmount}${v.dataAmountUnit || 'GB'}/日高速` : '—' },
          { key: 'roam', label: '漫游地', value: Array.isArray(v.dataRoamArea) ? v.dataRoamArea.join('、') : '香港.台湾' },
          { key: 'valid', label: '有效期', value: v.validityLabel || '自然天' },
          { key: 'charge', label: '资费', value: '一次性' },
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
