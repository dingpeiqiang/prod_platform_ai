<template>
  <div class="assistant-home rd-home">
    <section class="hero">
      <div class="hero-copy">
        <p class="eyebrow">产商品研发助手</p>
        <h1>让商品配置更快、更准、更合规</h1>
        <p class="subtitle">面向商品配置、批量导入、历史复用与配置合规校验的一体化 AI 工作台。</p>
      </div>
      <div class="hero-actions">
        <button class="primary" @click="launch('chat')">智聊·对话配置</button>
        <button @click="launch('file')">智读·批量生成</button>
        <button @click="launch('query')">AI智查</button>
      </div>
    </section>

    <section class="cards">
      <button class="card" @click="launch('chat')">
        <strong>对话配置</strong>
        <span>直接说业务，本体自动填字段并拦截冲突</span>
      </button>
      <button class="card" @click="launch('file')">
        <strong>批量生成</strong>
        <span>导入方案文档，一次映射多个配置草稿</span>
      </button>
      <button class="card" @click="launch('query')">
        <strong>历史查询</strong>
        <span>快速检索已有商品，复用成熟配置</span>
      </button>
      <button class="card" @click="launch('chat')">
        <strong>合规校验</strong>
        <span>配置当下完成冲突拦截，而不是事后稽核</span>
      </button>
    </section>
  </div>
</template>

<script setup>
const emit = defineEmits(['launch-skill'])
function launch(skill) {
  const text =
    skill === 'chat'
      ? '给家庭用户做一个融合套餐，月费158，带500M宽带，全渠道销售'
      : skill === 'file'
        ? '帮我导入校园迎新方案'
        : '查一下近30天大学生套餐配置'
  emit('launch-skill', { skill, text })
}
</script>

<style scoped>
.assistant-home { padding: 28px; display: flex; flex-direction: column; gap: 18px; }
.hero { background: linear-gradient(135deg, #eff6ff, #f8fafc); border: 1px solid #dbeafe; border-radius: 20px; padding: 28px; }
.eyebrow { color: #2563eb; font-weight: 700; margin: 0 0 8px; }
h1 { margin: 0; font-size: 30px; color: #0f172a; }
.subtitle { margin: 10px 0 0; color: #475569; line-height: 1.7; }
.hero-actions { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 18px; }
button { border: 1px solid #cbd5e1; background: #fff; color: #0f172a; border-radius: 999px; padding: 10px 16px; cursor: pointer; }
button.primary { background: #2563eb; color: #fff; border-color: #2563eb; }
.cards { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.card { text-align: left; padding: 18px; border-radius: 18px; background: #fff; }
.card strong { display: block; margin-bottom: 8px; }
.card span { color: #64748b; line-height: 1.6; }
@media (max-width: 900px) { .cards { grid-template-columns: 1fr; } }
</style>
