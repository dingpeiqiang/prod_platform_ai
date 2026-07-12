<template>
  <div class="demo-dashboard">
    <div class="demo-header">
      <button class="back-btn" @click="$emit('go-back')">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="15 18 9 12 15 6"></polyline>
        </svg>
        返回
      </button>
      <h1 class="demo-title">产品智能配置助手</h1>
      <p class="demo-subtitle">三大智能演示场景,体验AI驱动的产品配置新方式</p>
    </div>

    <div class="scenario-cards">
      <div
        v-for="scenario in scenarios"
        :key="scenario.key"
        class="scenario-card"
        :class="`scenario-${scenario.key}`"
        @click="$emit('enter-scenario', scenario.key)"
      >
        <div class="scenario-icon" v-html="scenario.icon"></div>
        <div class="scenario-content">
          <h2 class="scenario-name">{{ scenario.name }}</h2>
          <p class="scenario-tagline">{{ scenario.tagline }}</p>
          <p class="scenario-desc">{{ scenario.description }}</p>
          <div class="scenario-highlights">
            <span v-for="highlight in scenario.highlights" :key="highlight" class="highlight-tag">
              {{ highlight }}
            </span>
          </div>
        </div>
        <div class="scenario-action">
          <span class="enter-text">进入演示</span>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="5" y1="12" x2="19" y2="12"/>
            <polyline points="12 5 19 12 12 19"/>
          </svg>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
const scenarios = [
  {
    key: 'search',
    name: '智查・一键复制',
    tagline: '存量配置复用',
    description: '通过自然语言语义检索历史配置方案,一键克隆并修改差异字段,3分钟完成原本1小时的配置工作。',
    highlights: ['语义检索', '差异预览', '一键克隆', '本体校验'],
    icon: '<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="11" y1="8" x2="11" y2="14"/><line x1="8" y1="11" x2="14" y2="11"/></svg>'
  },
  {
    key: 'doc',
    name: '智读・批量生成',
    tagline: '方案文档批量开品',
    description: '上传Word营销方案文档,AI自动解析并拆分多档套餐,并行批量生成结构化配置草稿,效率提升80%。',
    highlights: ['文档智读', '批量解析', '并行生成', '批量稽核'],
    icon: '<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>'
  },
  {
    key: 'dialog',
    name: '智聊・对话式配置',
    tagline: '零基础极简配置',
    description: '通过多轮对话描述需求,AI自动拆解配置框架,可视化画布实时同步,所见即所得的配置体验。',
    highlights: ['多轮对话', '可视化画布', '智能追问', '拖拽编辑'],
    icon: '<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>'
  }
]
</script>

<style scoped>
.demo-dashboard {
  height: 100%;
  overflow-y: auto;
  background: var(--bg-secondary, #f5f7fa);
  padding: 32px 48px;
}

.demo-header {
  margin-bottom: 40px;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: transparent;
  border: 1px solid var(--border-color, #e0e0e0);
  border-radius: 8px;
  color: var(--text-secondary, #666);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 24px;
}

.back-btn:hover {
  background: var(--bg-primary, #fff);
  color: var(--color-primary, #3b82f6);
  border-color: var(--color-primary, #3b82f6);
}

.demo-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary, #1a1a1a);
  margin: 0 0 8px 0;
}

.demo-subtitle {
  font-size: 15px;
  color: var(--text-tertiary, #999);
  margin: 0;
}

.scenario-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
  gap: 24px;
  max-width: 1400px;
}

.scenario-card {
  background: var(--bg-primary, #fff);
  border-radius: 16px;
  padding: 32px;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 2px solid transparent;
  display: flex;
  flex-direction: column;
  gap: 20px;
  position: relative;
  overflow: hidden;
}

.scenario-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  opacity: 0;
  transition: opacity 0.3s;
}

.scenario-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.1);
}

.scenario-card:hover::before {
  opacity: 1;
}

.scenario-search::before { background: linear-gradient(90deg, #3b82f6, #60a5fa); }
.scenario-doc::before { background: linear-gradient(90deg, #10b981, #34d399); }
.scenario-dialog::before { background: linear-gradient(90deg, #f59e0b, #fbbf24); }

.scenario-search:hover { border-color: #3b82f6; }
.scenario-doc:hover { border-color: #10b981; }
.scenario-dialog:hover { border-color: #f59e0b; }

.scenario-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.scenario-search .scenario-icon { background: rgba(59, 130, 246, 0.1); color: #3b82f6; }
.scenario-doc .scenario-icon { background: rgba(16, 185, 129, 0.1); color: #10b981; }
.scenario-dialog .scenario-icon { background: rgba(245, 158, 11, 0.1); color: #f59e0b; }

.scenario-content {
  flex: 1;
}

.scenario-name {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary, #1a1a1a);
  margin: 0 0 4px 0;
}

.scenario-tagline {
  font-size: 13px;
  color: var(--text-tertiary, #999);
  margin: 0 0 12px 0;
}

.scenario-desc {
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-secondary, #666);
  margin: 0 0 16px 0;
}

.scenario-highlights {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.highlight-tag {
  padding: 4px 12px;
  background: var(--bg-tertiary, #f0f2f5);
  border-radius: 12px;
  font-size: 12px;
  color: var(--text-secondary, #666);
}

.scenario-search .highlight-tag { background: rgba(59, 130, 246, 0.08); color: #3b82f6; }
.scenario-doc .highlight-tag { background: rgba(16, 185, 129, 0.08); color: #10b981; }
.scenario-dialog .highlight-tag { background: rgba(245, 158, 11, 0.08); color: #f59e0b; }

.scenario-action {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--border-color, #f0f0f0);
}

.enter-text {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-tertiary, #999);
  transition: color 0.2s;
}

.scenario-card:hover .enter-text {
  color: var(--text-primary, #1a1a1a);
}

.scenario-search:hover .enter-text { color: #3b82f6; }
.scenario-doc:hover .enter-text { color: #10b981; }
.scenario-dialog:hover .enter-text { color: #f59e0b; }
</style>
