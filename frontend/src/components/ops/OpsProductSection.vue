<template>
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
          <div class="ov-kpi-icon" :class="'ov-ki-' + (['到达','活跃率'].some((s) => m.label.includes(s)) ? 'blue' : 'green')">
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
  </div>
</template>

<script setup>
import { fmt, fmtPct, fmtRatio, deltaCls, pctWidth } from './opsFormat.js'

defineProps({
  prod: { type: Object, required: true },
})
</script>

<style src="./opsView.css" scoped></style>
