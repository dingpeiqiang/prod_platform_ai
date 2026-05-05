<template>
  <!-- 纯聊天意图无需额外面板，流式文本由 ChatAssistant.vue 统一渲染 -->
  <div class="chat-intent-panel">
    <!-- 空面板 — 聊天意图的文本渲染由父组件 msg.streamText 处理 -->
  </div>
</template>

<script setup>
/**
 * ChatPanel - 纯聊天意图面板（占位组件）
 * 纯聊天的 SSE 流式文本由 ChatAssistant.vue 的通用文本渲染逻辑处理，
 * 此组件仅作为注册器中的占位，方便未来扩展聊天意图的专属 UI。
 */
</script>
