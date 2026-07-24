<template>
  <div class="assistant-home rd-home">
    <section class="hero">
      <div class="hero-copy">
        <p class="eyebrow">AI 原生 · 产商品研发</p>
        <h1>产商品研发助手</h1>
        <p class="subtitle">
          对话配置、方案导入、历史复用与合规校验一体完成，让商品上架更快、更准、更合规。
        </p>
        <ul class="tags">
          <li>智聊配置</li>
          <li>智读批量</li>
          <li>历史复用</li>
          <li>事前合规</li>
        </ul>
      </div>
      <div class="hero-actions">
        <button class="primary" type="button" @click="launch('chat')">对话配置</button>
        <button type="button" @click="launch('file')">批量生成</button>
        <button type="button" @click="launch('query')">AI智查</button>
        <button type="button" @click="launch('compliance')">合规校验</button>
      </div>
    </section>

    <section class="cards" aria-label="研发场景">
      <button class="card" type="button" @click="launch('chat')">
        <strong>对话配置</strong>
        <span>直接说业务诉求，本体自动填字段并拦截冲突</span>
        <em>示例：家庭融合套餐 158 元 / 500M</em>
      </button>
      <button class="card" type="button" @click="launch('file')">
        <strong>批量生成</strong>
        <span>导入方案文档，一次映射多套合规配置草稿</span>
        <em>示例：校园迎新方案批量入库</em>
      </button>
      <button class="card" type="button" @click="launch('query')">
        <strong>AI智查</strong>
        <span>检索历史商品与成熟配置，快速复制复用</span>
        <em>示例：近30天大学生套餐</em>
      </button>
      <button class="card" type="button" @click="launch('compliance')">
        <strong>合规校验</strong>
        <span>配置当下完成规则校验，事前拦截在架冲突</span>
        <em>示例：校验当前配置是否可上架</em>
      </button>
    </section>
  </div>
</template>

<script setup>
const emit = defineEmits(['launch-skill'])

const prompts = {
  chat: '给家庭用户做一个融合套餐，月费158，带500M宽带，全渠道销售',
  file: '帮我导入校园迎新方案',
  query: '查一下近30天大学生套餐配置',
  compliance: '校验当前配置是否符合在架规则',
}

function launch(skill) {
  const map = {
    chat: 'chat',
    file: 'file',
    query: 'query',
    compliance: 'chat',
  }
  emit('launch-skill', { skill: map[skill] || 'chat', text: prompts[skill] })
}
</script>

<style scoped>
.assistant-home { padding: 28px; display: flex; flex-direction: column; gap: 18px; }
.hero {
  background: linear-gradient(135deg, #eff6ff, #f8fafc);
  border: 1px solid #dbeafe;
  border-radius: 20px;
  padding: 28px;
}
.eyebrow { color: #2563eb; font-weight: 700; margin: 0 0 8px; font-size: 12px; letter-spacing: 0.06em; text-transform: uppercase; }
h1 { margin: 0; font-size: 28px; color: #0f172a; letter-spacing: -0.02em; }
.subtitle { margin: 10px 0 0; color: #475569; line-height: 1.7; max-width: 640px; }
.tags { list-style: none; display: flex; flex-wrap: wrap; gap: 8px; margin: 16px 0 0; padding: 0; }
.tags li {
  font-size: 12px; font-weight: 600; color: #2563eb; background: #dbeafe;
  border-radius: 999px; padding: 5px 12px;
}
.hero-actions { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 18px; }
button {
  border: 1px solid #cbd5e1; background: #fff; color: #0f172a;
  border-radius: 999px; padding: 10px 16px; cursor: pointer; font: inherit;
}
button.primary { background: #2563eb; color: #fff; border-color: #2563eb; }
.cards { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.card {
  text-align: left; padding: 18px; border-radius: 18px; background: #fff;
  border: 1px solid #e2e8f0; display: flex; flex-direction: column; gap: 8px;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.card:hover { border-color: #93c5fd; box-shadow: 0 8px 20px rgba(37, 99, 235, 0.1); }
.card strong { display: block; color: #0f172a; }
.card span { color: #64748b; line-height: 1.6; font-size: 13px; }
.card em { font-style: normal; font-size: 12px; color: #94a3b8; }
@media (max-width: 900px) { .cards { grid-template-columns: 1fr; } }
</style>
