<template>
  <div class="ov-drill-wrap">
    <button type="button" class="ov-drill-toggle" @click="$emit('toggle')">
      <span class="ov-drill-toggle-left">
        <svg class="ov-drill-toggle-icon" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>
        <span class="ov-drill-toggle-title">下钻至产品</span>
        <span class="ov-drill-toggle-sub">从业务「5G新通话」下钻查看代表性套餐的运营情况</span>
      </span>
      <svg class="ov-drill-toggle-arrow" :class="{ open }" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
    </button>

    <div v-show="open" class="ov-drill-body">
      <!-- 代表性套餐选择层 -->
      <template v-if="!current">
        <div class="ov-drill-list">
          <button
            v-for="key in drillKeys"
            :key="key"
            type="button"
            class="ov-drill-fam"
            :class="{ 'ov-drill-fam-highlight': highlightKey === key }"
            @click="$emit('select', key)"
          >
            <div class="ov-drill-fam-top">
              <span class="ov-drill-fam-name">{{ lookup[key].name }}</span>
              <span v-if="lookup[key].launched" class="ov-drill-fam-health ov-drill-fam-new">新</span>
              <span v-else class="ov-drill-fam-health" :class="drillHealthCls(productLevel(key))">{{ productTotal(key) }}</span>
            </div>
            <div class="ov-drill-fam-meta">
              <span v-if="lookup[key].launched" class="ov-drill-fam-status ov-drill-fam-new-status">新上架 · 数据积累中</span>
              <template v-else>
                <span class="ov-drill-fam-status" :class="drillHealthCls(productLevel(key))">{{ productLevel(key) }}</span>
              </template>
              <span class="ov-drill-fam-count">{{ lookup[key].scale.label }} {{ lookup[key].scale.value }}</span>
            </div>
            <div class="ov-drill-fam-scale">新增 {{ lookup[key].added.value }} <template v-if="lookup[key].added.delta"> · {{ lookup[key].added.delta }}</template></div>
          </button>
        </div>
      </template>

      <!-- 单产品健康度视图 -->
      <template v-else>
        <div class="ov-drill-prodbar">
          <button type="button" class="ov-drill-back" @click="$emit('select', null)">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="15 18 9 12 15 6"/></svg>
            返回套餐列表
          </button>
          <span class="ov-drill-prod-title">{{ current.name }}</span>
          <span class="ov-drill-prod-sub">5G新通话 · 2026年7月</span>
          <button
            v-if="!current.launched && level === '亚健康'"
            type="button"
            class="ov-drill-rootcause"
            title="进入对话场景，对该套餐进行根因分析"
            @click="$emit('open-rootcause', activeKey)"
          >
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="11" y1="8" x2="11" y2="14"/><line x1="8" y1="11" x2="14" y2="11"/></svg>
            根因分析
          </button>
        </div>

        <!-- 刚上架产品：暂无运营数据，友好占位（不开放根因分析） -->
        <div v-if="current.launched" class="ov-drill-empty-state">
          <div class="ov-empty-icon">
            <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><path d="M3 3v18h18"/><path d="M7 14l4-4 4 2 5-6"/><path d="M12 20V10"/></svg>
          </div>
          <div class="ov-empty-title">运营数据积累中</div>
          <div class="ov-empty-desc">该套餐刚完成上架销售，当前暂无运营数据。待次日运营通报生成健康度后，即可在此查看四大维度与异动预警，并进行根因分析。</div>
          <div class="ov-empty-sub">上架时间：{{ current.launchedAt }}</div>
        </div>

        <template v-if="!current.launched">
          <div class="ov-sec">
            <div class="ov-sec-title"><span class="ov-sec-badge ov-badge-purple"><svg class="ov-sec-ico" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg></span>健康度总览</div>
            <OpsHealthOverview :drill="current" :dims="dims" :total="total" :level="level" />
          </div>

          <div class="ov-sec">
            <div class="ov-sec-title"><span class="ov-sec-badge ov-badge-orange"><svg class="ov-sec-ico" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg></span>异动预警</div>
            <div class="ov-drill-alerts">
              <div v-for="(a, i) in current.anomalies" :key="i" class="ov-drill-alert" :class="'ov-alert-' + a.level">
                <span class="ov-drill-alert-tag">{{ a.level === 'high' ? '高' : a.level === 'mid' ? '中' : '低' }}</span>
                <span class="ov-drill-alert-text">{{ a.text }}</span>
              </div>
              <div v-if="!current.anomalies.length" class="ov-drill-alert ov-alert-low"><span class="ov-drill-alert-tag">无</span><span class="ov-drill-alert-text">当前无显著异动</span></div>
            </div>
          </div>

          <div class="ov-sec ov-sec-noborder">
            <div class="ov-sec-title"><span class="ov-sec-badge ov-badge-blue"><svg class="ov-sec-ico" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg></span>关键运营指标</div>
            <div class="ov-sec-grid ov-sec-grid-3">
              <div v-for="q in current.quality" :key="q.label" class="ov-kpi">
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
</template>

<script setup>
import OpsHealthOverview from './OpsHealthOverview.vue'
import { drillHealthCls, drillDeltaCls } from './opsFormat.js'

defineProps({
  open: { type: Boolean, default: true },
  drillKeys: { type: Array, required: true },
  lookup: { type: Object, required: true },
  highlightKey: { type: String, default: null },
  activeKey: { type: String, default: null },
  current: { type: Object, default: null },
  dims: { type: Array, required: true },
  total: { type: Number, required: true },
  level: { type: String, required: true },
  productTotal: { type: Function, required: true },
  productLevel: { type: Function, required: true },
})

defineEmits(['toggle', 'select', 'open-rootcause'])
</script>

<style src="./opsView.css" scoped></style>
