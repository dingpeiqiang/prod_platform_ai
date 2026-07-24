<template>
  <div class="assistant-home ops-home">
    <section class="hero">
      <div class="hero-copy">
        <p class="eyebrow">AI 原生 · 产商品运营</p>
        <h1>产商品运营助手</h1>
        <p class="subtitle">
          市场洞察、立项研判、异动归因与风险稽核一屏直达，用本体推理定位问题，用规则保障决策合规。
        </p>
        <ul class="tags">
          <li>市场洞察</li>
          <li>立项研判</li>
          <li>异动归因</li>
          <li>风险稽核</li>
        </ul>
      </div>
      <div class="hero-actions">
        <button class="primary" type="button" @click="launch('ops-rootcause')">异动归因</button>
        <button type="button" @click="launch('ops-risk')">风险稽核</button>
        <button type="button" @click="launch('ops-online')">立项研判</button>
        <button type="button" @click="launch('ops-query')">市场洞察</button>
      </div>
    </section>

    <section class="cards" aria-label="运营场景">
      <button class="card" type="button" @click="launch('ops-query')">
        <strong>市场洞察</strong>
        <span>自然语言检索在售商品、增长指标与竞品态势</span>
        <em>示例：在售5G套餐与风险商品</em>
      </button>
      <button class="card" type="button" @click="launch('ops-online')">
        <strong>立项研判</strong>
        <span>评估新品是否满足上线门槛与风险红线</span>
        <em>示例：青春卡套餐能否立项</em>
      </button>
      <button class="card" type="button" @click="launch('ops-rootcause')">
        <strong>异动归因</strong>
        <span>多跳关联推理，定位收入、留存、渠道变化主因</span>
        <em>示例：家庭融合畅享128收入下滑</em>
      </button>
      <button class="card" type="button" @click="launch('ops-risk')">
        <strong>风险稽核</strong>
        <span>批量识别零费、低效与长期零销商品并给处置建议</span>
        <em>示例：筛查在架0元资费风险</em>
      </button>
    </section>
  </div>
</template>

<script setup>
const emit = defineEmits(['launch-skill', 'open-online'])

const prompts = {
  'ops-rootcause': '分析家庭融合畅享128本月收入下滑原因',
  'ops-risk': '筛查所有在架的0元资费风险商品',
  'ops-online': '评估新推出的青春卡套餐能否通过立项审核',
  'ops-query': '查一下在售5G套餐的增长趋势和风险商品',
}

function launch(skill) {
  if (skill === 'ops-online') {
    emit('open-online')
    return
  }
  emit('launch-skill', { skill: 'ops', text: prompts[skill] })
}
</script>

<style scoped>
.assistant-home { padding: 28px; display: flex; flex-direction: column; gap: 18px; }
.hero {
  background: linear-gradient(135deg, #ecfeff, #f8fafc);
  border: 1px solid #a5f3fc;
  border-radius: 20px;
  padding: 28px;
}
.eyebrow { color: #0f766e; font-weight: 700; margin: 0 0 8px; font-size: 12px; letter-spacing: 0.06em; text-transform: uppercase; }
h1 { margin: 0; font-size: 28px; color: #0f172a; letter-spacing: -0.02em; }
.subtitle { margin: 10px 0 0; color: #475569; line-height: 1.7; max-width: 640px; }
.tags { list-style: none; display: flex; flex-wrap: wrap; gap: 8px; margin: 16px 0 0; padding: 0; }
.tags li {
  font-size: 12px; font-weight: 600; color: #0f766e; background: #ccfbf1;
  border-radius: 999px; padding: 5px 12px;
}
.hero-actions { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 18px; }
button {
  border: 1px solid #cbd5e1; background: #fff; color: #0f172a;
  border-radius: 999px; padding: 10px 16px; cursor: pointer; font: inherit;
}
button.primary { background: #0f766e; color: #fff; border-color: #0f766e; }
.cards { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.card {
  text-align: left; padding: 18px; border-radius: 18px; background: #fff;
  border: 1px solid #e2e8f0; display: flex; flex-direction: column; gap: 8px;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.card:hover { border-color: #5eead4; box-shadow: 0 8px 20px rgba(15, 118, 110, 0.1); }
.card strong { display: block; color: #0f172a; }
.card span { color: #64748b; line-height: 1.6; font-size: 13px; }
.card em { font-style: normal; font-size: 12px; color: #94a3b8; }
@media (max-width: 900px) { .cards { grid-template-columns: 1fr; } }
</style>
