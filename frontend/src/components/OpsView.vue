<template>
  <div class="ops-view">
    <!-- ============ 产品运营（原有视图） ============ -->
    <div v-if="opsViewTab === 'product'">
      <!-- ① 收入总览 -->
      <div class="ov-sec">
        <div class="ov-panel-title-bar">
          <h3 class="ov-title">收入总览</h3>
          <span class="ov-title-note">单位：万元 ｜ 数据口径：全省 · 2026年7月</span>
        </div>
        <div class="ov-rev-row">
          <div v-for="card in revenueCards" :key="card.key" class="ov-rev-card">
            <div class="ov-rev-card-head">
              <div class="ov-rev-icon" :class="card.iconCls">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" />
                </svg>
              </div>
              <div class="ov-rev-head-title">{{ card.title }}</div>
            </div>
            <div class="ov-rev-body">
              <div>
                <div class="ov-rev-label">报表收入</div>
                <div class="ov-rev-value-row">
                  <span>{{ fmt2(card.report.now) }}</span>
                  <span class="ov-rev-unit">万元</span>
                </div>
                <div class="ov-rev-sub">
                  <span class="ov-rev-cum">当年累计 {{ fmt2(card.report.cum) }}万</span>
                  <span class="ov-rev-yoy">
                    <span class="ov-yoy-tag">累计同比</span>
                    <span class="ov-yoy-val" :class="yoyCls(card.report.yoy)">
                      {{ fmtPct(card.report.yoy) }}
                      <svg class="ov-yoy-arrow" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4">
                        <polyline v-if="card.report.yoy >= 0" points="18 15 12 9 6 15" />
                        <polyline v-else points="6 9 12 15 18 9" />
                      </svg>
                    </span>
                  </span>
                </div>
              </div>
              <div>
                <div class="ov-rev-label">出账收入(税后)</div>
                <div class="ov-rev-value-row">
                  <span>{{ fmt2(card.bill.now) }}</span>
                  <span class="ov-rev-unit">万元</span>
                </div>
                <div class="ov-rev-sub">
                  <span class="ov-rev-cum">当年累计 {{ fmt2(card.bill.cum) }}万</span>
                  <span class="ov-rev-yoy">
                    <span class="ov-yoy-tag">累计同比</span>
                    <span class="ov-yoy-val" :class="yoyCls(card.bill.yoy)">
                      {{ fmtPct(card.bill.yoy) }}
                      <svg class="ov-yoy-arrow" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4">
                        <polyline v-if="card.bill.yoy >= 0" points="18 15 12 9 6 15" />
                        <polyline v-else points="6 9 12 15 18 9" />
                      </svg>
                    </span>
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 重点产品运营 -->
      <div class="ov-sec">
        <div class="ov-prod-titlebar">
          <div class="ov-prod-titlebar-left">
            <h3 class="ov-title">重点产品运营</h3>
            <button
              v-if="abnormalProducts.count > 0"
              type="button"
              class="ov-alert-badge"
              :title="`${abnormalProducts.count} 个产品处于异动状态，点击定位到 5G新通话 下的 WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)数据卡`"
              @click="locateAbnormal"
            >
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
                <line x1="12" y1="9" x2="12" y2="13" />
                <line x1="12" y1="17" x2="12.01" y2="17" />
              </svg>
              <span>{{ abnormalProducts.count }}</span>
            </button>
          </div>
          <div class="ov-prod-search">
            <input v-model="productKeyword" type="text" placeholder="搜索产品名称" autocomplete="off" />
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
          </div>
        </div>
        <div class="ov-prod-tabs">
          <button
            v-for="prod in filteredProducts"
            :key="prod.key"
            type="button"
            class="ov-prod-tab"
            :class="{ active: activeProductKey === prod.key }"
            @click="activeProductKey = prod.key"
          >
            {{ prod.name }}
          </button>
          <div v-if="!filteredProducts.length" class="ov-prod-empty">未找到相关产品</div>
        </div>
        <div v-if="activeProductKey" class="ov-prod-body">
          <div v-for="prod in activeProductList" :key="prod.key" class="ov-prod-block">
            <div class="ov-prod-block-head">
              <span class="ov-prod-block-title">{{ prod.name }}</span>
              <span class="ov-prod-block-sub">全省运营数据 · 2026年7月通报（全省行口径）</span>
            </div>

            <!-- ① 规模 & 活跃 -->
            <div class="ov-sec">
              <div class="ov-sec-title">
                <span class="ov-sec-badge ov-badge-blue">①</span>规模 &amp; 活跃
              </div>
              <div class="ov-sec-grid ov-sec-grid-3">
                <div v-for="item in prod.scale" :key="item.label" class="ov-kpi">
                  <div class="ov-kpi-icon" :class="'ov-ki-' + (['到达', '活跃率'].some((k) => item.label.includes(k)) ? 'blue' : 'green')">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <rect x="3" y="3" width="18" height="18" rx="2" />
                      <path d="M9 8h6M9 12h6M9 16h4" />
                    </svg>
                  </div>
                  <div class="ov-kpi-txt">
                    <div class="ov-kpi-label">
                      {{ item.label }}<span v-if="item.unitIn" class="ov-kpi-unit">{{ item.unitIn }}</span>
                    </div>
                    <div class="ov-kpi-value">{{ item.value }}</div>
                    <div v-if="item.yoy != null" class="ov-kpi-yoy">
                      <span>同比</span>
                      <span :class="yoyCls(item.yoy)">{{ fmtPct(item.yoy) }}</span>
                    </div>
                    <div v-else-if="item.note" class="ov-kpi-note">{{ item.note }}</div>
                  </div>
                </div>
              </div>
            </div>

            <!-- ② 结构占比 -->
            <div class="ov-sec">
              <div class="ov-sec-title">
                <span class="ov-sec-badge ov-badge-amber">②</span>结构占比
              </div>
              <div class="ov-sec-struct">
                <div class="ov-struct-extra ov-struct-extra-full">
                  <div v-for="group in prod.struct.groups" :key="group.label" class="ov-struct-group">
                    <div class="ov-struct-group-label">{{ group.label }}</div>
                    <div class="ov-struct-rows">
                      <div v-for="row in group.rows" :key="row.label" class="ov-struct-row">
                        <span class="ov-struct-name">{{ row.label }}</span>
                        <div class="ov-struct-bar">
                          <div class="ov-struct-bar-fill" :style="{ width: barPct(row.value) }"></div>
                        </div>
                        <span class="ov-struct-row-val">{{ fmtRatio(row.value) }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- ③ 新增 -->
            <div class="ov-sec">
              <div class="ov-sec-title">
                <span class="ov-sec-badge ov-badge-blue">③</span>新增
              </div>
              <div class="ov-sec-grid ov-sec-grid-3">
                <div v-for="item in prod.added" :key="item.label" class="ov-kpi">
                  <div class="ov-kpi-icon ov-ki-green">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <line x1="12" y1="5" x2="12" y2="19" />
                      <polyline points="19 12 12 19 5 12" />
                    </svg>
                  </div>
                  <div class="ov-kpi-txt">
                    <div class="ov-kpi-label">
                      {{ item.label }}<span v-if="item.unitIn" class="ov-kpi-unit">{{ item.unitIn }}</span>
                    </div>
                    <div class="ov-kpi-value">{{ item.value }}</div>
                    <div v-if="item.yoy != null" class="ov-kpi-yoy">
                      <span>同比</span>
                      <span :class="yoyCls(item.yoy)">{{ fmtPct(item.yoy) }}</span>
                    </div>
                    <div v-else-if="item.warn" class="ov-kpi-note">{{ item.warn }}</div>
                  </div>
                </div>
              </div>
            </div>

            <!-- ④ 新增质量 & 效益 -->
            <div class="ov-sec">
              <div class="ov-sec-title">
                <span class="ov-sec-badge ov-badge-purple">④</span>新增质量 &amp; 效益
              </div>
              <div class="ov-sec-grid ov-sec-grid-4">
                <div v-for="item in prod.quality" :key="item.label" class="ov-kpi ov-kpi-sm">
                  <div class="ov-kpi-icon ov-ki-purple">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M12 2l2.5 6.5L21 9l-5 4 1.5 7L12 16.5 6.5 20 8 13l-5-4 6.5-.5z" />
                    </svg>
                  </div>
                  <div class="ov-kpi-txt">
                    <div class="ov-kpi-label">
                      {{ item.label }}<span v-if="item.unitIn" class="ov-kpi-unit">{{ item.unitIn }}</span>
                    </div>
                    <div class="ov-kpi-value ov-kpi-value-sm">{{ item.value }}</div>
                    <div v-if="item.note" class="ov-kpi-note">{{ item.note }}</div>
                  </div>
                </div>
              </div>
            </div>

            <!-- ⑤ 收入 & 渠道 -->
            <div class="ov-sec ov-sec-noborder">
              <div class="ov-sec-title">
                <span class="ov-sec-badge ov-badge-orange">⑤</span>收入 &amp; 渠道
              </div>
              <div class="ov-sec-grid ov-sec-income">
                <div class="ov-income-block">
                  <div class="ov-income-cap ov-cap-blue">报表收入</div>
                  <div class="ov-income-row">
                    <span>当月</span><b>{{ fmt2(prod.income.report.now) }}万</b>
                  </div>
                  <div class="ov-income-row">
                    <span>当年累计</span><b>{{ fmt2(prod.income.report.cum) }}万</b>
                  </div>
                  <div class="ov-income-row">
                    <span>累计同比</span><b :class="yoyCls(prod.income.report.yoy)">{{ fmtPct(prod.income.report.yoy) }}</b>
                  </div>
                </div>
                <div class="ov-income-block">
                  <div class="ov-income-cap ov-cap-green">出账收入(税后)</div>
                  <div class="ov-income-row">
                    <span>当月</span><b>{{ fmt2(prod.income.bill.now) }}万</b>
                  </div>
                  <div class="ov-income-row">
                    <span>当年累计</span><b>{{ fmt2(prod.income.bill.cum) }}万</b>
                  </div>
                  <div class="ov-income-row">
                    <span>累计同比</span><b :class="yoyCls(prod.income.bill.yoy)">{{ fmtPct(prod.income.bill.yoy) }}</b>
                  </div>
                </div>
                <div v-if="prod.income.extra" class="ov-income-block">
                  <div class="ov-income-cap ov-cap-amber">{{ prod.income.extra.name }}</div>
                  <div class="ov-income-row">
                    <span>当月</span><b>{{ fmt2(prod.income.extra.now) }}万</b>
                  </div>
                  <div class="ov-income-row">
                    <span>当年累计</span><b>{{ fmt2(prod.income.extra.cum) }}万</b>
                  </div>
                  <div class="ov-income-row">
                    <span>累计同比</span><b :class="yoyCls(prod.income.extra.yoy)">{{ fmtPct(prod.income.extra.yoy) }}</b>
                  </div>
                </div>
                <div class="ov-income-block ov-channel">
                  <div class="ov-income-cap ov-cap-orange">渠道结构</div>
                  <div class="ov-channel-rows">
                    <div v-for="ch in prod.channel" :key="ch.label" class="ov-channel-row">
                      <span class="ov-struct-name">{{ ch.label }}</span>
                      <div class="ov-struct-bar">
                        <div class="ov-struct-bar-fill ov-bar-orange" :style="{ width: barPct(ch.value) }"></div>
                      </div>
                      <span class="ov-struct-row-val">{{ fmtRatio(ch.value) }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 下钻：5G新通话 -->
            <div v-if="isDrillable(prod.key)" ref="drillWrap" class="ov-drill-wrap">
              <button type="button" class="ov-drill-toggle" @click="drillOpen = !drillOpen">
                <span class="ov-drill-toggle-left">
                  <svg class="ov-drill-toggle-icon" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M12 5v14M5 12h14" />
                  </svg>
                  <span class="ov-drill-toggle-title">下钻至产品</span>
                  <span class="ov-drill-toggle-sub">从业务「5G新通话」下钻查看代表性套餐的运营情况</span>
                </span>
                <svg class="ov-drill-toggle-arrow" :class="{ open: drillOpen }" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="6 9 12 15 18 9" />
                </svg>
              </button>
              <div v-show="drillOpen" class="ov-drill-body">
                <!-- 套餐详情 -->
                <template v-if="activeDrillItem">
                  <div class="ov-drill-prodbar">
                    <button type="button" class="ov-drill-back" @click="activeDrill = null">
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                        <polyline points="15 18 9 12 15 6" />
                      </svg>
                      返回套餐列表
                    </button>
                    <span class="ov-drill-prod-title">{{ activeDrillItem.name }}</span>
                    <span class="ov-drill-prod-sub">5G新通话 · 2026年7月</span>
                    <button
                      v-if="!activeDrillItem.launched && drillHealthLevel === '亚健康'"
                      type="button"
                      class="ov-drill-rootcause"
                      title="进入对话场景，对该套餐进行根因分析"
                      @click="$emit('open-rootcause', activeDrill)"
                    >
                      <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                        <circle cx="11" cy="11" r="7" />
                        <line x1="21" y1="21" x2="16.65" y2="16.65" />
                        <line x1="11" y1="8" x2="11" y2="14" />
                        <line x1="8" y1="11" x2="14" y2="11" />
                      </svg>
                      根因分析
                    </button>
                  </div>

                  <!-- 新上架空态 -->
                  <div v-if="activeDrillItem.launched" class="ov-drill-empty-state">
                    <div class="ov-empty-icon">
                      <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M3 3v18h18" />
                        <path d="M7 14l4-4 4 2 5-6" />
                        <path d="M12 20V10" />
                      </svg>
                    </div>
                    <div class="ov-empty-title">运营数据积累中</div>
                    <div class="ov-empty-desc">该套餐刚完成上架销售，当前暂无运营数据。待次日运营通报生成健康度后，即可在此查看四大维度与异动预警，并进行根因分析。</div>
                    <div class="ov-empty-sub">上架时间：{{ activeDrillItem.launchedAt }}</div>
                  </div>

                  <template v-else>
                    <!-- 健康度总览 -->
                    <div class="ov-sec">
                      <div class="ov-sec-title">
                        <span class="ov-sec-badge ov-badge-purple">
                          <svg class="ov-sec-ico" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
                            <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
                          </svg>
                        </span>健康度总览
                      </div>
                      <div class="ov-drill-health-card">
                        <div class="ov-drill-health-gauge">
                          <div class="ov-drill-gauge-ring" :class="healthCls(drillHealthLevel)">
                            <span class="ov-drill-gauge-num">{{ drillHealthScore }}</span>
                            <span class="ov-drill-gauge-unit">分</span>
                          </div>
                        </div>
                        <div class="ov-drill-health-info">
                          <div class="ov-drill-health-level" :class="healthCls(drillHealthLevel)">
                            {{ drillHealthLevel }} · 较上月 {{ activeDrillItem.healthDelta }}分
                          </div>
                          <div class="ov-drill-health-note">{{ activeDrillItem.healthNote }}</div>
                          <div class="ov-drill-health-metrics">
                            <div class="ov-kpi ov-kpi-sm">
                              <div class="ov-kpi-icon ov-ki-blue">
                                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                  <rect x="3" y="3" width="18" height="18" rx="2" />
                                  <path d="M9 8h6M9 12h6M9 16h4" />
                                </svg>
                              </div>
                              <div class="ov-kpi-txt">
                                <div class="ov-kpi-label">{{ activeDrillItem.scale.label }}</div>
                                <div class="ov-kpi-value ov-kpi-value-sm">{{ activeDrillItem.scale.value }}</div>
                              </div>
                            </div>
                            <div class="ov-kpi ov-kpi-sm">
                              <div class="ov-kpi-icon ov-ki-green">
                                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                  <line x1="12" y1="5" x2="12" y2="19" />
                                  <polyline points="19 12 12 19 5 12" />
                                </svg>
                              </div>
                              <div class="ov-kpi-txt">
                                <div class="ov-kpi-label">
                                  {{ activeDrillItem.added.label }}
                                  <span class="ov-kpi-delta" :class="deltaCls(activeDrillItem.added.delta)">{{ activeDrillItem.added.delta }}</span>
                                </div>
                                <div class="ov-kpi-value ov-kpi-value-sm">{{ activeDrillItem.added.value }}</div>
                              </div>
                            </div>
                            <div class="ov-kpi ov-kpi-sm">
                              <div class="ov-kpi-icon ov-ki-amber">
                                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                  <path d="M12 2l2.5 6.5L21 9l-5 4 1.5 7L12 16.5 6.5 20 8 13l-5-4 6.5-.5z" />
                                </svg>
                              </div>
                              <div class="ov-kpi-txt">
                                <div class="ov-kpi-label">
                                  {{ activeDrillItem.income.label }}
                                  <span class="ov-kpi-delta" :class="deltaCls(activeDrillItem.income.delta)">{{ activeDrillItem.income.delta }}</span>
                                </div>
                                <div class="ov-kpi-value ov-kpi-value-sm">{{ activeDrillItem.income.value }}</div>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                      <div class="ov-drill-dims">
                        <div v-for="dim in drillDims" :key="dim.key" class="ov-drill-dim" :class="'ov-dim-' + dim.key">
                          <div class="ov-drill-dim-head">
                            <span class="ov-drill-dim-name">{{ dim.name }}</span>
                            <span class="ov-drill-dim-weight">权重 {{ dim.weight }} · 满分{{ dim.full }}分</span>
                            <span class="ov-drill-dim-score">{{ dim.score }}<i>分</i></span>
                          </div>
                          <div class="ov-drill-dim-bar">
                            <div class="ov-drill-dim-fill" :style="{ width: dim.pct + '%' }"></div>
                          </div>
                          <div class="ov-drill-dim-items">
                            <div v-for="item in dim.items" :key="item.name" class="ov-drill-dim-item">
                              <span class="ov-drill-dim-item-name">{{ item.name }}</span>
                              <span class="ov-drill-dim-item-value">{{ item.value }}</span>
                              <b :class="item.score >= 8 ? 'ov-item-ok' : item.score >= 6 ? 'ov-item-mid' : 'ov-item-warn'">{{ item.score }}</b>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>

                    <!-- 异动预警 -->
                    <div class="ov-sec">
                      <div class="ov-sec-title">
                        <span class="ov-sec-badge ov-badge-orange">
                          <svg class="ov-sec-ico" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
                            <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
                            <line x1="12" y1="9" x2="12" y2="13" />
                            <line x1="12" y1="17" x2="12.01" y2="17" />
                          </svg>
                        </span>异动预警
                      </div>
                      <div class="ov-drill-alerts">
                        <div
                          v-for="(alert, i) in activeDrillItem.anomalies"
                          :key="i"
                          class="ov-drill-alert"
                          :class="'ov-alert-' + alert.level"
                        >
                          <span class="ov-drill-alert-tag">{{ alert.level === 'high' ? '高' : alert.level === 'mid' ? '中' : '低' }}</span>
                          <span class="ov-drill-alert-text">{{ alert.text }}</span>
                        </div>
                        <div v-if="!activeDrillItem.anomalies.length" class="ov-drill-alert ov-alert-low">
                          <span class="ov-drill-alert-tag">无</span>
                          <span class="ov-drill-alert-text">当前无显著异动</span>
                        </div>
                      </div>
                    </div>

                    <!-- 关键运营指标 -->
                    <div class="ov-sec ov-sec-noborder">
                      <div class="ov-sec-title">
                        <span class="ov-sec-badge ov-badge-blue">
                          <svg class="ov-sec-ico" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
                            <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
                          </svg>
                        </span>关键运营指标
                      </div>
                      <div class="ov-sec-grid ov-sec-grid-3">
                        <div v-for="item in activeDrillItem.quality" :key="item.label" class="ov-kpi">
                          <div class="ov-kpi-icon ov-ki-purple">
                            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                              <path d="M12 2l2.5 6.5L21 9l-5 4 1.5 7L12 16.5 6.5 20 8 13l-5-4 6.5-.5z" />
                            </svg>
                          </div>
                          <div class="ov-kpi-txt">
                            <div class="ov-kpi-label">
                              {{ item.label }}
                              <span class="ov-kpi-delta" :class="deltaCls(item.delta)">{{ item.delta }}</span>
                            </div>
                            <div class="ov-kpi-value ov-kpi-value-sm">{{ item.value }}</div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </template>
                </template>

                <!-- 套餐列表 -->
                <div v-else class="ov-drill-list">
                  <button
                    v-for="key in drillProductKeys"
                    :key="key"
                    type="button"
                    class="ov-drill-fam"
                    :class="{ 'ov-drill-fam-highlight': highlightKey === key }"
                    @click="activeDrill = key"
                  >
                    <div class="ov-drill-fam-top">
                      <span class="ov-drill-fam-name">{{ drillProductMap[key].name }}</span>
                      <span v-if="drillProductMap[key].launched" class="ov-drill-fam-health ov-drill-fam-new">新</span>
                      <span v-else class="ov-drill-fam-health" :class="healthCls(productHealthLevel(key))">{{ productHealthScore(key) }}</span>
                    </div>
                    <div class="ov-drill-fam-meta">
                      <span v-if="drillProductMap[key].launched" class="ov-drill-fam-status ov-drill-fam-new-status">新上架 · 数据积累中</span>
                      <span v-else class="ov-drill-fam-status" :class="healthCls(productHealthLevel(key))">{{ productHealthLevel(key) }}</span>
                      <span class="ov-drill-fam-count">{{ drillProductMap[key].scale.label }} {{ drillProductMap[key].scale.value }}</span>
                    </div>
                    <div class="ov-drill-fam-scale">
                      新增 {{ drillProductMap[key].added.value }}
                      <template v-if="drillProductMap[key].added.delta"> · {{ drillProductMap[key].added.delta }}</template>
                    </div>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ============ 流量运营（智能运营视图） ============ -->
    <div v-else>
      <!-- 驾驶舱 -->
      <div class="ov-panel ov-tf-dashboard">
        <div class="ov-panel-title-bar">
          <h3 class="ov-title">{{ tfPersp === 'platform' ? '流量运营全景总览' : '代理商运营全景总览' }}</h3>
          <span class="ov-title-note">数据口径：{{ tfOverview.period }} ｜ 单位 GB ｜ 按实际流量用量结算</span>
        </div>
        <div class="ov-tf-dash-cards">
          <div v-if="tfPersp === 'platform'" class="ov-tf-dc">
            <div class="ov-tf-dc-label">流量池总量</div>
            <div class="ov-tf-dc-value">{{ fmt2(tfOverview.totalBought) }}<span>GB</span></div>
            <div class="ov-tf-dc-sub"><i class="ov-tf-arrow">{{ trendArrow(tfOverview.boughtMoM) }}</i>同比 {{ tfOverview.boughtYoY }} · 环比 {{ tfOverview.boughtMoM }}</div>
          </div>
          <div v-else class="ov-tf-dc">
            <div class="ov-tf-dc-label">个人用户总数</div>
            <div class="ov-tf-dc-value">{{ fmt2(tfOverview.totalUsers) }}<span>人</span></div>
            <div class="ov-tf-dc-sub">活跃 {{ agentDetail.activeUserCount }} 人</div>
          </div>
          <div class="ov-tf-dc">
            <div class="ov-tf-dc-label">已使用流量</div>
            <div class="ov-tf-dc-value">{{ fmt2(tfOverview.totalUsed) }}<span>GB</span></div>
            <div class="ov-tf-dc-sub"><i class="ov-tf-arrow">{{ trendArrow(tfOverview.usedMoM) }}</i>同比 {{ tfOverview.usedYoY }} · 环比 {{ tfOverview.usedMoM }}</div>
          </div>
          <div class="ov-tf-dc ov-tf-dc-highlight">
            <div class="ov-tf-dc-label">整体使用率</div>
            <div class="ov-tf-dc-value">{{ tfOverview.overallUsageRate }}<span>%</span></div>
            <div class="ov-tf-dc-sub ov-tf-dc-sub-note">{{ riskLevel(tfOverview.overallUsageRate).text }}</div>
            <div class="ov-tf-dc-sub"><i class="ov-tf-arrow">{{ trendArrow(tfUsageRateMoM) }}</i>同比 {{ tfUsageRateYoY }} · 环比 {{ tfUsageRateMoM }}</div>
          </div>
          <div class="ov-tf-dc">
            <div class="ov-tf-dc-label">剩余流量</div>
            <div class="ov-tf-dc-value">{{ fmt2(tfOverview.totalRemaining) }}<span>GB</span></div>
            <div class="ov-tf-dc-sub"><i class="ov-tf-arrow">{{ trendArrow(tfOverview.remainingMoM) }}</i>同比 {{ tfOverview.remainingYoY }} · 环比 {{ tfOverview.remainingMoM }}</div>
          </div>
          <div v-if="tfPersp === 'platform'" class="ov-tf-dc">
            <div class="ov-tf-dc-label">在售代理商</div>
            <div class="ov-tf-dc-value">{{ tfOverview.activeAgents }}<span>家</span></div>
            <div class="ov-tf-dc-sub">覆盖 {{ tfOverview.activeRegions }} 地市</div>
          </div>
          <div v-if="tfPersp === 'platform'" class="ov-tf-dc">
            <div class="ov-tf-dc-label">活跃用户</div>
            <div class="ov-tf-dc-value">{{ tfOverview.activeUsers }}<span>万</span></div>
            <div class="ov-tf-dc-sub">终端用户持续增长</div>
          </div>
          <div v-else class="ov-tf-dc">
            <div class="ov-tf-dc-label">在用套餐</div>
            <div class="ov-tf-dc-value">{{ tfOverview.packageCount }}<span>款</span></div>
            <div class="ov-tf-dc-sub">个人用户在用套餐结构</div>
          </div>
        </div>
        <div class="ov-tf-dash-alert">
          <span class="ov-tf-dash-alert-tag">运营提醒</span>
          <span class="ov-tf-dash-alert-text">{{ tfOverview.summary }}</span>
        </div>
      </div>

      <!-- 运营分析 -->
      <div class="ov-panel ov-tf-situation">
        <div class="ov-panel-title-bar">
          <h3 class="ov-title">运营分析</h3>
          <span class="ov-title-note">看事实 · 流量趋势 / 组织 / 地区 / 产品</span>
        </div>
        <div class="ov-tf-an-tabs">
          <template v-if="tfPersp === 'platform'">
            <button type="button" class="ov-tf-sit-tab" :class="{ active: anTab === 'trend' }" @click="anTab = 'trend'">流量趋势</button>
            <button type="button" class="ov-tf-sit-tab" :class="{ active: anTab === 'agent' }" @click="anTab = 'agent'">代理商</button>
            <button type="button" class="ov-tf-sit-tab" :class="{ active: anTab === 'region' }" @click="anTab = 'region'">地区</button>
          </template>
          <template v-else>
            <button type="button" class="ov-tf-sit-tab" :class="{ active: anTab === 'customer' }" @click="anTab = 'customer'">个人用户</button>
            <button type="button" class="ov-tf-sit-tab" :class="{ active: anTab === 'channel' }" @click="anTab = 'channel'">子渠道</button>
            <button type="button" class="ov-tf-sit-tab" :class="{ active: anTab === 'pkg' }" @click="anTab = 'pkg'">套餐</button>
            <button type="button" class="ov-tf-sit-tab" :class="{ active: anTab === 'profit' }" @click="anTab = 'profit'">成本</button>
          </template>
        </div>

        <template v-if="tfPersp === 'platform'">
          <!-- 流量趋势 -->
          <div v-if="anTab === 'trend'" class="ov-tf-trend">
            <div class="ov-tf-trend-head">
              <div class="ov-tf-trend-ranges">
                <button
                  v-for="r in trendRanges"
                  :key="r.k"
                  type="button"
                  class="ov-tf-sit-tab"
                  :class="{ active: trendRange === r.k }"
                  @click="trendRange = r.k; trendSelected = null"
                >{{ r.t }}</button>
              </div>
              <div class="ov-tf-trend-metrics">
                <button
                  v-for="m in trendMetrics"
                  :key="m.k"
                  type="button"
                  class="ov-tf-sit-tab"
                  :class="{ active: trendMetric === m.k }"
                  @click="trendMetric = m.k"
                >{{ m.t }}</button>
              </div>
            </div>
            <div class="ov-tf-trend-title">
              {{ trendRangeLabel }}流量{{ trendMetricLabel }}趋势
              <i class="ov-tf-trend-hint">点击数据点查看详情并分析</i>
            </div>
            <div class="ov-tf-trend-chart" :class="trendData.length > 12 ? 'ov-tf-trend-dense' : ''">
              <button
                v-for="(point, i) in trendData"
                :key="i"
                type="button"
                class="ov-tf-trend-col"
                :class="{ 'ov-tf-trend-cur': i === trendData.length - 1, 'ov-tf-trend-sel': i === trendSelected }"
                :title="'点击分析 ' + point.label + ' 的' + trendMetricLabel"
                @click="selectTrend(i)"
              >
                <div class="ov-tf-trend-val">{{ trendValue(point, trendMetric) }}<i>{{ trendUnit(trendMetric) }}</i></div>
                <div class="ov-tf-trend-bar">
                  <div class="ov-tf-trend-fill" :style="{ height: trendBarHeight(point) + '%' }"></div>
                </div>
                <div class="ov-tf-trend-label">{{ point.label }}</div>
                <div class="ov-tf-trend-mom">{{ point.momPct > 0 ? '+' + point.momPct + '%' : '—' }}</div>
              </button>
            </div>
            <div v-if="trendSelectedPoint" class="ov-tf-trend-selbar">
              <span class="ov-tf-trend-sel-tag">已选 {{ trendSelectedPoint.label }}</span>
              <span class="ov-tf-trend-sel-text">
                使用量 {{ trendSelectedPoint.usedGB }}GB · 使用率 {{ trendSelectedPoint.usageRate }}% · 剩余 {{ trendSelectedPoint.remainingGB }}GB · 环比 {{ trendSelectedPoint.momPct > 0 ? '+' + trendSelectedPoint.momPct + '%' : trendSelectedPoint.momPct + '%' }} · 主要增长
                <b class="ov-tf-up">{{ topGrowthAgent.name }} +{{ topGrowthAgent.momPct }}%</b> · 主要下降
                <b class="ov-tf-down">{{ topDeclineAgent.name }} {{ topDeclineAgent.momPct }}%</b>
              </span>
              <button type="button" class="ov-tf-trend-reason" @click="selectTrend(trendSelected)">
                让 AI 分析
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="9 18 15 12 9 6" />
                </svg>
              </button>
            </div>
          </div>

          <!-- 代理商 -->
          <div v-else-if="anTab === 'agent'" class="ov-tf-an-block">
            <div class="ov-tf-agentrank-head">
              <div class="ov-tf-sit-block-title">代理商运营（按 {{ agentSortLabel }} 排序）</div>
              <div class="ov-tf-agentrank-sort">
                <button
                  v-for="s in agentSortOptions"
                  :key="s.k"
                  type="button"
                  class="ov-tf-sit-tab"
                  :class="{ active: agentSort === s.k }"
                  @click="agentSort = s.k"
                >{{ s.t }}</button>
              </div>
            </div>
            <div class="ov-tf-agentrank-grid">
              <div class="ov-tf-sit-block ov-tf-agentrank-left">
                <div class="ov-tf-sit-block-title">流量使用排名</div>
                <div
                  v-for="(agent, i) in agentRank.slice(0, 5)"
                  :key="agent.code"
                  class="ov-tf-agentrank-row"
                  @click="askAi(`分析 ${agent.name}（${agent.code}·${agent.region}）的流量用量与量利结构`)"
                >
                  <span class="ov-tf-agentrank-idx" :class="'ov-tf-ar-idx-' + i">{{ i + 1 }}</span>
                  <span class="ov-tf-agentrank-name">{{ agent.name }}</span>
                  <span class="ov-tf-agentrank-bar">
                    <span class="ov-tf-agentrank-fill" :style="{ width: Math.min(100, (agent.usedGB / agentRank[0].usedGB) * 100) + '%' }"></span>
                  </span>
                  <span class="ov-tf-agentrank-val">{{ agent.usedGB }}GB</span>
                </div>
                <div class="ov-tf-agentrank-hint">点击代理商可让 AI 分析其流量用量与量利结构</div>
              </div>
              <div class="ov-tf-agentrank-right">
                <div class="ov-tf-sit-table">
                  <div class="ov-tf-table-head">
                    <span>代理商</span><span>地市</span><span>用量</span><span>使用率</span><span>剩余</span><span>环比</span><span>状态</span>
                  </div>
                  <button
                    v-for="agent in agentRank"
                    :key="agent.code"
                    type="button"
                    class="ov-tf-table-row"
                    :title="'点击让 AI 分析 ' + agent.name + ' 量利结构'"
                    @click="askAi(`分析 ${agent.name}（${agent.code}·${agent.region}）的流量用量结构，找出高使用率套餐与量利优化机会`)"
                  >
                    <span class="ov-tf-row-name">{{ agent.name }}</span>
                    <span>{{ agent.region }}</span>
                    <span>{{ agent.usedGB }}</span>
                    <span class="ov-tf-usage-rate" :class="riskLevel(agent.usageRate).cls">{{ agent.usageRate }}%</span>
                    <span>{{ agent.remainingGB }}</span>
                    <span class="ov-tf-mom" :class="'ov-tf-mom-' + momDir(agent.momPct)">{{ agent.momPct > 0 ? '+' + agent.momPct + '%' : agent.momPct + '%' }}</span>
                    <span class="ov-tf-status" :class="'ov-tf-st-' + statusKey(agent.status)">{{ agent.status }}</span>
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- 地区 -->
          <div v-else-if="anTab === 'region'" class="ov-tf-an-block">
            <template v-if="activeRegion">
              <div class="ov-tf-sel-head">
                <button type="button" class="ov-tf-sel-back" @click="regionSelected = null">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                    <polyline points="15 18 9 12 15 6" />
                  </svg>
                  返回地区列表
                </button>
                <span class="ov-tf-sel-title">{{ activeRegion.name }} 地区（{{ activeRegion.agents }} 家代理商）</span>
                <button type="button" class="ov-tf-sel-ask" @click="askAi(`分析 ${activeRegion.name} 地区流量用量与套餐使用结构，找出高使用率套餐与优化机会`)">
                  让 AI 分析
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6" /></svg>
                </button>
              </div>
              <div class="ov-tf-detail-kpis">
                <div class="ov-kpi">
                  <div class="ov-kpi-icon ov-ki-blue">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" /></svg>
                  </div>
                  <div class="ov-kpi-txt"><div class="ov-kpi-label">批发买进</div><div class="ov-kpi-value">{{ activeRegion.boughtGB }}<span style="font-size: 10px; color: #94a3b8">GB</span></div></div>
                </div>
                <div class="ov-kpi">
                  <div class="ov-kpi-icon ov-ki-green">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 12h-4l-3 9L9 3l-3 9H2" /></svg>
                  </div>
                  <div class="ov-kpi-txt"><div class="ov-kpi-label">实际用量</div><div class="ov-kpi-value">{{ activeRegion.usedGB }}<span style="font-size: 10px; color: #94a3b8">GB</span></div></div>
                </div>
                <div class="ov-kpi">
                  <div class="ov-kpi-icon ov-ki-amber">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20V10M18 20V14M6 20v-4" /></svg>
                  </div>
                  <div class="ov-kpi-txt"><div class="ov-kpi-label">使用率</div><div class="ov-kpi-value">{{ activeRegion.usageRate }}%</div></div>
                </div>
                <div class="ov-kpi">
                  <div class="ov-kpi-icon ov-ki-purple">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" /><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" /></svg>
                  </div>
                  <div class="ov-kpi-txt"><div class="ov-kpi-label">剩余流量</div><div class="ov-kpi-value">{{ activeRegion.remainingGB }}<span style="font-size: 10px; color: #94a3b8">GB</span></div></div>
                </div>
              </div>
              <div class="ov-tf-sel-cust-title">该地区代理商</div>
              <div class="ov-tf-sit-table">
                <div class="ov-tf-table-head">
                  <span>代理商</span><span>买进</span><span>用量</span><span>剩余</span><span>使用率</span><span>环比</span><span>状态</span>
                </div>
                <button
                  v-for="agent in regionAgents"
                  :key="agent.code"
                  type="button"
                  class="ov-tf-table-row"
                  @click="askAi(`分析 ${agent.name}（${agent.code}·${agent.region}）的流量用量结构，找出高使用率套餐与量利优化机会`)"
                >
                  <span class="ov-tf-row-name">{{ agent.name }}</span>
                  <span>{{ agent.boughtGB }}</span>
                  <span>{{ agent.usedGB }}</span>
                  <span>{{ agent.remainingGB }}</span>
                  <span class="ov-tf-usage-rate" :class="riskLevel(agent.usageRate).cls">{{ agent.usageRate }}%</span>
                  <span class="ov-tf-mom" :class="'ov-tf-mom-' + momDir(agent.momPct)">{{ agent.momPct > 0 ? '+' + agent.momPct + '%' : agent.momPct + '%' }}</span>
                  <span class="ov-tf-status" :class="'ov-tf-st-' + statusKey(agent.status)">{{ agent.status }}</span>
                </button>
              </div>
            </template>
            <div v-else class="ov-tf-sit-table">
              <div class="ov-tf-table-head">
                <span>国家/地区</span><span>代理商</span><span>买进</span><span>用量</span><span>使用率</span><span>环比</span><span>状态</span>
              </div>
              <button
                v-for="region in regionRank"
                :key="region.name"
                type="button"
                class="ov-tf-table-row"
                :title="'点击下钻查看 ' + region.name + ' 地区的代理商与套餐'"
                @click="regionSelected = region.name"
              >
                <span class="ov-tf-row-name">{{ region.name }}</span>
                <span>{{ region.agents }} 家</span>
                <span>{{ region.boughtGB }}</span>
                <span>{{ region.usedGB }}</span>
                <span class="ov-tf-usage-rate" :class="riskLevel(region.usageRate).cls">{{ region.usageRate }}%</span>
                <span class="ov-tf-mom" :class="'ov-tf-mom-' + momDir(region.momPct)">{{ region.momPct > 0 ? '+' + region.momPct + '%' : region.momPct + '%' }}</span>
                <span class="ov-tf-status" :class="'ov-tf-st-' + statusKey(region.status)">{{ region.status }}</span>
              </button>
            </div>
          </div>
        </template>

        <template v-else>
          <!-- 个人用户 -->
          <div v-if="anTab === 'customer'" class="ov-tf-an-block">
            <div class="ov-tf-agentrank-head">
              <div class="ov-tf-sit-block-title">个人用户运营（按 {{ userSortLabel }} 排序）<span class="ov-tf-agentwrap-sub">　总代直属的个人用户</span></div>
              <div class="ov-tf-agentrank-sort">
                <button v-for="s in userSortOptions" :key="s.k" type="button" class="ov-tf-sit-tab" :class="{ active: userSort === s.k }" @click="userSort = s.k">{{ s.t }}</button>
              </div>
            </div>
            <div class="ov-tf-sit-table ov-tf-sit-custgrid">
              <div class="ov-tf-table-head">
                <span>个人用户</span><span>手机号</span><span>用量</span><span>使用率</span><span>剩余量</span><span>趋势</span><span>在用套餐</span><span>状态</span>
              </div>
              <button
                v-for="user in personalUserList"
                :key="user.name"
                type="button"
                class="ov-tf-table-row"
                @click="askAi(`分析 个人用户 ${user.name}（${user.tel}）的流量用量与被订阅套餐 ${user.pkg}，使用率 ${user.usageRate}%，给出套餐续费或升档建议`)"
              >
                <span class="ov-tf-row-name">{{ user.name }}</span>
                <span>{{ user.tel }}</span>
                <span>{{ user.usedGB }}GB</span>
                <span class="ov-tf-usage-rate" :class="riskLevel(user.usageRate).cls">{{ user.usageRate }}%</span>
                <span>{{ user.remainingGB }}GB</span>
                <span :class="trendCls(user.trend)">{{ trendIcon(user.trend) }}</span>
                <span>{{ user.pkg }}</span>
                <span class="ov-tf-status" :class="'ov-tf-st-' + (user.status === '高消耗' ? 'high' : user.status === '偏低' || user.status === '低消耗' ? 'low' : user.status === '偏高' ? 'high' : 'mid')">{{ user.status }}</span>
              </button>
            </div>
          </div>

          <!-- 子渠道 -->
          <div v-else-if="anTab === 'channel'" class="ov-tf-an-block">
            <div class="ov-tf-agentrank-head">
              <div class="ov-tf-sit-block-title">子渠道运营（按 {{ channelSortLabel }} 排序）<span class="ov-tf-agentwrap-sub">　代理商(总代)下的二级/三级代理：旅行社 / 公司 / 营业厅</span></div>
              <div class="ov-tf-agentrank-sort">
                <button v-for="s in channelSortOptions" :key="s.k" type="button" class="ov-tf-sit-tab" :class="{ active: channelSort === s.k }" @click="channelSort = s.k">{{ s.t }}</button>
              </div>
            </div>
            <div class="ov-tf-sit-table ov-tf-sit-chgrid">
              <div class="ov-tf-table-head">
                <span>子渠道</span><span>级别</span><span>类型</span><span>订购量</span><span>用量</span><span>使用率</span><span>剩余量</span><span>趋势</span><span>状态</span>
              </div>
              <button
                v-for="ch in subChannelList"
                :key="ch.name"
                type="button"
                class="ov-tf-table-row"
                @click="askAi(`分析 子渠道 ${ch.name}（${ch.level}级·${ch.type}）的流量采购与使用情况，订购 ${ch.boughtGB}GB、使用率 ${ch.usageRate}%，给出流量补给或套餐优化建议`)"
              >
                <span class="ov-tf-row-name">{{ ch.name }}</span>
                <span class="ov-tf-status" :class="'ov-tf-st-' + (ch.level === 2 ? 'high' : 'mid')">{{ ch.level }}级</span>
                <span>{{ ch.type }}</span>
                <span>{{ ch.boughtGB }}GB</span>
                <span>{{ ch.usedGB }}GB</span>
                <span class="ov-tf-usage-rate" :class="riskLevel(ch.usageRate).cls">{{ ch.usageRate }}%</span>
                <span>{{ ch.remainingGB }}GB</span>
                <span :class="trendCls(ch.trend)">{{ trendIcon(ch.trend) }}</span>
                <span class="ov-tf-status" :class="'ov-tf-st-' + (ch.status === '高负载' ? 'high' : ch.status === '低消耗' ? 'low' : 'mid')">{{ ch.status }}</span>
              </button>
            </div>
          </div>

          <!-- 套餐 -->
          <div v-else-if="anTab === 'pkg'" class="ov-tf-an-block">
            <div v-if="activePkg" class="ov-tf-sel">
              <div class="ov-tf-sel-head">
                <button type="button" class="ov-tf-sel-back" @click="activePkgName = ''">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="15 18 9 12 15 6" /></svg>
                  返回套餐运营
                </button>
                <span class="ov-tf-sel-title">{{ activePkg.name }}　套餐详情<span class="ov-tf-sel-sub">　{{ activePkg.code }} · {{ activePkg.customerCount }} 单订购</span></span>
                <button type="button" class="ov-tf-sel-ask" @click="askAi(`分析 套餐 ${activePkg.name.replace('WeWalk ', '')} 的基础信息、个人用户使用情况，给出套餐优化与升档建议`)">
                  让 AI 分析
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6" /></svg>
                </button>
              </div>
              <!-- 规模指标 -->
              <div class="ov-tf-detail-kpis">
                <div class="ov-kpi ov-kpi-main">
                  <div class="ov-kpi-icon ov-ki-blue"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" /></svg></div>
                  <div class="ov-kpi-txt"><div class="ov-kpi-label">订购量</div><div class="ov-kpi-value">{{ activePkg.customerCount }}<span>单</span></div></div>
                </div>
                <div class="ov-kpi ov-kpi-main">
                  <div class="ov-kpi-icon ov-ki-purple"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5.5 21a7 7 0 0 1 13 0M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z" /></svg></div>
                  <div class="ov-kpi-txt"><div class="ov-kpi-label">去重用户</div><div class="ov-kpi-value">{{ activePkg.userCount }}<span>人</span></div></div>
                </div>
                <div class="ov-kpi">
                  <div class="ov-kpi-icon ov-ki-green"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 12h-4l-3 9L9 3l-3 9H2" /></svg></div>
                  <div class="ov-kpi-txt"><div class="ov-kpi-label">批发买进</div><div class="ov-kpi-value">{{ activePkg.boughtGB }}<span>GB</span></div></div>
                </div>
                <div class="ov-kpi">
                  <div class="ov-kpi-icon ov-ki-amber"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20V10M18 20V14M6 20v-4" /></svg></div>
                  <div class="ov-kpi-txt"><div class="ov-kpi-label">剩余流量</div><div class="ov-kpi-value">{{ activePkg.remainingGB }}<span>GB</span></div></div>
                </div>
              </div>
              <!-- 使用效率 + 成本结构 -->
              <div class="ov-tf-pkg-grid">
                <div class="ov-tf-pkg-panel">
                  <div class="ov-tf-pkg-panel-title"><span>使用效率</span><span class="ov-tf-pkg-panel-tag" :class="riskLevel(activePkg.usageRate).cls">{{ riskLevel(activePkg.usageRate).text }}</span></div>
                  <div class="ov-tf-pkg-stats">
                    <div class="ov-tf-pkg-stat"><span>实际用量</span><b>{{ activePkg.usedGB }} <i>GB</i></b></div>
                    <div class="ov-tf-pkg-stat"><span>使用率</span><b>{{ activePkg.usageRate }}%</b></div>
                  </div>
                  <div class="ov-tf-pkg-bar"><div class="ov-tf-pkg-bar-fill" :class="riskLevel(activePkg.usageRate).cls" :style="{ width: Math.min(activePkg.usageRate, 100) + '%' }"></div></div>
                  <div class="ov-tf-pkg-bar-note">剩余 {{ activePkg.remainingGB }}GB · 可用余量偏{{ activePkg.usageRate >= 80 ? '紧' : '足' }}</div>
                </div>
                <div class="ov-tf-pkg-panel">
                  <div class="ov-tf-pkg-panel-title">成本结构</div>
                  <div class="ov-tf-pkg-stats">
                    <div class="ov-tf-pkg-stat"><span>档位容量</span><b>{{ activePkg.capacityGB }} <i>GB</i></b></div>
                    <div class="ov-tf-pkg-stat"><span>单位采购价</span><b>{{ activePkg.unitCost }} <i>元/GB</i></b></div>
                    <div class="ov-tf-pkg-stat"><span>单卡成本</span><b>{{ activePkg.costPerCard }} <i>元</i></b></div>
                  </div>
                  <div class="ov-tf-pkg-sugg">📌 运营建议　{{ activePkg.suggestion }}</div>
                </div>
              </div>
              <!-- 客户结构 -->
              <div class="ov-tf-pkg-panel">
                <div class="ov-tf-pkg-panel-title">客户结构<span class="ov-tf-pkg-panel-sub">各客户的用量占比 · {{ activePkg.customerCount }} 笔订购 / {{ activePkg.userCount }} 个去重用户</span></div>
                <div class="ov-tf-pkg-custs">
                  <div v-for="c in activePkg.customers" :key="c.name" class="ov-tf-pkg-cust">
                    <span class="ov-tf-pkg-cust-name">{{ c.name }}</span>
                    <span class="ov-tf-pkg-cust-bar"><span class="ov-tf-pkg-cust-fill" :style="{ width: c.usageRate + '%' }"></span></span>
                    <span class="ov-tf-pkg-cust-users">{{ c.users }} 人</span>
                    <span class="ov-tf-pkg-cust-share">{{ c.share }}</span>
                  </div>
                  <div class="ov-tf-pkg-cust-empty" v-if="!activePkg.customers || !activePkg.customers.length">暂无客户明细</div>
                </div>
              </div>
              <!-- AI 分析 -->
              <div class="ov-tf-focus-detail-ai">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2l2.09 6.26L20 10l-5.91 1.74L12 18l-2.09-6.26L4 10l5.91-1.74z" /></svg>
                <span><b>AI 分析</b>　{{ activePkg.insight }}。建议让 AI 结合该套餐个人用户使用情况给出升档迁移与规格优化方案。</span>
                <button type="button" class="ov-tf-focus-ask" @click="askAi(`分析 套餐 ${activePkg.name.replace('WeWalk ', '')}，${activePkg.insight}，请给出个人用户升档迁移与套餐规格优化方案`)">
                  让 AI 分析
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6" /></svg>
                </button>
              </div>
            </div>
            <template v-else>
              <div class="ov-tf-sit-block-title">套餐运营<span class="ov-tf-agentwrap-sub">　订购量 = 套餐订购笔数 · 用户数 = 去重后的个人用户数（一个用户可订购多套餐）· 点击套餐查看详情</span></div>
              <div class="ov-tf-sit-table ov-tf-sit-pkggrid">
                <div class="ov-tf-table-head">
                  <span>套餐</span><span>订购量</span><span>用户数</span><span>用量</span><span>使用率</span><span>剩余量</span><span>趋势</span><span>状态</span><span>操作</span>
                </div>
                <button v-for="pkg in packageList" :key="pkg.name" type="button" class="ov-tf-table-row" @click="activePkgName = pkg.name">
                  <span class="ov-tf-row-name">{{ pkg.nameShort }}</span>
                  <span>{{ pkg.orderCount }} 单</span>
                  <span>{{ pkg.userCount }} 人</span>
                  <span>{{ pkg.usedGB }}GB</span>
                  <span class="ov-tf-usage-rate" :class="riskLevel(pkg.usageRate).cls">{{ pkg.usageRate }}%</span>
                  <span>{{ pkg.remainingGB }}GB</span>
                  <span :class="trendCls(pkg.trend)">{{ trendIcon(pkg.trend) }}</span>
                  <span class="ov-tf-status" :class="'ov-tf-st-' + (pkg.usageRate >= 80 ? 'high' : pkg.usageRate >= 50 ? 'mid' : 'low')">{{ pkg.status }}</span>
                  <span class="ov-tf-row-op">查看详情 ›</span>
                </button>
              </div>
            </template>
          </div>

          <!-- 套餐优化机会 -->
          <div v-if="anTab === 'pkg' && !activePkg" class="ov-tf-an-block">
            <div class="ov-tf-sit-block-title">套餐优化机会<span class="ov-tf-agentwrap-sub">　高使用率套餐 · 高余量套餐 · 套餐结构优化</span></div>
            <div class="ov-tf-sel-cust-title" style="padding: 12px 16px 4px">🔴 高使用率套餐（容量偏紧，建议评估更高流量规格）</div>
            <div class="ov-tf-focus-list">
              <div v-for="(item, i) in optimize.highUsage" :key="item.name" class="ov-tf-focus-item">
                <span class="ov-tf-agentrank-idx">{{ i + 1 }}</span>
                <div class="ov-tf-focus-main">
                  <div class="ov-tf-focus-title">
                    {{ item.name.replace('WeWalk ', '') }}<span class="ov-tf-focus-type">套餐</span><span class="ov-tf-focus-name">{{ item.customers }} 单</span><span class="ov-tf-focus-name">用户 {{ item.userCount }} 人</span>
                  </div>
                  <div class="ov-tf-focus-detail">使用率 {{ item.usageRate }}%、余量仅 {{ item.remainingGB }}GB。<b>优化理由：</b>{{ item.reason }}</div>
                </div>
                <button type="button" class="ov-tf-focus-ask" @click="askAi(`分析 套餐 ${item.name.replace('WeWalk ', '')} 使用率 ${item.usageRate}% 偏高，${item.reason}，评估套餐升档迁移方案`)">
                  让 AI 分析
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6" /></svg>
                </button>
              </div>
            </div>
            <div class="ov-tf-sel-cust-title" style="padding: 8px 16px 4px">🟡 高余量套餐（容量利用率较低，可评估规格合理性）</div>
            <div class="ov-tf-focus-list">
              <div v-for="(item, i) in optimize.highRemaining" :key="item.name" class="ov-tf-focus-item">
                <span class="ov-tf-agentrank-idx">{{ i + 1 }}</span>
                <div class="ov-tf-focus-main">
                  <div class="ov-tf-focus-title">
                    {{ item.name.replace('WeWalk ', '') }}<span class="ov-tf-focus-type">套餐</span><span class="ov-tf-focus-name">{{ item.customers }} 单</span><span class="ov-tf-focus-name">用户 {{ item.userCount }} 人</span>
                  </div>
                  <div class="ov-tf-focus-detail">使用率 {{ item.usageRate }}%、余量 {{ item.remainingGB }}GB 较高。<b>优化理由：</b>{{ item.reason }}</div>
                </div>
                <button type="button" class="ov-tf-focus-ask" @click="askAi(`分析 套餐 ${item.name.replace('WeWalk ', '')} 余量较高，${item.reason}，评估套餐规格与促销机会`)">
                  让 AI 分析
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6" /></svg>
                </button>
              </div>
            </div>
            <div class="ov-tf-sel-cust-title" style="padding: 8px 16px 4px">💡 套餐结构优化</div>
            <div class="ov-tf-insight">
              <div class="ov-tf-insight-item">
                <span class="ov-tf-insight-ico ov-tf-ii-mid">✦</span>
                <div class="ov-tf-insight-main">
                  <div class="ov-tf-insight-title">{{ optimize.structure }}</div>
                  <div v-if="optimize.highRemainingReason" class="ov-tf-insight-detail">⚡ 大流量规格建议　{{ optimize.highRemainingReason }}</div>
                </div>
                <button type="button" class="ov-tf-insight-ask" @click="askAi('当前套餐结构中 1GB 使用率持续升高、30GB 长期低利用，请分析套餐容量结构并给出整体优化建议')">
                  让 AI 分析
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6" /></svg>
                </button>
              </div>
            </div>
          </div>

          <!-- 成本收益分析 -->
          <div v-else-if="anTab === 'profit'" class="ov-tf-an-block">
            <div class="ov-tf-sit-block-title">成本收益分析<span class="ov-tf-agentwrap-sub">　套餐经营效益 · 价格 · 流量成本 · 使用率 · 收益</span></div>
            <div class="ov-tf-detail-kpis">
              <div class="ov-kpi">
                <div class="ov-kpi-icon ov-ki-blue"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 1v22M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" /></svg></div>
                <div class="ov-kpi-txt"><div class="ov-kpi-label">销售收入</div><div class="ov-kpi-value">{{ profitDetail.revenue }}<span style="font-size: 10px; color: #94a3b8">万元</span></div></div>
              </div>
              <div class="ov-kpi">
                <div class="ov-kpi-icon ov-ki-amber"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20V10M18 20V14M6 20v-4" /></svg></div>
                <div class="ov-kpi-txt"><div class="ov-kpi-label">采购成本</div><div class="ov-kpi-value">{{ profitDetail.cost }}<span style="font-size: 10px; color: #94a3b8">万元</span></div></div>
              </div>
              <div class="ov-kpi">
                <div class="ov-kpi-icon ov-ki-green"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 12h-4l-3 9L9 3l-3 9H2" /></svg></div>
                <div class="ov-kpi-txt"><div class="ov-kpi-label">经营毛利</div><div class="ov-kpi-value">{{ profitDetail.profit }}<span style="font-size: 10px; color: #94a3b8">万元</span></div></div>
              </div>
              <div class="ov-kpi">
                <div class="ov-kpi-icon ov-ki-purple"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20V10M18 20V14M6 20v-4" /></svg></div>
                <div class="ov-kpi-txt"><div class="ov-kpi-label">综合毛利率</div><div class="ov-kpi-value">{{ profitDetail.margin }}%</div></div>
              </div>
            </div>
            <div class="ov-tf-sel-cust-title" style="padding: 12px 16px 4px">📊 套餐经营效益（价格 / 流量成本 / 使用率 / 收益）</div>
            <div class="ov-tf-sit-table">
              <div class="ov-tf-table-head">
                <span>套餐</span><span>售价/GB</span><span>流量成本/GB</span><span>使用率</span><span>档毛利</span><span>毛利率</span><span>收益情况</span>
              </div>
              <button
                v-for="pkg in profitDetail.byPackage"
                :key="pkg.name"
                type="button"
                class="ov-tf-table-row"
                @click="askAi(`分析 套餐 ${pkg.name.replace('WeWalk ', '')} 售价 ${pkg.price}/GB、流量成本 ${pkg.unitCost}/GB、使用率 ${pkg.usageRate}%、档毛利 ${pkg.profit} 万元、毛利率 ${pkg.marginOfPackage}%，评估该套餐的成本收益并给出优化建议`)"
              >
                <span class="ov-tf-row-name">{{ pkg.name.replace('WeWalk ', '') }}</span>
                <span>{{ pkg.price }} 元</span>
                <span>{{ pkg.unitCost }} 元</span>
                <span class="ov-tf-usage-rate" :class="riskLevel(pkg.usageRate).cls">{{ pkg.usageRate }}%</span>
                <span>{{ pkg.profit }} 万</span>
                <span :class="'ov-tf-st-' + marginKey(pkg.marginOfPackage)">{{ pkg.marginOfPackage }}%</span>
                <span class="ov-tf-status" :class="'ov-tf-st-' + (pkg.usageRate >= 80 ? 'high' : pkg.usageRate >= 60 ? 'mid' : 'low')">{{ pkg.gain }}</span>
              </button>
            </div>
            <div class="ov-tf-focus-detail-ai" style="margin: 12px 16px 16px">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2l2.09 6.26L20 10l-5.91 1.74L12 18l-2.09-6.26L4 10l5.91-1.74z" /></svg>
              <span><b>AI 建议</b>　{{ profitDetail.insight }}。是否测算「把 1GB 订单调整为 2GB」后的成本与收益变化？</span>
              <button type="button" class="ov-tf-focus-ask" @click="askAi('帮我测算 WeWalk 1GB 订单迁移到 2GB 后，流量成本与经营收益的变化')">
                测算 1GB→2GB 影响
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6" /></svg>
              </button>
            </div>
          </div>
        </template>
      </div>

      <!-- 重点运营机会（平台视角） -->
      <div v-if="tfPersp === 'platform'" class="ov-panel ov-tf-focus">
        <div class="ov-panel-title-bar">
          <h3 class="ov-title">重点运营机会</h3>
          <span class="ov-title-note">流量异常 · 使用率异常 · 流量增长 · 资源利用</span>
        </div>
        <div class="ov-tf-focus-tabs">
          <button
            v-for="tab in focusTabs"
            :key="tab.key"
            type="button"
            class="ov-tf-focus-tab"
            :class="{ active: focusTab === tab.key }"
            @click="focusTab = tab.key; focusSelected = null"
          >{{ tab.label }}<em v-if="tab.items.length">{{ tab.items.length }}</em></button>
        </div>
        <div v-if="focusSelected && focusDetail" class="ov-tf-focus-detail">
          <div class="ov-tf-sel-head">
            <button type="button" class="ov-tf-sel-back" @click="focusSelected = null">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="15 18 9 12 15 6" /></svg>
              返回重点运营机会
            </button>
            <span class="ov-tf-sel-title">{{ focusDetail.name }}　{{ focusDetail.typeLabel }}流量分析</span>
            <button type="button" class="ov-tf-sel-ask" @click="askAi(focusSelected.ask)">
              让 AI 分析
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6" /></svg>
            </button>
          </div>
          <div class="ov-tf-detail-kpis">
            <div class="ov-kpi">
              <div class="ov-kpi-icon ov-ki-blue"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" /></svg></div>
              <div class="ov-kpi-txt"><div class="ov-kpi-label">批发买进</div><div class="ov-kpi-value">{{ focusDetail.boughtGB }}<span style="font-size: 10px; color: #94a3b8">GB</span></div></div>
            </div>
            <div class="ov-kpi">
              <div class="ov-kpi-icon ov-ki-green"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 12h-4l-3 9L9 3l-3 9H2" /></svg></div>
              <div class="ov-kpi-txt"><div class="ov-kpi-label">实际用量</div><div class="ov-kpi-value">{{ focusDetail.usedGB }}<span style="font-size: 10px; color: #94a3b8">GB</span></div></div>
            </div>
            <div class="ov-kpi">
              <div class="ov-kpi-icon ov-ki-amber"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20V10M18 20V14M6 20v-4" /></svg></div>
              <div class="ov-kpi-txt"><div class="ov-kpi-label">使用率</div><div class="ov-kpi-value">{{ focusDetail.usageRate }}%</div></div>
            </div>
            <div class="ov-kpi">
              <div class="ov-kpi-icon ov-ki-purple"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" /><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" /></svg></div>
              <div class="ov-kpi-txt"><div class="ov-kpi-label">剩余流量</div><div class="ov-kpi-value">{{ focusDetail.remainingGB }}<span style="font-size: 10px; color: #94a3b8">GB</span></div></div>
            </div>
          </div>
          <div class="ov-tf-focus-detail-sec">
            <div class="ov-tf-focus-detail-g">
              <div class="ov-tf-sel-cust-title">{{ focusDetail.typeLabel === '地区' ? '该地区代理商' : '归属地区' }}</div>
              <div v-for="item in focusRelated" :key="item.name" class="ov-tf-sel-cust">
                <span class="ov-tf-sel-cust-name">{{ item.name }}<span v-if="item.region" class="ov-tf-sel-cust-meta" style="display: block">{{ item.region }}</span></span>
                <span class="ov-tf-sel-cust-meta">{{ item.usedGB }}GB · {{ item.usageRate }}%</span>
                <span class="ov-tf-sel-cust-rate" :class="'ov-tf-sel-t-' + (item.usageRate >= 80 ? 'high' : item.usageRate >= 55 ? 'mid' : 'low')">{{ item.usageRate }}%</span>
              </div>
            </div>
            <div class="ov-tf-focus-detail-g">
              <div class="ov-tf-sel-cust-title">使用率排名（Top 3）</div>
              <div v-for="item in topUsageRegions" :key="item.name" class="ov-tf-sel-cust">
                <span class="ov-tf-sel-cust-name">{{ item.name }}</span>
                <span class="ov-tf-sel-cust-meta">{{ item.usedGB }}GB · {{ item.agents }} 家代理商</span>
                <span class="ov-tf-sel-cust-rate" :class="'ov-tf-sel-t-' + (item.usageRate >= 80 ? 'high' : item.usageRate >= 55 ? 'mid' : 'low')">{{ item.usageRate }}%</span>
              </div>
            </div>
          </div>
          <div class="ov-tf-focus-detail-ai">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2l2.09 6.26L20 10l-5.91 1.74L12 18l-2.09-6.26L4 10l5.91-1.74z" /></svg>
            <span><b>AI 洞察</b>　{{ focusSelected.detail }}。该 {{ focusDetail.typeLabel }} 为当前值得关注的运营机会，建议让 AI 分析根因并给出处理建议。</span>
            <button type="button" class="ov-tf-focus-ask" @click="askAi(focusSelected.ask)">
              让 AI 分析
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6" /></svg>
            </button>
          </div>
        </div>
        <div v-else class="ov-tf-focus-list">
          <div v-for="item in focusItems" :key="item.name + item.detail" class="ov-tf-focus-item">
            <div
              class="ov-tf-focus-main"
              :class="{ clickable: item.type === 'pkg' }"
              :title="item.type === 'pkg' ? '点击进入套餐运营分析' : ''"
              @click="openFocusItem(item)"
            >
              <div class="ov-tf-focus-title">
                <span class="ov-tf-focus-badge" :class="'ov-tf-fb-' + item.level">{{ item.level === 'high' ? '高' : item.level === 'mid' ? '中' : '低' }}</span>
                <span class="ov-tf-focus-type">{{ item.type === 'pkg' ? '套餐' : item.type === 'agent' ? '代理商' : item.type === 'customer' ? '客户' : '地区' }}</span>
                <span class="ov-tf-focus-name">{{ item.name }}</span>
              </div>
              <div class="ov-tf-focus-detail">{{ item.detail }}</div>
            </div>
            <button type="button" class="ov-tf-focus-ask" @click="askAi(item.ask)">
              让 AI 分析
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6" /></svg>
            </button>
          </div>
        </div>
      </div>

      <!-- AI 运营洞察（平台视角） -->
      <div v-if="tfPersp === 'platform'" class="ov-panel ov-tf-insight">
        <div class="ov-panel-title-bar">
          <h3 class="ov-title ov-tf-insight-title">AI 运营洞察</h3>
          <span class="ov-title-note">主动发现 · 主动决策 · 持续验证</span>
        </div>
        <div class="ov-tf-insight-items">
          <div v-for="insight in aiInsights" :key="insight.title" class="ov-tf-insight-item">
            <span class="ov-tf-insight-ico" :class="'ov-tf-ii-' + insight.level">{{ insight.level === 'high' ? '!' : '✦' }}</span>
            <div class="ov-tf-insight-main">
              <div class="ov-tf-insight-title">{{ insight.title }}</div>
              <div class="ov-tf-insight-detail">{{ insight.detail }}</div>
            </div>
            <button type="button" class="ov-tf-insight-ask" @click="askAi(insight.ask)">
              {{ insight.action }}
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6" /></svg>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted } from 'vue'
import {
  platformOverview,
  agents,
  regions,
  packages,
  trafficMonthly,
  trafficDaily,
  platformFocus,
  agentViewOverview,
  agentViewDetail,
  agentProfitDetail,
} from '../config/trafficOpsData.js'
import { useWorkOrderStore } from '../stores/workOrder.js'
import { usePerspectiveStore } from '../stores/perspective.js'

const props = defineProps({
  initialDrill: { type: String, default: null },
  initialTab: { type: String, default: 'product' },
})

const emit = defineEmits(['open-rootcause', 'update:drill', 'ask-ai'])

const workOrderStore = useWorkOrderStore()
const perspectiveStore = usePerspectiveStore()

/* ---------------- 格式化工具 ---------------- */
function fmtDateTime(v) {
  if (!v) return '—'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return '—'
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
function fmt2(v) {
  if (v == null) return '—'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function fmtPct(v) {
  if (v == null) return '—'
  return (v > 0 ? '+' : '') + v.toFixed(2) + '%'
}
function fmtRatio(v) {
  if (v == null) return '—'
  return (v * 100).toFixed(2) + '%'
}
function yoyCls(v) {
  if (v == null) return 'ov-yoy-flat'
  return v > 0 ? 'ov-yoy-up' : v < 0 ? 'ov-yoy-down' : 'ov-yoy-flat'
}
function barPct(v) {
  return Math.min(100, Math.max(2, Math.round(v * 100))) + '%'
}

/* ================= 产品运营（原有视图） ================= */
const revenueCards = [
  { key: 'cnfamily', title: 'CN及家庭产品', iconCls: 'ov-icon-blue', report: { now: 64653.57, cum: 454312.06, yoy: -0.64 }, bill: { now: 23107.78, cum: 158966.95, yoy: 5.95 } },
  { key: 'family', title: '家庭产品', iconCls: 'ov-icon-emerald', report: { now: 54532.19, cum: 378076.8, yoy: 1.67 }, bill: { now: 18693.26, cum: 126629.51, yoy: 7.83 } },
  { key: 'cn', title: 'CN产品', iconCls: 'ov-icon-amber', report: { now: 10121.38, cum: 76235.27, yoy: -10.68 }, bill: { now: 4414.52, cum: 32337.44, yoy: -0.81 } },
]

/* ---- 重点产品 数据 ---- */
const productDataMap = {jtkd:{name:"家庭宽带",key:"jtkd",scale:[{label:"到达用户",value:"1182.03万",yoy:null},{label:"校园宽带到达",value:"46.39万",yoy:null},{label:"活跃率",value:"83.09%",yoy:null},{label:"活跃率(剔校园宽带)",value:"85.93%",yoy:null}],struct:{caption:"到达速率结构",groups:[{label:"到达速率",rows:[{label:"1000M",value:.3096},{label:"300-500M",value:.432},{label:"300M以下",value:.2584}]},{label:"新增速率",rows:[{label:"1000M",value:.3306},{label:"300-500M",value:.5765},{label:"300M以下",value:.0928}]},{label:"首宽/二宽",rows:[{label:"首宽",value:.7624},{label:"二宽",value:.2372}]},{label:"资费结构",rows:[{label:"129元及以上",value:.2591},{label:"59元以下",value:.3798}]}]},added:[{label:"当月新增",value:"8.90万",yoy:-36.47}],quality:[{label:"T+1活跃率",value:"92.57%",note:"新增用户"},{label:"T+3离网率",value:"1.57%",note:"1497户"},{label:"T+6离网率",value:"4.93%",note:"7620户"},{label:"T+3沉默率",value:"6.94%",note:null},{label:"T+6沉默率",value:"3.92%",note:null},{label:"户均实收(融合群)",value:"118.33元",note:"元/户"},{label:"低于50元占比",value:"8.44%",note:null},{label:"单宽户均实收",value:"20.70元",note:"元/户"}],income:{report:{now:40787.33,cum:283350.15,yoy:2.66},bill:{now:3977.45,cum:27728.41,yoy:.39}},channel:[{label:"线下",value:.7763},{label:"10086",value:.1447},{label:"线上",value:.079}]},znzw:{name:"智能组网",key:"znzw",scale:[{label:"到达用户",value:"360.87万",yoy:null},{label:"组网(不含FTTR)",value:"308.10万",yoy:null},{label:"活跃率",value:"89.28%",yoy:null}],struct:{caption:"到达结构",groups:[{label:"到达结构",rows:[{label:"FTTR",value:.1462},{label:"WiFi6",value:.7872},{label:"融合网关",value:.0231},{label:"WiFi5",value:.0435}]},{label:"新增结构",rows:[{label:"FTTR",value:.3061},{label:"WiFi6",value:.6714},{label:"融合网关",value:.0225},{label:"WiFi5",value:0}]}]},added:[{label:"当月新增",value:"9.01万",yoy:-41.14},{label:"新增(不含FTTR)",value:"6.25万",yoy:null}],quality:[{label:"T+3退订率",value:"7.14%",note:"5109户"},{label:"T+6退订率",value:"6.63%",note:"8055户"},{label:"T+3沉默率",value:"3.22%",note:null},{label:"T+6沉默率",value:"1.93%",note:null},{label:"户均实收",value:"9.77元",note:"元/户"},{label:"低值占比(<10元)",value:"4.35%",note:null}],income:{report:{now:5394.8,cum:34381.5,yoy:5.96},bill:{now:2366.38,cum:16756.35,yoy:-.28}},channel:[{label:"线下",value:.8626},{label:"10086",value:.1285},{label:"线上",value:.0087}]},ydyp:{name:"移动云盘",key:"ydyp",scale:[{label:"到达用户",value:"1020.16万",yoy:null},{label:"叠加态到达",value:"85.38万",yoy:null},{label:"付费活跃",value:"25.09万",yoy:null},{label:"付费活跃率",value:"2.46%",yoy:null},{label:"APP活跃",value:"56.56万",yoy:null},{label:"高价值",value:"49.84万",yoy:null}],struct:{caption:"到达结构",groups:[{label:"到达产品结构",rows:[{label:"空间包",value:.9738},{label:"会员",value:.0262}]},{label:"新增产品结构",rows:[{label:"空间包",value:.9608},{label:"会员",value:.0392}]}]},added:[{label:"当月新增",value:"34.70万",yoy:-16.33},{label:"叠加态新增",value:"1.80万",yoy:null}],quality:[{label:"T+1活跃率",value:"6.55%",note:null},{label:"T+3退订率",value:"13.44%",note:null},{label:"户均实收",value:"11.06元",note:"元/户"},{label:"低值占比",value:"17.88%",note:null}],income:{report:{now:3843.57,cum:26527.23,yoy:.61},bill:{now:117.88,cum:666.45,yoy:-8.87},extra:{name:"叠加态收入",now:499.26,cum:3467.11,yoy:-23.87}},channel:[{label:"线下",value:.4327},{label:"10086",value:.5053},{label:"线上",value:.062}]},spcl:{name:"视频彩铃",key:"spcl",scale:[{label:"到达用户",value:"1750.07万",yoy:null},{label:"叠加态到达",value:"145.32万",yoy:null},{label:"付费活跃",value:"103.83万",yoy:null},{label:"付费活跃率",value:"83.84%",yoy:null},{label:"高价值",value:"74.09万",yoy:null}],struct:{caption:"到达结构",groups:[{label:"到达产品结构",rows:[{label:"功能产品",value:.9292},{label:"会员产品",value:.0708}]},{label:"新增产品结构",rows:[{label:"功能产品",value:.9563},{label:"会员产品",value:.0437}]}]},added:[{label:"当月新增",value:"53.97万",yoy:-3.97},{label:"叠加态新增",value:"2.21万",yoy:null}],quality:[{label:"T+3退订率",value:"6.50%",note:"O20口径"},{label:"T+6退订率",value:"15.06%",note:"P20口径"},{label:"户均实收",value:"3.90元",note:"元/户"},{label:"低值占比",value:"7.26%",note:null}],income:{report:{now:2826.21,cum:17832.9,yoy:-22.55},bill:{now:1437.37,cum:7441.77,yoy:.89},extra:{name:"叠加态收入",now:1599.98,cum:8640.08,yoy:-3.67}},channel:[{label:"线下",value:.5056},{label:"10086",value:.4066},{label:"线上",value:.017}]},mgsp:{name:"咪咕视频",key:"mgsp",scale:[{label:"到达用户",value:"884.70万",yoy:null},{label:"叠加态到达",value:"105.72万",yoy:null},{label:"付费活跃",value:"33.32万",yoy:null},{label:"付费活跃率",value:"3.77%",yoy:null},{label:"APP活跃",value:"87.61万",yoy:null},{label:"高价值",value:"35.28万",yoy:null}],struct:{caption:"到达结构",groups:[{label:"到达产品结构",rows:[{label:"视频会员",value:.9981},{label:"体育会员",value:.0019}]},{label:"新增产品结构",rows:[{label:"视频会员",value:.99995},{label:"体育会员",value:5e-5}]}]},added:[{label:"当月新增",value:"28.02万",yoy:-40.06},{label:"叠加态新增",value:"2.83万",yoy:null}],quality:[{label:"T+1活跃率",value:"8.21%",note:null},{label:"T+3退订率",value:"16.36%",note:"P20口径"},{label:"户均实收",value:"2.57元",note:"元/户"},{label:"低值占比",value:"5.30%",note:null}],income:{report:{now:2475.23,cum:17732.58,yoy:-22.98},bill:{now:199.98,cum:1770.28,yoy:-21.68},extra:{name:"叠加态收入",now:608.59,cum:4746.11,yoy:-43.72}},channel:[{label:"线下",value:.6264},{label:"10086",value:.3178},{label:"线上",value:.0557}]},wxth:{name:"5G新通话",key:"wxth",scale:[{label:"到达用户",value:"94.50万",yoy:null},{label:"付费活跃",value:"7.71万",yoy:null},{label:"付费活跃率",value:"8.16%",yoy:null}],struct:{caption:"到达结构",groups:[{label:"到达产品结构",rows:[{label:"明星来电",value:.6231},{label:"AI速记",value:.0169},{label:"能量包",value:.0016}]},{label:"新增产品结构",rows:[{label:"明星来电",value:.8475},{label:"AI速记",value:.0496},{label:"能量包",value:7e-4}]}]},added:[{label:"当月新增",value:"17.52万",yoy:null,warn:"同比+1279.59%（低基数）"}],quality:[{label:"T+3退订率",value:"3.36%",note:null},{label:"T+6退订率",value:"17.12%",note:null},{label:"户均实收",value:"1.18元",note:"元/户"},{label:"低值占比",value:"91.51%",note:null}],income:{report:{now:122.18,cum:386.36,yoy:1775.78},bill:{now:97.42,cum:466.84,yoy:2104.99},extra:{name:"叠加态收入",now:96.47,cum:464.43,yoy:100}},channel:[{label:"线下",value:.594},{label:"10086",value:.3807},{label:"线上",value:.0255}]},ldgj:{name:"来电管家",key:"ldgj",scale:[{label:"到达用户",value:"521.84万",yoy:null},{label:"叠加态到达",value:"457.60万",yoy:null},{label:"付费活跃",value:"377.53万",yoy:null},{label:"付费活跃率",value:"72.35%",yoy:null},{label:"高价值",value:"244.56万",yoy:null}],struct:{caption:"到达结构",groups:[{label:"到达产品结构",rows:[{label:"升级版",value:.0252}]},{label:"新增产品结构",rows:[{label:"升级版",value:.1538}]}]},added:[{label:"当月新增",value:"9.77万",yoy:-38.26},{label:"叠加态新增",value:"9.76万",yoy:null}],quality:[{label:"T+3退订率",value:"3.30%",note:null},{label:"T+6退订率",value:"7.07%",note:"N20口径"},{label:"户均实收",value:"1.94元",note:"元/户"},{label:"低值占比",value:"17.88%",note:null}],income:{report:{now:1105.91,cum:7868.66,yoy:-2.35},bill:{now:1000.2,cum:7023.34,yoy:1.07}},channel:[{label:"线下",value:.9908},{label:"10086",value:.0073},{label:"线上",value:.0019}]},dphy:{name:"大屏会员",key:"dphy",scale:[{label:"到达用户",value:"200.73万",yoy:null,note:"当月活跃率暂缺"}],struct:{caption:"到达结构",groups:[{label:"会员结构",rows:[{label:"重点会员占比",value:.3792},{label:"新增重点会员占比",value:.7523}]}]},added:[{label:"当月新增",value:"4.69万",yoy:null}],quality:[{label:"T+3退订率",value:"13.86%",note:"4882户"},{label:"T+6退订率",value:"25.97%",note:"18086户"},{label:"户均实收",value:"13.80元",note:"元/户"},{label:"实收0元占比",value:"1.81%",note:null}],income:{report:{now:1748.93,cum:13700.67,yoy:-5.41},bill:{now:1452.62,cum:10170.51,yoy:-5.37}},channel:[{label:"线下",value:.3428},{label:"10086",value:.0316},{label:"线上",value:.6255}]}}
const dimDefinitions = [{key:"eff",name:"效益维度",weight:"45%",full:45,items:[{name:"累计收入",full:9},{name:"月均ARPU",full:11.25},{name:"收入环比",full:6.75},{name:"营销费用占比",full:9},{name:"利润率",full:9}]},{key:"market",name:"市场维度",weight:"25%",full:25,items:[{name:"当前活跃用户",full:8.75},{name:"月净增用户",full:6.25},{name:"订购转化率",full:6.25},{name:"渠道上架率",full:3.75}]},{key:"quality",name:"运营质量维度",weight:"20%",full:20,items:[{name:"资费投诉率",full:8},{name:"月度退订率",full:6},{name:"业务办理成功率",full:6}]},{key:"life",name:"生命周期维度",weight:"10%",full:10,items:[{name:"新用户吸引占比",full:4.5},{name:"沉默用户占比",full:3.5},{name:"同类资费排名",full:2}]}]
const suiteHealthMap = {mxld:{name:"5G新通话-明星来电",health:88,healthDelta:"+3",healthLevel:"良好",healthNote:"整体稳健，新增与收入双升",dims:{eff:[{s:9,v:"286.5万"},{s:8,v:"1.02元"},{s:9,v:"+18.3%"},{s:8,v:"11.2%"},{s:8,v:"21.5%"}],market:[{s:10,v:"58.9万"},{s:9,v:"11.2万"},{s:8,v:"8.6%"},{s:9,v:"92%"}],quality:[{s:9,v:"0.12%"},{s:8,v:"2.6%"},{s:9,v:"99.1%"}],life:[{s:9,v:"18.4%"},{s:8,v:"7.2%"},{s:9,v:"前20%"}]},anomalies:[{level:"high",text:"T+6退订率 17.4%，超同类均值 5.3pp，重点监控"},{level:"mid",text:"权益装机带动新增，但低值占比偏高"}],scale:{label:"到达用户",value:"58.9万"},added:{label:"当月新增",value:"11.2万",delta:"+18.3%"},income:{label:"报表收入",value:"75.3万",delta:"+89.6%"},quality:[{label:"付费活跃率",value:"8.42%",delta:"+1.1pp"},{label:"T+6退订率",value:"17.4%",delta:"+2.6pp"},{label:"户均实收",value:"1.02元",delta:null}]},dlsp:{name:"5G新通话-点亮屏幕标准版",health:85,healthDelta:"+2",healthLevel:"健康",healthNote:"版本结构逐步收敛，新增回升、退订健康",dims:{eff:[{s:8,v:"112.6万"},{s:8,v:"0.72元"},{s:9,v:"+6.8%"},{s:8,v:"13.5%"},{s:9,v:"18.4%"}],market:[{s:8,v:"21.4万"},{s:8,v:"1.2万"},{s:9,v:"7.8%"},{s:9,v:"92%"}],quality:[{s:9,v:"0.18%"},{s:8,v:"8.6%"},{s:9,v:"99.0%"}],life:[{s:9,v:"15.2%"},{s:8,v:"9.6%"},{s:9,v:"中上游"}]},anomalies:[{level:"low",text:"新增小幅回升，版本结构逐步收敛，暂无异动"}],scale:{label:"到达用户",value:"21.4万"},added:{label:"当月新增",value:"1.8万",delta:"+18.6%"},income:{label:"报表收入",value:"28.9万",delta:"+9.4%"},quality:[{label:"付费活跃率",value:"6.2%",delta:"+0.8pp"},{label:"T+6退订率",value:"12.4%",delta:"-1.6pp"},{label:"户均实收",value:"1.34元",delta:null}]},aisj:{name:"5G新通话-AI速记100分钟包",health:85,healthDelta:"+1",healthLevel:"健康",healthNote:"分钟包梯度清晰，长期包承接良好，增长稳健",dims:{eff:[{s:9,v:"46.2万"},{s:8,v:"2.74元"},{s:9,v:"+9.2%"},{s:8,v:"12.8%"},{s:8,v:"18.6%"}],market:[{s:9,v:"3.2万"},{s:8,v:"1.0万"},{s:8,v:"8.1%"},{s:9,v:"90%"}],quality:[{s:9,v:"0.12%"},{s:9,v:"7.4%"},{s:9,v:"98.9%"}],life:[{s:8,v:"13.8%"},{s:8,v:"9.2%"},{s:9,v:"中上游"}]},anomalies:[{level:"low",text:"推出 300 分钟包后，长期包承接良好，本包新增稳定"}],scale:{label:"到达用户",value:"3.2万"},added:{label:"当月新增",value:"1.0万",delta:"+9.2%"},income:{label:"报表收入",value:"9.2万",delta:"+14.1%"},quality:[{label:"付费活跃率",value:"13.4%",delta:"+0.8pp"},{label:"T+6退订率",value:"7.4%",delta:"-1.2pp"},{label:"户均实收",value:"2.74元",delta:null}]},znfy:{name:"5G新通话-智能翻译100分钟包",health:84,healthDelta:"+2",healthLevel:"良好",healthNote:"需求稳定，复购良好",dims:{eff:[{s:9,v:"58.6万"},{s:9,v:"2.81元"},{s:9,v:"+9.4%"},{s:9,v:"9.6%"},{s:9,v:"24.8%"}],market:[{s:9,v:"1.4万"},{s:9,v:"0.4万"},{s:9,v:"8.8%"},{s:9,v:"95%"}],quality:[{s:9,v:"0.08%"},{s:9,v:"7.9%"},{s:10,v:"99.3%"}],life:[{s:9,v:"16.2%"},{s:9,v:"6.4%"},{s:9,v:"前20%"}]},anomalies:[{level:"low",text:"叠加包复购占比提升至 32%，可做到期提醒运营"}],scale:{label:"到达用户",value:"1.4万"},added:{label:"当月新增",value:"0.4万",delta:"+5.6%"},income:{label:"报表收入",value:"3.9万",delta:"+7.2%"},quality:[{label:"付费活跃率",value:"14.1%",delta:"+0.4pp"},{label:"T+6退订率",value:"7.9%",delta:"-0.9pp"},{label:"户均实收",value:"2.81元",delta:null}]},tscy:{name:"WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)",health:72,healthDelta:"-9",healthLevel:"亚健康",healthNote:'典型"竞争性新增萎缩"——高速配额配置落后竞品均值29%，年轻出境客群转化下降；存量漫游用户基本稳定，问题在获客端',dims:{eff:[{s:8,v:"1,451万/月"},{s:7,v:"168元"},{s:7,v:"-1.6%"},{s:7,v:"16.5%"},{s:7,v:"14.2%"}],market:[{s:7,v:"86,420户"},{s:6,v:"0.28万"},{s:6,v:"5.2%"},{s:8,v:"90%"}],quality:[{s:8,v:"0.03%"},{s:8,v:"3.1%"},{s:9,v:"99.2%"}],life:[{s:6,v:"11.2%"},{s:6,v:"9.8%"},{s:6,v:"中游偏下"}]},anomalies:[{level:"high",text:"新增下滑 35% 已持续 7 天，累计影响新增订购约 670 单，折合月收入损失约 11.8 万元"},{level:"high",text:"新增转化仅 62 分（↓18），跌破正常水平，年轻客群在信息流渠道被截流"},{level:"mid",text:"竞品对标 68 分（↓12），高速配额 30GB 落后竞品均值 42.5GB 约 29%"},{level:"low",text:"存量基本盘稳定：投诉率 85 分、退订日均 82 单（+5.1%）均在正常范围"}],scale:{label:"累计销售",value:"86,420户"},added:{label:"日均新增",value:"177单",delta:"-35%"},income:{label:"月收入",value:"1,451万",delta:"+0.3%"},quality:[{label:"月度退订",value:"82单/日",delta:"+5.1%"},{label:"投诉率",value:"85分",delta:"-2"},{label:"续约率",value:"78分",delta:"-5"}]},nlb:{name:"5G新通话能量包A",health:85,healthDelta:"+2",healthLevel:"健康",healthNote:"体量稳步增长，户均实收改善，结构健康",dims:{eff:[{s:8,v:"27.8万"},{s:8,v:"0.71元"},{s:9,v:"+4.8%"},{s:8,v:"14.2%"},{s:9,v:"16.8%"}],market:[{s:8,v:"1.8万"},{s:8,v:"0.4万"},{s:9,v:"7.6%"},{s:9,v:"84%"}],quality:[{s:9,v:"0.16%"},{s:8,v:"9.8%"},{s:8,v:"98.2%"}],life:[{s:9,v:"12.6%"},{s:8,v:"10.4%"},{s:9,v:"中上游"}]},anomalies:[{level:"low",text:"覆盖与活跃双升，暂无显著异动"}],scale:{label:"到达用户",value:"1.8万"},added:{label:"当月新增",value:"0.4万",delta:"+18.6%"},income:{label:"报表收入",value:"2.1万",delta:"+8.4%"},quality:[{label:"付费活跃率",value:"10.6%",delta:"+0.9pp"},{label:"T+6退订率",value:"9.8%",delta:"-1.2pp"},{label:"户均实收",value:"0.71元",delta:null}]},aith:{name:"5G新通话-AI通话黄金会员",health:82,healthDelta:"+2",healthLevel:"良好",healthNote:"会员分层承接良好",dims:{eff:[{s:9,v:"96.2万"},{s:10,v:"2.84元"},{s:9,v:"+11.8%"},{s:8,v:"8.4%"},{s:9,v:"26.1%"}],market:[{s:9,v:"2.6万"},{s:9,v:"0.7万"},{s:9,v:"9.2%"},{s:10,v:"96%"}],quality:[{s:9,v:"0.06%"},{s:9,v:"8.4%"},{s:9,v:"99.4%"}],life:[{s:9,v:"15.8%"},{s:9,v:"5.6%"},{s:10,v:"前20%"}]},anomalies:[{level:"low",text:"黄金/钻石会员 ARPU 明显高于基础会员"}],scale:{label:"到达用户",value:"2.6万"},added:{label:"当月新增",value:"0.7万",delta:"+9.2%"},income:{label:"报表收入",value:"7.4万",delta:"+11.8%"},quality:[{label:"付费活跃率",value:"11.3%",delta:"+0.7pp"},{label:"T+6退订率",value:"8.4%",delta:"-1.0pp"},{label:"户均实收",value:"2.84元",delta:null}]},qwth:{name:"5G新通话-趣味通话",health:86,healthDelta:"+1",healthLevel:"健康",healthNote:"趣味玩法持续带动低频用户活跃，增长健康",dims:{eff:[{s:9,v:"32.6万"},{s:8,v:"2.24元"},{s:9,v:"+6.2%"},{s:9,v:"11.4%"},{s:8,v:"18.2%"}],market:[{s:8,v:"1.1万"},{s:8,v:"0.4万"},{s:9,v:"8.6%"},{s:9,v:"88%"}],quality:[{s:9,v:"0.12%"},{s:9,v:"7.8%"},{s:9,v:"98.8%"}],life:[{s:8,v:"15.4%"},{s:9,v:"8.6%"},{s:9,v:"中上游"}]},anomalies:[{level:"low",text:"潮智版占比提升，年轻客群稳定增长"}],scale:{label:"到达用户",value:"1.1万"},added:{label:"当月新增",value:"0.4万",delta:"+12.8%"},income:{label:"报表收入",value:"2.6万",delta:"+9.4%"},quality:[{label:"付费活跃率",value:"11.6%",delta:"+0.7pp"},{label:"T+6退订率",value:"7.8%",delta:"-0.9pp"},{label:"户均实收",value:"2.24元",delta:null}]},yyb:{name:"5G新通话6元包",health:86,healthDelta:"+1",healthLevel:"健康",healthNote:"入门引流型产品，覆盖稳定，向高阶包转化良好",dims:{eff:[{s:8,v:"68.4万"},{s:9,v:"0.48元"},{s:9,v:"+12.4%"},{s:8,v:"15.6%"},{s:8,v:"15.8%"}],market:[{s:9,v:"12.3万"},{s:9,v:"2.6万"},{s:9,v:"8.8%"},{s:9,v:"92%"}],quality:[{s:9,v:"0.18%"},{s:9,v:"8.4%"},{s:9,v:"98.6%"}],life:[{s:9,v:"21.4%"},{s:8,v:"8.8%"},{s:8,v:"中上游"}]},anomalies:[{level:"low",text:"作为低门槛入口，向高阶功能包转化良好"}],scale:{label:"到达用户",value:"12.3万"},added:{label:"当月新增",value:"2.6万",delta:"+12.4%"},income:{label:"报表收入",value:"5.6万",delta:"+13.8%"},quality:[{label:"付费活跃率",value:"9.8%",delta:"+0.7pp"},{label:"T+6退订率",value:"8.4%",delta:"-1.1pp"},{label:"户均实收",value:"0.48元",delta:null}]},thzm:{name:"5G新通话-通话字幕",health:83,healthDelta:"+1",healthLevel:"良好",healthNote:"基础能力承载，使用稳定",dims:{eff:[{s:9,v:"46.2万"},{s:9,v:"0.81元"},{s:9,v:"+9.8%"},{s:9,v:"10.4%"},{s:8,v:"22.6%"}],market:[{s:9,v:"3.6万"},{s:9,v:"0.5万"},{s:9,v:"8.4%"},{s:9,v:"93%"}],quality:[{s:9,v:"0.09%"},{s:9,v:"9.7%"},{s:9,v:"99.0%"}],life:[{s:9,v:"14.6%"},{s:9,v:"8.2%"},{s:9,v:"前30%"}]},anomalies:[{level:"low",text:"作为能力底座，承载各功能产品增强包"}],scale:{label:"到达用户",value:"3.6万"},added:{label:"当月新增",value:"0.5万",delta:"+4.2%"},income:{label:"报表收入",value:"2.9万",delta:"+9.8%"},quality:[{label:"付费活跃率",value:"10.4%",delta:"+0.2pp"},{label:"T+6退订率",value:"9.7%",delta:"-0.5pp"},{label:"户均实收",value:"0.81元",delta:null}]}}

const productKeyword = ref('')
const filteredProducts = computed(() => {
  const keys = Object.keys(productDataMap)
  const kw = productKeyword.value.trim()
  if (!kw) return keys.map((k) => ({ key: k, name: productDataMap[k].name }))
  return keys.filter((k) => productDataMap[k].name.includes(kw)).map((k) => ({ key: k, name: productDataMap[k].name }))
})
const activeProductKey = ref(null)
const activeProductList = computed(() => (activeProductKey.value ? [productDataMap[activeProductKey.value]].filter(Boolean) : []))

/* ---- 5G新通话 下钻 ---- */
function isDrillable(key) {
  return key === 'wxth'
}
const drillOpen = ref(false)
const drillWrap = ref(null)
const activeDrill = ref(null)
const highlightKey = ref(null)

const drillProductKeys = computed(() => Object.keys(drillProductMap.value))
const activeDrillItem = computed(() => (activeDrill.value ? drillProductMap.value[activeDrill.value] || null : null))
const drillHealthScore = computed(() => (activeDrillItem.value ? activeDrillItem.value.health : '—'))
const drillHealthLevel = computed(() => (activeDrillItem.value ? activeDrillItem.value.healthLevel : '—'))

function healthCls(level) {
  if (level === '健康' || level === '良好') return 'ov-health-good'
  if (level === '亚健康') return 'ov-health-warn'
  return 'ov-health-bad'
}
function deltaCls(delta) {
  const s = String(delta || '')
  return /^\+/.test(s) ? 'ov-delta-up' : /^-/.test(s) ? 'ov-delta-down' : 'ov-delta-flat'
}
function productHealthLevel(key) {
  return (drillProductMap.value[key] || {}).healthLevel || '—'
}
function productHealthScore(key) {
  return (drillProductMap.value[key] || {}).health || '—'
}

/* ---- 健康度维度（h × p.dims） ---- */
const drillDims = computed(() => {
  const item = activeDrillItem.value
  if (!item || !item.dims) return []
  return dimDefinitions.map((dim) => {
    const scores = item.dims[dim.key] || []
    const items = (dim.items || []).map((it, i) => {
      const s = scores[i] ? scores[i].s : 0
      const v = scores[i] ? scores[i].v : '—'
      return { name: it.name, value: v, score: s, full: it.full }
    })
    const total = items.reduce((sum, it) => sum + (it.score || 0), 0)
    const fullTotal = dim.full || items.reduce((sum, it) => sum + (it.full || 0), 0) || 1
    return { ...dim, items, score: total, pct: Math.min(100, Math.round((total / fullTotal) * 100)) }
  })
})

/* ---- 异动定位 ---- */
const abnormalProducts = computed(() => {
  const count = Object.values(suiteHealthMap).filter((s) => s.healthLevel === '亚健康' && !s.launched).length
  return { count }
})
function locateAbnormal() {
  activeProductKey.value = 'wxth'
  drillOpen.value = true
  const target = drillProductKeys.value.find((k) => drillProductMap.value[k].healthLevel === '亚健康' && !drillProductMap.value[k].launched)
  if (target) {
    activeDrill.value = target
    highlightKey.value = target
  }
  nextTick(() => {
    if (drillWrap.value && drillWrap.value.scrollIntoView) {
      drillWrap.value.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
    setTimeout(() => {
      highlightKey.value = null
    }, 3000)
  })
}

/* ================= 流量运营（智能运营视图） ================= */
const tfPersp = computed({
  get: () => perspectiveStore.opsPerspective,
  set: (v) => perspectiveStore.setOpsPerspective(v),
})

const tfOverview = computed(() =>
  tfPersp.value === 'platform' ? platformOverview : agentViewOverview
)
const tfUsageRateMoM = computed(
  () => tfOverview.value.usageRateMoM ?? tfOverview.value.overallUsageRateMoM ?? '—'
)
const tfUsageRateYoY = computed(
  () => tfOverview.value.usageRateYoY ?? tfOverview.value.overallUsageRateYoY ?? '—'
)

const agentDetail = computed(() => agentViewDetail)
const profitDetail = computed(() => agentProfitDetail || { byPackage: [] })

function trendArrow(v) {
  const s = String(v || '').trim()
  return /^-/.test(s) ? '↓' : '↑'
}
function riskLevel(rate) {
  if (rate >= 80) return { text: '偏高 · 成本受挤压', cls: 'ov-tf-risk' }
  if (rate >= 50) return { text: '中等', cls: 'ov-tf-mid' }
  return { text: '偏低 · 利润结构优', cls: 'ov-tf-ok' }
}
function statusKey(status) {
  if (status === '偏高') return 'high'
  if (status === '偏低') return 'low'
  return 'mid'
}
function momDir(v) {
  return v > 0 ? 'up' : v < 0 ? 'down' : 'flat'
}
function marginKey(v) {
  if (v >= 80) return 'high'
  if (v >= 55) return 'mid'
  return 'low'
}
function trendIcon(t) {
  return t === 'up' ? '↑' : t === 'down' ? '↓' : '→'
}
function trendCls(t) {
  return t === 'up' ? 'ov-tf-up' : t === 'down' ? 'ov-tf-down' : 'ov-tf-flat'
}

/* ---- 运营分析 Tab ---- */
// 初始即按当前运营视角的默认页签展示（平台→流量趋势 / 代理商→个人用户），
// 避免进入代理商视角时停留在平台页签导致"运营分析"区域空白需手动点击铺开
const anTab = ref(perspectiveStore.opsDefaultTab)
watch(tfPersp, (v) => {
  anTab.value = v === 'agent' ? 'customer' : 'trend'
})

/* ---- 流量趋势 ---- */
const trendMetric = ref('used')
const trendRange = ref('d7')
const trendRanges = [
  { k: 'd7', t: '近7天' },
  { k: 'd30', t: '近30天' },
  { k: 'm3', t: '近3个月' },
  { k: 'm6', t: '近6个月' },
  { k: 'm12', t: '近12个月' },
]
const trendMetrics = [
  { k: 'used', t: '使用量' },
  { k: 'rate', t: '使用率' },
  { k: 'remain', t: '剩余量' },
]
const trendRangeLabel = computed(() => trendRanges.find((r) => r.k === trendRange.value)?.t || '')
const trendMetricLabel = computed(() => trendMetrics.find((m) => m.k === trendMetric.value)?.t || '')

const trendData = computed(() => {
  const r = trendRange.value
  if (r === 'd7') return trafficDaily.slice(-7)
  if (r === 'd30') return trafficDaily
  if (r === 'm3') return trafficMonthly.slice(-3)
  if (r === 'm6') return trafficMonthly.slice(-6)
  return trafficMonthly
})
function trendValue(point, metric) {
  if (metric === 'used') return point.usedGB
  if (metric === 'rate') return point.usageRate
  return point.remainingGB
}
function trendUnit(metric) {
  return metric === 'rate' ? '%' : 'GB'
}
function trendBarHeight(point) {
  if (trendMetric.value === 'rate') return Math.min(100, point.usageRate)
  const key = trendMetric.value === 'used' ? 'usedGB' : 'remainingGB'
  const max = Math.max(...trendData.value.map((p) => p[key] ?? 0))
  return max > 0 ? Math.max(6, (point[key] / max) * 100) : 6
}

const trendSelected = ref(null)
const trendSelectedPoint = computed(() => {
  const i = trendSelected.value
  return i != null ? trendData.value[i] || null : null
})
const topGrowthAgent = computed(() => [...agents].sort((a, b) => b.momPct - a.momPct).slice(0, 2)[0] || { name: '—', momPct: 0 })
const topDeclineAgent = computed(() => [...agents].sort((a, b) => a.momPct - b.momPct).slice(0, 2)[0] || { name: '—', momPct: 0 })

function selectTrend(i) {
  trendSelected.value = i
  const point = trendData.value[i]
  if (!point) return
  const up = topGrowthAgent.value
  const down = topDeclineAgent.value
  askAi(
    `分析${trendRangeLabel.value}${point.label}的${trendMetricLabel.value}（${trendValue(point, trendMetric.value)}${trendUnit(trendMetric.value)}，环比 ${point.momPct > 0 ? '+' + point.momPct : point.momPct}%），主要增长代理商 ${up.name}（+${up.momPct}%）、主要下降代理商 ${down.name}（${down.momPct}%），请定位来源并判断是否异常`
  )
}

/* ---- 代理商 ---- */
const agentSort = ref('used')
const agentSortOptions = [
  { k: 'used', t: '使用量' },
  { k: 'rate', t: '使用率' },
  { k: 'mom', t: '增长率' },
  { k: 'remain', t: '剩余量' },
]
const agentSortLabel = computed(() => agentSortOptions.find((s) => s.k === agentSort.value)?.t || '使用量')
const agentRank = computed(() => {
  const key = { used: 'usedGB', rate: 'usageRate', mom: 'momPct', remain: 'remainingGB' }[agentSort.value]
  return [...agents].sort((a, b) => (b[key] ?? 0) - (a[key] ?? 0))
})

/* ---- 地区 ---- */
const regionSelected = ref(null)
const regionRank = computed(() => [...regions].sort((a, b) => b.usageRate - a.usageRate))
const activeRegion = computed(() => (regionSelected.value ? regions.find((r) => r.name === regionSelected.value) || null : null))
const regionAgents = computed(() => {
  if (!activeRegion.value) return []
  return (activeRegion.value.agentFlow || []).map((a) => {
    const known = agents.find((x) => x.code === a.code)
    const bought = known ? known.boughtGB : Math.round(a.usedGB / (a.usageRate / 100) || 0)
    return {
      ...a,
      region: known ? known.region : '',
      boughtGB: bought,
      remainingGB: Math.max(bought - a.usedGB, 0),
      status: a.usageRate >= 60 ? '偏高' : a.usageRate >= 50 ? '正常' : '偏低',
    }
  })
})

/* ---- 个人用户 ---- */
const userSort = ref('used')
const userSortOptions = [
  { k: 'used', t: '使用量' },
  { k: 'rate', t: '使用率' },
  { k: 'remain', t: '剩余量' },
]
const userSortLabel = computed(() => ({ used: '使用量', rate: '使用率', remain: '剩余量' }[userSort.value] || '使用量'))
const personalUserList = computed(() => {
  const list = agentDetail.value.personalUsers || agentDetail.value.customers || []
  const keyFn = { used: (r) => r.usedGB, rate: (r) => r.usageRate, remain: (r) => r.remainingGB }[userSort.value] || ((r) => r.usedGB)
  return [...list].sort((a, b) => keyFn(b) - keyFn(a))
})

/* ---- 子渠道 ---- */
const channelSort = ref('used')
const channelSortOptions = [
  { k: 'used', t: '使用量' },
  { k: 'rate', t: '使用率' },
  { k: 'remain', t: '剩余量' },
  { k: 'level', t: '级别' },
]
const channelSortLabel = computed(() => ({ used: '使用量', rate: '使用率', remain: '剩余量', level: '级别' }[channelSort.value] || '使用量'))
const subChannelList = computed(() => {
  const list = agentDetail.value.subChannels || []
  const keyFn = {
    used: (r) => r.usedGB,
    rate: (r) => r.usageRate,
    remain: (r) => r.remainingGB,
    level: (r) => r.level,
  }[channelSort.value] || ((r) => r.usedGB)
  return [...list].sort((a, b) => keyFn(b) - keyFn(a))
})

/* ---- 套餐 ---- */
const activePkgName = ref('')
const activePkg = computed(() => (activePkgName.value && (agentDetail.value.pkgDetail || {})[activePkgName.value]) || null)
const packageList = computed(() =>
  (agentDetail.value.byPackage || []).map((p) => {
    const d = (agentDetail.value.pkgDetail || {})[p.name]
    return {
      ...p,
      nameShort: p.name.replace('WeWalk ', ''),
      userCount: d?.userCount ?? p.customers,
      orderCount: p.customers ?? d?.customerCount ?? 0, // 订购量（使用该套餐的订购客户数）
    }
  })
)
const optimize = computed(() => {
  const o = agentDetail.value.optimize || { highUsage: [], highRemaining: [], structure: '' }
  const withCount = (arr = []) =>
    arr.map((it) => {
      const d = (agentDetail.value.pkgDetail || {})[it.name]
      return { ...it, userCount: d?.userCount ?? it.customers }
    })
  return { ...o, highUsage: withCount(o.highUsage), highRemaining: withCount(o.highRemaining) }
})

/* ---- 重点运营机会 ---- */
const focusTab = ref('flow')
const focusTabs = computed(() => platformFocus || [])
const focusItems = computed(() => {
  const t = focusTabs.value.find((x) => x.key === focusTab.value)
  return t ? t.items : []
})
const focusSelected = ref(null)
const focusDetail = computed(() => {
  const item = focusSelected.value
  if (!item) return null
  if (item.type === 'region') {
    const r = regions.find((x) => x.name === item.name)
    if (r) return { typeLabel: '地区', ...r }
  }
  const a = agents.find((x) => x.name === item.name)
  if (a) return { typeLabel: '代理商', ...a }
  return {
    typeLabel: item.type === 'agent' ? '代理商' : '地区',
    name: item.name,
    boughtGB: '—',
    usedGB: '—',
    usageRate: '—',
    remainingGB: '—',
    momPct: '—',
  }
})
const topUsageRegions = computed(() => [...regions].sort((a, b) => b.usageRate - a.usageRate).slice(0, 3))
const focusRelated = computed(() => {
  const item = focusSelected.value
  if (!item) return []
  if (item.type === 'region') {
    const r = regions.find((x) => x.name === item.name)
    return (r?.agentFlow || []).map((a) => {
      const known = agents.find((x) => x.code === a.code)
      return { name: a.name, usedGB: a.usedGB, usageRate: a.usageRate, momPct: a.momPct, region: known ? known.region : '' }
    })
  }
  const a = agents.find((x) => x.name === item.name)
  return a ? [{ name: a.name, usedGB: a.usedGB, usageRate: a.usageRate, momPct: a.momPct, region: a.region }] : []
})
function openFocusItem(item) {
  if (item) focusSelected.value = item
}

/* ---- AI 运营洞察 ---- */
const aiInsights = [
  {
    level: 'high',
    title: '鑫联国际流量异常增长',
    detail: '本月流量使用量 2216GB 较前周期大幅增长，需关注流量去向与承接能力。',
    action: '查看原因',
    ask: '鑫联国际本月流量用量异常增长，请分析主要增长来源与流量去向',
  },
  {
    level: 'high',
    title: '中国内地漫游地流量异常增长',
    detail: '漫游地用量 3210GB、使用率 61.7%，环比 +8.6%，主要来自高使用率代理商。',
    action: '查看原因',
    ask: '中国内地漫游地流量用量与使用率双升，请分析主要增长来源与代理商构成',
  },
  {
    level: 'mid',
    title: '南非漫游地流量使用率低迷',
    detail: '漫游地使用率 37.5%、余量充足，低档套餐铺货后消化偏慢，资源未充分变现。',
    action: '查看原因',
    ask: '南非漫游地使用率低迷，分析是否涉及客户流失或套餐调整、评估资源利用机会',
  },
]

function askAi(text) {
  if (text) emit('ask-ai', text)
}

/* ================= 视图 Tab 与下钻状态 ================= */
const opsViewTab = ref('traffic')
watch(
  () => props.initialTab,
  (v) => {
    if (v === 'traffic') opsViewTab.value = 'traffic'
  }
)

/* ---- 产品下钻（5G新通话） ---- */
const drillProductMap = computed(() => {
  const map = {}
  for (const [key, s] of Object.entries(suiteHealthMap)) {
    map[key] = { ...s }
  }
  for (const wo of workOrderStore.archived || []) {
    const name = wo.productName || ''
    if (!name) continue
    if (Object.values(map).some((m) => m.name === name)) continue
    if (wo.seeded) continue
    if (wo.status === 'offline') continue
    const key = 'wo_' + (wo.workOrderId || name.replace(/[^\w\u4e00-\u9fff]/g, ''))
    if (map[key]) continue
    map[key] = {
      name,
      launched: true,
      launchedAt: wo.salesAt ? fmtDateTime(wo.salesAt) : '—',
      scale: { label: '到达用户', value: '待通报' },
      added: { label: '当月新增', value: '—', delta: null },
      income: { label: '报表收入', value: '—', delta: null },
      quality: [],
      dims: { eff: [], market: [], quality: [], life: [] },
      anomalies: [],
      health: '—',
      healthLevel: '—',
      healthNote: '',
    }
  }
  return map
})
</script>

<script>
import { reactive } from 'vue'

export const opsDrillState = reactive({
  key: null,
  reset() {
    this.key = null
  },
})
</script>

<style scoped>
.ops-view{width:100%;max-width:980px;margin:0 auto;text-align:left;display:flex;flex-direction:column;gap:16px;font-family:Noto Sans SC,-apple-system,BlinkMacSystemFont,Segoe UI,sans-serif}
.ov-panel{background:linear-gradient(180deg,#e6effb,#f0f9ff 6%,#fafbff 15%,#fff 28%);background-color:#fff;border-radius:8px;box-shadow:0 1px 3px #0000000f;border:1px solid #eef2f7;overflow:hidden}
.ov-title{font-size:16px;font-weight:600;color:#334155;margin:0}
.ov-rev-panel{padding-bottom:18px}
.ov-panel-title-bar{display:flex;align-items:baseline;justify-content:space-between;gap:10px;flex-wrap:wrap;padding:16px 18px 12px}
.ov-title-note{font-size:11px;font-weight:500;color:#94a3b8}
.ov-rev-row{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;padding:6px 18px 0}
.ov-rev-card{background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:14px}
.ov-rev-card-head{display:flex;align-items:center;gap:10px;margin-bottom:12px}
.ov-rev-icon{width:38px;height:38px;border-radius:50%;display:flex;align-items:center;justify-content:center;flex-shrink:0}
.ov-icon-blue{background:#e0f2fe;color:#2563eb}
.ov-icon-emerald{background:#d1fae5;color:#059669}
.ov-icon-amber{background:#fef3c7;color:#d97706}
.ov-rev-head-title{font-size:15px;font-weight:700;color:#1e293b}
.ov-rev-body{display:flex;flex-direction:column;gap:12px}
.ov-rev-metric{padding-top:10px;border-top:1px dashed #eef2f7}
.ov-rev-label{font-size:11px;color:#94a3b8;margin-bottom:3px}
.ov-rev-value-row{display:flex;align-items:baseline}
.ov-rev-now{font-size:26px;font-weight:800;color:#0f172a;line-height:1.1;letter-spacing:-.02em}
.ov-rev-unit{font-size:12px;font-weight:600;color:#64748b;margin-left:3px}
.ov-rev-sub{display:flex;align-items:center;justify-content:space-between;margin-top:5px;gap:6px;flex-wrap:wrap}
.ov-rev-cum{font-size:11px;color:#94a3b8}
.ov-rev-yoy{display:inline-flex;align-items:center;gap:4px;font-size:11px}
.ov-yoy-tag{color:#94a3b8}
.ov-yoy-val{display:inline-flex;align-items:center;gap:2px}
.ov-yoy-arrow{display:inline-block}
.ov-yoy-up{color:#dc2626;font-weight:700}
.ov-yoy-down{color:#16a34a;font-weight:700}
.ov-yoy-flat{color:#64748b;font-weight:600}
.ov-prod-panel{padding-bottom:8px}
.ov-prod-titlebar{display:flex;align-items:center;justify-content:space-between;gap:10px;flex-wrap:wrap;padding:16px 18px 12px}
.ov-prod-search{position:relative;display:inline-flex;align-items:center}
.ov-prod-search input{padding:8px 30px 8px 14px;border:1px solid #d1d5db;border-radius:8px;font-size:13px;color:#374151;width:200px}
.ov-prod-search input:focus{outline:none;border-color:#93c5fd;box-shadow:0 0 0 3px #2563eb1a}
.ov-prod-search svg{position:absolute;right:10px;color:#9ca3af;pointer-events:none}
.ov-prod-empty{color:#94a3b8;font-size:13px;padding:12px 18px}
.ov-prod-titlebar-left{display:inline-flex;align-items:center;gap:10px}
.ov-alert-badge{position:relative;display:inline-flex;align-items:center;justify-content:center;width:30px;height:30px;border:1px solid #fecaca;border-radius:8px;background:#fef2f2;color:#dc2626;cursor:pointer;padding:0;transition:border-color .15s,box-shadow .15s,background .15s}
.ov-alert-badge:hover{border-color:#fca5a5;background:#fee2e2;box-shadow:0 0 0 3px #dc26261f}
.ov-alert-count{position:absolute;top:-7px;right:-7px;min-width:17px;height:17px;padding:0 4px;display:inline-flex;align-items:center;justify-content:center;background:#dc2626;color:#fff;font-size:11px;font-weight:700;border-radius:999px;border:2px solid #f8fafc;line-height:1}
.ov-drill-empty-state{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;text-align:center;padding:34px 20px;border:1px dashed #dbe4ef;border-radius:8px;background:#fafcff}
.ov-empty-icon{width:56px;height:56px;border-radius:50%;display:flex;align-items:center;justify-content:center;color:#93c5fd;background:#eef6ff;margin-bottom:2px}
.ov-empty-title{font-size:15px;font-weight:700;color:#334155}
.ov-empty-desc{font-size:12.5px;line-height:1.7;color:#64748b;max-width:560px}
.ov-empty-muted{color:#94a3b8;font-size:12px}
.ov-empty-sub{font-size:11.5px;color:#94a3b8;margin-top:6px}
.ov-prod-tabs{display:flex;gap:0;overflow-x:auto;padding:0 16px}
.ov-prod-tab{padding:10px 16px;font-size:14px;color:#64748b;cursor:pointer;border-bottom:3px solid transparent;background:none;white-space:nowrap}
.ov-prod-tab:hover{color:#475569}
.ov-prod-tab.active{color:#2563eb;font-weight:600;border-bottom-color:#2563eb}
.ov-prod-body{padding:8px 18px}
.ov-prod-block-head{display:flex;align-items:baseline;gap:8px;flex-wrap:wrap;margin:10px 0 16px}
.ov-prod-block-title{font-size:17px;font-weight:700;color:#1e293b}
.ov-prod-block-sub{font-size:11px;font-weight:500;color:#94a3b8}
.ov-sec{margin-bottom:18px;padding-bottom:16px;border-bottom:1px dashed #e8edf4}
.ov-sec-noborder{border-bottom:none;margin-bottom:0;padding-bottom:0}
.ov-sec-title{font-size:13px;font-weight:700;color:#334155;margin-bottom:12px;display:flex;align-items:center;gap:8px}
.ov-sec-badge{width:20px;height:20px;border-radius:6px;font-size:11px;display:inline-flex;align-items:center;justify-content:center;color:#fff;font-weight:700}
.ov-sec-ico{display:block;flex-shrink:0}
.ov-badge-blue{background:#2563eb}
.ov-badge-amber{background:#f59e0b}
.ov-badge-purple{background:#8b5cf6}
.ov-badge-orange{background:#f97316}
.ov-sec-grid{display:grid;gap:12px}
.ov-sec-grid-3{grid-template-columns:repeat(3,1fr)}
.ov-sec-grid-4{grid-template-columns:repeat(4,1fr)}
.ov-kpi{background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:12px 14px;display:flex;align-items:flex-start;gap:10px;transition:box-shadow .15s ease,border-color .15s ease}
.ov-kpi:hover{box-shadow:0 2px 10px #0f172a0f;border-color:#c7d7f5}
.ov-kpi-icon{width:30px;height:30px;border-radius:8px;flex-shrink:0;display:flex;align-items:center;justify-content:center}
.ov-ki-blue{background:#dbeafe;color:#2563eb}
.ov-ki-green{background:#d1fae5;color:#059669}
.ov-ki-purple{background:#ede9fe;color:#7c3aed}
.ov-kpi-txt{flex:1;min-width:0}
.ov-kpi-label{font-size:11.5px;color:#64748b}
.ov-kpi-unit{font-size:10px;color:#94a3b8;margin-left:2px}
.ov-kpi-value{font-size:21px;font-weight:800;color:#0f172a;line-height:1.2;margin:2px 0;letter-spacing:-.01em}
.ov-kpi-value-sm{font-size:16px}
.ov-kpi-yoy{font-size:11px;display:flex;align-items:center;gap:5px;color:#94a3b8}
.ov-kpi-note{font-size:10.5px;color:#94a3b8;margin-top:2px}
.ov-kpi-trend{display:flex;align-items:center;gap:8px;margin-top:3px}
.ov-kpi-trend span{font-size:10px;color:#475569;background:#f1f5f9;border:1px solid #e2e8f0;border-radius:4px;padding:1px 5px;white-space:nowrap}
.ov-sec-struct{align-items:start}
.ov-struct-extra{display:grid;grid-template-columns:1fr 1fr;gap:8px 22px}
.ov-struct-extra-full{grid-template-columns:repeat(auto-fill,minmax(220px,1fr))}
.ov-struct-group{background:#fff;border:1px solid #eef2f7;border-radius:8px;padding:10px 12px}
.ov-struct-group-label{font-size:11.5px;font-weight:700;color:#334155;margin-bottom:8px}
.ov-struct-rows{display:flex;flex-direction:column;gap:6px}
.ov-struct-row,.ov-channel-row{display:flex;align-items:center;gap:8px;font-size:11px;color:#64748b}
.ov-struct-name{width:78px;flex-shrink:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.ov-struct-bar{flex:1;height:8px;background:#eef2f7;border-radius:4px;overflow:hidden}
.ov-struct-bar-fill{height:100%;border-radius:4px;background:linear-gradient(90deg,#2563eb,#0ea5e9)}
.ov-bar-orange{background:linear-gradient(90deg,#f59e0b,#f97316)}
.ov-struct-row-val{width:56px;text-align:right;font-weight:700;color:#0f172a;flex-shrink:0}
.ov-sec-income{grid-template-columns:repeat(3,1fr)}
.ov-income-block{background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:12px 14px}
.ov-income-cap{font-size:12px;font-weight:700;margin-bottom:8px}
.ov-cap-blue{color:#2563eb}
.ov-cap-green{color:#059669}
.ov-cap-amber{color:#d97706}
.ov-cap-orange{color:#ea580c}
.ov-income-row{display:flex;align-items:center;justify-content:space-between;font-size:11.5px;color:#64748b;padding:4px 0;border-bottom:1px dashed #f1f5f9}
.ov-income-row:last-child{border-bottom:none}
.ov-income-row b{color:#0f172a;font-weight:700;font-size:12.5px}
.ov-income-row b.ov-yoy-up{color:#dc2626}
.ov-income-row b.ov-yoy-down{color:#16a34a}
.ov-channel-rows{display:flex;flex-direction:column;gap:8px}
.ov-drill-wrap{border:1px solid #eef2f7;border-radius:8px;overflow:hidden;margin-top:16px}
.ov-drill-toggle{width:100%;display:flex;align-items:center;justify-content:space-between;gap:12px;background:#f8fafc;border:none;cursor:pointer;padding:12px 14px;text-align:left;transition:background .15s ease}
.ov-drill-toggle:hover{background:#f1f5f9}
.ov-drill-toggle-left{display:flex;align-items:center;gap:8px;min-width:0;flex-wrap:wrap}
.ov-drill-toggle-icon{color:#2563eb;flex-shrink:0}
.ov-drill-toggle-title{font-size:13.5px;font-weight:700;color:#1e293b}
.ov-drill-toggle-sub{font-size:11px;color:#94a3b8}
.ov-drill-toggle-arrow{color:#64748b;transition:transform .2s ease;flex-shrink:0}
.ov-drill-toggle-arrow.open{transform:rotate(180deg)}
.ov-drill-body{padding:14px;border-top:1px dashed #e8edf4}
.ov-drill-list{display:grid;grid-template-columns:repeat(auto-fill,minmax(210px,1fr));gap:12px}
.ov-drill-fam{text-align:left;background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:12px 14px;cursor:pointer;transition:box-shadow .15s ease,border-color .15s ease}
.ov-drill-fam:hover{box-shadow:0 3px 12px #0f172a14;border-color:#c7d7f5}
.ov-drill-fam-highlight,.ov-drill-fam.ov-drill-fam-highlight{border-color:#f59e0b!important;box-shadow:0 0 0 3px #f59e0b47,0 4px 14px #fbbf2440!important;background:#fffdf5}
.ov-drill-fam-top{display:flex;align-items:center;justify-content:space-between;gap:8px}
.ov-drill-fam-name{font-size:12.5px;font-weight:700;color:#1e293b;line-height:1.35}
.ov-drill-fam-health{width:30px;height:30px;border-radius:50%;display:inline-flex;align-items:center;justify-content:center;font-weight:800;font-size:13px;color:#fff;flex-shrink:0}
.ov-drill-fam-meta{display:flex;align-items:center;gap:8px;margin:8px 0 6px}
.ov-drill-fam-status{font-size:11px;font-weight:700;padding:1px 7px;border-radius:10px}
.ov-drill-fam-count{font-size:11px;color:#94a3b8}
.ov-drill-fam-scale{font-size:11.5px;color:#64748b}
.ov-drill-health-ok{background:#10b981;color:#fff}
.ov-drill-health-mid{background:#f59e0b;color:#fff}
.ov-drill-health-warn{background:#ef4444;color:#fff}
.ov-drill-health-ok.ov-drill-fam-status{background:#ecfdf5;color:#059669}
.ov-drill-health-mid.ov-drill-fam-status{background:#fef3c7;color:#b45309}
.ov-drill-health-warn.ov-drill-fam-status{background:#fee2e2;color:#dc2626}
.ov-drill-fam-new{background:#3b82f6;color:#fff}
.ov-drill-fam-new-status{background:#eff6ff;color:#2563eb}
.ov-drill-back{display:inline-flex;align-items:center;gap:4px;border:1px solid #d1d5db;background:#fff;color:#475569;border-radius:6px;padding:4px 10px;font-size:12px;cursor:pointer;transition:all .15s}
.ov-drill-back:hover{border-color:#93c5fd;color:#2563eb}
.ov-drill-prodbar{display:flex;align-items:center;gap:10px;flex-wrap:wrap;padding-bottom:12px;margin-bottom:12px;border-bottom:1px dashed #e8edf4}
.ov-drill-prod-title{font-size:15px;font-weight:700;color:#1e293b}
.ov-drill-prod-sub{font-size:11px;font-weight:500;color:#94a3b8}
.ov-drill-health-card{display:flex;gap:18px;align-items:center;flex-wrap:wrap;background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:16px}
.ov-drill-health-gauge{flex-shrink:0}
.ov-drill-gauge-ring{width:92px;height:92px;border-radius:50%;display:flex;flex-direction:column;align-items:center;justify-content:center;color:#fff}
.ov-drill-gauge-num{font-size:30px;font-weight:800;line-height:1}
.ov-drill-gauge-unit{font-size:11px;opacity:.9;margin-top:2px}
.ov-drill-health-info{flex:1;min-width:240px}
.ov-drill-health-level{display:inline-flex;align-items:center;width:fit-content;font-size:13px;font-weight:800;color:#fff;padding:4px 12px;border-radius:6px;margin-bottom:8px}
.ov-drill-health-level.ov-drill-health-ok{background:#10b981}
.ov-drill-health-level.ov-drill-health-mid{background:#f59e0b}
.ov-drill-health-level.ov-drill-health-warn{background:#ef4444}
.ov-drill-health-level-row{display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin-bottom:8px}
.ov-drill-health-level-row .ov-drill-health-level{margin-bottom:0}
.ov-drill-rootcause{margin-left:auto;display:inline-flex;align-items:center;gap:5px;font-size:12px;font-weight:600;color:#fff;background:linear-gradient(135deg,#6366f1,#8b5cf6);border:none;border-radius:6px;padding:5px 12px;cursor:pointer;transition:opacity .15s,transform .15s}
.ov-drill-rootcause:hover{opacity:.9;transform:translateY(-1px)}
.ov-drill-rootcause:active{transform:translateY(0)}
.ov-drill-health-note{font-size:12px;color:#64748b;margin-bottom:12px}
.ov-drill-health-metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}
.ov-kpi-delta{font-size:10px;font-weight:700;margin-left:4px}
.ov-kpi-delta.ov-up{color:#dc2626}
.ov-kpi-delta.ov-down{color:#16a34a}
.ov-kpi-delta.ov-flat{color:#94a3b8}
.ov-drill-dims{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-top:14px}
.ov-drill-dim{background:#f8fafc;border:1px solid #eef2f7;border-radius:8px;padding:12px 14px}
.ov-drill-dim-head{display:flex;align-items:baseline;gap:8px;margin-bottom:8px}
.ov-drill-dim-name{font-size:13px;font-weight:800;color:#1e293b}
.ov-drill-dim-weight{font-size:10.5px;color:#94a3b8;margin-left:4px}
.ov-drill-dim-score{margin-left:auto;font-size:15px;font-weight:800;color:#0f172a}
.ov-drill-dim-score i{font-style:normal;font-size:11px;font-weight:600;color:#94a3b8;margin-left:1px}
.ov-drill-dim-bar{height:7px;background:#eef2f7;border-radius:4px;overflow:hidden}
.ov-drill-dim-fill{height:100%;border-radius:4px}
.ov-dim-eff .ov-drill-dim-fill{background:linear-gradient(90deg,#8b5cf6,#7c3aed)}
.ov-dim-market .ov-drill-dim-fill{background:linear-gradient(90deg,#3b82f6,#2563eb)}
.ov-dim-quality .ov-drill-dim-fill{background:linear-gradient(90deg,#f59e0b,#f97316)}
.ov-dim-life .ov-drill-dim-fill{background:linear-gradient(90deg,#10b981,#059669)}
.ov-drill-dim-items{display:flex;flex-direction:column;gap:7px;margin-top:10px}
.ov-drill-dim-item{display:flex;align-items:center;gap:8px;font-size:12px;color:#334155}
.ov-drill-dim-item-name{width:116px;flex-shrink:0}
.ov-drill-dim-item-value{margin-left:auto;font-size:12px;font-weight:700;color:#0f172a}
.ov-drill-dim-item b{min-width:26px;height:20px;padding:0 6px;border-radius:10px;display:inline-flex;align-items:center;justify-content:center;font-size:11.5px;font-weight:800;color:#fff}
.ov-item-ok{background:#10b981}
.ov-item-mid{background:#f59e0b}
.ov-item-warn{background:#ef4444}
.ov-drill-alerts{display:flex;flex-direction:column;gap:8px}
.ov-drill-alert{display:flex;align-items:flex-start;gap:10px;border:1px solid;border-radius:8px;padding:10px 12px;font-size:12.5px}
.ov-alert-high{background:#fef2f2;border-color:#fecaca;color:#991b1b}
.ov-alert-mid{background:#fffbeb;border-color:#fde68a;color:#92400e}
.ov-alert-low{background:#f0fdf4;border-color:#bbf7d0;color:#166534}
.ov-drill-alert-tag{flex-shrink:0;width:20px;height:20px;border-radius:50%;font-size:11px;font-weight:800;display:inline-flex;align-items:center;justify-content:center;color:#fff;background:#9ca3af}
.ov-alert-high .ov-drill-alert-tag{background:#ef4444}
.ov-alert-mid .ov-drill-alert-tag{background:#f59e0b}
.ov-alert-low .ov-drill-alert-tag{background:#10b981}
.ov-drill-alert-text{line-height:1.6}
.ov-tabbar{display:flex;gap:8px;margin-bottom:12px;border-bottom:1px solid #e5e7eb;padding-bottom:10px}
.ov-tab{display:inline-flex;align-items:center;gap:6px;height:34px;padding:0 16px;border-radius:8px;border:1px solid #e5e7eb;background:#fff;color:#64748b;font-size:13px;font-weight:600;cursor:pointer;transition:all .16s ease}
.ov-tab:hover{border-color:#93c5fd;color:#1d4ed8}
.ov-tab.active{background:linear-gradient(135deg,#eff6ff,#dbeafe);border-color:#3b82f6;color:#1d4ed8;box-shadow:0 2px 8px #3b82f629}
.ov-tf-overview-cards{display:grid;grid-template-columns:repeat(5,1fr);gap:12px;margin-top:4px}
.ov-tf-oc{background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:14px 16px}
.ov-tf-oc-highlight{background:linear-gradient(135deg,#eff6ff,#dbeafe);border-color:#93c5fd}
.ov-tf-oc-label{font-size:12px;color:#64748b}
.ov-tf-oc-value{font-size:22px;font-weight:800;color:#0f172a;margin-top:4px}
.ov-tf-oc-highlight .ov-tf-oc-value{color:#1d4ed8}
.ov-tf-oc-unit{font-size:12px;font-weight:600;color:#94a3b8;margin-left:3px}
.ov-tf-oc-sub{font-size:11.5px;color:#94a3b8;margin-top:4px}
.ov-tf-oc-sub-note{font-weight:600;color:#1d4ed8}
.ov-tf-overview-note{margin-top:12px;padding:10px 14px;border-radius:8px;background:#f8fafc;border:1px solid #eef2f7;color:#475569;font-size:12.5px;line-height:1.7}
.ov-tf-agent-search{display:flex;align-items:center;gap:6px;margin-left:auto}
.ov-tf-agent-search input{width:180px;height:30px;padding:0 10px;border:1px solid #e5e7eb;border-radius:6px;font-size:12px;color:#0f172a;outline:none;background:#fff}
.ov-tf-agent-search input:focus{border-color:#3b82f6}
.ov-tf-agent-search svg{color:#94a3b8}
.ov-tf-agent-table,.ov-tf-region-table,.ov-tf-pkg-table{display:flex;flex-direction:column;margin-top:6px}
.ov-tf-table-head{display:grid;grid-template-columns:1.6fr .9fr 1fr 1fr 1fr .9fr 1fr;gap:10px;padding:10px 14px;border-radius:8px 8px 0 0;background:#f1f5f9;color:#475569;font-size:12px;font-weight:600}
.ov-tf-region-table .ov-tf-table-head{grid-template-columns:1.2fr 1fr 1fr 1fr 1fr .9fr}
.ov-tf-pkg-table .ov-tf-table-head{grid-template-columns:1.8fr .8fr .9fr .9fr .8fr 1fr 1.2fr}
.ov-tf-table-row{display:grid;grid-template-columns:1.6fr .9fr 1fr 1fr 1fr .9fr 1fr;gap:10px;align-items:center;padding:12px 14px;text-align:left;border:1px solid #eef2f7;border-top:none;background:#fff;font-size:13px;color:#0f172a}
.ov-tf-region-panel .ov-tf-table-row{grid-template-columns:1.2fr 1fr 1fr 1fr 1fr .9fr}
.ov-tf-pkg-panel .ov-tf-table-row{grid-template-columns:1.8fr .8fr .9fr .9fr .8fr 1fr 1.2fr}
button.ov-tf-table-row{cursor:pointer;transition:background .15s ease}
button.ov-tf-table-row:hover{background:#f8fafc;border-color:#bfdbfe}
.ov-tf-table-row:last-child{border-radius:0 0 8px 8px}
.ov-tf-row-static{cursor:default}
.ov-tf-row-name{font-weight:600}
.ov-tf-usage-rate{font-weight:700}
.ov-tf-usage-bar{height:8px;border-radius:4px;background:#eef2f7;overflow:hidden;display:block}
.ov-tf-usage-fill{display:block;height:100%;border-radius:4px}
.ov-tf-usage-fill.ov-tf-risk{background:linear-gradient(90deg,#fca5a5,#ef4444)}
.ov-tf-usage-fill.ov-tf-mid{background:linear-gradient(90deg,#fcd34d,#f59e0b)}
.ov-tf-usage-fill.ov-tf-ok{background:linear-gradient(90deg,#6ee7b7,#10b981)}
.ov-tf-risk{color:#dc2626}
.ov-tf-mid{color:#d97706}
.ov-tf-ok{color:#059669}
.ov-tf-pkg-judge{font-size:12px;font-weight:600}
.ov-tf-pkg-conclusion{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-top:14px}
.ov-tf-conclusion-block{padding:12px 14px;border-radius:8px;border:1px solid}
.ov-tf-conclusion-risk{background:#fef2f2;border-color:#fecaca}
.ov-tf-conclusion-ok{background:#f0fdf4;border-color:#bbf7d0}
.ov-tf-conclusion-title{font-size:12.5px;font-weight:700;margin-bottom:6px}
.ov-tf-conclusion-risk .ov-tf-conclusion-title{color:#991b1b}
.ov-tf-conclusion-ok .ov-tf-conclusion-title{color:#166534}
.ov-tf-conclusion-text{font-size:12.5px;color:#475569;line-height:1.7}
.ov-tf-agent-detail{border:1px solid #eef2f7;border-radius:10px;padding:14px;margin-top:8px;background:#fff}
.ov-tf-agent-detail-head{display:flex;align-items:center;gap:12px;margin-bottom:14px}
.ov-tf-back{display:inline-flex;align-items:center;gap:4px;height:28px;padding:0 10px;border:1px solid #e5e7eb;border-radius:6px;background:#fff;color:#64748b;font-size:12px;cursor:pointer;transition:all .15s ease}
.ov-tf-back:hover{border-color:#93c5fd;color:#1d4ed8}
.ov-tf-agent-detail-title{font-size:14px;font-weight:700;color:#0f172a}
.ov-tf-agent-detail-sub{font-size:12px;color:#94a3b8}
.ov-tf-detail-kpis{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}
.ov-tf-detail-insight{margin-top:14px;padding:12px 14px;border-radius:8px;background:#f8fafc;border:1px solid #eef2f7;display:flex;gap:10px;align-items:flex-start;font-size:12.5px;line-height:1.7;color:#475569}
.ov-tf-insight-tag{flex-shrink:0;height:22px;padding:0 8px;border-radius:6px;background:#dbeafe;color:#1d4ed8;font-size:11px;font-weight:700;display:inline-flex;align-items:center}
.ov-tf-empty{padding:30px;text-align:center;color:#94a3b8;font-size:13px}
.ov-wxth-body{display:grid;grid-template-columns:240px 1fr;gap:22px;padding:16px 18px}
.ov-wxth-gauge{display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;padding-right:20px;border-right:1px dashed #e2e8f0}
.ov-wxth-gauge-ring{width:132px;height:132px;border-radius:50%;display:flex;flex-direction:column;align-items:center;justify-content:center;background:radial-gradient(circle at 50% 45%,#f0f9ff,#e0f2fe);border:8px solid #bfdbfe;color:#1e40af}
.ov-wxth-gauge-ring.ov-wxth-ok{border-color:#6ee7b7;background:radial-gradient(circle at 50% 45%,#ecfdf5,#d1fae5);color:#065f46}
.ov-wxth-gauge-ring.ov-wxth-mid{border-color:#fcd34d;background:radial-gradient(circle at 50% 45%,#fffbeb,#fef3c7);color:#92400e}
.ov-wxth-gauge-ring.ov-wxth-warn{border-color:#fca5a5;background:radial-gradient(circle at 50% 45%,#fef2f2,#fee2e2);color:#991b1b}
.ov-wxth-gauge-num{font-size:30px;font-weight:800;line-height:1}
.ov-wxth-gauge-unit{font-size:11px;opacity:.85;margin-top:3px}
.ov-wxth-gauge-level{margin-top:12px;font-size:13px;font-weight:700}
.ov-wxth-gauge-level.ov-wxth-ok{color:#059669}
.ov-wxth-gauge-level.ov-wxth-mid{color:#d97706}
.ov-wxth-gauge-level.ov-wxth-warn{color:#dc2626}
.ov-wxth-gauge-note{margin-top:8px;font-size:12px;color:#64748b;line-height:1.7;max-width:220px}
.ov-wxth-right{display:flex;flex-direction:column;gap:14px}
.ov-wxth-kpis{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}
.ov-wxth-dims{display:flex;flex-direction:column;gap:12px}
.ov-wxth-dim-head{display:flex;align-items:center;gap:8px;margin-bottom:4px}
.ov-wxth-dim-name{font-size:13px;font-weight:700;color:#0f172a}
.ov-wxth-dim-meta{font-size:11px;color:#94a3b8;flex:1}
.ov-wxth-dim-score{font-size:13px;font-weight:800}
.ov-wxth-dim-score.ov-wxth-ok{color:#059669}
.ov-wxth-dim-score.ov-wxth-mid{color:#d97706}
.ov-wxth-dim-score.ov-wxth-warn{color:#dc2626}
.ov-wxth-dim-bar{height:8px;border-radius:5px;background:#f1f5f9;overflow:hidden}
.ov-wxth-dim-fill{display:block;height:100%;border-radius:5px}
.ov-wxth-dim-fill.ov-wxth-ok{background:linear-gradient(90deg,#6ee7b7,#10b981)}
.ov-wxth-dim-fill.ov-wxth-mid{background:linear-gradient(90deg,#fcd34d,#f59e0b)}
.ov-wxth-dim-fill.ov-wxth-warn{background:linear-gradient(90deg,#fca5a5,#ef4444)}
.ov-wxth-dim-sub{margin-top:4px;font-size:11.5px;color:#64748b;display:flex;gap:8px}
.ov-wxth-dim-sub em{font-style:normal;font-weight:600}
.ov-wxth-dim-sub em:empty{display:none}
.ov-tf-persp{display:flex;align-items:center;gap:12px;padding:2px 2px 0;flex-wrap:wrap}
.ov-tf-persp-label{font-size:12.5px;font-weight:700;color:#475569}
.ov-tf-persp-seg{display:flex;border:1px solid #e2e8f0;border-radius:8px;overflow:hidden;background:#fff}
.ov-tf-persp-btn{display:inline-flex;align-items:center;gap:6px;height:32px;padding:0 14px;border:none;background:#fff;color:#64748b;font-size:12.5px;font-weight:600;cursor:pointer}
.ov-tf-persp-btn+.ov-tf-persp-btn{border-left:1px solid #e2e8f0}
.ov-tf-persp-btn:hover{background:#f8fafc}
.ov-tf-persp-btn.active{background:#1d4ed8;color:#fff}
.ov-tf-persp-desc{font-size:12px;color:#94a3b8}
.ov-tf-dash-cards{display:grid;grid-template-columns:repeat(6,1fr);gap:12px;padding:16px 18px 0}
.ov-tf-dc{background:#f8fafc;border:1px solid #eef2f7;border-radius:8px;padding:12px 14px}
.ov-tf-dc-highlight{background:#eff6ff;border-color:#bfdbfe}
.ov-tf-dc-label{font-size:12px;color:#64748b}
.ov-tf-dc-value{font-size:20px;font-weight:800;color:#0f172a;margin-top:4px}
.ov-tf-dc-highlight .ov-tf-dc-value{color:#1d4ed8}
.ov-tf-dc-value span{font-size:11px;font-weight:600;color:#94a3b8;margin-left:3px}
.ov-tf-dc-sub{font-size:11.5px;color:#94a3b8;margin-top:3px}
.ov-tf-arrow{font-style:normal;color:#f59e0b;font-weight:700;margin-right:2px}
.ov-tf-dc-sub-note{font-weight:600;color:#1d4ed8}
.ov-tf-dash-alert{margin:14px 18px 18px;padding:10px 14px;border-radius:8px;background:#f8fafc;border:1px solid #eef2f7;display:flex;gap:10px;align-items:flex-start;font-size:12.5px;color:#475569;line-height:1.7}
.ov-tf-dash-alert-tag{flex-shrink:0;height:22px;padding:0 8px;border-radius:6px;background:#dbeafe;color:#1d4ed8;font-size:11px;font-weight:700;display:inline-flex;align-items:center}
.ov-tf-trend{padding:14px 18px 18px}
.ov-tf-trend-title{font-size:13px;font-weight:700;color:#0f172a;margin-bottom:14px}
.ov-tf-trend-hint{font-style:normal;font-size:11px;font-weight:500;color:#94a3b8;margin-left:8px}
.ov-tf-trend-head{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap;margin-bottom:12px}
.ov-tf-trend-ranges,.ov-tf-trend-metrics{display:flex;gap:6px}
.ov-tf-trend-dense{gap:2px}
.ov-tf-trend-dense .ov-tf-trend-col{padding:4px 1px}
.ov-tf-trend-dense .ov-tf-trend-fill{width:46%;min-width:3px;max-width:16px}
.ov-tf-trend-dense .ov-tf-trend-label{font-size:9px}
.ov-tf-up{color:#ef4444}
.ov-tf-down{color:#10b981}
.ov-tf-flat{color:#64748b}
.ov-tf-agentwrap-sub{font-weight:400;color:#64748b;font-size:12px}
.ov-tf-trend-chart{display:flex;align-items:flex-end;gap:14px;height:200px;padding:20px 10px 0;border-bottom:1px solid #e5e7eb;border-left:1px solid #eef2f7}
.ov-tf-trend-col{flex:1;display:flex;flex-direction:column;align-items:center;justify-content:flex-end;gap:4px;min-width:0;border:none;background:transparent;cursor:pointer;border-radius:8px;padding:4px 6px}
.ov-tf-trend-col:hover{background:#f1f5f9}
.ov-tf-trend-sel{background:#eff6ff;outline:1px solid #bfdbfe}
.ov-tf-trend-val{font-size:10.5px;color:#64748b;font-weight:600}
.ov-tf-trend-val i{font-style:normal;color:#94a3b8;margin-left:1px}
.ov-tf-trend-bar{width:100%;height:120px;display:flex;align-items:flex-end;justify-content:center}
.ov-tf-trend-fill{width:60%;max-width:34px;border-radius:4px 4px 0 0;background:linear-gradient(180deg,#60a5fa,#2563eb)}
.ov-tf-trend-cur .ov-tf-trend-fill{background:linear-gradient(180deg,#f97316,#ea580c)}
.ov-tf-trend-label{font-size:12px;font-weight:600;color:#475569}
.ov-tf-trend-rate{font-size:11px;font-weight:700;color:#2563eb}
.ov-tf-trend-mom{font-size:10.5px;color:#059669}
.ov-tf-trend-selbar{display:flex;align-items:center;gap:10px;margin-top:14px;padding:10px 12px;background:#f8fafc;border:1px solid #eef2f7;border-radius:8px}
.ov-tf-trend-sel-tag{flex-shrink:0;font-size:11px;font-weight:700;color:#1d4ed8;background:#dbeafe;padding:2px 8px;border-radius:20px}
.ov-tf-trend-sel-text{flex:1;font-size:12px;color:#475569}
.ov-tf-trend-reason{flex-shrink:0;display:inline-flex;align-items:center;gap:4px;font-size:12px;font-weight:600;color:#2563eb;background:#eff6ff;border:1px solid #bfdbfe;border-radius:6px;padding:5px 10px;cursor:pointer}
.ov-tf-trend-reason:hover{background:#dbeafe}
.ov-tf-sit-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px;padding:0 18px 18px}
.ov-tf-an-tabs{display:flex;gap:8px;padding:2px 18px 12px;border-bottom:1px solid #eef2f7;margin-bottom:4px}
.ov-tf-an-block{padding:4px 18px 18px}
.ov-tf-sit-block{background:#f8fafc;border:1px solid #eef2f7;border-radius:8px;padding:14px}
.ov-tf-sit-block-title{font-size:13px;font-weight:700;color:#0f172a;margin-bottom:10px}
.ov-tf-sit-tabs{display:flex;gap:8px;margin-bottom:10px}
.ov-tf-sit-tab{height:26px;padding:0 12px;border-radius:6px;border:1px solid #e2e8f0;background:#fff;color:#64748b;font-size:12px;font-weight:600;cursor:pointer}
.ov-tf-sit-tab.active{background:#1d4ed8;border-color:#1d4ed8;color:#fff}
.ov-tf-sit-table .ov-tf-table-head,.ov-tf-sit-table .ov-tf-table-row{grid-template-columns:1.4fr .9fr .9fr .9fr 1.1fr .7fr .7fr}
.ov-tf-sit-pkggrid .ov-tf-table-head,.ov-tf-sit-pkggrid .ov-tf-table-row{grid-template-columns:1.8fr .8fr .8fr .8fr .8fr .8fr .6fr .7fr .9fr}
.ov-tf-sit-custgrid .ov-tf-table-head,.ov-tf-sit-custgrid .ov-tf-table-row{grid-template-columns:1fr .9fr .7fr .8fr .8fr .6fr 1.6fr .7fr}
.ov-tf-sit-chgrid .ov-tf-table-head,.ov-tf-sit-chgrid .ov-tf-table-row{grid-template-columns:1.5fr .6fr .8fr .8fr .8fr .8fr .8fr .6fr .7fr}
.ov-tf-sit-8grid .ov-tf-table-head,.ov-tf-sit-8grid .ov-tf-table-row{grid-template-columns:1.3fr 1.4fr .8fr .9fr .8fr .6fr 1.3fr .9fr}
.ov-tf-sit-9grid .ov-tf-table-head,.ov-tf-sit-9grid .ov-tf-table-row{grid-template-columns:1.3fr .6fr .9fr .8fr .8fr .8fr .8fr .5fr .8fr}
.ov-tf-mom{font-size:11px;font-weight:600}
.ov-tf-mom-up{color:#ef4444}
.ov-tf-mom-down{color:#10b981}
.ov-tf-mom-flat{color:#94a3b8}
.ov-tf-status{font-size:11px;font-weight:600;padding:2px 8px;border-radius:10px;justify-self:start}
.ov-tf-st-high{color:#ef4444;background:#fee2e2}
.ov-tf-st-mid{color:#d97706;background:#fef3c7}
.ov-tf-st-low{color:#10b981;background:#d1fae5}
.ov-tf-sit-table{display:flex;flex-direction:column;gap:6px}
.ov-tf-sit-table .ov-tf-table-head,.ov-tf-sit-table .ov-tf-table-row{display:grid;align-items:center;gap:8px;border-radius:6px;padding:8px 10px;font-size:12px}
.ov-tf-sit-table .ov-tf-table-head{background:#fff;border:1px solid #eef2f7;color:#64748b;font-weight:600}
.ov-tf-sit-table .ov-tf-table-row{background:#fff;border:1px solid #eef2f7;color:#0f172a;text-align:left}
button.ov-tf-sit-table .ov-tf-table-row{cursor:pointer}
button.ov-tf-sit-table{border:none;background:transparent;padding:0;width:100%}
button.ov-tf-sit-table .ov-tf-table-row:hover{border-color:#bfdbfe;background:#f8fafc}
.ov-tf-pkg-class{display:flex;flex-direction:column;gap:8px;margin-bottom:14px}
.ov-tf-pkg-class-row{display:flex;align-items:center;gap:8px;font-size:12px}
.ov-tf-pkg-class-tag{flex-shrink:0;min-width:92px;padding:3px 8px;border-radius:6px;font-size:11px;font-weight:700;text-align:center}
.ov-tf-pkg-class-low{background:#ecfdf5;color:#059669}
.ov-tf-pkg-class-mid{background:#fef3c7;color:#d97706}
.ov-tf-pkg-class-high{background:#fee2e2;color:#dc2626}
.ov-tf-pkg-class-list{color:#475569}
.ov-tf-agentrank-head{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:12px;flex-wrap:wrap}
.ov-tf-agentrank-sort{display:flex;gap:6px}
.ov-tf-agentrank-grid{display:grid;grid-template-columns:320px 1fr;gap:14px;align-items:start}
.ov-tf-agentrank-row{display:flex;align-items:center;gap:8px;padding:7px 8px;border-radius:6px;cursor:pointer}
.ov-tf-agentrank-row:hover{background:#eff6ff}
.ov-tf-agentrank-idx{width:20px;height:20px;border-radius:6px;background:#e2e8f0;color:#475569;font-size:11px;font-weight:700;display:flex;align-items:center;justify-content:center;flex-shrink:0}
.ov-tf-ar-idx-0{background:#f59e0b;color:#fff}
.ov-tf-ar-idx-1{background:#94a3b8;color:#fff}
.ov-tf-ar-idx-2{background:#cbd5e1;color:#475569}
.ov-tf-agentrank-name{width:72px;font-size:12px;font-weight:600;color:#0f172a;flex-shrink:0}
.ov-tf-agentrank-bar{flex:1;height:8px;background:#eef2f7;border-radius:6px;overflow:hidden}
.ov-tf-agentrank-fill{height:100%;background:linear-gradient(90deg,#60a5fa,#2563eb);border-radius:6px}
.ov-tf-agentrank-val{font-size:11.5px;font-weight:700;color:#2563eb;width:46px;text-align:right;flex-shrink:0}
.ov-tf-agentrank-hint{margin-top:12px;font-size:11px;color:#94a3b8}
.ov-tf-pkg-cards{display:flex;flex-direction:column;gap:8px;margin:10px 0}
.ov-tf-pkg-card{padding:9px 11px;border-radius:8px;border:1px solid}
.ov-tf-pkg-card-hi{background:#fef2f2;border-color:#fecaca;cursor:pointer;transition:box-shadow .15s ease,transform .15s ease}
.ov-tf-pkg-card-hi:hover{box-shadow:0 2px 8px #dc262626;transform:translateY(-1px)}
.ov-tf-pkg-card-hi:after{content:"点击让 AI 生成优化方案 →";display:block;font-size:10.5px;color:#dc2626;margin-top:6px;font-weight:600}
.ov-tf-pkg-card-lo{background:#f0fdf4;border-color:#bbf7d0}
.ov-tf-pkg-card-head{display:flex;align-items:center;gap:8px;font-size:12.5px}
.ov-tf-pkg-card-head b{color:#0f172a}
.ov-tf-pkg-card-tag{font-size:10px;font-weight:700;padding:1px 7px;border-radius:20px}
.ov-tf-pkg-card-tag.hi{background:#fee2e2;color:#dc2626}
.ov-tf-pkg-card-tag.lo{background:#dcfce7;color:#059669}
.ov-tf-pkg-card-meta{font-size:11.5px;color:#64748b;margin:4px 0 7px}
.ov-tf-pkg-card-meta b{color:#dc2626}
.ov-tf-pkg-card-lo .ov-tf-pkg-card-meta b{color:#059669}
.ov-tf-pkg-card-acts{display:flex;gap:6px}
.ov-tf-pkg-card-acts button{font-size:11px;font-weight:600;padding:3px 9px;border-radius:6px;cursor:pointer}
.ov-tf-pkg-card-hi .ov-tf-pkg-card-acts button{color:#dc2626;background:#fff;border:1px solid #fecaca}
.ov-tf-pkg-card-hi .ov-tf-pkg-card-acts button:hover{background:#fee2e2}
.ov-tf-pkg-card-lo .ov-tf-pkg-card-acts button{color:#059669;background:#fff;border:1px solid #bbf7d0}
.ov-tf-pkg-card-lo .ov-tf-pkg-card-acts button:hover{background:#dcfce7}
.ov-wxth-mini-head{display:flex;justify-content:space-between;align-items:center;font-size:12.5px;font-weight:700;color:#0f172a;margin-bottom:8px}
.ov-wxth-mini-head b.ov-wxth-ok{color:#059669}
.ov-wxth-mini-head b.ov-wxth-mid{color:#d97706}
.ov-wxth-mini-head b.ov-wxth-warn{color:#dc2626}
.ov-wxth-mini-dims{display:flex;flex-direction:column;gap:6px}
.ov-wxth-mini-dim{display:flex;align-items:center;gap:8px;font-size:11.5px}
.ov-wxth-mini-name{flex:0 0 86px;font-weight:600;color:#475569}
.ov-wxth-mini-bar{flex:1;height:7px;border-radius:4px;background:#f1f5f9;overflow:hidden}
.ov-wxth-mini-fill{display:block;height:100%;border-radius:4px}
.ov-wxth-mini-fill.ov-wxth-ok{background:linear-gradient(90deg,#6ee7b7,#10b981)}
.ov-wxth-mini-fill.ov-wxth-mid{background:linear-gradient(90deg,#fcd34d,#f59e0b)}
.ov-wxth-mini-fill.ov-wxth-warn{background:linear-gradient(90deg,#fca5a5,#ef4444)}
.ov-wxth-mini-rate{flex:0 0 42px;text-align:right;font-weight:700}
.ov-wxth-mini-rate.ov-wxth-ok{color:#059669}
.ov-wxth-mini-rate.ov-wxth-mid{color:#d97706}
.ov-wxth-mini-rate.ov-wxth-warn{color:#dc2626}
.ov-tf-agentwrap{position:relative;padding:0 18px 18px}
.ov-tf-agentwrap-close{position:absolute;top:-6px;right:18px;font-size:12px;color:#1d4ed8;cursor:pointer;font-weight:600;z-index:2}
.ov-tf-cust-stats{display:grid;grid-template-columns:1fr 1fr;gap:10px}
.ov-tf-cust-stat{background:#fff;border:1px solid #eef2f7;border-radius:8px;padding:10px 12px}
.ov-tf-cust-stat-label{display:block;font-size:11.5px;color:#64748b}
.ov-tf-cust-stat-value{display:block;font-size:17px;font-weight:800;color:#0f172a;margin-top:2px}
.ov-tf-cust-stat-value i{font-style:normal;font-size:11px;color:#94a3b8;margin-left:2px}
.ov-tf-focus-tabs{display:flex;gap:8px;padding:4px 18px 12px;border-bottom:1px solid #eef2f7}
.ov-tf-focus-tab{display:inline-flex;align-items:center;gap:6px;height:30px;padding:0 14px;border-radius:7px;border:1px solid #e2e8f0;background:#fff;color:#64748b;font-size:12.5px;font-weight:600;cursor:pointer}
.ov-tf-focus-tab em{font-style:normal;min-width:16px;height:16px;padding:0 4px;border-radius:8px;display:inline-flex;align-items:center;justify-content:center;background:#f1f5f9;color:#475569;font-size:11px}
.ov-tf-focus-tab.active{background:#1d4ed8;border-color:#1d4ed8;color:#fff}
.ov-tf-focus-tab.active em{background:#ffffff40;color:#fff}
.ov-tf-focus-list{padding:12px 18px 18px;display:flex;flex-direction:column;gap:10px}
.ov-tf-focus-item{display:flex;align-items:center;gap:12px;padding:12px 14px;border:1px solid #eef2f7;border-radius:8px;background:#f8fafc}
.ov-tf-focus-badge{flex-shrink:0;width:22px;height:22px;border-radius:50%;display:inline-flex;align-items:center;justify-content:center;font-size:11px;font-weight:800;color:#fff}
.ov-tf-focus-main{flex:1;min-width:0}
.ov-tf-focus-title{display:flex;align-items:center;gap:8px;font-size:13px;font-weight:700;color:#0f172a}
.ov-tf-focus-name{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.ov-tf-focus-badge{flex-shrink:0;height:18px;min-width:22px;padding:0 6px;border-radius:9px;display:inline-flex;align-items:center;justify-content:center;font-size:11px;font-weight:800;color:#fff}
.ov-tf-focus-type{flex-shrink:0;font-size:10.5px;font-weight:600;color:#1d4ed8;background:#eff6ff;border:1px solid #bfdbfe;border-radius:4px;padding:1px 7px;white-space:nowrap}
.ov-tf-focus-meta{margin-left:8px;font-size:10.5px;font-weight:600;color:#2563eb;background:#eff6ff;border:1px solid #bfdbfe;border-radius:4px;padding:1px 6px;white-space:nowrap}
.ov-tf-focus-detail{margin-top:5px;font-size:12px;color:#64748b}
.ov-tf-focus-ask{display:inline-flex;align-items:center;gap:5px;flex-shrink:0;height:28px;padding:0 12px;border-radius:6px;border:1px solid #bfdbfe;background:#eff6ff;color:#1d4ed8;font-size:12px;font-weight:700;cursor:pointer}
.ov-tf-focus-ask:hover{background:#dbeafe}
.ov-tf-focus-main.clickable{cursor:pointer}
.ov-tf-focus-main.clickable:hover .ov-tf-focus-title{color:#1d4ed8}
.ov-tf-focus-detail{padding:12px 18px 18px}
.ov-tf-focus-detail-sec{display:flex;gap:18px;margin-top:6px}
.ov-tf-focus-detail-g{flex:1;min-width:0}
.ov-tf-focus-trendline{display:flex;align-items:flex-end;gap:8px;margin-top:8px}
.ov-tf-focus-trend-node{flex:1;text-align:center;font-size:11px;font-weight:700;color:#1d4ed8;background:#eff6ff;border:1px solid #bfdbfe;border-radius:6px;padding:6px 2px}
.ov-tf-focus-trend-node.cur{color:#fff;background:#ea580c;border-color:#ea580c}
.ov-tf-focus-detail-ai{display:flex;align-items:center;gap:10px;margin-top:14px;padding:10px 12px;background:#fffbeb;border:1px solid #fde68a;border-radius:8px;color:#92400e;font-size:12px}
.ov-tf-focus-detail-ai svg{flex-shrink:0;color:#f59e0b}
.ov-tf-focus-detail-ai b{color:#b45309}
.ov-tf-focus-detail-ai .ov-tf-focus-ask{margin-left:auto;flex-shrink:0}
.ov-tf-insight{border:1px solid #E7B9B9}
.ov-tf-insight-title{color:#b23a3a}
.ov-tf-insight-items{padding:6px 18px 18px;display:flex;flex-direction:column;gap:10px}
.ov-tf-insight-item{display:flex;align-items:center;gap:12px;padding:12px 14px;border:1px solid #f1e3e3;border-radius:8px;background:#fff8f6}
.ov-tf-insight-ico{flex-shrink:0;width:24px;height:24px;border-radius:50%;display:inline-flex;align-items:center;justify-content:center;font-size:13px;font-weight:800;color:#fff}
.ov-tf-ii-high{background:#ef4444}
.ov-tf-ii-mid{background:#f59e0b}
.ov-tf-insight-main{flex:1;min-width:0}
.ov-tf-insight-title{font-size:13px;font-weight:700;color:#0f172a}
.ov-tf-insight-detail{margin-top:3px;font-size:12px;color:#64748b;line-height:1.6}
.ov-tf-insight-ask{flex-shrink:0;display:inline-flex;align-items:center;gap:5px;height:28px;padding:0 12px;border-radius:6px;border:1px solid #f3c6c6;background:#fff1f0;color:#b23a3a;font-size:12px;font-weight:700;cursor:pointer}
.ov-tf-insight-ask:hover{background:#ffe4e2}
.ov-tf-sel-head{display:flex;align-items:center;gap:12px;margin-bottom:14px}
.ov-tf-sel-back{display:inline-flex;align-items:center;gap:5px;height:28px;padding:0 10px;border-radius:6px;border:1px solid #e2e8f0;background:#fff;color:#64748b;font-size:12px;font-weight:600;cursor:pointer}
.ov-tf-sel-back:hover{border-color:#93c5fd;color:#1d4ed8}
.ov-tf-sel-title{font-size:14px;font-weight:800;color:#0f172a;flex:1}
.ov-tf-sel-ask{display:inline-flex;align-items:center;gap:5px;height:28px;padding:0 12px;border-radius:6px;border:1px solid #bfdbfe;background:#eff6ff;color:#1d4ed8;font-size:12px;font-weight:700;cursor:pointer}
.ov-tf-sel-ask:hover{background:#dbeafe}
.ov-tf-sel-ask-alt{border-color:#a7f3d0;background:#ecfdf5;color:#059669}
.ov-tf-sel-ask-alt:hover{background:#d1fae5}
.ov-tf-sel-cust-title{font-size:12.5px;font-weight:700;color:#0f172a;margin:14px 0 8px}
.ov-tf-sel-custs{display:flex;flex-direction:column;gap:7px}
.ov-tf-sel-cust{display:flex;align-items:center;gap:8px;padding:8px 10px;border:1px solid #eef2f7;border-radius:6px;background:#fff;font-size:12px}
.ov-tf-sel-cust-name{font-weight:600;color:#0f172a;width:84px;flex-shrink:0}
.ov-tf-sel-cust-meta{flex:1;color:#94a3b8}
.ov-tf-sel-cust-rate{font-weight:800;color:#0f172a}
.ov-tf-sel-pkgs{display:flex;flex-direction:column;gap:9px}
.ov-tf-sel-pkg{display:flex;align-items:center;gap:8px;font-size:12px}
.ov-tf-sel-pkg-name{flex:0 0 110px;font-weight:600;color:#475569}
.ov-tf-sel-pkg-bar{flex:1;height:8px;border-radius:4px;background:#f1f5f9;overflow:hidden}
.ov-tf-sel-pkg-fill{display:block;height:100%;border-radius:4px}
.ov-tf-sel-pkg-fill.ov-tf-fb-high{background:linear-gradient(90deg,#fca5a5,#ef4444)}
.ov-tf-sel-pkg-fill.ov-tf-fb-mid{background:linear-gradient(90deg,#fcd34d,#f59e0b)}
.ov-tf-sel-pkg-fill.ov-tf-fb-low{background:linear-gradient(90deg,#6ee7b7,#10b981)}
.ov-tf-sel-pkg-rate{flex:0 0 44px;text-align:right;font-weight:800}
.ov-tf-sel-pkg-status{flex:0 0 46px;text-align:center;font-size:11px;font-weight:700}
.ov-tf-sel-pkg-rate.ov-tf-sel-t-high,.ov-tf-sel-pkg-status.ov-tf-sel-t-high{color:#dc2626}
.ov-tf-sel-pkg-rate.ov-tf-sel-t-mid,.ov-tf-sel-pkg-status.ov-tf-sel-t-mid{color:#d97706}
.ov-tf-sel-pkg-rate.ov-tf-sel-t-low,.ov-tf-sel-pkg-status.ov-tf-sel-t-low{color:#059669}
.ov-tf-sel-cust-rate.ov-tf-sel-t-high{color:#dc2626}
.ov-tf-sel-cust-rate.ov-tf-sel-t-mid{color:#d97706}
.ov-tf-sel-cust-rate.ov-tf-sel-t-low{color:#059669}
.ov-tf-sel-note{margin-top:14px;padding:9px 12px;border-radius:7px;background:#fef2f2;border:1px solid #fecaca;color:#991b1b;font-size:12px;line-height:1.7}
.ov-tf-fb-high{background:#ef4444}
.ov-tf-fb-mid{background:#f59e0b}
.ov-tf-fb-low{background:#10b981}
.ov-tf-sel-pkg-fill{width:0}
.ov-tf-pkg-meta{display:flex;flex-direction:column;gap:6px;margin-bottom:10px}
.ov-tf-pkg-meta-row{display:flex;justify-content:space-between;align-items:center;font-size:12px;padding:7px 10px;border:1px solid #eef2f7;border-radius:6px;background:#fff}
.ov-tf-pkg-meta-row span{color:#64748b}
.ov-tf-pkg-meta-row b{font-weight:700;color:#0f172a}
.ov-tf-pkg-sugg{padding:9px 12px;border-radius:7px;background:#fffbeb;border:1px solid #fde68a;color:#92400e;font-size:12px;line-height:1.7}
.ov-tf-row-op{color:#2563eb;font-size:11.5px;font-weight:600;white-space:nowrap}
/* ---- 套餐详情结构化（订购/使用/成本/客户） ---- */
.ov-kpi-main{background:#f8fafc;border-color:#e2e8f0}
.ov-tf-pkg-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin:14px 0 0}
.ov-tf-pkg-panel{background:#fff;border:1px solid #eef2f7;border-radius:10px;padding:12px 14px;margin-top:12px}
.ov-tf-pkg-grid .ov-tf-pkg-panel{margin-top:0}
.ov-tf-pkg-panel-title{display:flex;align-items:center;gap:8px;font-size:12.5px;font-weight:700;color:#0f172a;margin-bottom:10px}
.ov-tf-pkg-panel-sub{font-size:11px;font-weight:500;color:#94a3b8}
.ov-tf-pkg-panel-tag{height:18px;padding:0 8px;border-radius:9px;font-size:10.5px;font-weight:700;display:inline-flex;align-items:center}
.ov-tf-pkg-panel-tag.ov-tf-risk{color:#dc2626;background:#fee2e2}
.ov-tf-pkg-panel-tag.ov-tf-mid{color:#d97706;background:#fef3c7}
.ov-tf-pkg-panel-tag.ov-tf-ok{color:#059669;background:#d1fae5}
.ov-tf-pkg-stats{display:grid;grid-template-columns:1fr 1fr;gap:6px 16px}
.ov-tf-pkg-stat{display:flex;justify-content:space-between;align-items:center;font-size:12.5px;padding:5px 0}
.ov-tf-pkg-stat>span{color:#64748b}
.ov-tf-pkg-stat>b{font-weight:700;color:#0f172a}
.ov-tf-pkg-stat b i{font-style:normal;font-size:10.5px;color:#94a3b8;font-weight:600}
.ov-tf-pkg-bar{height:10px;border-radius:6px;background:#f1f5f9;overflow:hidden;margin:7px 0 4px}
.ov-tf-pkg-bar-fill{display:block;height:100%;border-radius:6px}
.ov-tf-pkg-bar-fill.ov-tf-risk{background:linear-gradient(90deg,#fca5a5,#ef4444)}
.ov-tf-pkg-bar-fill.ov-tf-mid{background:linear-gradient(90deg,#fcd34d,#f59e0b)}
.ov-tf-pkg-bar-fill.ov-tf-ok{background:linear-gradient(90deg,#6ee7b7,#10b981)}
.ov-tf-pkg-bar-note{font-size:11px;color:#94a3b8}
.ov-tf-pkg-sugg{padding:9px 12px;border-radius:7px;background:#fffbeb;border:1px solid #fde68a;color:#92400e;font-size:12px;line-height:1.7;font-weight:500}
.ov-tf-pkg-custs{display:flex;flex-direction:column;gap:8px}
.ov-tf-pkg-cust{display:flex;align-items:center;gap:12px;font-size:12px;padding:7px 10px;border:1px solid #f1f5f9;border-radius:8px;background:#f8fafc}
.ov-tf-pkg-cust-name{flex:0 0 90px;font-weight:600;color:#0f172a;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.ov-tf-pkg-cust-bar{flex:1;height:8px;border-radius:5px;background:#eef2f7;overflow:hidden}
.ov-tf-pkg-cust-fill{display:block;height:100%;border-radius:5px;background:linear-gradient(90deg,#60a5fa,#2563eb)}
.ov-tf-pkg-cust-users{flex:0 0 58px;text-align:right;font-weight:700;color:#0f172a}
.ov-tf-pkg-cust-share{flex:0 0 auto;max-width:200px;font-size:11px;color:#64748b}
.ov-tf-pkg-cust-empty{padding:14px;text-align:center;color:#94a3b8;font-size:12px}
</style>
