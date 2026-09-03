<template>
  <div class="ov-panel ov-rev-panel">
    <div class="ov-panel-title-bar">
      <h3 class="ov-title">收入总览</h3>
      <span class="ov-title-note">单位：万元 ｜ 数据口径：全省 · 2026年7月</span>
    </div>
    <div class="ov-rev-row">
      <div v-for="card in cards" :key="card.key" class="ov-rev-card">
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
</template>

<script setup>
import { revenueCards as cards } from './opsData.js'
import { fmt, fmtPct, deltaCls } from './opsFormat.js'
</script>

<style src="./opsView.css" scoped></style>
