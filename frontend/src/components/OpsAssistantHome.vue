<template>
  <div class="assistant-home ops-home">
    <section class="hero">
      <div class="hero-copy">
        <p class="eyebrow">产商品运营助手</p>
        <h1>市场洞察、立项研判、异动归因、风险稽核一屏直达</h1>
        <p class="subtitle">基于本体图谱与规则引擎的运营工作台，直接面向产商品运营场景。</p>
      </div>
      <div class="hero-actions">
        <button class="primary" @click="launch('ops-rootcause')">异动根因分析</button>
        <button @click="launch('ops-risk')">高风险商品稽核</button>
        <button @click="launch('ops-online')">立项研判</button>
      </div>
    </section>

    <section class="cards">
      <button class="card" @click="launch('ops-query')">
        <strong>市场洞察</strong>
        <span>自然语言检索在售商品、指标与竞品态势</span>
      </button>
      <button class="card" @click="launch('ops-online')">
        <strong>立项研判</strong>
        <span>判断新品是否满足上线门槛与风险红线</span>
      </button>
      <button class="card" @click="launch('ops-rootcause')">
        <strong>异动归因</strong>
        <span>定位收入、留存、渠道变化背后的主因</span>
      </button>
      <button class="card" @click="launch('ops-risk')">
        <strong>风险稽核</strong>
        <span>批量识别零费、低效与长期零销商品</span>
      </button>
    </section>
  </div>
</template>

<script setup>
const emit = defineEmits(['launch-skill', 'open-online'])
function launch(skill) {
  const text =
    skill === 'ops-rootcause'
      ? '分析家庭融合畅享128本月收入下滑原因'
      : skill === 'ops-risk'
        ? '筛查所有在架的0元资费风险商品'
        : skill === 'ops-online'
          ? '判断这个新品能不能立项'
          : '查一下在售5G套餐和风险商品'
  if (skill === 'ops-online') {
    emit('open-online')
    return
  }
  emit('launch-skill', { skill: 'ops', text })
}
</script>

<style scoped>
.assistant-home { padding: 28px; display: flex; flex-direction: column; gap: 18px; }
.hero { background: linear-gradient(135deg, #ecfeff, #f8fafc); border: 1px solid #a5f3fc; border-radius: 20px; padding: 28px; }
.eyebrow { color: #0f766e; font-weight: 700; margin: 0 0 8px; }
h1 { margin: 0; font-size: 30px; color: #0f172a; }
.subtitle { margin: 10px 0 0; color: #475569; line-height: 1.7; }
.hero-actions { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 18px; }
button { border: 1px solid #cbd5e1; background: #fff; color: #0f172a; border-radius: 999px; padding: 10px 16px; cursor: pointer; }
button.primary { background: #0f766e; color: #fff; border-color: #0f766e; }
.cards { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.card { text-align: left; padding: 18px; border-radius: 18px; background: #fff; }
.card strong { display: block; margin-bottom: 8px; }
.card span { color: #64748b; line-height: 1.6; }
@media (max-width: 900px) { .cards { grid-template-columns: 1fr; } }
</style>
