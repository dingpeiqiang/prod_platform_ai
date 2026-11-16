<template>
  <div class="ops-view">
    <!-- ============ 模块一：收入总览 ============ -->
    <div class="ov-panel ov-rev-panel">
      <div class="ov-panel-title-bar">
        <h3 class="ov-title">收入总览</h3>
        <span class="ov-title-note">单位：万元 ｜ 数据口径：全省 · 2026年7月</span>
      </div>
      <div class="ov-rev-row">
        <div v-for="card in revenueCards" :key="card.key" class="ov-rev-card">
          <div class="ov-rev-card-head">
            <div class="ov-rev-icon" :class="card.iconCls">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/></svg>
            </div>
            <div class="ov-rev-head-title">{{ card.title }}</div>
          </div>
          <div class="ov-rev-body">
            <div class="ov-rev-metric">
              <div class="ov-rev-label">报表收入</div>
              <div class="ov-rev-value-row">
                <span class="ov-rev-now">{{ fmt(card.report.now) }}<span class="ov-rev-unit">万元</span></span>
              </div>
              <div class="ov-rev-sub">
                <span class="ov-rev-cum">当年累计 {{ fmt(card.report.cum) }}万</span>
                <span class="ov-rev-yoy"><span class="ov-yoy-tag">累计同比</span><span :class="deltaCls(card.report.yoy)" class="ov-yoy-val">{{ fmtPct(card.report.yoy) }}<svg class="ov-yoy-arrow" width="9" height="9" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline v-if="card.report.yoy >= 0" points="18 15 12 9 6 15"/><polyline v-else points="6 9 12 15 18 9"/></svg></span></span>
              </div>
            </div>
            <div class="ov-rev-metric">
              <div class="ov-rev-label">出账收入(税后)</div>
              <div class="ov-rev-value-row">
                <span class="ov-rev-now">{{ fmt(card.bill.now) }}<span class="ov-rev-unit">万元</span></span>
              </div>
              <div class="ov-rev-sub">
                <span class="ov-rev-cum">当年累计 {{ fmt(card.bill.cum) }}万</span>
                <span class="ov-rev-yoy"><span class="ov-yoy-tag">累计同比</span><span :class="deltaCls(card.bill.yoy)" class="ov-yoy-val">{{ fmtPct(card.bill.yoy) }}<svg class="ov-yoy-arrow" width="9" height="9" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline v-if="card.bill.yoy >= 0" points="18 15 12 9 6 15"/><polyline v-else points="6 9 12 15 18 9"/></svg></span></span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ============ 模块二：重点产品运营 ============ -->
    <div class="ov-panel ov-prod-panel">
      <div class="ov-prod-titlebar">
        <div class="ov-prod-titlebar-left">
          <h3 class="ov-title">重点产品运营</h3>
          <button
            v-if="anomalyAlert.count > 0"
            type="button"
            class="ov-alert-badge"
            :title="`${anomalyAlert.count} 个产品处于异动状态，点击定位到 5G新通话 下的畅享59元5G套餐`"
            @click="onAlertLocate"
          >
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
            <span class="ov-alert-count">{{ anomalyAlert.count }}</span>
          </button>
        </div>
        <div class="ov-prod-search">
          <input v-model="productKeyword" type="text" placeholder="搜索产品名称" autocomplete="off" />
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
        </div>
      </div>
      <div class="ov-prod-tabs">
        <button
          v-for="p in visibleProducts"
          :key="p.key"
          type="button"
          class="ov-prod-tab"
          :class="{ active: activeProduct === p.key }"
          @click="activeProduct = p.key"
        >{{ p.name }}</button>
        <div v-if="!visibleProducts.length" class="ov-prod-empty">未找到相关产品</div>
      </div>

      <div v-if="activeProduct" class="ov-prod-body">
        <template v-for="prod in activeProductData" :key="prod.key">
          <div class="ov-prod-block">
            <div class="ov-prod-block-head">
              <span class="ov-prod-block-title">{{ prod.name }}</span>
              <span class="ov-prod-block-sub">全省运营数据 · 2026年7月通报（全省行口径）</span>
            </div>

            <!-- ① 规模 & 活跃 -->
            <div class="ov-sec">
              <div class="ov-sec-title"><span class="ov-sec-badge ov-badge-blue">①</span>规模 &amp; 活跃</div>
              <div class="ov-sec-grid ov-sec-grid-3">
                <div v-for="m in prod.scale" :key="m.label" class="ov-kpi">
                  <div class="ov-kpi-icon" :class="'ov-ki-' + (['到达','活跃率'].some(s=>m.label.includes(s)) ? 'blue':'green')">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M9 8h6M9 12h6M9 16h4"/></svg>
                  </div>
                  <div class="ov-kpi-txt">
                    <div class="ov-kpi-label">{{ m.label }}<span class="ov-kpi-unit" v-if="m.unitIn">{{ m.unitIn }}</span></div>
                    <div class="ov-kpi-value">{{ m.value }}</div>
                    <div class="ov-kpi-yoy" v-if="m.yoy != null"><span>同比</span><span :class="deltaCls(m.yoy)">{{ fmtPct(m.yoy) }}</span></div>
                    <div class="ov-kpi-note" v-else-if="m.note">{{ m.note }}</div>
                  </div>
                </div>
              </div>
            </div>

            <!-- ② 结构占比 -->
            <div class="ov-sec">
              <div class="ov-sec-title"><span class="ov-sec-badge ov-badge-amber">②</span>结构占比</div>
              <div class="ov-sec-struct">
                <div class="ov-struct-extra ov-struct-extra-full">
                  <template v-for="g in prod.struct.groups" :key="g.label">
                    <div class="ov-struct-group">
                      <div class="ov-struct-group-label">{{ g.label }}</div>
                      <div class="ov-struct-rows">
                        <div v-for="r in g.rows" :key="r.label" class="ov-struct-row">
                          <span class="ov-struct-name">{{ r.label }}</span>
                          <div class="ov-struct-bar"><div class="ov-struct-bar-fill" :style="{ width: pctWidth(r.value) }"></div></div>
                          <span class="ov-struct-row-val">{{ fmtRatio(r.value) }}</span>
                        </div>
                      </div>
                    </div>
                  </template>
                </div>
              </div>
            </div>

            <!-- ③ 新增 -->
            <div class="ov-sec">
              <div class="ov-sec-title"><span class="ov-sec-badge ov-badge-blue">③</span>新增</div>
              <div class="ov-sec-grid ov-sec-grid-3">
                <div v-for="m in prod.added" :key="m.label" class="ov-kpi">
                  <div class="ov-kpi-icon ov-ki-green">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><polyline points="19 12 12 19 5 12"/></svg>
                  </div>
                  <div class="ov-kpi-txt">
                    <div class="ov-kpi-label">{{ m.label }}<span class="ov-kpi-unit" v-if="m.unitIn">{{ m.unitIn }}</span></div>
                    <div class="ov-kpi-value">{{ m.value }}</div>
                    <div class="ov-kpi-yoy" v-if="m.yoy != null"><span>同比</span><span :class="deltaCls(m.yoy)">{{ fmtPct(m.yoy) }}</span></div>
                    <div class="ov-kpi-note" v-else-if="m.warn">{{ m.warn }}</div>
                  </div>
                </div>
              </div>
            </div>

            <!-- ④ 新增质量 & 效益 -->
            <div class="ov-sec">
              <div class="ov-sec-title"><span class="ov-sec-badge ov-badge-purple">④</span>新增质量 &amp; 效益</div>
              <div class="ov-sec-grid ov-sec-grid-4">
                <div v-for="m in prod.quality" :key="m.label" class="ov-kpi ov-kpi-sm">
                  <div class="ov-kpi-icon ov-ki-purple">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2l2.5 6.5L21 9l-5 4 1.5 7L12 16.5 6.5 20 8 13l-5-4 6.5-.5z"/></svg>
                  </div>
                  <div class="ov-kpi-txt">
                    <div class="ov-kpi-label">{{ m.label }}<span class="ov-kpi-unit" v-if="m.unitIn">{{ m.unitIn }}</span></div>
                    <div class="ov-kpi-value ov-kpi-value-sm">{{ m.value }}</div>
                    <div class="ov-kpi-note" v-if="m.note">{{ m.note }}</div>
                  </div>
                </div>
              </div>
            </div>

            <!-- ⑤ 收入 & 渠道 -->
            <div class="ov-sec ov-sec-noborder">
              <div class="ov-sec-title"><span class="ov-sec-badge ov-badge-orange">⑤</span>收入 &amp; 渠道</div>
              <div class="ov-sec-grid ov-sec-income">
                <div class="ov-income-block">
                  <div class="ov-income-cap ov-cap-blue">报表收入</div>
                  <div class="ov-income-row"><span>当月</span><b>{{ fmt(prod.income.report.now) }}万</b></div>
                  <div class="ov-income-row"><span>当年累计</span><b>{{ fmt(prod.income.report.cum) }}万</b></div>
                  <div class="ov-income-row"><span>累计同比</span><b :class="deltaCls(prod.income.report.yoy)">{{ fmtPct(prod.income.report.yoy) }}</b></div>
                </div>
                <div class="ov-income-block">
                  <div class="ov-income-cap ov-cap-green">出账收入(税后)</div>
                  <div class="ov-income-row"><span>当月</span><b>{{ fmt(prod.income.bill.now) }}万</b></div>
                  <div class="ov-income-row"><span>当年累计</span><b>{{ fmt(prod.income.bill.cum) }}万</b></div>
                  <div class="ov-income-row"><span>累计同比</span><b :class="deltaCls(prod.income.bill.yoy)">{{ fmtPct(prod.income.bill.yoy) }}</b></div>
                </div>
                <div v-if="prod.income.extra" class="ov-income-block">
                  <div class="ov-income-cap ov-cap-amber">{{ prod.income.extra.name }}</div>
                  <div class="ov-income-row"><span>当月</span><b>{{ fmt(prod.income.extra.now) }}万</b></div>
                  <div class="ov-income-row"><span>当年累计</span><b>{{ fmt(prod.income.extra.cum) }}万</b></div>
                  <div class="ov-income-row"><span>累计同比</span><b :class="deltaCls(prod.income.extra.yoy)">{{ fmtPct(prod.income.extra.yoy) }}</b></div>
                </div>
                <div class="ov-income-block ov-channel">
                  <div class="ov-income-cap ov-cap-orange">渠道结构</div>
                  <div class="ov-channel-rows">
                    <div v-for="c in prod.channel" :key="c.label" class="ov-channel-row">
                      <span class="ov-struct-name">{{ c.label }}</span>
                      <div class="ov-struct-bar"><div class="ov-struct-bar-fill ov-bar-orange" :style="{ width: pctWidth(c.value) }"></div></div>
                      <span class="ov-struct-row-val">{{ fmtRatio(c.value) }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 下钻至产品：仅 5G新通话 / 上架产品 显示，可折叠 -->
            <div v-if="drillableProduct(prod.key)" class="ov-drill-wrap" :ref="captureDrillWrap">
              <button type="button" class="ov-drill-toggle" @click="wxthDrillOpen = !wxthDrillOpen">
                <span class="ov-drill-toggle-left">
                  <svg class="ov-drill-toggle-icon" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>
                  <span class="ov-drill-toggle-title">下钻至产品</span>
                  <span class="ov-drill-toggle-sub">从业务「5G新通话」下钻查看代表性套餐的运营情况</span>
                </span>
                <svg class="ov-drill-toggle-arrow" :class="{ open: wxthDrillOpen }" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
              </button>

              <div v-show="wxthDrillOpen" class="ov-drill-body">
                <!-- 代表性套餐选择层 -->
                <template v-if="!currentDrill">
                  <div class="ov-drill-list">
                    <button
                      v-for="key in drillKeys"
                      :key="key"
                      type="button"
                      class="ov-drill-fam"
                      :class="{ 'ov-drill-fam-highlight': highlightDrillKey === key }"
                      @click="wxthDrillActive = key"
                    >
                      <div class="ov-drill-fam-top">
                        <span class="ov-drill-fam-name">{{ drillLookup[key].name }}</span>
                        <span v-if="drillLookup[key].launched" class="ov-drill-fam-health ov-drill-fam-new">新</span>
                        <span v-else class="ov-drill-fam-health" :class="drillHealthCls(productLevel(key))">{{ productTotal(key) }}</span>
                      </div>
                      <div class="ov-drill-fam-meta">
                        <span v-if="drillLookup[key].launched" class="ov-drill-fam-status ov-drill-fam-new-status">新上架 · 数据积累中</span>
                        <template v-else>
                          <span class="ov-drill-fam-status" :class="drillHealthCls(productLevel(key))">{{ productLevel(key) }}</span>
                        </template>
                        <span class="ov-drill-fam-count">{{ drillLookup[key].scale.label }} {{ drillLookup[key].scale.value }}</span>
                      </div>
                      <div class="ov-drill-fam-scale">新增 {{ drillLookup[key].added.value }} <template v-if="drillLookup[key].added.delta"> · {{ drillLookup[key].added.delta }}</template></div>
                    </button>
                  </div>
                </template>

                <!-- 单产品健康度视图 -->
                <template v-else>
                  <div class="ov-drill-prodbar">
                    <button type="button" class="ov-drill-back" @click="wxthDrillActive = null">
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="15 18 9 12 15 6"/></svg>
                      返回套餐列表
                    </button>
                    <span class="ov-drill-prod-title">{{ currentDrill.name }}</span>
                    <span class="ov-drill-prod-sub">5G新通话 · 2026年7月</span>
                    <button
                      v-if="!currentDrill.launched && currentDrillLevel === '亚健康'"
                      type="button"
                      class="ov-drill-rootcause"
                      title="进入对话场景，对该套餐进行根因分析"
                      @click="$emit('open-rootcause', wxthDrillActive)"
                    >
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="11" y1="8" x2="11" y2="14"/><line x1="8" y1="11" x2="14" y2="11"/></svg>
                      根因分析
                    </button>
                  </div>

                  <!-- 刚上架产品：暂无运营数据，友好占位（不开放根因分析） -->
                  <div v-if="currentDrill.launched" class="ov-drill-empty-state">
                    <div class="ov-empty-icon">
                      <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M3 3v18h18"/><path d="M7 14l4-4 4 2 5-6"/><path d="M12 20V10"/></svg>
                    </div>
                    <div class="ov-empty-title">运营数据积累中</div>
                    <div class="ov-empty-desc">该套餐刚完成上架销售，当前暂无运营数据。待次日运营通报生成健康度后，即可在此查看四大维度与异动预警，并进行根因分析。</div>
                    <div class="ov-empty-sub">上架时间：{{ currentDrill.launchedAt }}</div>
                  </div>

                  <template v-if="!currentDrill.launched">
                  <!-- 健康总览 -->
                  <div class="ov-sec">
                    <div class="ov-sec-title"><span class="ov-sec-badge ov-badge-purple"><svg class="ov-sec-ico" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg></span>健康度总览</div>
                    <div class="ov-drill-health-card">
                      <div class="ov-drill-health-gauge">
                        <div class="ov-drill-gauge-ring" :class="drillHealthCls(currentDrillLevel)">
                          <span class="ov-drill-gauge-num">{{ currentDrillTotal }}</span>
                          <span class="ov-drill-gauge-unit">分</span>
                        </div>
                      </div>
                      <div class="ov-drill-health-info">
                        <div class="ov-drill-health-level" :class="drillHealthCls(currentDrillLevel)">{{ currentDrillLevel }} · 较上月 {{ currentDrill.healthDelta }}分</div>
                        <div class="ov-drill-health-note">{{ currentDrill.healthNote }}</div>
                        <div class="ov-drill-health-metrics">
                          <div class="ov-kpi ov-kpi-sm">
                            <div class="ov-kpi-icon ov-ki-blue"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="M9 8h6M9 12h6M9 16h4"/></svg></div>
                            <div class="ov-kpi-txt"><div class="ov-kpi-label">{{ currentDrill.scale.label }}</div><div class="ov-kpi-value ov-kpi-value-sm">{{ currentDrill.scale.value }}</div></div>
                          </div>
                          <div class="ov-kpi ov-kpi-sm">
                            <div class="ov-kpi-icon ov-ki-green"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><polyline points="19 12 12 19 5 12"/></svg></div>
                            <div class="ov-kpi-txt"><div class="ov-kpi-label">{{ currentDrill.added.label }}<span class="ov-kpi-delta" :class="drillDeltaCls(currentDrill.added.delta)">{{ currentDrill.added.delta }}</span></div><div class="ov-kpi-value ov-kpi-value-sm">{{ currentDrill.added.value }}</div></div>
                          </div>
                          <div class="ov-kpi ov-kpi-sm">
                            <div class="ov-kpi-icon ov-ki-amber"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2l2.5 6.5L21 9l-5 4 1.5 7L12 16.5 6.5 20 8 13l-5-4 6.5-.5z"/></svg></div>
                            <div class="ov-kpi-txt"><div class="ov-kpi-label">{{ currentDrill.income.label }}<span class="ov-kpi-delta" :class="drillDeltaCls(currentDrill.income.delta)">{{ currentDrill.income.delta }}</span></div><div class="ov-kpi-value ov-kpi-value-sm">{{ currentDrill.income.value }}</div></div>
                          </div>
                        </div>
                      </div>
                    </div>
                    <!-- 四大维度 + 全部二级指标（后评估指标） -->
                    <div class="ov-drill-dims">
                      <div v-for="d in currentDrillDims" :key="d.key" class="ov-drill-dim" :class="'ov-dim-' + d.key">
                        <div class="ov-drill-dim-head">
                          <span class="ov-drill-dim-name">{{ d.name }}</span>
                          <span class="ov-drill-dim-weight">权重 {{ d.weight }} · 满分{{ d.full }}分</span>
                          <span class="ov-drill-dim-score">{{ d.score }}<i>分</i></span>
                        </div>
                        <div class="ov-drill-dim-bar"><div class="ov-drill-dim-fill" :style="{ width: d.pct + '%' }"></div></div>
                        <div class="ov-drill-dim-items">
                          <div v-for="it in d.items" :key="it.name" class="ov-drill-dim-item">
                            <span class="ov-drill-dim-item-name">{{ it.name }}</span>
                            <span class="ov-drill-dim-item-value">{{ it.value }}</span>
                            <b :class="it.score >= 8 ? 'ov-item-ok' : it.score >= 6 ? 'ov-item-mid' : 'ov-item-warn'">{{ it.score }}</b>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <!-- 异动预警 -->
                  <div class="ov-sec">
                    <div class="ov-sec-title"><span class="ov-sec-badge ov-badge-orange"><svg class="ov-sec-ico" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg></span>异动预警</div>
                    <div class="ov-drill-alerts">
                      <div v-for="(a, i) in currentDrill.anomalies" :key="i" class="ov-drill-alert" :class="'ov-alert-' + a.level">
                        <span class="ov-drill-alert-tag">{{ a.level === 'high' ? '高' : a.level === 'mid' ? '中' : '低' }}</span>
                        <span class="ov-drill-alert-text">{{ a.text }}</span>
                      </div>
                      <div v-if="!currentDrill.anomalies.length" class="ov-drill-alert ov-alert-low"><span class="ov-drill-alert-tag">无</span><span class="ov-drill-alert-text">当前无显著异动</span></div>
                    </div>
                  </div>

                  <!-- 关键指标 -->
                  <div class="ov-sec ov-sec-noborder">
                    <div class="ov-sec-title"><span class="ov-sec-badge ov-badge-blue"><svg class="ov-sec-ico" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg></span>关键运营指标</div>
                    <div class="ov-sec-grid ov-sec-grid-3">
                      <div v-for="q in currentDrill.quality" :key="q.label" class="ov-kpi">
                        <div class="ov-kpi-icon ov-ki-purple"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2l2.5 6.5L21 9l-5 4 1.5 7L12 16.5 6.5 20 8 13l-5-4 6.5-.5z"/></svg></div>
                        <div class="ov-kpi-txt">
                          <div class="ov-kpi-label">{{ q.label }}<span class="ov-kpi-delta" :class="drillDeltaCls(q.delta)">{{ q.delta }}</span></div>
                          <div class="ov-kpi-value ov-kpi-value-sm">{{ q.value }}</div>
                        </div>
                      </div>
                    </div>
                  </div>
                  </template>
                </template>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script>
// 模块级共享状态：下钻选中的套餐 key 在组件卸载/重挂载后依然保留，
// 保证折叠后「恢复运营视图」能还原到关闭前的单产品视图（不依赖 prop 时序）
// 供 ChatPage 在新会话/切换助手时调用 reset 复位，使产品运营视图回到总览
export const opsDrillState = {
  key: null,
  reset() {
    this.key = null
  },
}
</script>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { useWorkOrderStore } from '../stores/workOrder.js'

const workOrderStore = useWorkOrderStore()

// 格式化上架时间（时间戳 → 年-月-日 时:分）
function formatSaleTime(ts) {
  if (!ts) return '—'
  const d = new Date(ts)
  if (Number.isNaN(d.getTime())) return '—'
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const props = defineProps({
  // 恢复时传入上次选中的套餐 key，用于还原到单产品视图（null 表示套餐列表层）
  initialDrill: { type: String, default: null },
})
const emit = defineEmits(['open-rootcause', 'update:drill'])

// ============ 工具 ============
function fmt(n) {
  if (n === null || n === undefined) return '—'
  return Number(n).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function fmtPct(n) {
  if (n === null || n === undefined) return '—'
  return (n > 0 ? '+' : '') + n.toFixed(2) + '%'
}
// 结构占比：value 为 0~1 的小数，需乘 100 显示，且占比不带正负号
function fmtRatio(n) {
  if (n === null || n === undefined) return '—'
  return (n * 100).toFixed(2) + '%'
}
function deltaCls(n) {
  if (n === null || n === undefined) return 'ov-yoy-flat'
  // 通信领域惯例：上涨红、下跌绿
  return n > 0 ? 'ov-yoy-up' : n < 0 ? 'ov-yoy-down' : 'ov-yoy-flat'
}
function pctWidth(v) {
  return Math.min(100, Math.max(2, Math.round(v * 100))) + '%'
}

// ============ 模块一：收入总览 ============
const revenueCards = [
  {
    key: 'cnfamily', title: 'CN及家庭产品', iconCls: 'ov-icon-blue',
    report: { now: 64653.57, cum: 454312.06, yoy: -0.64 },
    bill: { now: 23107.78, cum: 158966.95, yoy: 5.95 },
  },
  {
    key: 'family', title: '家庭产品', iconCls: 'ov-icon-emerald',
    report: { now: 54532.19, cum: 378076.80, yoy: 1.67 },
    bill: { now: 18693.26, cum: 126629.51, yoy: 7.83 },
  },
  {
    key: 'cn', title: 'CN产品', iconCls: 'ov-icon-amber',
    report: { now: 10121.38, cum: 76235.27, yoy: -10.68 },
    bill: { now: 4414.52, cum: 32337.44, yoy: -0.81 },
  },
]
// 说明：家庭产品出账收入为(税后含调测费)口径；CN及家庭/CN产品为(税后)口径

// ============ 模块二：重点产品数据 ============
const products = {
  jtkd: {
    name: '家庭宽带', key: 'jtkd',
    scale: [
      { label: '到达用户', value: '1182.03万', yoy: null },
      { label: '校园宽带到达', value: '46.39万', yoy: null },
      { label: '活跃率', value: '83.09%', yoy: null },
      { label: '活跃率(剔校园宽带)', value: '85.93%', yoy: null },
    ],
    struct: {
      caption: '到达速率结构',
      groups: [
        { label: '到达速率', rows: [
          { label: '1000M', value: 0.3096 },
          { label: '300-500M', value: 0.4320 },
          { label: '300M以下', value: 0.2584 },
        ] },
        { label: '新增速率', rows: [
          { label: '1000M', value: 0.3306 },
          { label: '300-500M', value: 0.5765 },
          { label: '300M以下', value: 0.0928 },
        ] },
        { label: '首宽/二宽', rows: [
          { label: '首宽', value: 0.7624 },
          { label: '二宽', value: 0.2372 },
        ] },
        { label: '资费结构', rows: [
          { label: '129元及以上', value: 0.2591 },
          { label: '59元以下', value: 0.3798 },
        ] },
      ],
    },
    added: [
      { label: '当月新增', value: '8.90万', yoy: -36.47 },
    ],
    quality: [
      { label: 'T+1活跃率', value: '92.57%', note: '新增用户' },
      { label: 'T+3离网率', value: '1.57%', note: '1497户' },
      { label: 'T+6离网率', value: '4.93%', note: '7620户' },
      { label: 'T+3沉默率', value: '6.94%', note: null },
      { label: 'T+6沉默率', value: '3.92%', note: null },
      { label: '户均实收(融合群)', value: '118.33元', note: '元/户' },
      { label: '低于50元占比', value: '8.44%', note: null },
      { label: '单宽户均实收', value: '20.70元', note: '元/户' },
    ],
    income: {
      report: { now: 40787.33, cum: 283350.15, yoy: 2.66 },
      bill: { now: 3977.45, cum: 27728.41, yoy: 0.39 },
    },
    channel: [
      { label: '线下', value: 0.7763 },
      { label: '10086', value: 0.1447 },
      { label: '线上', value: 0.0790 },
    ],
  },

  znzw: {
    name: '智能组网', key: 'znzw',
    scale: [
      { label: '到达用户', value: '360.87万', yoy: null },
      { label: '组网(不含FTTR)', value: '308.10万', yoy: null },
      { label: '活跃率', value: '89.28%', yoy: null },
    ],
    struct: {
      caption: '到达结构',
      groups: [
        { label: '到达结构', rows: [
          { label: 'FTTR', value: 0.1462 },
          { label: 'WiFi6', value: 0.7872 },
          { label: '融合网关', value: 0.0231 },
          { label: 'WiFi5', value: 0.0435 },
        ] },
        { label: '新增结构', rows: [
          { label: 'FTTR', value: 0.3061 },
          { label: 'WiFi6', value: 0.6714 },
          { label: '融合网关', value: 0.0225 },
          { label: 'WiFi5', value: 0 },
        ] },
      ],
    },
    added: [
      { label: '当月新增', value: '9.01万', yoy: -41.14 },
      { label: '新增(不含FTTR)', value: '6.25万', yoy: null },
    ],
    quality: [
      { label: 'T+3退订率', value: '7.14%', note: '5109户' },
      { label: 'T+6退订率', value: '6.63%', note: '8055户' },
      { label: 'T+3沉默率', value: '3.22%', note: null },
      { label: 'T+6沉默率', value: '1.93%', note: null },
      { label: '户均实收', value: '9.77元', note: '元/户' },
      { label: '低值占比(<10元)', value: '4.35%', note: null },
    ],
    income: {
      report: { now: 5394.80, cum: 34381.50, yoy: 5.96 },
      bill: { now: 2366.38, cum: 16756.35, yoy: -0.28 },
    },
    channel: [
      { label: '线下', value: 0.8626 },
      { label: '10086', value: 0.1285 },
      { label: '线上', value: 0.0087 },
    ],
  },

  ydyp: {
    name: '移动云盘', key: 'ydyp',
    scale: [
      { label: '到达用户', value: '1020.16万', yoy: null },
      { label: '叠加态到达', value: '85.38万', yoy: null },
      { label: '付费活跃', value: '25.09万', yoy: null },
      { label: '付费活跃率', value: '2.46%', yoy: null },
      { label: 'APP活跃', value: '56.56万', yoy: null },
      { label: '高价值', value: '49.84万', yoy: null },
    ],
    struct: {
      caption: '到达结构',
      groups: [
        { label: '到达产品结构', rows: [
          { label: '空间包', value: 0.9738 },
          { label: '会员', value: 0.0262 },
        ] },
        { label: '新增产品结构', rows: [
          { label: '空间包', value: 0.9608 },
          { label: '会员', value: 0.0392 },
        ] },
      ],
    },
    added: [
      { label: '当月新增', value: '34.70万', yoy: -16.33 },
      { label: '叠加态新增', value: '1.80万', yoy: null },
    ],
    quality: [
      { label: 'T+1活跃率', value: '6.55%', note: null },
      { label: 'T+3退订率', value: '13.44%', note: null },
      { label: '户均实收', value: '11.06元', note: '元/户' },
      { label: '低值占比', value: '17.88%', note: null },
    ],
    income: {
      report: { now: 3843.57, cum: 26527.23, yoy: 0.61 },
      bill: { now: 117.88, cum: 666.45, yoy: -8.87 },
      extra: { name: '叠加态收入', now: 499.26, cum: 3467.11, yoy: -23.87 },
    },
    channel: [
      { label: '线下', value: 0.4327 },
      { label: '10086', value: 0.5053 },
      { label: '线上', value: 0.0620 },
    ],
  },

  spcl: {
    name: '视频彩铃', key: 'spcl',
    scale: [
      { label: '到达用户', value: '1750.07万', yoy: null },
      { label: '叠加态到达', value: '145.32万', yoy: null },
      { label: '付费活跃', value: '103.83万', yoy: null },
      { label: '付费活跃率', value: '83.84%', yoy: null },
      { label: '高价值', value: '74.09万', yoy: null },
    ],
    struct: {
      caption: '到达结构',
      groups: [
        { label: '到达产品结构', rows: [
          { label: '功能产品', value: 0.9292 },
          { label: '会员产品', value: 0.0708 },
        ] },
        { label: '新增产品结构', rows: [
          { label: '功能产品', value: 0.9563 },
          { label: '会员产品', value: 0.0437 },
        ] },
      ],
    },
    added: [
      { label: '当月新增', value: '53.97万', yoy: -3.97 },
      { label: '叠加态新增', value: '2.21万', yoy: null },
    ],
    quality: [
      { label: 'T+3退订率', value: '6.50%', note: 'O20口径' },
      { label: 'T+6退订率', value: '15.06%', note: 'P20口径' },
      { label: '户均实收', value: '3.90元', note: '元/户' },
      { label: '低值占比', value: '7.26%', note: null },
    ],
    income: {
      report: { now: 2826.21, cum: 17832.90, yoy: -22.55 },
      bill: { now: 1437.37, cum: 7441.77, yoy: 0.89 },
      extra: { name: '叠加态收入', now: 1599.98, cum: 8640.08, yoy: -3.67 },
    },
    channel: [
      { label: '线下', value: 0.5056 },
      { label: '10086', value: 0.4066 },
      { label: '线上', value: 0.0170 },
    ],
  },

  mgsp: {
    name: '咪咕视频', key: 'mgsp',
    scale: [
      { label: '到达用户', value: '884.70万', yoy: null },
      { label: '叠加态到达', value: '105.72万', yoy: null },
      { label: '付费活跃', value: '33.32万', yoy: null },
      { label: '付费活跃率', value: '3.77%', yoy: null },
      { label: 'APP活跃', value: '87.61万', yoy: null },
      { label: '高价值', value: '35.28万', yoy: null },
    ],
    struct: {
      caption: '到达结构',
      groups: [
        { label: '到达产品结构', rows: [
          { label: '视频会员', value: 0.9981 },
          { label: '体育会员', value: 0.0019 },
        ] },
        { label: '新增产品结构', rows: [
          { label: '视频会员', value: 0.99995 },
          { label: '体育会员', value: 0.00005 },
        ] },
      ],
    },
    added: [
      { label: '当月新增', value: '28.02万', yoy: -40.06 },
      { label: '叠加态新增', value: '2.83万', yoy: null },
    ],
    quality: [
      { label: 'T+1活跃率', value: '8.21%', note: null },
      { label: 'T+3退订率', value: '16.36%', note: 'P20口径' },
      { label: '户均实收', value: '2.57元', note: '元/户' },
      { label: '低值占比', value: '5.30%', note: null },
    ],
    income: {
      report: { now: 2475.23, cum: 17732.58, yoy: -22.98 },
      bill: { now: 199.98, cum: 1770.28, yoy: -21.68 },
      extra: { name: '叠加态收入', now: 608.59, cum: 4746.11, yoy: -43.72 },
    },
    channel: [
      { label: '线下', value: 0.6264 },
      { label: '10086', value: 0.3178 },
      { label: '线上', value: 0.0557 },
    ],
  },

  wxth: {
    name: '5G新通话', key: 'wxth',
    scale: [
      { label: '到达用户', value: '94.50万', yoy: null },
      { label: '付费活跃', value: '7.71万', yoy: null },
      { label: '付费活跃率', value: '8.16%', yoy: null },
    ],
    struct: {
      caption: '到达结构',
      groups: [
        { label: '到达产品结构', rows: [
          { label: '明星来电', value: 0.6231 },
          { label: 'AI速记', value: 0.0169 },
          { label: '能量包', value: 0.0016 },
        ] },
        { label: '新增产品结构', rows: [
          { label: '明星来电', value: 0.8475 },
          { label: 'AI速记', value: 0.0496 },
          { label: '能量包', value: 0.0007 },
        ] },
      ],
    },
    added: [
      { label: '当月新增', value: '17.52万', yoy: null, warn: '同比+1279.59%（低基数）' },
    ],
    quality: [
      { label: 'T+3退订率', value: '3.36%', note: null },
      { label: 'T+6退订率', value: '17.12%', note: null },
      { label: '户均实收', value: '1.18元', note: '元/户' },
      { label: '低值占比', value: '91.51%', note: null },
    ],
    income: {
      report: { now: 122.18, cum: 386.36, yoy: 1775.78 },
      bill: { now: 97.42, cum: 466.84, yoy: 2104.99 },
      extra: { name: '叠加态收入', now: 96.47, cum: 464.43, yoy: 100 },
    },
    channel: [
      { label: '线下', value: 0.5940 },
      { label: '10086', value: 0.3807 },
      { label: '线上', value: 0.0255 },
    ],
  },

  ldgj: {
    name: '来电管家', key: 'ldgj',
    scale: [
      { label: '到达用户', value: '521.84万', yoy: null },
      { label: '叠加态到达', value: '457.60万', yoy: null },
      { label: '付费活跃', value: '377.53万', yoy: null },
      { label: '付费活跃率', value: '72.35%', yoy: null },
      { label: '高价值', value: '244.56万', yoy: null },
    ],
    struct: {
      caption: '到达结构',
      groups: [
        { label: '到达产品结构', rows: [
          { label: '升级版', value: 0.0252 },
        ] },
        { label: '新增产品结构', rows: [
          { label: '升级版', value: 0.1538 },
        ] },
      ],
    },
    added: [
      { label: '当月新增', value: '9.77万', yoy: -38.26 },
      { label: '叠加态新增', value: '9.76万', yoy: null },
    ],
    quality: [
      { label: 'T+3退订率', value: '3.30%', note: null },
      { label: 'T+6退订率', value: '7.07%', note: 'N20口径' },
      { label: '户均实收', value: '1.94元', note: '元/户' },
      { label: '低值占比', value: '17.88%', note: null },
    ],
    income: {
      report: { now: 1105.91, cum: 7868.66, yoy: -2.35 },
      bill: { now: 1000.20, cum: 7023.34, yoy: 1.07 },
    },
    channel: [
      { label: '线下', value: 0.9908 },
      { label: '10086', value: 0.0073 },
      { label: '线上', value: 0.0019 },
    ],
  },

  dphy: {
    name: '大屏会员', key: 'dphy',
    scale: [
      { label: '到达用户', value: '200.73万', yoy: null, note: '当月活跃率暂缺' },
    ],
    struct: {
      caption: '到达结构',
      groups: [
        { label: '会员结构', rows: [
          { label: '重点会员占比', value: 0.3792 },
          { label: '新增重点会员占比', value: 0.7523 },
        ] },
      ],
    },
    added: [
      { label: '当月新增', value: '4.69万', yoy: null },
    ],
    quality: [
      { label: 'T+3退订率', value: '13.86%', note: '4882户' },
      { label: 'T+6退订率', value: '25.97%', note: '18086户' },
      { label: '户均实收', value: '13.80元', note: '元/户' },
      { label: '实收0元占比', value: '1.81%', note: null },
    ],
    income: {
      report: { now: 1748.93, cum: 13700.67, yoy: -5.41 },
      bill: { now: 1452.62, cum: 10170.51, yoy: -5.37 },
    },
    channel: [
      { label: '线下', value: 0.3428 },
      { label: '10086', value: 0.0316 },
      { label: '线上', value: 0.6255 },
    ],
  },
}

// ============ 5G新通话 → 单产品下钻（代表性套餐，演示 mock 数据） ============
// 依据《业务和产品对应清单.xlsx》中 5G新通话 下的产品，直接选取 10 个代表性套餐；指标为演示用虚拟值
// 健康度按《产产品资费质量提升方案》第五章第4小节：四大维度 + 后评估指标，二级指标 0-10 分加权
// dims 顺序对应 dimMeta：效益 eff[5] / 市场 market[4] / 运营质量 quality[3] / 生命周期 life[3]
const dimMeta = [
  { key: 'eff', name: '效益维度', weight: '45%', full: 45, items: [
    { name: '累计收入', full: 9 }, { name: '月均ARPU', full: 11.25 }, { name: '收入环比', full: 6.75 },
    { name: '营销费用占比', full: 9 }, { name: '利润率', full: 9 },
  ] },
  { key: 'market', name: '市场维度', weight: '25%', full: 25, items: [
    { name: '当前活跃用户', full: 8.75 }, { name: '月净增用户', full: 6.25 },
    { name: '订购转化率', full: 6.25 }, { name: '渠道上架率', full: 3.75 },
  ] },
  { key: 'quality', name: '运营质量维度', weight: '20%', full: 20, items: [
    { name: '资费投诉率', full: 8 }, { name: '月度退订率', full: 6 }, { name: '业务办理成功率', full: 6 },
  ] },
  { key: 'life', name: '生命周期维度', weight: '10%', full: 10, items: [
    { name: '新用户吸引占比', full: 4.5 }, { name: '沉默用户占比', full: 3.5 }, { name: '同类资费排名', full: 2 },
  ] },
]

const wxthDrill = {
  mxld: {
    name: '5G新通话-明星来电',
    health: 88, healthDelta: '+3', healthLevel: '良好',
    healthNote: '整体稳健，新增与收入双升',
    dims: {
      eff: [{ s: 9, v: '286.5万' }, { s: 8, v: '1.02元' }, { s: 9, v: '+18.3%' }, { s: 8, v: '11.2%' }, { s: 8, v: '21.5%' }],
      market: [{ s: 10, v: '58.9万' }, { s: 9, v: '11.2万' }, { s: 8, v: '8.6%' }, { s: 9, v: '92%' }],
      quality: [{ s: 9, v: '0.12%' }, { s: 8, v: '2.6%' }, { s: 9, v: '99.1%' }],
      life: [{ s: 9, v: '18.4%' }, { s: 8, v: '7.2%' }, { s: 9, v: '前20%' }],
    },
    anomalies: [
      { level: 'high', text: 'T+6退订率 17.4%，超同类均值 5.3pp，重点监控' },
      { level: 'mid', text: '权益装机带动新增，但低值占比偏高' },
    ],
    scale: { label: '到达用户', value: '58.9万' },
    added: { label: '当月新增', value: '11.2万', delta: '+18.3%' },
    income: { label: '报表收入', value: '75.3万', delta: '+89.6%' },
    quality: [
      { label: '付费活跃率', value: '8.42%', delta: '+1.1pp' },
      { label: 'T+6退订率', value: '17.4%', delta: '+2.6pp' },
      { label: '户均实收', value: '1.02元', delta: null },
    ],
  },
  dlsp: {
    name: '5G新通话-点亮屏幕标准版',
    health: 85, healthDelta: '+2', healthLevel: '健康',
    healthNote: '版本结构逐步收敛，新增回升、退订健康',
    dims: {
      eff: [{ s: 8, v: '112.6万' }, { s: 8, v: '0.72元' }, { s: 9, v: '+6.8%' }, { s: 8, v: '13.5%' }, { s: 9, v: '18.4%' }],
      market: [{ s: 8, v: '21.4万' }, { s: 8, v: '1.2万' }, { s: 9, v: '7.8%' }, { s: 9, v: '92%' }],
      quality: [{ s: 9, v: '0.18%' }, { s: 8, v: '8.6%' }, { s: 9, v: '99.0%' }],
      life: [{ s: 9, v: '15.2%' }, { s: 8, v: '9.6%' }, { s: 9, v: '中上游' }],
    },
    anomalies: [
      { level: 'low', text: '新增小幅回升，版本结构逐步收敛，暂无异动' },
    ],
    scale: { label: '到达用户', value: '21.4万' },
    added: { label: '当月新增', value: '1.8万', delta: '+18.6%' },
    income: { label: '报表收入', value: '28.9万', delta: '+9.4%' },
    quality: [
      { label: '付费活跃率', value: '6.2%', delta: '+0.8pp' },
      { label: 'T+6退订率', value: '12.4%', delta: '-1.6pp' },
      { label: '户均实收', value: '1.34元', delta: null },
    ],
  },
  aisj: {
    name: '5G新通话-AI速记100分钟包',
    health: 85, healthDelta: '+1', healthLevel: '健康',
    healthNote: '分钟包梯度清晰，长期包承接良好，增长稳健',
    dims: {
      eff: [{ s: 9, v: '46.2万' }, { s: 8, v: '2.74元' }, { s: 9, v: '+9.2%' }, { s: 8, v: '12.8%' }, { s: 8, v: '18.6%' }],
      market: [{ s: 9, v: '3.2万' }, { s: 8, v: '1.0万' }, { s: 8, v: '8.1%' }, { s: 9, v: '90%' }],
      quality: [{ s: 9, v: '0.12%' }, { s: 9, v: '7.4%' }, { s: 9, v: '98.9%' }],
      life: [{ s: 8, v: '13.8%' }, { s: 8, v: '9.2%' }, { s: 9, v: '中上游' }],
    },
    anomalies: [
      { level: 'low', text: '推出 300 分钟包后，长期包承接良好，本包新增稳定' },
    ],
    scale: { label: '到达用户', value: '3.2万' },
    added: { label: '当月新增', value: '1.0万', delta: '+9.2%' },
    income: { label: '报表收入', value: '9.2万', delta: '+14.1%' },
    quality: [
      { label: '付费活跃率', value: '13.4%', delta: '+0.8pp' },
      { label: 'T+6退订率', value: '7.4%', delta: '-1.2pp' },
      { label: '户均实收', value: '2.74元', delta: null },
    ],
  },
  znfy: {
    name: '5G新通话-智能翻译100分钟包',
    health: 84, healthDelta: '+2', healthLevel: '良好',
    healthNote: '需求稳定，复购良好',
    dims: {
      eff: [{ s: 9, v: '58.6万' }, { s: 9, v: '2.81元' }, { s: 9, v: '+9.4%' }, { s: 9, v: '9.6%' }, { s: 9, v: '24.8%' }],
      market: [{ s: 9, v: '1.4万' }, { s: 9, v: '0.4万' }, { s: 9, v: '8.8%' }, { s: 9, v: '95%' }],
      quality: [{ s: 9, v: '0.08%' }, { s: 9, v: '7.9%' }, { s: 10, v: '99.3%' }],
      life: [{ s: 9, v: '16.2%' }, { s: 9, v: '6.4%' }, { s: 9, v: '前20%' }],
    },
    anomalies: [
      { level: 'low', text: '叠加包复购占比提升至 32%，可做到期提醒运营' },
    ],
    scale: { label: '到达用户', value: '1.4万' },
    added: { label: '当月新增', value: '0.4万', delta: '+5.6%' },
    income: { label: '报表收入', value: '3.9万', delta: '+7.2%' },
    quality: [
      { label: '付费活跃率', value: '14.1%', delta: '+0.4pp' },
      { label: 'T+6退订率', value: '7.9%', delta: '-0.9pp' },
      { label: '户均实收', value: '2.81元', delta: null },
    ],
  },
  tscy: {
    name: '畅享59元5G套餐',
    health: 72, healthDelta: '-9', healthLevel: '亚健康',
    healthNote: '典型"竞争性新增萎缩"——流量配置落后竞品，年轻新客转化下降；存量基本盘稳定，问题在获客端',
    dims: {
      eff: [{ s: 8, v: '5000万/月' }, { s: 7, v: '58元' }, { s: 7, v: '-1.6%' }, { s: 7, v: '16.5%' }, { s: 7, v: '14.2%' }],
      market: [{ s: 7, v: '86.0万' }, { s: 6, v: '0.28万' }, { s: 6, v: '5.2%' }, { s: 8, v: '90%' }],
      quality: [{ s: 8, v: '0.03%' }, { s: 8, v: '3.1%' }, { s: 9, v: '99.2%' }],
      life: [{ s: 6, v: '11.2%' }, { s: 6, v: '9.8%' }, { s: 6, v: '中游偏下' }],
    },
    anomalies: [
      { level: 'high', text: '新增下滑 35% 已持续 7 天，累计影响新增订购约 670 单，折合月收入损失约 3.9 万元' },
      { level: 'high', text: '新增转化仅 62 分（↓18），跌破正常水平，年轻客群在信息流渠道被截流' },
      { level: 'mid', text: '竞品对标 68 分（↓12），流量 30GB 落后竞品均值 42.5GB 约 29%' },
      { level: 'low', text: '存量基本盘稳定：投诉率 85 分、退订日均 82 单（+5.1%）均在正常范围' },
    ],
    scale: { label: '在网用户', value: '86.0万' },
    added: { label: '日均新增', value: '177单', delta: '-35%' },
    income: { label: '月收入', value: '5000万', delta: '+0.3%' },
    quality: [
      { label: '月度退订', value: '82单/日', delta: '+5.1%' },
      { label: '投诉率', value: '85分', delta: '-2' },
      { label: '续约率', value: '78分', delta: '-5' },
    ],
  },
  nlb: {
    name: '5G新通话能量包A',
    health: 85, healthDelta: '+2', healthLevel: '健康',
    healthNote: '体量稳步增长，户均实收改善，结构健康',
    dims: {
      eff: [{ s: 8, v: '27.8万' }, { s: 8, v: '0.71元' }, { s: 9, v: '+4.8%' }, { s: 8, v: '14.2%' }, { s: 9, v: '16.8%' }],
      market: [{ s: 8, v: '1.8万' }, { s: 8, v: '0.4万' }, { s: 9, v: '7.6%' }, { s: 9, v: '84%' }],
      quality: [{ s: 9, v: '0.16%' }, { s: 8, v: '9.8%' }, { s: 8, v: '98.2%' }],
      life: [{ s: 9, v: '12.6%' }, { s: 8, v: '10.4%' }, { s: 9, v: '中上游' }],
    },
    anomalies: [
      { level: 'low', text: '覆盖与活跃双升，暂无显著异动' },
    ],
    scale: { label: '到达用户', value: '1.8万' },
    added: { label: '当月新增', value: '0.4万', delta: '+18.6%' },
    income: { label: '报表收入', value: '2.1万', delta: '+8.4%' },
    quality: [
      { label: '付费活跃率', value: '10.6%', delta: '+0.9pp' },
      { label: 'T+6退订率', value: '9.8%', delta: '-1.2pp' },
      { label: '户均实收', value: '0.71元', delta: null },
    ],
  },
  aith: {
    name: '5G新通话-AI通话黄金会员',
    health: 82, healthDelta: '+2', healthLevel: '良好',
    healthNote: '会员分层承接良好',
    dims: {
      eff: [{ s: 9, v: '96.2万' }, { s: 10, v: '2.84元' }, { s: 9, v: '+11.8%' }, { s: 8, v: '8.4%' }, { s: 9, v: '26.1%' }],
      market: [{ s: 9, v: '2.6万' }, { s: 9, v: '0.7万' }, { s: 9, v: '9.2%' }, { s: 10, v: '96%' }],
      quality: [{ s: 9, v: '0.06%' }, { s: 9, v: '8.4%' }, { s: 9, v: '99.4%' }],
      life: [{ s: 9, v: '15.8%' }, { s: 9, v: '5.6%' }, { s: 10, v: '前20%' }],
    },
    anomalies: [
      { level: 'low', text: '黄金/钻石会员 ARPU 明显高于基础会员' },
    ],
    scale: { label: '到达用户', value: '2.6万' },
    added: { label: '当月新增', value: '0.7万', delta: '+9.2%' },
    income: { label: '报表收入', value: '7.4万', delta: '+11.8%' },
    quality: [
      { label: '付费活跃率', value: '11.3%', delta: '+0.7pp' },
      { label: 'T+6退订率', value: '8.4%', delta: '-1.0pp' },
      { label: '户均实收', value: '2.84元', delta: null },
    ],
  },
  qwth: {
    name: '5G新通话-趣味通话',
    health: 86, healthDelta: '+1', healthLevel: '健康',
    healthNote: '趣味玩法持续带动低频用户活跃，增长健康',
    dims: {
      eff: [{ s: 9, v: '32.6万' }, { s: 8, v: '2.24元' }, { s: 9, v: '+6.2%' }, { s: 9, v: '11.4%' }, { s: 8, v: '18.2%' }],
      market: [{ s: 8, v: '1.1万' }, { s: 8, v: '0.4万' }, { s: 9, v: '8.6%' }, { s: 9, v: '88%' }],
      quality: [{ s: 9, v: '0.12%' }, { s: 9, v: '7.8%' }, { s: 9, v: '98.8%' }],
      life: [{ s: 8, v: '15.4%' }, { s: 9, v: '8.6%' }, { s: 9, v: '中上游' }],
    },
    anomalies: [
      { level: 'low', text: '潮智版占比提升，年轻客群稳定增长' },
    ],
    scale: { label: '到达用户', value: '1.1万' },
    added: { label: '当月新增', value: '0.4万', delta: '+12.8%' },
    income: { label: '报表收入', value: '2.6万', delta: '+9.4%' },
    quality: [
      { label: '付费活跃率', value: '11.6%', delta: '+0.7pp' },
      { label: 'T+6退订率', value: '7.8%', delta: '-0.9pp' },
      { label: '户均实收', value: '2.24元', delta: null },
    ],
  },
  yyb: {
    name: '5G新通话6元包',
    health: 86, healthDelta: '+1', healthLevel: '健康',
    healthNote: '入门引流型产品，覆盖稳定，向高阶包转化良好',
    dims: {
      eff: [{ s: 8, v: '68.4万' }, { s: 9, v: '0.48元' }, { s: 9, v: '+12.4%' }, { s: 8, v: '15.6%' }, { s: 8, v: '15.8%' }],
      market: [{ s: 9, v: '12.3万' }, { s: 9, v: '2.6万' }, { s: 9, v: '8.8%' }, { s: 9, v: '92%' }],
      quality: [{ s: 9, v: '0.18%' }, { s: 9, v: '8.4%' }, { s: 9, v: '98.6%' }],
      life: [{ s: 9, v: '21.4%' }, { s: 8, v: '8.8%' }, { s: 8, v: '中上游' }],
    },
    anomalies: [
      { level: 'low', text: '作为低门槛入口，向高阶功能包转化良好' },
    ],
    scale: { label: '到达用户', value: '12.3万' },
    added: { label: '当月新增', value: '2.6万', delta: '+12.4%' },
    income: { label: '报表收入', value: '5.6万', delta: '+13.8%' },
    quality: [
      { label: '付费活跃率', value: '9.8%', delta: '+0.7pp' },
      { label: 'T+6退订率', value: '8.4%', delta: '-1.1pp' },
      { label: '户均实收', value: '0.48元', delta: null },
    ],
  },
  thzm: {
    name: '5G新通话-通话字幕',
    health: 83, healthDelta: '+1', healthLevel: '良好',
    healthNote: '基础能力承载，使用稳定',
    dims: {
      eff: [{ s: 9, v: '46.2万' }, { s: 9, v: '0.81元' }, { s: 9, v: '+9.8%' }, { s: 9, v: '10.4%' }, { s: 8, v: '22.6%' }],
      market: [{ s: 9, v: '3.6万' }, { s: 9, v: '0.5万' }, { s: 9, v: '8.4%' }, { s: 9, v: '93%' }],
      quality: [{ s: 9, v: '0.09%' }, { s: 9, v: '9.7%' }, { s: 9, v: '99.0%' }],
      life: [{ s: 9, v: '14.6%' }, { s: 9, v: '8.2%' }, { s: 9, v: '前30%' }],
    },
    anomalies: [
      { level: 'low', text: '作为能力底座，承载各功能产品增强包' },
    ],
    scale: { label: '到达用户', value: '3.6万' },
    added: { label: '当月新增', value: '0.5万', delta: '+4.2%' },
    income: { label: '报表收入', value: '2.9万', delta: '+9.8%' },
    quality: [
      { label: '付费活跃率', value: '10.4%', delta: '+0.2pp' },
      { label: 'T+6退订率', value: '9.7%', delta: '-0.5pp' },
      { label: '户均实收', value: '0.81元', delta: null },
    ],
  },
}
const wxthDrillKeys = Object.keys(wxthDrill)
// 哪些业务 tab 支持下钻到单产品（当前仅 5G新通话）
const drillableProduct = (key) => key === 'wxth'

// ============ 动态下钻套餐：已完成上架（状态=销售中）的产品动态纳入 ============
// 用户完成 配置→测试→备案→渠道选择 后，工单归档为 listed（销售中），
// 这里将其作为一种"刚上架、暂无运营数据"的套餐动态加入 5G新通话 的下钻列表，
// 上架时间取归档时的真实 salesAt（即用户提交销售渠道的时间）。
// 已在 wxthDrill 中通过 name 硬编码展示过的套餐不再重复纳入。
// 仅纳入用户本次真实走完上架流程的产品（exclude 演示种子工单 seeded=true），
// 保证"刚上架"卡片不是默认出现。
const dynamicDrillEntries = computed(() => {
  const mockNames = new Set(Object.values(wxthDrill).map((x) => x.name))
  return (workOrderStore.archived || [])
    .filter((w) => w.status !== 'offline' && !w.seeded && !mockNames.has(w.productName))
    .map((w) => ({
      key: 'wo_' + w.workOrderId,
      name: w.productName,
      launched: true,
      launchedAt: formatSaleTime(w.salesAt),
      healthNote: '该套餐刚完成上架销售，运营数据仍在积累中，待次日运营通报后生成健康度。',
      dims: { eff: [], market: [], quality: [], life: [] },
      anomalies: [],
      scale: { label: '在网用户', value: '—' },
      added: { label: '日均新增', value: '—', delta: null },
      income: { label: '月收入', value: '—', delta: null },
      quality: [
        { label: '月度退订', value: '—', delta: null },
        { label: '投诉率', value: '—', delta: null },
        { label: '续约率', value: '—', delta: null },
      ],
    }))
})

// 下钻套餐总列表（硬编码代表性套餐 + 动态上架产品）
const drillEntries = computed(() => [
  ...wxthDrillKeys.map((k) => wxthDrill[k]),
  ...dynamicDrillEntries.value,
])
// key → 套餐数据（硬编码以对象属性名 mxld/tscy/... 为 key，动态以 wo_xxx 为 key）
const drillLookup = computed(() => {
  const map = {}
  wxthDrillKeys.forEach((k) => { map[k] = wxthDrill[k] })
  dynamicDrillEntries.value.forEach((d) => { map[d.key] = d })
  return map
})

// 下钻套餐 key 列表（硬编码 + 动态）
const drillKeys = computed(() => [
  ...wxthDrillKeys,
  ...dynamicDrillEntries.value.map((d) => d.key),
])

const productKeys = ['jtkd', 'znzw', 'ydyp', 'spcl', 'mgsp', 'wxth', 'ldgj', 'dphy']

// ============ 交互状态 ============
const activeProduct = ref('jtkd')
const productKeyword = ref('')

const visibleProducts = computed(() => {
  const kw = (productKeyword.value || '').trim().toLowerCase()
  const list = productKeys.map((k) => products[k])
  if (!kw) return list
  return list.filter((p) => p.name.toLowerCase().includes(kw))
})

const activeProductData = computed(() => {
  const list = visibleProducts.value
  if (!list.length) return []
  if (!list.some((p) => p.key === activeProduct.value)) {
    activeProduct.value = list[0].key
  }
  return list.filter((p) => p.key === activeProduct.value)
})

// ============ 单产品下钻交互状态 ============
// 下钻模块是否展开（可折叠，默认展开）
const wxthDrillOpen = ref(true)
// 当前下钻选中的代表性套餐 key（null 表示停留在套餐列表层）
const wxthDrillActive = ref(null)

// ============ 异动预警（重点产品运营标题旁） ============
// 预警来源：5G新通话 下钻中处于"亚健康"状态的代表性套餐（排除刚上架、数据积累中的产品）
const anomalyAlert = computed(() => {
  const keys = wxthDrillKeys.filter(
    (k) => !drillLookup.value[k]?.launched && drillLookup.value[k]?.healthLevel === '亚健康'
  )
  return { count: keys.length, keys }
})
// 点击预警图标后高亮的套餐卡片 key（用于定位到具体卡片）
const highlightDrillKey = ref(null)
// 点击预警图标：切到 5G新通话 tab、展开下钻、定位并高亮"亚健康"套餐卡片
const onAlertLocate = () => {
  if (!anomalyAlert.value.keys.length) return
  const target = anomalyAlert.value.keys[0]
  activeProduct.value = 'wxth'
  wxthDrillOpen.value = true
  wxthDrillActive.value = null
  highlightDrillKey.value = target
  nextTick(() => {
    scrollToDrill()
    // 高亮保持一段时间后自动淡出
    clearTimeout(onAlertLocate._t)
    onAlertLocate._t = setTimeout(() => {
      highlightDrillKey.value = null
    }, 3000)
  })
}

// 恢复运营视图时，需同时回到「5G新通话」业务 tab，否则下钻模块（v-if=drillableProduct）不显示
const restoreProductKey = props.initialDrill || opsDrillState.key
// 初始下钻状态：优先取外部传入的 initialDrill，否则回退到模块级记忆的上次选中
if (props.initialDrill) {
  wxthDrillActive.value = props.initialDrill
} else if (opsDrillState.key) {
  wxthDrillActive.value = opsDrillState.key
}
// 若恢复的是单产品下钻（tscy 等），确保业务 tab 切到 5G新通话，下钻模块才会渲染
if (restoreProductKey) {
  activeProduct.value = 'wxth'
}
// 下钻模块元素引用（用于重新打开时自动定位滚动）
let drillWrapEl = null
const captureDrillWrap = (el) => {
  drillWrapEl = el
}
// 复原到单产品下钻时，自动定位到「下钻至产品」模块，避免停留在页面顶部
const scrollToDrill = () => {
  if (drillWrapEl) {
    drillWrapEl.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}
onMounted(() => {
  if (restoreProductKey) {
    nextTick(scrollToDrill)
  }
})
// 选中状态变化时：更新模块级记忆，并同步给父级
watch(wxthDrillActive, (v) => {
  opsDrillState.key = v
  emit('update:drill', v)
})
// 切换业务 tab 时重置下钻状态
watch(activeProduct, () => {
  wxthDrillActive.value = null
})
const currentDrill = computed(() =>
  wxthDrillActive.value ? drillLookup.value[wxthDrillActive.value] || null : null
)
// 四维度二级指标打分（每项 0-10 分），按满分折算得到各维度得分
const computeDims = (p) => {
  if (!p || !p.dims) return []
  return dimMeta.map((d) => {
    const arr = p.dims[d.key] || []
    let sub = 0
    const items = d.items.map((it, i) => {
      const s = arr[i]?.s ?? 0
      sub += (s / 10) * it.full
      return { name: it.name, score: s, value: arr[i]?.v ?? '' }
    })
    const score = Math.round(sub)
    return { key: d.key, name: d.name, weight: d.weight, full: d.full, score, pct: Math.round((sub / d.full) * 100), items }
  })
}
// 综合分 = 四维度得分之和
const computeTotal = (dims) => dims.reduce((s, d) => s + d.score, 0)
// 等级：健康≥85 / 亚健康 60≤x<85 / 不健康<60
const computeLevel = (total) => (total >= 85 ? '健康' : total >= 60 ? '亚健康' : '不健康')
// 供套餐列表层与单产品层共用同一套分数，保证一致性
const productTotal = (key) => computeTotal(computeDims(drillLookup.value[key]))
const productLevel = (key) => computeLevel(productTotal(key))

const currentDrillDims = computed(() => computeDims(currentDrill.value))
const currentDrillTotal = computed(() => computeTotal(currentDrillDims.value))
const currentDrillLevel = computed(() => computeLevel(currentDrillTotal.value))
const drillHealthCls = (level) =>
  level === '不健康' ? 'ov-drill-health-warn' : level === '亚健康' ? 'ov-drill-health-mid' : 'ov-drill-health-ok'
function drillDeltaCls(n) {
  if (!n) return 'ov-flat'
  const up = n.endsWith('%')
  if (n.startsWith('-')) return 'ov-down'
  return 'ov-up'
}
</script>

<style scoped>
.ops-view {
  width: 100%;
  max-width: 980px;
  margin: 0 auto;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 16px;
  font-family: 'Noto Sans SC', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

/* ===== 面板：顶部浅蓝渐变到底部全白（参考 cmi_zb_pom） ===== */
.ov-panel {
  background: linear-gradient(180deg, #e6effb 0%, #f0f9ff 6%, #fafbff 15%, #ffffff 28%);
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  border: 1px solid #eef2f7;
  overflow: hidden;
}

.ov-title {
  font-size: 16px;
  font-weight: 600;
  color: #334155;
  margin: 0;
}

/* ===== 模块一 收入总览 ===== */
.ov-rev-panel { padding-bottom: 18px; }
.ov-panel-title-bar {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  padding: 16px 18px 12px;
}
.ov-title-note { font-size: 11px; font-weight: 500; color: #94a3b8; }

.ov-rev-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  padding: 6px 18px 0;
}
.ov-rev-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 14px;
}
.ov-rev-card-head { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.ov-rev-icon {
  width: 38px; height: 38px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.ov-icon-blue { background: #e0f2fe; color: #2563eb; }
.ov-icon-emerald { background: #d1fae5; color: #059669; }
.ov-icon-amber { background: #fef3c7; color: #d97706; }
.ov-rev-head-title { font-size: 15px; font-weight: 700; color: #1e293b; }

.ov-rev-body { display: flex; flex-direction: column; gap: 12px; }
.ov-rev-metric { padding-top: 10px; border-top: 1px dashed #eef2f7; }
.ov-rev-label { font-size: 11px; color: #94a3b8; margin-bottom: 3px; }
.ov-rev-value-row { display: flex; align-items: baseline; }
.ov-rev-now { font-size: 26px; font-weight: 800; color: #0f172a; line-height: 1.1; letter-spacing: -0.02em; }
.ov-rev-unit { font-size: 12px; font-weight: 600; color: #64748b; margin-left: 3px; }
.ov-rev-sub { display: flex; align-items: center; justify-content: space-between; margin-top: 5px; gap: 6px; flex-wrap: wrap; }
.ov-rev-cum { font-size: 11px; color: #94a3b8; }
.ov-rev-yoy { display: inline-flex; align-items: center; gap: 4px; font-size: 11px; }
.ov-yoy-tag { color: #94a3b8; }
.ov-yoy-val { display: inline-flex; align-items: center; gap: 2px; }
.ov-yoy-arrow { display: inline-block; }

.ov-yoy-up { color: #dc2626; font-weight: 700; }
.ov-yoy-down { color: #16a34a; font-weight: 700; }
.ov-yoy-flat { color: #64748b; font-weight: 600; }

/* ===== 模块二 重点产品 ===== */
.ov-prod-panel { padding-bottom: 8px; }
.ov-prod-titlebar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  padding: 16px 18px 12px;
}
.ov-prod-search { position: relative; display: inline-flex; align-items: center; }
.ov-prod-search input {
  padding: 8px 30px 8px 14px; border: 1px solid #d1d5db; border-radius: 8px;
  font-size: 13px; color: #374151; width: 200px;
}
.ov-prod-search input:focus { outline: none; border-color: #93c5fd; box-shadow: 0 0 0 3px rgba(37,99,235,0.1); }
.ov-prod-search svg { position: absolute; right: 10px; color: #9ca3af; pointer-events: none; }
.ov-prod-empty { color: #94a3b8; font-size: 13px; padding: 12px 18px; }

/* ===== 异动预警图标（重点产品运营标题旁） ===== */
.ov-prod-titlebar-left { display: inline-flex; align-items: center; gap: 10px; }
.ov-alert-badge {
  position: relative;
  display: inline-flex; align-items: center; justify-content: center;
  width: 30px; height: 30px;
  border: 1px solid #fecaca; border-radius: 8px;
  background: #fef2f2; color: #dc2626;
  cursor: pointer; padding: 0;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
}
.ov-alert-badge:hover { border-color: #fca5a5; background: #fee2e2; box-shadow: 0 0 0 3px rgba(220,38,38,0.12); }
.ov-alert-count {
  position: absolute; top: -7px; right: -7px;
  min-width: 17px; height: 17px; padding: 0 4px;
  display: inline-flex; align-items: center; justify-content: center;
  background: #dc2626; color: #fff; font-size: 11px; font-weight: 700;
  border-radius: 999px; border: 2px solid #f8fafc; line-height: 1;
}

/* ===== 刚上架产品：无运营数据占位 ===== */
.ov-drill-empty-state {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 8px; text-align: center; padding: 34px 20px;
  border: 1px dashed #dbe4ef; border-radius: 8px; background: #fafcff;
}
.ov-empty-icon {
  width: 56px; height: 56px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: #93c5fd; background: #eef6ff; margin-bottom: 2px;
}
.ov-empty-title { font-size: 15px; font-weight: 700; color: #334155; }
.ov-empty-desc { font-size: 12.5px; line-height: 1.7; color: #64748b; max-width: 560px; }
.ov-empty-muted { color: #94a3b8; font-size: 12px; }
.ov-empty-sub { font-size: 11.5px; color: #94a3b8; margin-top: 6px; }

.ov-prod-tabs {
  display: flex;
  gap: 0;
  overflow-x: auto;
  padding: 0 16px;
}
.ov-prod-tab {
  padding: 10px 16px; font-size: 14px; color: #64748b; cursor: pointer;
  border-bottom: 3px solid transparent; background: none; white-space: nowrap;
}
.ov-prod-tab:hover { color: #475569; }
.ov-prod-tab.active { color: #2563eb; font-weight: 600; border-bottom-color: #2563eb; }

.ov-prod-body { padding: 8px 18px; }
.ov-prod-block-head { display: flex; align-items: baseline; gap: 8px; flex-wrap: wrap; margin: 10px 0 16px; }
.ov-prod-block-title { font-size: 17px; font-weight: 700; color: #1e293b; }
.ov-prod-block-sub { font-size: 11px; font-weight: 500; color: #94a3b8; }

/* ===== 区块 ===== */
.ov-sec { margin-bottom: 18px; padding-bottom: 16px; border-bottom: 1px dashed #e8edf4; }
.ov-sec-noborder { border-bottom: none; margin-bottom: 0; padding-bottom: 0; }
.ov-sec-title {
  font-size: 13px; font-weight: 700; color: #334155;
  margin-bottom: 12px; display: flex; align-items: center; gap: 8px;
}
.ov-sec-badge {
  width: 20px; height: 20px; border-radius: 6px; font-size: 11px;
  display: inline-flex; align-items: center; justify-content: center; color: #fff; font-weight: 700;
}
.ov-sec-ico { display: block; flex-shrink: 0; }
.ov-badge-blue { background: #2563eb; }
.ov-badge-amber { background: #f59e0b; }
.ov-badge-purple { background: #8b5cf6; }
.ov-badge-orange { background: #f97316; }

.ov-sec-grid { display: grid; gap: 12px; }
.ov-sec-grid-3 { grid-template-columns: repeat(3, 1fr); }
.ov-sec-grid-4 { grid-template-columns: repeat(4, 1fr); }

/* ===== KPI 卡（图标 + 数值，参考收入卡样式） ===== */
.ov-kpi {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px 14px;
  display: flex; align-items: flex-start; gap: 10px;
  transition: box-shadow 0.15s ease, border-color 0.15s ease;
}
.ov-kpi:hover { box-shadow: 0 2px 10px rgba(15,23,42,0.06); border-color: #c7d7f5; }
.ov-kpi-icon {
  width: 30px; height: 30px; border-radius: 8px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
}
.ov-ki-blue { background: #dbeafe; color: #2563eb; }
.ov-ki-green { background: #d1fae5; color: #059669; }
.ov-ki-purple { background: #ede9fe; color: #7c3aed; }
.ov-kpi-txt { flex: 1; min-width: 0; }
.ov-kpi-label { font-size: 11.5px; color: #64748b; }
.ov-kpi-unit { font-size: 10px; color: #94a3b8; margin-left: 2px; }
.ov-kpi-value { font-size: 21px; font-weight: 800; color: #0f172a; line-height: 1.2; margin: 2px 0; letter-spacing: -0.01em; }
.ov-kpi-value-sm { font-size: 16px; }
.ov-kpi-yoy { font-size: 11px; display: flex; align-items: center; gap: 5px; color: #94a3b8; }
.ov-kpi-note { font-size: 10.5px; color: #94a3b8; margin-top: 2px; }

/* ===== 结构 ===== */
.ov-sec-struct { align-items: start; }
.ov-struct-extra { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 22px; }
.ov-struct-extra-full { grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); }
.ov-struct-group { background: #fff; border: 1px solid #eef2f7; border-radius: 8px; padding: 10px 12px; }
.ov-struct-group-label { font-size: 11.5px; font-weight: 700; color: #334155; margin-bottom: 8px; }
.ov-struct-rows { display: flex; flex-direction: column; gap: 6px; }
.ov-struct-row, .ov-channel-row { display: flex; align-items: center; gap: 8px; font-size: 11px; color: #64748b; }
.ov-struct-name { width: 78px; flex-shrink: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ov-struct-bar { flex: 1; height: 8px; background: #eef2f7; border-radius: 4px; overflow: hidden; }
.ov-struct-bar-fill {
  height: 100%; border-radius: 4px;
  background: linear-gradient(90deg, #2563eb, #0ea5e9);
}
.ov-bar-orange { background: linear-gradient(90deg, #f59e0b, #f97316); }
.ov-struct-row-val { width: 56px; text-align: right; font-weight: 700; color: #0f172a; flex-shrink: 0; }

/* ===== 收入 & 渠道 ===== */
.ov-sec-income { grid-template-columns: repeat(3, 1fr); }
.ov-income-block {
  background: #fff; border: 1px solid #e5e7eb; border-radius: 8px; padding: 12px 14px;
}
.ov-income-cap { font-size: 12px; font-weight: 700; margin-bottom: 8px; }
.ov-cap-blue { color: #2563eb; }
.ov-cap-green { color: #059669; }
.ov-cap-amber { color: #d97706; }
.ov-cap-orange { color: #ea580c; }
.ov-income-row { display: flex; align-items: center; justify-content: space-between; font-size: 11.5px; color: #64748b; padding: 4px 0; border-bottom: 1px dashed #f1f5f9; }
.ov-income-row:last-child { border-bottom: none; }
.ov-income-row b { color: #0f172a; font-weight: 700; font-size: 12.5px; }
.ov-income-row b.ov-yoy-up { color: #dc2626; }
.ov-income-row b.ov-yoy-down { color: #16a34a; }
.ov-channel-rows { display: flex; flex-direction: column; gap: 8px; }

/* ===== 单产品下钻（5G新通话 → 代表性套餐，可折叠） ===== */
.ov-drill-wrap { border: 1px solid #eef2f7; border-radius: 8px; overflow: hidden; margin-top: 16px; }
.ov-drill-toggle {
  width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 12px;
  background: #f8fafc; border: none; cursor: pointer; padding: 12px 14px; text-align: left;
  transition: background 0.15s ease;
}
.ov-drill-toggle:hover { background: #f1f5f9; }
.ov-drill-toggle-left { display: flex; align-items: center; gap: 8px; min-width: 0; flex-wrap: wrap; }
.ov-drill-toggle-icon { color: #2563eb; flex-shrink: 0; }
.ov-drill-toggle-title { font-size: 13.5px; font-weight: 700; color: #1e293b; }
.ov-drill-toggle-sub { font-size: 11px; color: #94a3b8; }
.ov-drill-toggle-arrow { color: #64748b; transition: transform 0.2s ease; flex-shrink: 0; }
.ov-drill-toggle-arrow.open { transform: rotate(180deg); }

.ov-drill-body { padding: 14px; border-top: 1px dashed #e8edf4; }

.ov-drill-list {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 12px;
}
.ov-drill-fam {
  text-align: left; background: #fff; border: 1px solid #e5e7eb; border-radius: 8px;
  padding: 12px 14px; cursor: pointer; transition: box-shadow 0.15s ease, border-color 0.15s ease;
}
.ov-drill-fam:hover { box-shadow: 0 3px 12px rgba(15,23,42,0.08); border-color: #c7d7f5; }
.ov-drill-fam-highlight,
.ov-drill-fam.ov-drill-fam-highlight {
  border-color: #f59e0b !important;
  box-shadow: 0 0 0 3px rgba(245,158,11,0.28), 0 4px 14px rgba(251,191,36,0.25) !important;
  background: #fffdf5;
}
.ov-drill-fam-top { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.ov-drill-fam-name { font-size: 12.5px; font-weight: 700; color: #1e293b; line-height: 1.35; }
.ov-drill-fam-health {
  width: 30px; height: 30px; border-radius: 50%; display: inline-flex;
  align-items: center; justify-content: center; font-weight: 800; font-size: 13px; color: #fff;
  flex-shrink: 0;
}
.ov-drill-fam-meta { display: flex; align-items: center; gap: 8px; margin: 8px 0 6px; }
.ov-drill-fam-status { font-size: 11px; font-weight: 700; padding: 1px 7px; border-radius: 10px; }
.ov-drill-fam-count { font-size: 11px; color: #94a3b8; }
.ov-drill-fam-scale { font-size: 11.5px; color: #64748b; }

.ov-drill-health-ok { background: #10b981; color: #fff; }
.ov-drill-health-mid { background: #f59e0b; color: #fff; }
.ov-drill-health-warn { background: #ef4444; color: #fff; }
.ov-drill-health-ok.ov-drill-fam-status { background: #ecfdf5; color: #059669; }
.ov-drill-health-mid.ov-drill-fam-status { background: #fef3c7; color: #b45309; }
.ov-drill-health-warn.ov-drill-fam-status { background: #fee2e2; color: #dc2626; }

/* 刚上架套餐：数据积累中（新）标签 */
.ov-drill-fam-new { background: #3b82f6; color: #fff; }
.ov-drill-fam-new-status { background: #eff6ff; color: #2563eb; }

.ov-drill-back {
  display: inline-flex; align-items: center; gap: 4px;
  border: 1px solid #d1d5db; background: #fff; color: #475569;
  border-radius: 6px; padding: 4px 10px; font-size: 12px; cursor: pointer;
  transition: all 0.15s;
}
.ov-drill-back:hover { border-color: #93c5fd; color: #2563eb; }

.ov-drill-prodbar {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  padding-bottom: 12px; margin-bottom: 12px; border-bottom: 1px dashed #e8edf4;
}
.ov-drill-prod-title { font-size: 15px; font-weight: 700; color: #1e293b; }
.ov-drill-prod-sub { font-size: 11px; font-weight: 500; color: #94a3b8; }

.ov-drill-health-card {
  display: flex; gap: 18px; align-items: center; flex-wrap: wrap;
  background: #fff; border: 1px solid #e5e7eb; border-radius: 8px; padding: 16px;
}
.ov-drill-health-gauge { flex-shrink: 0; }
.ov-drill-gauge-ring {
  width: 92px; height: 92px; border-radius: 50%; display: flex;
  flex-direction: column; align-items: center; justify-content: center; color: #fff;
}
.ov-drill-gauge-num { font-size: 30px; font-weight: 800; line-height: 1; }
.ov-drill-gauge-unit { font-size: 11px; opacity: 0.9; margin-top: 2px; }
.ov-drill-health-info { flex: 1; min-width: 240px; }
.ov-drill-health-level {
  display: inline-flex; align-items: center; width: fit-content;
  font-size: 13px; font-weight: 800; color: #fff;
  padding: 4px 12px; border-radius: 6px; margin-bottom: 8px;
}
.ov-drill-health-level.ov-drill-health-ok { background: #10b981; }
.ov-drill-health-level.ov-drill-health-mid { background: #f59e0b; }
.ov-drill-health-level.ov-drill-health-warn { background: #ef4444; }
.ov-drill-health-level-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 8px; }
.ov-drill-health-level-row .ov-drill-health-level { margin-bottom: 0; }
.ov-drill-rootcause {
  margin-left: auto;
  display: inline-flex; align-items: center; gap: 5px;
  font-size: 12px; font-weight: 600; color: #fff;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border: none; border-radius: 6px; padding: 5px 12px;
  cursor: pointer; transition: opacity 0.15s, transform 0.15s;
}
.ov-drill-rootcause:hover { opacity: 0.9; transform: translateY(-1px); }
.ov-drill-rootcause:active { transform: translateY(0); }
.ov-drill-health-note { font-size: 12px; color: #64748b; margin-bottom: 12px; }
.ov-drill-health-metrics { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.ov-kpi-delta { font-size: 10px; font-weight: 700; margin-left: 4px; }
.ov-kpi-delta.ov-up { color: #dc2626; }
.ov-kpi-delta.ov-down { color: #16a34a; }
.ov-kpi-delta.ov-flat { color: #94a3b8; }

/* 健康度总览：四大维度 + 二级指标（后评估指标） */
.ov-drill-dims {
  display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-top: 14px;
}
.ov-drill-dim {
  background: #f8fafc; border: 1px solid #eef2f7; border-radius: 8px; padding: 12px 14px;
}
.ov-drill-dim-head {
  display: flex; align-items: baseline; gap: 8px; margin-bottom: 8px;
}
.ov-drill-dim-name { font-size: 13px; font-weight: 800; color: #1e293b; }
.ov-drill-dim-weight { font-size: 10.5px; color: #94a3b8; margin-left: 4px; }
.ov-drill-dim-score { margin-left: auto; font-size: 15px; font-weight: 800; color: #0f172a; }
.ov-drill-dim-score i { font-style: normal; font-size: 11px; font-weight: 600; color: #94a3b8; margin-left: 1px; }
.ov-drill-dim-bar { height: 7px; background: #eef2f7; border-radius: 4px; overflow: hidden; }
.ov-drill-dim-fill { height: 100%; border-radius: 4px; }
.ov-dim-eff .ov-drill-dim-fill { background: linear-gradient(90deg, #8b5cf6, #7c3aed); }
.ov-dim-market .ov-drill-dim-fill { background: linear-gradient(90deg, #3b82f6, #2563eb); }
.ov-dim-quality .ov-drill-dim-fill { background: linear-gradient(90deg, #f59e0b, #f97316); }
.ov-dim-life .ov-drill-dim-fill { background: linear-gradient(90deg, #10b981, #059669); }
.ov-drill-dim-items {
  display: flex; flex-direction: column; gap: 7px; margin-top: 10px;
}
.ov-drill-dim-item {
  display: flex; align-items: center; gap: 8px;
  font-size: 12px; color: #334155;
}
.ov-drill-dim-item-name { width: 116px; flex-shrink: 0; }
.ov-drill-dim-item-value { margin-left: auto; font-size: 12px; font-weight: 700; color: #0f172a; }
.ov-drill-dim-item b {
  min-width: 26px; height: 20px; padding: 0 6px; border-radius: 10px;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 11.5px; font-weight: 800; color: #fff;
}
.ov-item-ok { background: #10b981; }
.ov-item-mid { background: #f59e0b; }
.ov-item-warn { background: #ef4444; }
@media (max-width: 620px) {
  .ov-drill-dims { grid-template-columns: 1fr; }
}

.ov-drill-alerts { display: flex; flex-direction: column; gap: 8px; }
.ov-drill-alert {
  display: flex; align-items: flex-start; gap: 10px;
  border: 1px solid; border-radius: 8px; padding: 10px 12px; font-size: 12.5px;
}
.ov-alert-high { background: #fef2f2; border-color: #fecaca; color: #991b1b; }
.ov-alert-mid { background: #fffbeb; border-color: #fde68a; color: #92400e; }
.ov-alert-low { background: #f0fdf4; border-color: #bbf7d0; color: #166534; }
.ov-drill-alert-tag {
  flex-shrink: 0; width: 20px; height: 20px; border-radius: 50%; font-size: 11px; font-weight: 800;
  display: inline-flex; align-items: center; justify-content: center; color: #fff; background: #9ca3af;
}
.ov-alert-high .ov-drill-alert-tag { background: #ef4444; }
.ov-alert-mid .ov-drill-alert-tag { background: #f59e0b; }
.ov-alert-low .ov-drill-alert-tag { background: #10b981; }
.ov-drill-alert-text { line-height: 1.6; }

@media (max-width: 860px) {
  .ov-rev-row { grid-template-columns: 1fr; }
  .ov-sec-grid-3, .ov-sec-grid-4, .ov-sec-income { grid-template-columns: 1fr 1fr; }
  .ov-struct-extra-full { grid-template-columns: 1fr 1fr; }
  .ov-drill-health-metrics { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 480px) {
  .ov-sec-grid-3, .ov-sec-grid-4, .ov-sec-income { grid-template-columns: 1fr; }
}
</style>
