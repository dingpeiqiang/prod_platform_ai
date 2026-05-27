<template>
  <div class="scene-manager">
    <!-- 顶部操作栏 -->
    <div class="header">
      <div class="header-left">
        <el-button @click="goBack" class="back-btn">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <h1 class="page-title">场景管理</h1>
      </div>
      <div class="stats-bar" v-if="stats">
        <div class="stat-item">
          <span class="stat-value">{{ stats.total }}</span>
          <span class="stat-label">总场景</span>
        </div>
        <div class="stat-item active">
          <span class="stat-value">{{ stats.active }}</span>
          <span class="stat-label">已启用</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">{{ stats.byType.center }}</span>
          <span class="stat-label">中心域</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">{{ stats.byType.business }}</span>
          <span class="stat-label">业务域</span>
        </div>
        <div class="stat-item">
          <span class="stat-value">{{ stats.byType.scene }}</span>
          <span class="stat-label">场景</span>
        </div>
      </div>
      <el-button type="primary" @click="handleAddCenter">
        <el-icon><Plus /></el-icon>
        新增中心域
      </el-button>
    </div>

    <!-- 主内容区 -->
    <div class="main-content">
      <!-- 左侧：场景树 -->
      <div class="tree-section">
        <div class="tree-header">
          <h3>场景结构</h3>
        </div>
        <div class="tree-search">
          <el-input 
            v-model="searchKeyword" 
            placeholder="搜索场景名称、关键词" 
            clearable
            size="small"
            :prefix-icon="Search"
          />
        </div>
        <div class="tree-container">
          <el-tree
            ref="treeRef"
            :data="filteredTreeData"
            :props="treeProps"
            node-key="id"
            default-expand-all
            highlight-current
            :expand-on-click-node="false"
            @node-click="handleNodeClick"
            class="scene-tree"
          >
            <template #default="{ node, data }">
              <div class="tree-node">
                <div class="node-content">
                  <!-- 节点图标 -->
                  <span class="node-icon" :class="`icon-${data.type}`">
                    <template v-if="data.type === 'center'">🏢</template>
                    <template v-else-if="data.type === 'business'">📁</template>
                    <template v-else>🎯</template>
                  </span>
                  <!-- 节点名称 -->
                  <span class="node-label">{{ data.sceneName || data.label }}
                    <span v-if="data.type === 'scene'" class="scene-count">(场景)</span>
                  </span>
                </div>
                <div class="node-actions">
                  <!-- 中心域：可以新增业务域 -->
                  <el-tooltip v-if="data.type === 'center'" content="新增业务域" placement="top">
                    <el-button size="small" link @click.stop="handleAddBusiness(data)" class="action-btn add-btn">
                      <el-icon><Plus /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <!-- 业务域：可以新增场景 -->
                  <el-tooltip v-if="data.type === 'business'" content="新增场景" placement="top">
                    <el-button size="small" link @click.stop="handleAddScene(data)" class="action-btn add-btn">
                      <el-icon><Plus /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <!-- 编辑 -->
                  <el-tooltip content="编辑" placement="top">
                    <el-button size="small" link @click.stop="handleEdit(data)" class="action-btn">
                      <el-icon><Edit /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <!-- 删除 -->
                  <el-tooltip content="删除" placement="top">
                    <el-button size="small" link type="danger" @click.stop="handleDelete(data)" class="action-btn">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </el-tooltip>
                </div>
              </div>
            </template>
          </el-tree>
        </div>
      </div>

      <!-- 右侧：详情区 -->
      <div class="detail-section">
        <!-- 编辑表单（新增/编辑） -->
        <div v-if="isEditing" class="edit-form-container">
          <div class="edit-header">
            <div class="header-title">
              <el-icon><Edit /></el-icon>
              <h2>{{ editingNode ? '编辑' : '新增' }} {{ typeLabel(currentType) }}</h2>
            </div>
            <div class="header-actions">
              <el-button @click="cancelEdit">
                <el-icon><ArrowLeft /></el-icon>
                取消
              </el-button>
            </div>
          </div>
          
          <el-form :model="formData" label-width="110px" ref="formRef" class="scene-form">
            <el-form-item label="编码" prop="sceneCode" required>
              <el-input v-model="formData.sceneCode" :disabled="!!editingNode" placeholder="请输入编码" @input="updateDefaultPromptCode" />
            </el-form-item>
            <el-form-item label="名称" prop="sceneName" required>
              <el-input v-model="formData.sceneName" placeholder="请输入名称" />
            </el-form-item>
            <el-form-item label="父级">
              <el-input v-model="parentInfo" disabled placeholder="无" />
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入描述" />
            </el-form-item>
            <el-form-item label="关键词">
              <div class="keyword-input-wrapper">
                <el-tag
                  v-for="(tag, index) in formData.keywords"
                  :key="index"
                  closable
                  @close="removeKeyword(index)"
                  style="margin-right: 8px"
                >
                  {{ tag }}
                </el-tag>
                <el-input
                  v-if="keywordInputVisible"
                  ref="keywordInputRef"
                  v-model="keywordInput"
                  size="small"
                  style="width: 200px"
                  @keyup.enter="confirmKeyword"
                  @blur="confirmKeyword"
                />
                <el-button v-else size="small" icon="Plus" @click="showKeywordInput" />
              </div>
            </el-form-item>
            <el-form-item label="优先级">
              <el-slider v-model="formData.priority" :min="1" :max="100" show-stops />
            </el-form-item>
            <el-form-item label="状态">
              <el-switch v-model="formData.isActive" />
            </el-form-item>
            <template v-if="currentType === 'scene'">
              <el-form-item label="关联提示词">
                <el-select v-model="formData.promptCode" placeholder="选择提示词" clearable filterable style="width: 100%">
                  <el-option
                    v-for="prompt in prompts"
                    :key="prompt.code"
                    :label="`${prompt.name} (${prompt.code})`"
                    :value="prompt.code"
                  />
                </el-select>
                <el-button v-if="formData.promptCode" link @click="openPromptEditor">编辑提示词</el-button>
              </el-form-item>
              
              <!-- 工作流配置 -->
              <el-form-item label="关联工作流">
                <div class="workflows-section">
                  <div class="workflows-intro">
                    <el-icon class="intro-icon"><Pointer /></el-icon>
                    <span class="intro-text">选择场景关联的工作流，支持多个工作流，默认工作流将优先被调用</span>
                  </div>
                  <div class="workflows-list">
                    <div
                      v-for="(workflow, index) in formData.workflows"
                      :key="index"
                      class="workflow-card"
                    >
                      <div class="workflow-card-header">
                        <div class="workflow-select-wrapper">
                          <el-select
                            v-model="workflow.code"
                            placeholder="请选择工作流"
                            size="small"
                            class="workflow-select"
                            @change="handleWorkflowChange(index, $event)"
                          >
                            <el-option
                              v-for="w in getAvailableWorkflows(index)"
                              :key="w.workflowCode"
                              :label="`${w.workflowCode} - ${w.workflowName}`"
                              :value="w.workflowCode"
                            />
                          </el-select>
                        </div>
                        <div class="workflow-card-actions">
                          <div class="default-badge" v-if="workflow.isDefault">
                            <el-tag type="primary" size="small">默认</el-tag>
                          </div>
                          <el-button
                            v-if="formData.workflows.length > 1"
                            size="small"
                            type="text"
                            icon="Delete"
                            @click="removeWorkflow(index)"
                            class="remove-btn"
                          />
                        </div>
                      </div>
                      <div v-if="workflow.code" class="workflow-card-body">
                        <div class="workflow-info-row">
                          <span class="info-label">工作流名称：</span>
                          <span class="info-value">{{ workflow.name }}</span>
                        </div>
                        <div class="workflow-info-row">
                          <span class="info-label">工作流编码：</span>
                          <span class="info-value">{{ workflow.code }}</span>
                        </div>
                        <div class="workflow-info-row">
                          <span class="info-label">描述：</span>
                          <span class="info-value">{{ workflow.description || '暂无描述' }}</span>
                        </div>
                        <div class="workflow-custom-desc">
                          <el-input
                            v-model="workflow.description"
                            placeholder="添加自定义描述（可选）"
                            size="small"
                          />
                        </div>
                        <div class="workflow-default-toggle">
                          <el-switch
                            v-model="workflow.isDefault"
                            active-text="设为默认"
                            inactive-text="取消默认"
                            size="small"
                          />
                        </div>
                      </div>
                    </div>
                  </div>
                  <div class="add-workflow-wrapper">
                    <el-button
                      size="small"
                      type="primary"
                      icon="Plus"
                      @click="addWorkflow"
                      :disabled="getAvailableWorkflows().length === 0"
                      class="add-workflow-btn"
                    >
                      添加工作流
                    </el-button>
                    <span v-if="getAvailableWorkflows().length === 0" class="no-workflow-tip">
                      已关联所有可用工作流
                    </span>
                  </div>
                </div>
              </el-form-item>
            </template>
          </el-form>
          
          <div class="form-footer">
            <el-button @click="cancelEdit">取消</el-button>
            <el-button type="primary" @click="handleSave" :loading="saving">
              {{ editingNode ? '保存修改' : '创建' }}
            </el-button>
          </div>
        </div>

        <!-- 详情视图 -->
        <div v-else-if="selectedNode" class="scene-detail">
          <div class="detail-header">
            <div class="header-title">
              <span class="scene-icon">
                <template v-if="selectedNode.type === 'center'">🏢</template>
                <template v-else-if="selectedNode.type === 'business'">📁</template>
                <template v-else>🎯</template>
              </span>
              <h2>{{ selectedNode.sceneName || selectedNode.label }}</h2>
            </div>
            <div class="header-actions">
              <el-button v-if="selectedNode.type === 'scene'" @click="testScene(selectedNode)">
                <el-icon><View /></el-icon>
                测试
              </el-button>
              <el-button type="primary" @click="handleEdit(selectedNode)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
            </div>
          </div>

          <!-- 类型标签 -->
          <div class="type-tags">
            <el-tag :type="typeColor(selectedNode.type)">
              {{ typeLabel(selectedNode.type) }}
            </el-tag>
            <el-tag :type="selectedNode.isActive ? 'success' : 'info'">
              {{ selectedNode.isActive ? '已启用' : '已禁用' }}
            </el-tag>
          </div>

          <!-- 基本信息 -->
          <el-card class="info-card">
            <template #header>
              <span class="card-title">基本信息</span>
            </template>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="编码">{{ selectedNode.sceneCode }}</el-descriptions-item>
              <el-descriptions-item label="父级">
                <span v-if="getParentInfo(selectedNode)">{{ getParentInfo(selectedNode) }}</span>
                <span v-else class="text-gray">无</span>
              </el-descriptions-item>
              <el-descriptions-item label="优先级">
                <el-badge :value="selectedNode.priority" :type="priorityType(selectedNode.priority)" />
              </el-descriptions-item>
              <el-descriptions-item label="描述">{{ selectedNode.description || '暂无描述' }}</el-descriptions-item>
            </el-descriptions>
          </el-card>

          <!-- 关键词 -->
          <el-card class="info-card">
            <template #header>
              <span class="card-title">关键词</span>
            </template>
            <div class="keywords-container">
              <el-tag
                v-for="(kw, idx) in selectedNode.keywords"
                :key="idx"
                size="large"
                class="keyword-tag"
              >
                {{ kw }}
              </el-tag>
              <span v-if="!selectedNode.keywords || selectedNode.keywords.length === 0" class="empty-tip">暂无关键词</span>
            </div>
          </el-card>

          <!-- 关联工作流 -->
          <el-card v-if="selectedNode.workflows && selectedNode.workflows.length > 0" class="info-card">
            <template #header>
              <span class="card-title">关联工作流</span>
            </template>
            <div class="workflows-display">
              <div
                v-for="(workflow, index) in selectedNode.workflows"
                :key="index"
                class="workflow-display-item"
              >
                <div class="workflow-info">
                  <span class="workflow-name">{{ workflow.name || workflow.code }}</span>
                  <span class="workflow-code-tag">{{ workflow.code }}</span>
                  <span v-if="workflow.isDefault" class="workflow-default-tag">默认</span>
                </div>
                <span v-if="workflow.description" class="workflow-desc">{{ workflow.description }}</span>
              </div>
            </div>
          </el-card>

          <!-- 提示词（仅场景） -->
          <el-card v-if="selectedNode.type === 'scene' && selectedNode.promptCode" class="info-card">
            <template #header>
              <div class="prompt-header-info">
                <span class="card-title">提示词</span>
                <span class="prompt-code">{{ selectedNode.promptCode }}</span>
              </div>
              <el-button link @click="openPromptEditorForSelected" class="preview-edit-btn">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
            </template>
            <div class="prompt-preview">
              <div v-if="loadingPrompt" class="loading-text">加载中...</div>
              <pre v-else class="preview-content">{{ currentPromptContent || '未找到提示词内容' }}</pre>
            </div>
          </el-card>

          <!-- 子节点列表 -->
          <div v-if="selectedNode.children && selectedNode.children.length > 0" class="children-section">
            <h3>子节点列表</h3>
            <el-table :data="selectedNode.children" style="width: 100%" size="small">
              <el-table-column prop="sceneName" label="名称" />
              <el-table-column prop="sceneCode" label="编码" width="150" />
              <el-table-column label="类型" width="100">
                <template #default="{ row }">
                  <el-tag :type="typeColor(row.type)" size="small">{{ typeLabel(row.type) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.isActive ? 'success' : 'info'" size="small">
                    {{ row.isActive ? '启用' : '禁用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button size="small" link @click="handleNodeClick(row, null)">查看</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-else class="empty-state">
          <div class="empty-content">
            <div class="empty-icon">
              <el-icon><Pointer /></el-icon>
            </div>
            <p class="empty-text">请从左侧选择一个节点查看详情</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingNode ? '编辑' : '新增' + ' ' + typeLabel(currentType)"
      width="600px"
      class="scene-dialog"
    >
      <el-form :model="formData" label-width="110px" ref="formRef">
        <el-form-item label="编码" prop="sceneCode" required>
          <el-input v-model="formData.sceneCode" :disabled="!!editingNode" placeholder="请输入编码" @input="updateDefaultPromptCode" />
        </el-form-item>
        <el-form-item label="名称" prop="sceneName" required>
          <el-input v-model="formData.sceneName" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="关键词">
          <div class="keyword-input-wrapper">
            <el-tag
              v-for="(tag, index) in formData.keywords"
              :key="index"
              closable
              @close="removeKeyword(index)"
              style="margin-right: 8px"
            >
              {{ tag }}
            </el-tag>
            <el-input
              v-if="keywordInputVisible"
              ref="keywordInputRef"
              v-model="keywordInput"
              size="small"
              style="width: 200px"
              @keyup.enter="confirmKeyword"
              @blur="confirmKeyword"
            />
            <el-button v-else size="small" icon="Plus" @click="showKeywordInput" />
          </div>
        </el-form-item>
        <el-form-item label="优先级">
          <el-slider v-model="formData.priority" :min="1" :max="100" show-stops />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="formData.isActive" />
        </el-form-item>
        <template v-if="currentType === 'scene'">
          <el-form-item label="关联提示词">
              <el-select v-model="formData.promptCode" placeholder="选择提示词" clearable filterable style="width: 100%">
                <el-option
                  v-for="prompt in prompts"
                  :key="prompt.code"
                  :label="`${prompt.name} (${prompt.code})`"
                  :value="prompt.code"
                />
              </el-select>
              <el-button v-if="formData.promptCode" link @click="openPromptEditor">编辑提示词</el-button>
            </el-form-item>
            
            <!-- 工作流配置 -->
            <el-form-item label="关联工作流">
              <div class="workflows-container">
                <div
                  v-for="(workflow, index) in formData.workflows"
                  :key="index"
                  class="workflow-item"
                >
                  <el-row :gutter="12">
                    <el-col :span="10">
                      <el-select
                        v-model="workflow.code"
                        placeholder="请选择工作流"
                        size="small"
                        class="workflow-select"
                        @change="handleWorkflowChange(index, $event)"
                      >
                        <el-option
                          v-for="w in getAvailableWorkflows(index)"
                          :key="w.workflowCode"
                          :label="`${w.workflowCode} - ${w.workflowName}`"
                          :value="w.workflowCode"
                        />
                      </el-select>
                    </el-col>
                    <el-col :span="8">
                      <el-input
                        v-model="workflow.description"
                        placeholder="描述（可选）"
                        size="small"
                        :disabled="!workflow.code"
                      />
                    </el-col>
                    <el-col :span="4">
                      <div class="workflow-actions">
                        <el-switch
                          v-model="workflow.isDefault"
                          active-text="默认"
                          inactive-text=""
                          size="small"
                        />
                        <el-button
                          v-if="formData.workflows.length > 1"
                          size="small"
                          type="danger"
                          icon="Delete"
                          @click="removeWorkflow(index)"
                        />
                      </div>
                    </el-col>
                  </el-row>
                </div>
                <el-button
                  size="small"
                  type="primary"
                  icon="Plus"
                  @click="addWorkflow"
                  class="add-workflow-btn"
                  :disabled="getAvailableWorkflows().length === 0"
                >
                  添加工作流
                </el-button>
              </div>
            </el-form-item>
          </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 场景测试对话框 -->
    <el-dialog v-model="testDialogVisible" title="场景识别测试" width="520px">
      <el-form label-width="90px">
        <el-form-item label="测试输入">
          <el-input
            v-model="testInput"
            type="textarea" :rows="4" placeholder="输入一段文本，测试场景识别效果..." />
        </el-form-item>
      </el-form>
      <div v-if="testResult" class="test-result">
        <div class="result-header">
          <span class="result-title">识别结果</span>
          <el-tag v-if="testResult.bestMatch" type="success">匹配成功</el-tag>
          <el-tag v-else type="warning">未匹配</el-tag>
        </div>
        <div v-if="testResult.bestMatch" class="match-info">
          <div class="match-item">
            <span class="match-label">最佳匹配</span>
            <span class="match-value">{{ testResult.bestMatch.sceneName }}</span>
          </div>
          <div class="match-item">
            <span class="match-label">匹配关键词</span>
            <el-tag size="small">{{ testResult.bestMatch.matchedKeyword }}</el-tag>
          </div>
          <div v-if="testResult.matchedCenter" class="match-item">
            <span class="match-label">匹配中心域</span>
            <span class="match-value">{{ testResult.matchedCenter }}</span>
          </div>
          <div v-if="testResult.matchedBusiness" class="match-item">
            <span class="match-label">匹配业务域</span>
            <span class="match-value">{{ testResult.matchedBusiness }}</span>
          </div>
        </div>
        <div class="result-detail">
          <pre>{{ JSON.stringify(testResult, null, 2) }}</pre>
        </div>
      </div>
      <template #footer>
        <el-button @click="testDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="handleTest" :loading="testing">
          开始测试
        </el-button>
      </template>
    </el-dialog>

    <!-- 提示词编辑器对话框 -->
    <el-dialog v-model="promptEditorVisible" title="提示词编辑器" width="800px" :close-on-click-modal="false">
      <div v-if="currentPrompt" class="prompt-editor">
        <div class="prompt-header">
          <el-tag type="info">{{ currentPrompt.code }}</el-tag>
          <span class="prompt-name">{{ currentPrompt.name }}</span>
          <el-tag :type="currentPrompt.isActive ? 'success' : 'warning'" size="small">
            {{ currentPrompt.isActive ? '启用' : '禁用' }}
          </el-tag>
        </div>
        <div class="prompt-description" v-if="currentPrompt.description">
          {{ currentPrompt.description }}
        </div>
        <el-form-item label="提示词内容" class="prompt-content-item">
          <el-input
            v-model="editingPromptContent"
            type="textarea"
            :rows="15"
            placeholder="请输入提示词内容..."
            class="prompt-textarea"
          />
        </el-form-item>
        <div v-if="currentPrompt.variables && currentPrompt.variables.length > 0" class="variables-info">
          <div class="variables-title">可用变量：</div>
          <div class="variables-list">
            <el-tag
              v-for="(varItem, idx) in currentPrompt.variables"
              :key="idx"
              size="small"
              class="variable-tag"
            >
              {{ varItem.name }}
              <span class="variable-desc">{{ varItem.description }}</span>
            </el-tag>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="promptEditorVisible = false">取消</el-button>
        <el-button type="primary" @click="savePromptChanges" :loading="savingPrompt">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, View, ArrowLeft, Pointer, Search } from '@element-plus/icons-vue'
import { listScenesTree, createScene, updateScene, deleteScene, toggleScene, testSceneRecognition, getSceneStats } from '../services/sceneApi.js'
import { listPrompts, getPrompt, savePrompt } from '../services/promptApi.js'
import { listForms } from '../services/formApi.js'
import { workflowApi } from '../services/workflowApi.js'

const emit = defineEmits(['go-back'])

const goBack = () => {
  emit('go-back')
}

const loading = ref(false)
const treeData = ref([])
const originalTreeData = ref([])
const treeRef = ref(null)
const selectedNode = ref(null)
const searchKeyword = ref('')
const dialogVisible = ref(false)
const editingNode = ref(null)
const saving = ref(false)
const testDialogVisible = ref(false)
const testInput = ref('')
const testResult = ref(null)
const testing = ref(false)
const keywordInputVisible = ref(false)
const keywordInput = ref('')
const keywordInputRef = ref(null)
const forms = ref([])
const prompts = ref([])
const workflows = ref([])
const formRef = ref(null)
const stats = ref(null)
const promptEditorVisible = ref(false)
const currentPrompt = ref(null)
const editingPromptContent = ref('')
const savingPrompt = ref(false)
const currentPromptContent = ref('')
const loadingPrompt = ref(false)
const isEditing = ref(false)

const creatingType = ref('scene')
const parentInfo = ref('')

const currentType = computed(() => {
  if (editingNode.value) {
    return editingNode.value.type
  }
  return creatingType.value
})

const filteredTreeData = computed(() => {
  if (!searchKeyword.value.trim()) {
    return treeData.value
  }
  const keyword = searchKeyword.value.toLowerCase()
  return filterTree(originalTreeData.value, keyword)
})

const filterTree = (nodes, keyword) => {
  const result = []
  for (const node of nodes) {
    const matchName = (node.sceneName || node.label || '').toLowerCase().includes(keyword)
    const matchCode = (node.sceneCode || '').toLowerCase().includes(keyword)
    const matchKeyword = (node.keywords || []).some(k => k.toLowerCase().includes(keyword))
    
    const children = node.children ? filterTree(node.children, keyword) : []
    
    if (matchName || matchCode || matchKeyword || children.length > 0) {
      result.push({
        ...node,
        children: children
      })
    }
  }
  return result
}

const formData = reactive({
  sceneCode: '',
  sceneName: '',
  description: '',
  keywords: [],
  priority: 10,
  isActive: true,
  formCode: '',
  actionPromptFile: '',
  type: 'scene',
  parentId: null,
  workflows: []
})

const treeProps = {
  children: 'children',
  label: (data) => data.sceneName || data.label,
  isLeaf: (data) => !data.children || data.children.length === 0
}

// 类型相关
const typeLabel = (type) => {
  const labels = {
    'center': '中心域',
    'business': '业务域',
    'scene': '场景'
  }
  return labels[type] || type
}

// 获取父级信息
const getParentInfo = (node) => {
  if (!node || !node.parentId) return null
  
  const findParent = (nodes, parentId) => {
    for (const n of nodes) {
      if (n.id === parentId) {
        return `${n.sceneName || n.label} (${typeLabel(n.type)})`
      }
      if (n.children) {
        const found = findParent(n.children, parentId)
        if (found) return found
      }
    }
    return null
  }
  
  return findParent(treeData.value, node.parentId)
}
const typeColor = (type) => {
  const colors = {
    'center': 'primary',
    'business': 'warning',
    'scene': 'success'
  }
  return colors[type] || ''
}
const priorityType = (priority) => {
  if (priority >= 80) return 'danger'
  if (priority >= 50) return 'warning'
  return 'info'
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const res = await listScenesTree()
    if (res.success) {
      treeData.value = res.data || []
      originalTreeData.value = JSON.parse(JSON.stringify(res.data || []))
    }
  } catch (e) {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const loadForms = async () => {
  try {
    const res = await listForms()
    if (res.success) {
      forms.value = res.data || []
    }
  } catch (e) {
    console.error(e)
  }
}

const loadPrompts = async () => {
  try {
    const res = await listPrompts()
    if (res.success) {
      prompts.value = res.data || []
    }
  } catch (e) {
    console.error(e)
  }
}

const loadWorkflows = async () => {
  try {
    const res = await workflowApi.getAllWorkflows()
    if (res.success) {
      workflows.value = res.data || []
    }
  } catch (e) {
    console.error(e)
  }
}

const loadStats = async () => {
  try {
    const res = await getSceneStats()
    if (res.success) {
      stats.value = res.data
    }
  } catch (e) {
    console.error(e)
  }
}

const getPromptName = (promptCode) => {
  if (!promptCode) return null
  const prompt = prompts.value.find(p => p.code === promptCode)
  return prompt ? prompt.name : promptCode
}

const loadPromptContent = async (promptCode) => {
  if (!promptCode) {
    currentPromptContent.value = null
    return
  }
  loadingPrompt.value = true
  try {
    const result = await getPrompt(promptCode)
    if (result.success) {
      currentPromptContent.value = result.data.content
    } else {
      currentPromptContent.value = null
    }
  } catch (e) {
    currentPromptContent.value = null
    console.error(e)
  } finally {
    loadingPrompt.value = false
  }
}

const openPromptEditor = async () => {
  if (!formData.promptCode) return
  loadingPrompt.value = true
  try {
    const result = await getPrompt(formData.promptCode)
    if (result.success) {
      currentPrompt.value = result.data
      editingPromptContent.value = result.data.content
      promptEditorVisible.value = true
    }
  } catch (e) {
    ElMessage.error('获取提示词失败')
  } finally {
    loadingPrompt.value = false
  }
}

const openPromptEditorForSelected = async () => {
  if (!selectedNode.value?.promptCode) return
  loadingPrompt.value = true
  try {
    const result = await getPrompt(selectedNode.value.promptCode)
    if (result.success) {
      currentPrompt.value = result.data
      editingPromptContent.value = result.data.content
      promptEditorVisible.value = true
    }
  } catch (e) {
    ElMessage.error('获取提示词失败')
  } finally {
    loadingPrompt.value = false
  }
}

const savePromptChanges = async () => {
  if (!currentPrompt.value) return
  
  savingPrompt.value = true
  try {
    const result = await savePrompt(currentPrompt.value.code, editingPromptContent.value)
    if (result.success) {
      ElMessage.success('提示词更新成功')
      await loadPrompts()
      promptEditorVisible.value = false
    } else {
      ElMessage.error(result.message || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    savingPrompt.value = false
  }
}

// 节点点击
const handleNodeClick = async (data, node) => {
  isEditing.value = false
  editingNode.value = null
  selectedNode.value = data
  if (data.type === 'scene' && data.promptCode) {
    await loadPromptContent(data.promptCode)
  } else {
    currentPromptContent.value = null
  }
}

// 新增中心域
const handleAddCenter = () => {
  editingNode.value = null
  creatingType.value = 'center'
  parentInfo.value = ''
  Object.assign(formData, {
    sceneCode: '',
    sceneName: '',
    description: '',
    keywords: [],
    priority: 10,
    isActive: true,
    promptCode: '',
    type: 'center',
    parentId: null
  })
  Promise.all([loadForms(), loadPrompts(), loadWorkflows()]).then(() => {
    isEditing.value = true
    selectedNode.value = null
  })
}

// 新增业务域
const handleAddBusiness = (parentData) => {
  editingNode.value = null
  creatingType.value = 'business'
  parentInfo.value = `${parentData.sceneName || parentData.label} (${typeLabel(parentData.type)})`
  Object.assign(formData, {
    sceneCode: '',
    sceneName: '',
    description: '',
    keywords: [],
    priority: 10,
    isActive: true,
    promptCode: '',
    type: 'business',
    parentId: parentData.id
  })
  Promise.all([loadForms(), loadPrompts(), loadWorkflows()]).then(() => {
    isEditing.value = true
    selectedNode.value = null
  })
}

// 新增场景
const handleAddScene = (parentData) => {
  editingNode.value = null
  creatingType.value = 'scene'
  parentInfo.value = `${parentData.sceneName || parentData.label} (${typeLabel(parentData.type)})`
  Object.assign(formData, {
    sceneCode: '',
    sceneName: '',
    description: '',
    keywords: [],
    priority: 10,
    isActive: true,
    promptCode: '',
    type: 'scene',
    parentId: parentData.id,
    workflows: []
  })
  Promise.all([loadForms(), loadPrompts(), loadWorkflows()]).then(() => {
    isEditing.value = true
    selectedNode.value = null
  })
}

// 取消编辑
const cancelEdit = () => {
  isEditing.value = false
  editingNode.value = null
  selectedNode.value = null
}

// 工作流管理
const addWorkflow = () => {
  const existingCodes = formData.workflows.map(w => w.code)
  const availableWorkflows = workflows.value.filter(w => !existingCodes.includes(w.workflowCode))
  
  formData.workflows.push({
    code: availableWorkflows.length > 0 ? availableWorkflows[0].workflowCode : '',
    name: availableWorkflows.length > 0 ? availableWorkflows[0].workflowName : '',
    description: availableWorkflows.length > 0 ? availableWorkflows[0].description : '',
    config: {},
    isDefault: formData.workflows.length === 0
  })
}

const removeWorkflow = (index) => {
  formData.workflows.splice(index, 1)
  // 如果删除的是默认工作流，设置第一个为默认
  if (formData.workflows.length > 0 && !formData.workflows.some(w => w.isDefault)) {
    formData.workflows[0].isDefault = true
  }
}

const getAvailableWorkflows = (excludeIndex = -1) => {
  const existingCodes = formData.workflows
    .filter((_, i) => i !== excludeIndex)
    .map(w => w.code)
  return workflows.value.filter(w => !existingCodes.includes(w.workflowCode))
}

const handleWorkflowChange = (index, workflowCode) => {
  const workflow = workflows.value.find(w => w.workflowCode === workflowCode)
  if (workflow && formData.workflows[index]) {
    formData.workflows[index].name = workflow.workflowName
    if (!formData.workflows[index].description) {
      formData.workflows[index].description = workflow.description || ''
    }
  }
}

const updateDefaultPromptCode = () => {
}

// 编辑
const handleEdit = async (data) => {
  editingNode.value = data
  parentInfo.value = getParentInfo(data) || ''
  Object.assign(formData, {
    sceneCode: data.sceneCode,
    sceneName: data.sceneName,
    description: data.description,
    keywords: [...(data.keywords || [])],
    priority: data.priority,
    isActive: data.isActive,
    promptCode: data.promptCode,
    type: data.type,
    parentId: data.parentId,
    workflows: data.workflows ? [...data.workflows] : []
  })
  await Promise.all([loadForms(), loadPrompts(), loadWorkflows()])
  isEditing.value = true
}

// 删除
const handleDelete = async (data) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除「${data.sceneName || data.label}」吗？\n这将同时删除所有子节点。`,
      '确认删除',
      { type: 'warning' }
    )
    const result = await deleteScene(data.sceneCode)
    if (result.success) {
      ElMessage.success('删除成功')
      await loadData()
      await loadStats()
      selectedNode.value = null
    } else {
      ElMessage.error(result.message || '删除失败')
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 测试场景
const testScene = (data) => {
  testInput.value = data.keywords?.[0] || ''
  testResult.value = null
  testDialogVisible.value = true
}

const handleTest = async () => {
  if (!testInput.value.trim()) {
    ElMessage.warning('请输入测试文本')
    return
  }
  testing.value = true
  testResult.value = null
  try {
    const result = await testSceneRecognition(testInput.value)
    if (result.success) {
      testResult.value = result
    } else {
      ElMessage.error(result.message || '测试失败')
    }
  } catch (e) {
    ElMessage.error('测试失败')
  } finally {
    testing.value = false
  }
}

// 关键词管理
const showKeywordInput = () => {
  keywordInputVisible.value = true
  nextTick(() => {
    keywordInputRef.value?.focus()
  })
}

const confirmKeyword = () => {
  if (keywordInput.value.trim()) {
    if (!formData.keywords.includes(keywordInput.value.trim())) {
      formData.keywords.push(keywordInput.value.trim())
    }
  }
  keywordInputVisible.value = false
  keywordInput.value = ''
}

const removeKeyword = (index) => {
  formData.keywords.splice(index, 1)
}

// 保存
const handleSave = async () => {
  if (!formData.sceneCode || !formData.sceneName) {
    ElMessage.warning('请填写编码和名称')
    return
  }

  saving.value = true
  try {
    let result
    if (editingNode.value) {
      result = await updateScene(formData.sceneCode, formData)
    } else {
      result = await createScene(formData)
    }

    if (result.success) {
      ElMessage.success(editingNode.value ? '更新成功' : '创建成功')
      isEditing.value = false
      editingNode.value = null
      await loadData()
      await loadStats()
      if (result.data && result.data.id) {
        setTimeout(() => {
          const findNode = (nodes, id) => {
            for (const node of nodes) {
              if (node.id === id) return node
              if (node.children) {
                const found = findNode(node.children, id)
                if (found) return found
              }
            }
            return null
          }
          const newNode = findNode(treeData.value, result.data.id)
          if (newNode) {
            selectedNode.value = newNode
          }
        }, 100)
      }
    } else {
      ElMessage.error(result.message || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadData()
  loadStats()
  loadPrompts()
})
</script>

<style scoped>
.scene-manager {
  height: 100vh;
  background: var(--bg-primary);
  display: flex;
  flex-direction: column;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: var(--bg-elevated);
  box-shadow: var(--shadow-sm);
  gap: 24px;
}

.stats-bar {
  display: flex;
  gap: 24px;
  padding: 8px 20px;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 8px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 60px;
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: #666;
}

.stat-item.active .stat-value {
  color: #10b981;
}

.stat-label {
  font-size: 12px;
  color: #999;
  margin-top: 2px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-btn {
  display: flex;
  align-items: center;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.main-content {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 16px 24px;
  overflow: hidden;
}

.tree-section {
  width: 360px;
  background: var(--bg-elevated);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border-light);
}

.tree-header {
  padding: 16px;
  border-bottom: 1px solid var(--border-light);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tree-header h3 {
  margin: 0;
  font-size: 16px;
  color: var(--text-primary);
}

.tree-search {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-light);
}

.tree-container {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
}

.scene-tree {
  font-size: 14px;
}

.tree-node {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding: 4px 0;
}

.node-content {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.node-icon {
  font-size: 16px;
}

.node-label {
  font-size: 14px;
}

.scene-count {
  margin-left: 4px;
  font-size: 12px;
  color: #999;
}

.node-actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}

.tree-node:hover .node-actions {
  opacity: 1;
}

.action-btn {
  padding: 4px 8px;
}

.detail-section {
  flex: 1;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  overflow-y: auto;
  padding: 20px;
}

.scene-detail {
  height: 100%;
  overflow-y: auto;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #eee;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.scene-icon {
  font-size: 28px;
}

.header-title h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
}

.type-tags {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.info-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-weight: 600;
  font-size: 15px;
}

.prompt-header-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.prompt-code {
  font-size: 12px;
  color: #666;
  padding: 2px 8px;
  background: #f5f7fa;
  border-radius: 4px;
  font-family: 'Monaco', 'Menlo', monospace;
}

.keywords-container {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.keyword-tag {
  padding: 6px 12px;
}

.empty-tip {
  color: #999;
  font-size: 14px;
}

.text-gray {
  color: #999;
}

.children-section {
  margin-top: 20px;
}

.children-section h3 {
  margin: 0 0 12px;
  font-size: 15px;
  color: #333;
}

.empty-state {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-content {
  text-align: center;
  color: #999;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-text {
  font-size: 14px;
  margin-bottom: 20px;
}

.empty-btn {
  margin-top: 12px;
}

.scene-dialog :deep(.el-dialog__body) {
  padding: 10px 20px;
}

.keyword-input-wrapper {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.test-result {
  margin-top: 16px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.result-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e8e8e8;
}

.result-title {
  font-weight: 600;
  font-size: 15px;
}

.match-info {
  margin-bottom: 16px;
}

.match-item {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.match-label {
  min-width: 90px;
  color: #666;
}

.match-value {
  font-weight: 500;
  color: #333;
}

.result-detail {
  margin-top: 12px;
}

.result-detail pre {
  margin: 0;
  background: white;
  padding: 12px;
  border-radius: 4px;
  font-size: 12px;
  overflow-x: auto;
  max-height: 200px;
  overflow-y: auto;
}

.prompt-editor {
  padding: 8px;
}

.prompt-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.prompt-name {
  font-weight: 600;
  font-size: 15px;
}

.prompt-description {
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;
  color: #666;
  font-size: 14px;
  margin-bottom: 16px;
}

.prompt-content-item {
  margin-bottom: 16px;
}

.prompt-textarea {
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 14px;
  line-height: 1.6;
}

.variables-info {
  padding: 12px;
  background: #fffbe6;
  border-radius: 4px;
}

.variables-title {
  font-weight: 500;
  margin-bottom: 8px;
  font-size: 14px;
}

.variables-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.variable-tag {
  background: #fff3cd;
  border-color: #ffeeba;
  color: #856404;
}

.variable-desc {
  margin-left: 6px;
  opacity: 0.7;
  font-size: 12px;
}

.preview-edit-btn {
  float: right;
  color: #409eff;
  font-size: 14px;
}

.preview-edit-btn:hover {
  color: #66b1ff;
}

.prompt-preview {
  margin-top: 8px;
}

.preview-content {
  margin: 0;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: #333;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow-y: auto;
}

.loading-text {
  padding: 12px;
  color: #999;
  text-align: center;
}

/* 工作流配置区域 */
.workflows-section {
  margin-top: 8px;
}

.workflows-intro {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #e8f4fd;
  border-radius: 8px;
  margin-bottom: 16px;
}

.intro-icon {
  color: #409eff;
  font-size: 14px;
}

.intro-text {
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
}

.workflows-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.workflow-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  overflow: hidden;
  transition: all 0.3s ease;
}

.workflow-card:hover {
  border-color: #dcdfe6;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.workflow-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
}

.workflow-select-wrapper {
  flex: 1;
  max-width: 400px;
}

.workflow-card-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.default-badge {
  margin-right: 8px;
}

.remove-btn {
  color: #909399;
  transition: color 0.2s;
}

.remove-btn:hover {
  color: #f56c6c;
}

.workflow-card-body {
  padding: 16px;
}

.workflow-info-row {
  display: flex;
  margin-bottom: 10px;
  font-size: 13px;
}

.info-label {
  color: #909399;
  min-width: 70px;
}

.info-value {
  color: #303133;
  flex: 1;
}

.workflow-custom-desc {
  margin: 12px 0;
}

.workflow-default-toggle {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
  border-top: 1px dashed #ebeef5;
}

.add-workflow-wrapper {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16px;
}

.add-workflow-btn {
  width: 160px;
}

.no-workflow-tip {
  font-size: 13px;
  color: #909399;
}

/* 工作流选择器样式 */
.workflow-select {
  width: 100%;
}

/* 工作流显示样式 */
.workflows-display {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.workflow-display-item {
  padding: 12px;
  background: #f9fafc;
  border-radius: 8px;
}

.workflow-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.workflow-name {
  font-weight: 500;
  color: #303133;
}

.workflow-code-tag {
  font-size: 12px;
  color: #909399;
  background: #f2f6fc;
  padding: 2px 8px;
  border-radius: 4px;
}

.workflow-default-tag {
  font-size: 12px;
  color: #fff;
  background: #409eff;
  padding: 2px 8px;
  border-radius: 4px;
}

.workflow-desc {
  font-size: 13px;
  color: #909399;
}

/* 编辑表单样式 */
.edit-form-container {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  padding: 20px;
}

.edit-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #eee;
}

.edit-header .header-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.edit-header .header-title h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.scene-form {
  max-height: calc(100vh - 280px);
  overflow-y: auto;
  padding-right: 10px;
}

.scene-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #eee;
}

.form-footer .el-button {
  min-width: 100px;
}
</style>
