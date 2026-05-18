<template>
  <div class="kb-manager">
    <!-- 头部 -->
    <div class="kb-header">
      <button class="back-btn" @click="$emit('go-back')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="15 18 9 12 15 6"></polyline>
        </svg>
        返回
      </button>
      <h1 class="page-title">📚 知识库管理</h1>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-cards">
      <div class="stat-card">
        <div class="stat-icon documents">📄</div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.total_entries || 0 }}</div>
          <div class="stat-label">文档总数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon sessions">📊</div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.total_sessions || 0 }}</div>
          <div class="stat-label">会话数</div>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon usage">📈</div>
        <div class="stat-info">
          <div class="stat-value">{{ (stats.utilization * 100).toFixed(1) }}%</div>
          <div class="stat-label">存储使用率</div>
        </div>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="kb-content">
      <!-- 左侧：问答区域 -->
      <div class="qa-panel">
        <div class="panel-header">
          <h2>💡 知识库问答</h2>
        </div>
        
        <div class="chat-container">
          <div class="message-list">
            <div v-for="(msg, index) in chatMessages" :key="index" :class="['message', msg.type]">
              <div class="message-avatar">{{ msg.type === 'user' ? '👤' : '🤖' }}</div>
              <div class="message-content">
                <div class="message-text">{{ msg.content }}</div>
                <div v-if="msg.sources && msg.sources.length" class="message-sources">
                  <div class="sources-label">来源：</div>
                  <div v-for="(src, i) in msg.sources" :key="i" class="source-tag">
                    {{ src.title }} ({{ (src.similarity * 100).toFixed(1) }}%)
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="input-area">
            <input 
              v-model="queryInput" 
              type="text" 
              placeholder="输入您的问题..."
              class="query-input"
              @keyup.enter="handleQA"
            />
            <button class="qa-btn" @click="handleQA" :disabled="!queryInput.trim()">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
              </svg>
              查询
            </button>
          </div>
        </div>
      </div>

      <!-- 右侧：文档管理 -->
      <div class="doc-panel">
        <div class="panel-header">
          <h2>📁 文档管理</h2>
          <button class="add-btn" @click="showAddModal = true">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"></line>
              <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
            添加文档
          </button>
        </div>

        <!-- 搜索框 -->
        <div class="search-box">
          <input 
            v-model="searchQuery" 
            type="text" 
            placeholder="搜索文档..."
            class="search-input"
            @input="handleSearch"
          />
        </div>

        <!-- 文档列表 -->
        <div class="doc-list">
          <div 
            v-for="doc in documentList" 
            :key="doc.id" 
            class="doc-item"
            @click="selectDocument(doc)"
            :class="{ selected: selectedDoc?.id === doc.id }"
          >
            <div class="doc-icon">📄</div>
            <div class="doc-info">
              <div class="doc-title">{{ doc.title || '未命名文档' }}</div>
              <div class="doc-meta">
                <span class="meta-item">相似度: {{ (doc.similarity * 100).toFixed(1) }}%</span>
                <span class="meta-item">创建: {{ formatDate(doc.created_at) }}</span>
              </div>
            </div>
            <button class="delete-btn" @click.stop="handleDelete(doc.id)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M3 6h18"></path>
                <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"></path>
                <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"></path>
              </svg>
            </button>
          </div>

          <div v-if="documentList.length === 0" class="empty-state">
            <div class="empty-icon">📭</div>
            <div class="empty-text">{{ searchQuery ? '未找到匹配的文档' : '暂无文档，请添加' }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 文档详情弹窗 -->
    <div v-if="selectedDoc" class="modal-overlay" @click="selectedDoc = null">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h3>{{ selectedDoc.title || '文档详情' }}</h3>
          <button class="close-btn" @click="selectedDoc = null">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <div class="detail-row">
            <span class="detail-label">ID:</span>
            <span class="detail-value">{{ selectedDoc.id }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">来源:</span>
            <span class="detail-value">{{ selectedDoc.source || '未知' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">创建时间:</span>
            <span class="detail-value">{{ formatDate(selectedDoc.created_at) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">访问次数:</span>
            <span class="detail-value">{{ selectedDoc.access_count }}</span>
          </div>
          <div class="content-section">
            <span class="detail-label">内容:</span>
            <pre class="doc-content">{{ selectedDoc.content }}</pre>
          </div>
        </div>
      </div>
    </div>

    <!-- 添加文档弹窗 -->
    <div v-if="showAddModal" class="modal-overlay" @click="showAddModal = false">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h3>添加新文档</h3>
          <button class="close-btn" @click="showAddModal = false">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>标题</label>
            <input v-model="newDoc.title" type="text" placeholder="请输入文档标题" />
          </div>
          <div class="form-group">
            <label>来源</label>
            <input v-model="newDoc.source" type="text" placeholder="文档来源（可选）" />
          </div>
          <div class="form-group">
            <label>重要度</label>
            <input v-model.number="newDoc.importance" type="range" min="0.1" max="2" step="0.1" />
            <span class="range-value">{{ newDoc.importance.toFixed(1) }}</span>
          </div>
          <div class="form-group">
            <label>内容</label>
            <textarea v-model="newDoc.content" rows="8" placeholder="请输入文档内容..."></textarea>
          </div>
          <div class="modal-footer">
            <button class="btn-cancel" @click="showAddModal = false">取消</button>
            <button class="btn-submit" @click="handleAddDocument">添加</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import * as kbApi from '../services/kbApi';

const emit = defineEmits(['go-back']);

const stats = ref({
  total_entries: 0,
  total_sessions: 0,
  utilization: 0
});

const queryInput = ref('');
const searchQuery = ref('');
const chatMessages = ref([]);
const documentList = ref([]);
const selectedDoc = ref(null);
const showAddModal = ref(false);

const newDoc = ref({
  title: '',
  source: '',
  content: '',
  importance: 1.0
});

const loadStats = async () => {
  try {
    const result = await kbApi.getStats();
    if (result.success) {
      stats.value = result.data;
    }
  } catch (error) {
    console.error('Failed to load stats:', error);
  }
};

const handleQA = async () => {
  if (!queryInput.value.trim()) return;
  
  chatMessages.value.push({
    type: 'user',
    content: queryInput.value
  });
  
  try {
    const result = await kbApi.qa(queryInput.value);
    chatMessages.value.push({
      type: 'bot',
      content: result.answer,
      sources: result.sources
    });
  } catch (error) {
    chatMessages.value.push({
      type: 'bot',
      content: '查询失败，请重试'
    });
  }
  
  queryInput.value = '';
};

const handleSearch = async () => {
  if (!searchQuery.value.trim()) {
    documentList.value = [];
    return;
  }
  
  try {
    const result = await kbApi.searchDocuments(searchQuery.value);
    if (result.success) {
      documentList.value = result.results;
    }
  } catch (error) {
    console.error('Search failed:', error);
  }
};

const selectDocument = async (doc) => {
  try {
    const result = await kbApi.getDocument(doc.id);
    selectedDoc.value = result;
  } catch (error) {
    console.error('Failed to get document:', error);
  }
};

const handleDelete = async (id) => {
  if (!confirm('确定要删除这个文档吗？')) return;
  
  try {
    const result = await kbApi.deleteDocument(id);
    if (result.success) {
      documentList.value = documentList.value.filter(d => d.id !== id);
      await loadStats();
    }
  } catch (error) {
    console.error('Delete failed:', error);
  }
};

const handleAddDocument = async () => {
  if (!newDoc.value.content.trim()) {
    alert('请输入文档内容');
    return;
  }
  
  try {
    const result = await kbApi.addDocument(
      newDoc.value.content,
      newDoc.value.title,
      newDoc.value.source,
      newDoc.value.importance
    );
    
    if (result.success) {
      showAddModal.value = false;
      newDoc.value = { title: '', source: '', content: '', importance: 1.0 };
      await loadStats();
      alert('文档添加成功');
    }
  } catch (error) {
    console.error('Add document failed:', error);
  }
};

const formatDate = (dateStr) => {
  if (!dateStr) return '未知';
  const date = new Date(dateStr);
  return date.toLocaleString('zh-CN');
};

onMounted(() => {
  loadStats();
});
</script>

<style scoped>
.kb-manager {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 20px;
  gap: 20px;
}

.kb-header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.back-btn:hover {
  background: var(--bg-hover);
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.stats-cards {
  display: flex;
  gap: 16px;
}

.stat-card {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.stat-icon.documents {
  background: linear-gradient(135deg, rgba(91, 124, 250, 0.15), rgba(91, 124, 250, 0.05));
}

.stat-icon.sessions {
  background: linear-gradient(135deg, rgba(34, 197, 94, 0.15), rgba(34, 197, 94, 0.05));
}

.stat-icon.usage {
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.15), rgba(139, 92, 246, 0.05));
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-primary);
}

.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.kb-content {
  flex: 1;
  display: flex;
  gap: 20px;
  min-height: 0;
}

.qa-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-default);
}

.panel-header h2 {
  font-size: 16px;
  font-weight: 500;
  margin: 0;
  color: var(--text-primary);
}

.add-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: var(--color-primary-500);
  border: none;
  border-radius: var(--radius-md);
  color: white;
  font-size: 13px;
  cursor: pointer;
  transition: background var(--transition-fast);
}

.add-btn:hover {
  background: var(--color-primary-600);
}

.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message {
  display: flex;
  gap: 10px;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}

.message.user .message-avatar {
  background: var(--color-primary-100);
}

.message.bot .message-avatar {
  background: var(--color-success-100);
}

.message-content {
  flex: 1;
  padding: 10px 14px;
  border-radius: var(--radius-lg);
  max-width: 85%;
}

.message.user .message-content {
  background: var(--color-primary-500);
  color: white;
  border-radius: var(--radius-lg) var(--radius-lg) 0 var(--radius-lg);
}

.message.bot .message-content {
  background: var(--bg-tertiary);
  color: var(--text-primary);
  border-radius: var(--radius-lg) var(--radius-lg) var(--radius-lg) 0;
}

.message-text {
  font-size: 14px;
  line-height: 1.6;
}

.message-sources {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid rgba(0,0,0,0.1);
}

.sources-label {
  font-size: 12px;
  color: var(--text-muted);
}

.source-tag {
  font-size: 12px;
  padding: 2px 8px;
  background: rgba(255,255,255,0.2);
  border-radius: var(--radius-sm);
}

.input-area {
  display: flex;
  gap: 10px;
  padding: 16px;
  border-top: 1px solid var(--border-default);
}

.query-input {
  flex: 1;
  padding: 10px 14px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  font-size: 14px;
  background: var(--bg-secondary);
  color: var(--text-primary);
}

.qa-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: var(--color-primary-500);
  border: none;
  border-radius: var(--radius-lg);
  color: white;
  font-size: 14px;
  cursor: pointer;
  transition: background var(--transition-fast);
}

.qa-btn:hover:not(:disabled) {
  background: var(--color-primary-600);
}

.qa-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.doc-panel {
  width: 400px;
  display: flex;
  flex-direction: column;
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.search-box {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-default);
}

.search-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  font-size: 13px;
  background: var(--bg-secondary);
  color: var(--text-primary);
  box-sizing: border-box;
}

.doc-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.doc-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.doc-item:hover {
  background: var(--bg-hover);
}

.doc-item.selected {
  background: var(--color-primary-50);
  border: 1px solid var(--color-primary-200);
}

.doc-icon {
  font-size: 20px;
}

.doc-info {
  flex: 1;
  min-width: 0;
}

.doc-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-meta {
  display: flex;
  gap: 12px;
  margin-top: 4px;
}

.meta-item {
  font-size: 12px;
  color: var(--text-secondary);
}

.delete-btn {
  display: none;
  background: none;
  border: none;
  padding: 6px;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.doc-item:hover .delete-btn {
  display: flex;
}

.delete-btn:hover {
  background: var(--color-error-50);
  color: var(--color-error-500);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: var(--text-muted);
}

.empty-icon {
  font-size: 40px;
  margin-bottom: 12px;
}

.empty-text {
  font-size: 14px;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  width: 90%;
  max-width: 600px;
  max-height: 80vh;
  background: var(--bg-elevated);
  border-radius: var(--radius-xl);
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-default);
}

.modal-header h3 {
  margin: 0;
  font-size: 16px;
  color: var(--text-primary);
}

.close-btn {
  background: none;
  border: none;
  padding: 6px;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.close-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.modal-body {
  padding: 20px;
  max-height: 60vh;
  overflow-y: auto;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  font-size: 14px;
  background: var(--bg-secondary);
  color: var(--text-primary);
  box-sizing: border-box;
}

.form-group textarea {
  resize: vertical;
}

.form-group input[type="range"] {
  padding: 0;
}

.range-value {
  display: inline-block;
  margin-left: 12px;
  font-size: 13px;
  color: var(--color-primary-500);
  font-weight: 500;
}

.detail-row {
  display: flex;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-light);
}

.detail-label {
  font-size: 13px;
  color: var(--text-muted);
  min-width: 80px;
}

.detail-value {
  font-size: 13px;
  color: var(--text-primary);
  flex: 1;
}

.content-section {
  margin-top: 16px;
}

.doc-content {
  margin-top: 8px;
  padding: 12px;
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-primary);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 300px;
  overflow-y: auto;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--border-default);
}

.btn-cancel {
  padding: 8px 20px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: 14px;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.btn-cancel:hover {
  background: var(--bg-hover);
}

.btn-submit {
  padding: 8px 20px;
  background: var(--color-primary-500);
  border: none;
  border-radius: var(--radius-md);
  color: white;
  font-size: 14px;
  cursor: pointer;
  transition: background var(--transition-fast);
}

.btn-submit:hover {
  background: var(--color-primary-600);
}
</style>