<template>
  <div class="assistant-home rd-home">
    <section class="hero">
      <div class="hero-copy">
        <p class="eyebrow">AI 原生 · 产商品研发</p>
        <h1>产商品研发助手</h1>
        <p class="subtitle">
          智聊·对话配置、智读·文件配置、智查·历史复用与智检·合规校验一体完成，让商品上架更快、更准、更合规。
        </p>
        <ul class="tags">
          <li>智聊·对话配置</li>
          <li>智读·文件配置</li>
          <li>智查·历史复用</li>
          <li>智检·合规校验</li>
        </ul>
      </div>
      <div class="hero-actions">
        <button class="primary" type="button" @click="launch('chat')">智聊·对话配置</button>
        <button type="button" @click="launch('file')">智读·文件配置</button>
        <button type="button" @click="launch('query')">智查·历史复用</button>
        <button type="button" @click="launch('compliance')">智检·合规校验</button>
      </div>
    </section>

    <section class="cards" aria-label="研发场景">
      <button class="card" type="button" @click="launch('chat')">
        <strong>智聊·对话配置</strong>
        <span>直接说业务诉求，本体自动填字段并拦截冲突</span>
        <em>示例：家庭融合套餐 158 元 / 500M</em>
      </button>
      <button class="card" type="button" @click="launch('file')">
        <strong>智读·文件配置</strong>
        <span>粘贴或上传方案文档，按内容映射多套配置草稿</span>
        <em>示例：粘贴方案段落 / 家庭融合测试稿</em>
      </button>
      <button class="card" type="button" @click="launch('query')">
        <strong>智查·历史复用</strong>
        <span>检索历史商品与成熟配置，快速复制复用</span>
        <em>示例：近30天大学生套餐</em>
      </button>
      <button class="card" type="button" @click="launch('compliance')">
        <strong>智检·合规校验</strong>
        <span>按套餐信息校验：已入库在架套餐或未入库配置草稿</span>
        <em>示例：校验校园体验流量包0元 / 校验当前配置</em>
      </button>
    </section>
  </div>
</template>

<script setup>
import { ZHIDU_TEST_PROMPT } from '../data/zhiduTestDoc.js'

const emit = defineEmits(['launch-skill'])

const prompts = {
  chat: '给家庭用户做一个融合套餐，月费158，带500M宽带，全渠道销售',
  file: ZHIDU_TEST_PROMPT,
  query: '查一下近30天大学生套餐配置',
  compliance: '校验校园体验流量包0元是否符合在架规则',
}

function launch(skill) {
  const map = {
    chat: 'chat',
    file: 'file',
    query: 'query',
    compliance: 'compliance',
  }
  // 生产：只进入场景并预填示例，不自动发送
  emit('launch-skill', {
    skill: map[skill] || 'chat',
    text: prompts[skill],
    autoSend: false,
  })
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
