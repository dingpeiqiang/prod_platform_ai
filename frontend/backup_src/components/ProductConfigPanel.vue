<template>
  <div class="config-panel" :style="{ width: panelWidth + 'px' }">
    <!-- 拖拽调宽�?-->
    <div
      class="config-panel-resizer"
      @mousedown="startResize"
      @touchstart.passive="startResize"
    >
      <div class="resizer-line"></div>
    </div>

    <!-- 顶部标题�?-->
    <div class="config-header">
      <div class="config-header-left">
        <svg class="config-header-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M12 2L2 7l10 5 10-5-10-5z"/>
          <path d="M2 17l10 5 10-5"/>
          <path d="M2 12l10 5 10-5"/>
        </svg>
        <div class="config-header-text">
          <span class="config-scheme-name">{{ productInfo.prodPrcName }}</span>
          <span class="config-template-badge">{{ productInfo.templateLabel }}</span>
        </div>
      </div>
      <div class="config-header-actions">
        <span class="config-status-tag" :class="statusClass">{{ statusText }}</span>
        <button v-if="isArchive && !copyMode" class="config-copy-btn" @click="onCopyFromArchive" title="基于该存量产品复制新套餐">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="9" y="9" width="13" height="13" rx="2"/>
            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
          </svg>
          <span>复制</span>
        </button>
        <button class="config-close-btn" @click="$emit('close')" title="关闭">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>
    </div>

    <!-- Tab 导航 -->
    <div class="config-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="config-tab"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        <svg v-if="tab.key === 'base'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
        </svg>
        <svg v-else-if="tab.key === 'resource'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
          <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
          <line x1="12" y1="22.08" x2="12" y2="12"/>
        </svg>
        <svg v-else-if="tab.key === 'marketing'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="12" y1="1" x2="12" y2="23"/>
          <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>
        </svg>
        <svg v-else-if="tab.key === 'sales'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/>
          <rect x="8" y="2" width="8" height="4" rx="1"/>
          <path d="M9 14l2 2 4-4"/>
        </svg>
        <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/>
          <path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/>
        </svg>
        {{ tab.label }}
      </button>
    </div>

    <!-- Tab 内容�?-->
    <div class="config-body">
      <!-- 基础信息 Tab -->
      <div v-show="activeTab === 'base'" class="tab-content">
        <div class="form-section">
          <div class="section-header open">
            <span class="section-title">基础信息</span>
          </div>
          <div class="section-content base-info-grid">
            <div class="form-row base-field-row" v-for="field in baseFields" :key="field.key">
              <label class="form-label">
                <span v-if="field.required" class="required">*</span>
                <span class="label-text">{{ field.label }}</span>
                <span v-if="fieldTag(field) === 'ai'" class="field-tag tag-ai" :data-reason="aiFilled[field.key] || field.tagReason">AI推荐</span>
                <span v-else-if="fieldTag(field) === 'reuse'" class="field-tag tag-reuse" :data-reason="field.tagReason">复用</span>
                <span v-else-if="fieldTag(field) === 'parsed'" class="field-tag tag-parsed" :data-reason="parsedFilled[field.key] || '根据用户需求解析得出'">已解析</span>
              </label>
              <div class="form-control">
                <select v-if="field.options" v-model="formData[field.key]" class="form-input form-select" :class="{ modified: modifiedFields[field.key] }" :disabled="submitted" @change="markModified(field.key)">
                  <option v-for="opt in field.options" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                </select>
                <input
                  v-else
                  :type="field.type || 'text'"
                  v-model="formData[field.key]"
                  class="form-input"
                  :class="{ modified: modifiedFields[field.key] }"
                  :disabled="submitted"
                  @input="markModified(field.key)"
                />
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 资源配置 Tab -->
      <div v-show="activeTab === 'resource'" class="tab-content">
        <div
          v-for="section in resourceSections"
          :key="section.key"
          class="form-section"
        >
          <div class="section-header" :class="{ open: expandedSections[section.key] }" @click="toggleSection(section.key)">
            <span class="section-title">{{ section.title }}</span>
            <svg class="section-chevron" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
          </div>
          <div v-show="expandedSections[section.key]" class="section-content">
            <!-- table 类型：表格 + 添加按钮 -->
            <template v-for="field in section.fields" :key="field.key">
              <div v-if="field.type === 'table'" class="table-field">
                <div class="table-field-header">
                  <label class="form-label table-field-label">
                    <span class="label-text">{{ field.label }}</span>
                    <span v-if="fieldTag(field) === 'ai'" class="field-tag tag-ai" :data-reason="aiFilled[field.key] || field.tagReason">AI推荐</span>
                    <span v-else-if="fieldTag(field) === 'reuse'" class="field-tag tag-reuse" :data-reason="field.tagReason">复用</span>
                <span v-else-if="fieldTag(field) === 'parsed'" class="field-tag tag-parsed" :data-reason="parsedFilled[field.key] || '根据用户需求解析得出'">已解析</span>
                  </label>
                  <button class="table-add-btn" :disabled="submitted" @click="addTableRow(field.key, field.columns)">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                      <line x1="12" y1="5" x2="12" y2="19"/>
                      <line x1="5" y1="12" x2="19" y2="12"/>
                    </svg>
                    添加
                  </button>
                </div>
                <div class="limit-table-wrap">
                  <table class="limit-table">
                    <thead>
                      <tr>
                        <th v-for="col in field.columns" :key="col.key">{{ col.label }}</th>
                        <th class="col-action">操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="(row, rowIdx) in tableRows(field.key)" :key="rowIdx">
                        <td v-for="col in field.columns" :key="col.key">
                          <div v-if="col.type === 'auto'" class="table-cell-auto">
                            {{ generatePreLimitTip(field.key, rowIdx) }}
                          </div>
                          <select
                            v-else-if="col.type === 'select' && col.dynamicOptions"
                            :value="row[col.key]"
                            class="table-cell-input table-cell-select"
                            :disabled="submitted"
                            @change="onDynamicOptionChange(field.key, rowIdx, col.key, $event)"
                          >
                            <option v-for="opt in getDynamicOptions(col, row)" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                          </select>
                          <select
                            v-else-if="col.type === 'select'"
                            :value="row[col.key]"
                            class="table-cell-input table-cell-select"
                            :disabled="submitted"
                            @change="onSelectCellChange(field.key, rowIdx, col.key, $event)"
                          >
                            <option v-for="opt in col.options" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                          </select>
                          <input
                            v-else
                            type="text"
                            :value="row[col.key]"
                            class="table-cell-input"
                            :placeholder="col.placeholder || ''"
                            :disabled="submitted"
                            @input="onInputCellChange(field.key, rowIdx, col.key, $event)"
                          />
                        </td>
                        <td class="col-action">
                          <button class="table-del-btn" :disabled="submitted" @click="deleteTableRow(field.key, rowIdx)" title="删除">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                              <polyline points="3 6 5 6 21 6"/>
                              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                            </svg>
                          </button>
                        </td>
                      </tr>
                      <tr v-if="tableRows(field.key).length === 0">
                        <td :colspan="field.columns.length + 1" class="table-empty">暂无数据，点击"添加"按钮新增</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
              <!-- 常规字段 -->
              <div v-else class="form-row">
              <label class="form-label">
                <span v-if="field.required" class="required">*</span>
                <span class="label-text">{{ field.label }}</span>
                <span v-if="fieldTag(field) === 'ai'" class="field-tag tag-ai" :data-reason="aiFilled[field.key] || field.tagReason">AI推荐</span>
                <span v-else-if="fieldTag(field) === 'reuse'" class="field-tag tag-reuse" :data-reason="field.tagReason">复用</span>
                <span v-else-if="fieldTag(field) === 'parsed'" class="field-tag tag-parsed" :data-reason="parsedFilled[field.key] || '根据用户需求解析得出'">已解析</span>
              </label>
              <div class="form-control">
                <div v-if="field.unit" class="input-with-unit">
                  <input
                    :type="field.type || 'text'"
                    v-model="formData[field.key]"
                    class="form-input"
                    :class="{ modified: modifiedFields[field.key], highlight: field.highlight }"
                    :disabled="submitted"
                    @input="markModified(field.key)"
                  />
                  <span class="input-unit">{{ field.unit }}</span>
                </div>
                <select
                  v-else-if="field.type === 'select'"
                  v-model="formData[field.key]"
                  class="form-input form-select"
                  :class="{ modified: modifiedFields[field.key] }"
                  :disabled="submitted"
                  @change="markModified(field.key)"
                >
                  <option v-for="opt in field.options" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                </select>
                <textarea
                  v-else-if="field.type === 'textarea'"
                  v-model="formData[field.key]"
                  class="form-input form-textarea"
                  :class="{ modified: modifiedFields[field.key], highlight: field.highlight }"
                  :disabled="submitted"
                  rows="3"
                  @input="markModified(field.key)"
                ></textarea>
                <input
                  v-else
                  :type="field.type || 'text'"
                  v-model="formData[field.key]"
                  class="form-input"
                  :class="{ modified: modifiedFields[field.key], highlight: field.highlight }"
                  :disabled="submitted"
                  @input="markModified(field.key)"
                />
              </div>
              </div>
            </template>
          </div>
        </div>
      </div>

      <!-- 营销资源 Tab -->
      <div v-show="activeTab === 'marketing'" class="tab-content">
        <div
          v-for="section in marketingSections"
          :key="section.key"
          class="form-section"
        >
          <div class="section-header" :class="{ open: expandedSections[section.key] }" @click="toggleSection(section.key)">
            <span class="section-title">{{ section.title }}</span>
            <svg class="section-chevron" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
          </div>
          <div v-show="expandedSections[section.key]" class="section-content">
            <div class="form-row" v-for="field in section.fields" :key="field.key">
              <label class="form-label">
                <span v-if="field.required" class="required">*</span>
                <span class="label-text">{{ field.label }}</span>
                <span v-if="fieldTag(field) === 'ai'" class="field-tag tag-ai" :data-reason="aiFilled[field.key] || field.tagReason">AI推荐</span>
                <span v-else-if="fieldTag(field) === 'reuse'" class="field-tag tag-reuse" :data-reason="field.tagReason">复用</span>
                <span v-else-if="fieldTag(field) === 'parsed'" class="field-tag tag-parsed" :data-reason="parsedFilled[field.key] || '根据用户需求解析得出'">已解析</span>
              </label>
              <div class="form-control">
                <div v-if="field.unit" class="input-with-unit">
                  <input
                    :type="field.type || 'text'"
                    v-model="formData[field.key]"
                    class="form-input"
                    :class="{ modified: modifiedFields[field.key], highlight: field.highlight }"
                    :disabled="submitted"
                    @input="markModified(field.key)"
                  />
                  <span class="input-unit">{{ field.unit }}</span>
                </div>
                <select
                  v-else-if="field.type === 'select'"
                  v-model="formData[field.key]"
                  class="form-input form-select"
                  :class="{ modified: modifiedFields[field.key] }"
                  :disabled="submitted"
                  @change="markModified(field.key)"
                >
                  <option v-for="opt in field.options" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                </select>
                <textarea
                  v-else-if="field.type === 'textarea'"
                  v-model="formData[field.key]"
                  class="form-input form-textarea"
                  :class="{ modified: modifiedFields[field.key], highlight: field.highlight }"
                  :disabled="submitted"
                  rows="3"
                  @input="markModified(field.key)"
                ></textarea>
                <input
                  v-else
                  :type="field.type || 'text'"
                  v-model="formData[field.key]"
                  class="form-input"
                  :class="{ modified: modifiedFields[field.key], highlight: field.highlight }"
                  :disabled="submitted"
                  @input="markModified(field.key)"
                />
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 销售规�?Tab -->
      <div v-show="activeTab === 'sales'" class="tab-content">
        <div
          v-for="section in salesSections"
          :key="section.key"
          class="form-section"
        >
          <div class="section-header" :class="{ open: expandedSections[section.key] }" @click="toggleSection(section.key)">
            <span class="section-title">{{ section.title }}</span>
            <svg class="section-chevron" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
          </div>
          <div v-show="expandedSections[section.key]" class="section-content">
            <!-- 遍历 section 内字�?-->
            <template v-for="field in section.fields" :key="field.key">
              <!-- table 类型：表�?+ 添加按钮 -->
              <div v-if="field.type === 'table'" class="table-field">
                <div class="table-field-header">
                  <label class="form-label table-field-label">
                    <span class="label-text">{{ field.label }}</span>
                    <span v-if="fieldTag(field) === 'ai'" class="field-tag tag-ai" :data-reason="aiFilled[field.key] || field.tagReason">AI推荐</span>
                    <span v-else-if="fieldTag(field) === 'reuse'" class="field-tag tag-reuse" :data-reason="field.tagReason">复用</span>
                <span v-else-if="fieldTag(field) === 'parsed'" class="field-tag tag-parsed" :data-reason="parsedFilled[field.key] || '根据用户需求解析得出'">已解析</span>
                  </label>
                  <button class="table-add-btn" :disabled="submitted" @click="addTableRow(field.key, field.columns)">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                      <line x1="12" y1="5" x2="12" y2="19"/>
                      <line x1="5" y1="12" x2="19" y2="12"/>
                    </svg>
                    添加
                  </button>
                </div>
                <div class="limit-table-wrap">
                  <table class="limit-table">
                    <thead>
                      <tr>
                        <th v-for="col in field.columns" :key="col.key">{{ col.label }}</th>
                        <th class="col-action">操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="(row, rowIdx) in tableRows(field.key)" :key="rowIdx">
                        <td v-for="col in field.columns" :key="col.key">
                          <!-- auto 类型：只读，根据同行其他单元格自动生成提示语 -->
                          <div v-if="col.type === 'auto'" class="table-cell-auto">
                            {{ generatePreLimitTip(field.key, rowIdx) }}
                          </div>
                          <!-- dynamicOptions 类型：选项依赖同行另一个字段的�?-->
                          <select
                            v-else-if="col.type === 'select' && col.dynamicOptions"
                            :value="row[col.key]"
                            class="table-cell-input table-cell-select"
                            :disabled="submitted"
                            @change="onDynamicOptionChange(field.key, rowIdx, col.key, $event)"
                          >
                            <option v-for="opt in getDynamicOptions(col, row)" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                          </select>
                          <!-- 普通select -->
                          <select
                            v-else-if="col.type === 'select'"
                            :value="row[col.key]"
                            class="table-cell-input table-cell-select"
                            :disabled="submitted"
                            @change="onSelectCellChange(field.key, rowIdx, col.key, $event)"
                          >
                            <option v-for="opt in col.options" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                          </select>
                          <!-- 普通input -->
                          <input
                            v-else
                            type="text"
                            :value="row[col.key]"
                            class="table-cell-input"
                            :placeholder="col.placeholder || ''"
                            :disabled="submitted"
                            @input="onInputCellChange(field.key, rowIdx, col.key, $event)"
                          />
                        </td>
                        <td class="col-action">
                          <button class="table-del-btn" :disabled="submitted" @click="deleteTableRow(field.key, rowIdx)" title="删除">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                              <polyline points="3 6 5 6 21 6"/>
                              <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                            </svg>
                          </button>
                        </td>
                      </tr>
                      <tr v-if="tableRows(field.key).length === 0">
                        <td :colspan="field.columns.length + 1" class="table-empty">暂无数据，点击"添加"按钮新增</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
              <!-- 常规字段：checkbox-group / select / input -->
              <div v-else class="form-row">
                <label class="form-label">
                  <span v-if="field.required" class="required">*</span>
                  <span class="label-text">{{ field.label }}</span>
                  <span v-if="fieldTag(field) === 'ai'" class="field-tag tag-ai" :data-reason="aiFilled[field.key] || field.tagReason">AI推荐</span>
                  <span v-else-if="fieldTag(field) === 'reuse'" class="field-tag tag-reuse" :data-reason="field.tagReason">复用</span>
                <span v-else-if="fieldTag(field) === 'parsed'" class="field-tag tag-parsed" :data-reason="parsedFilled[field.key] || '根据用户需求解析得出'">已解析</span>
                </label>
                <div class="form-control">
                  <!-- checkbox-group: 多选框�?-->
                  <div v-if="field.type === 'checkbox-group'" class="checkbox-group">
                    <label
                      v-for="opt in field.options"
                      :key="opt.value"
                      class="checkbox-item"
                      :class="{ checked: checkboxValue(field.key, opt.value) }"
                    >
                      <input
                        type="checkbox"
                        :value="opt.value"
                        :checked="checkboxValue(field.key, opt.value)"
                        :disabled="submitted"
                        @change="toggleCheckbox(field.key, opt.value)"
                      />
                      <span>{{ opt.label }}</span>
                    </label>
                  </div>
                  <div v-else-if="field.unit" class="input-with-unit">
                    <input
                      :type="field.type || 'text'"
                      v-model="formData[field.key]"
                      class="form-input"
                      :class="{ modified: modifiedFields[field.key], highlight: field.highlight }"
                      :disabled="submitted"
                      @input="markModified(field.key)"
                    />
                    <span class="input-unit">{{ field.unit }}</span>
                  </div>
                  <select
                    v-else-if="field.type === 'select'"
                    v-model="formData[field.key]"
                    class="form-input form-select"
                    :class="{ modified: modifiedFields[field.key] }"
                    :disabled="submitted"
                    @change="markModified(field.key)"
                  >
                    <option v-for="opt in field.options" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                  </select>
                  <textarea
                    v-else-if="field.type === 'textarea'"
                    v-model="formData[field.key]"
                    class="form-input form-textarea"
                    :class="{ modified: modifiedFields[field.key], highlight: field.highlight }"
                    :disabled="submitted"
                    rows="3"
                    @input="markModified(field.key)"
                  ></textarea>
                  <input
                    v-else
                    :type="field.type || 'text'"
                    v-model="formData[field.key]"
                    class="form-input"
                    :class="{ modified: modifiedFields[field.key], highlight: field.highlight }"
                    :disabled="submitted"
                    @input="markModified(field.key)"
                  />
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>

      <!-- 信息预览 Tab —�?APP手机壳模�?-->
      <div v-show="activeTab === 'summary'" class="tab-content phone-preview-tab">
        <div class="phone-frame">
          <!-- 手机状态栏 -->
          <div class="phone-statusbar">
            <span class="phone-time">{{ phoneTime }}</span>
            <div class="phone-status-icons">
              <svg width="14" height="10" viewBox="0 0 24 18" fill="currentColor"><path d="M1 14h2v6H1zM6 11h2v9H6zM11 8h2v12h-2zM16 5h2v15h-2zM21 2h2v18h-2z"/></svg>
              <svg width="14" height="10" viewBox="0 0 24 18" fill="currentColor"><path d="M12 3C7 3 2.7 5.1 0 8.5l2 2.5C4.2 8.3 7.9 6.5 12 6.5s7.8 1.8 10 4.5l2-2.5C21.3 5.1 17 3 12 3z"/></svg>
              <div class="phone-battery">
                <div class="phone-battery-bar"></div>
                <div class="phone-battery-cap"></div>
              </div>
            </div>
          </div>

          <!-- APP标题�?-->
          <div class="phone-navbar">
            <svg class="phone-nav-back" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
            <span class="phone-nav-title">{{ phoneNavTitle }}</span>
            <svg class="phone-nav-more" width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="5" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="12" cy="19" r="2"/></svg>
          </div>

          <!-- APP内容�?-->
          <div class="phone-screen">
            <!-- 板块1: 用户受理信息 -->
            <div v-if="phoneTab === 'user'" class="app-page">
              <!-- 套餐横幅卡片 -->
              <div class="app-banner-card">
                <div class="app-banner-bg"></div>
                <div class="app-banner-content">
                  <div class="app-banner-name">{{ productInfo.prodPrcName }}</div>
                  <div class="app-banner-fee"><span class="fee-num">{{ previewFee }}</span><span class="fee-unit">元/月</span></div>
                  <div class="app-banner-tags">
                    <span class="app-tag">{{ previewFlowAmount }}{{ previewFlowUnit }}流量</span>
                    <span class="app-tag">{{ previewVoiceAmount }}分钟</span>
                    <span class="app-tag">{{ dynamicPreview ? (previewRights.length ? previewRights.length + '项权益' : '视频权益') : '双视频权益' }}</span>
                  </div>
                </div>
              </div>
              <!-- 资源详情卡片 -->
              <div class="app-card">
                <div class="app-card-title">套餐资源</div>
                <div class="app-resource-grid">
                  <div class="app-resource-item">
                    <div class="app-resource-icon flow">
                      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M12 5l7 7-7 7"/></svg>
                    </div>
                    <div class="app-resource-num">{{ previewFlowAmount }}<span class="unit">{{ previewFlowUnit }}</span></div>
                    <div class="app-resource-label">通用流量</div>
                  </div>
                  <div class="app-resource-item">
                    <div class="app-resource-icon voice">
                      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/></svg>
                    </div>
                    <div class="app-resource-num">{{ previewVoiceAmount }}<span class="unit">分钟</span></div>
                    <div class="app-resource-label">本地语音</div>
                  </div>
                  <div class="app-resource-item">
                    <div class="app-resource-icon video">
                      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>
                    </div>
                    <div class="app-resource-num">{{ dynamicPreview ? previewRights.length : 2 }}<span class="unit">个</span></div>
                    <div class="app-resource-label">{{ dynamicPreview ? '权益资源' : '视频权益' }}</div>
                  </div>
                </div>
              </div>
              <!-- 权益详情 -->
              <div class="app-card">
                <div class="app-card-title">包含权益</div>
                <div class="app-equity-list">
                  <div class="app-equity-item" v-for="eq in equityList" :key="eq.name">
                    <div class="app-equity-icon" :style="{ background: eq.color }">{{ eq.short }}</div>
                    <div class="app-equity-info">
                      <div class="app-equity-name">{{ eq.name }}</div>
                      <div class="app-equity-desc">{{ eq.desc }}</div>
                    </div>
                  </div>
                </div>
              </div>
              <!-- 费用说明 -->
              <div class="app-card">
                <div class="app-card-title">费用说明</div>
                <div class="app-fee-list">
                  <div class="app-fee-row"><span>套餐月费</span><span class="app-fee-val">{{ previewFee }}.00元/月</span></div>
                  <div class="app-fee-row"><span>套外流量</span><span class="app-fee-val">{{ previewFlowOvercharge }}</span></div>
                  <div class="app-fee-row"><span>套外语音</span><span class="app-fee-val">{{ previewVoiceOvercharge }}</span></div>
                  <div v-if="previewSmsOvercharge" class="app-fee-row"><span>套外短信</span><span class="app-fee-val">{{ previewSmsOvercharge }}</span></div>
                  <div v-if="dynamicPreview" class="app-fee-row"><span>有效期</span><span class="app-fee-val">{{ optionLabel('validity', formData.validity) || '长期有效' }}</span></div>
                  <div v-if="!dynamicPreview" class="app-fee-row highlight"><span>新用户首月</span><span class="app-fee-val">半价 29.50元</span></div>
                </div>
              </div>
              <!-- 规则说明 -->
              <div class="app-card">
                <div class="app-card-title">规则说明</div>
                <div class="app-rule-list">
                  <div class="app-rule-item"><span class="app-rule-dot"></span>生效方式：{{ optionLabel('effRule', formData.effRule) || '立即生效' }}</div>
                  <div class="app-rule-item"><span class="app-rule-dot"></span>退订方式：{{ optionLabel('cancelRule', formData.cancelRule) || '立即失效' }}</div>
                  <div v-if="!dynamicPreview" class="app-rule-item"><span class="app-rule-dot"></span>流量可结转至次月</div>
                  <div class="app-rule-item"><span class="app-rule-dot"></span>使用范围：{{ (Array.isArray(formData.applicableArea) ? formData.applicableArea.map((v) => optionLabel('applicableArea', v)).filter(Boolean).join('、') : optionLabel('applicableArea', formData.applicableArea)) || '四川省内' }}</div>
                  <div v-if="dialogueMode" class="app-rule-item"><span class="app-rule-dot"></span>前项限制：{{ (Array.isArray(formData.preLimitTable) ? formData.preLimitTable : []).map((r) => r.tipMsg).filter(Boolean).join('；') || '目标客群为在校大学生，允许办理' }}</div>
                  <div v-else class="app-rule-item"><span class="app-rule-dot"></span>新老产品互斥，不可同时订购</div>
                </div>
              </div>
              <!-- 办理按钮 -->
              <div class="app-action-bar">
                <button class="app-btn-secondary">加入购物车</button>
                <button class="app-btn-primary">立即办理</button>
              </div>
            </div>

            <!-- 板块2: 免填�?-->
            <div v-if="phoneTab === 'freefill'" class="app-page">
              <div class="app-card">
                <div class="app-card-title">免填单信息</div>
                <div class="app-formfill-list">
                  <div class="app-formfill-row" v-for="item in freeFillItems" :key="item.label">
                    <span class="app-formfill-label">{{ item.label }}</span>
                    <span class="app-formfill-value" :class="{ highlight: item.highlight }">{{ item.value }}</span>
                  </div>
                </div>
              </div>
              <div v-if="!dynamicPreview" class="app-card">
                <div class="app-card-title">优惠信息</div>
                <div class="app-discount-box">
                  <div class="app-discount-tag">首月半价</div>
                  <div class="app-discount-detail">
                    <div class="app-discount-title">新用户专属</div>
                    <div class="app-discount-desc">首月仅需29.50元，次月起恢复59元/月</div>
                  </div>
                </div>
              </div>
              <div class="app-action-bar">
                <button class="app-btn-primary full">确认受理</button>
              </div>
            </div>

            <!-- 板块3: 短信模板预览 -->
            <div v-if="phoneTab === 'sms'" class="app-page">
              <div class="app-sms-chat">
                <div class="app-sms-time">今天 {{ phoneTime }}</div>
                <div class="app-sms-msg" v-for="(item, idx) in smsItems" :key="idx">
                  <div class="app-sms-avatar">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                  </div>
                  <div class="app-sms-bubble-wrap">
                    <div class="app-sms-label">{{ item.label }}</div>
                    <div class="app-sms-bubble">{{ item.value }}</div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 底部Tab导航 -->
          <div class="phone-tabbar">
            <div class="phone-tab-item" :class="{ active: phoneTab === 'user' }" @click="phoneTab = 'user'">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
              <span>受理信息</span>
            </div>
            <div class="phone-tab-item" :class="{ active: phoneTab === 'freefill' }" @click="phoneTab = 'freefill'">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
              <span>免填单</span>
            </div>
            <div class="phone-tab-item" :class="{ active: phoneTab === 'sms' }" @click="phoneTab = 'sms'">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
              <span>短信预览</span>
            </div>
          </div>

          <!-- 手机Home�?-->
          <div class="phone-home-indicator"></div>
        </div>
      </div>

      <!-- 测试报告 Tab —�?测试人员上传后自动解�?-->
      <div v-show="activeTab === 'testReport'" class="tab-content test-report-tab">
        <!-- 等待测试人员上传 -->
        <div v-if="!archiveTestReport.uploaded" class="tr-loading">
          <div class="tr-loading-spinner">
            <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2L2 7l10 5 10-5-10-5z"/>
              <path d="M2 17l10 5 10-5"/>
              <path d="M2 12l10 5 10-5"/>
            </svg>
          </div>
          <div class="tr-loading-title">测试进行中</div>
          <p class="tr-loading-desc">当前 {{ productInfo.prodPrcName }} 已进入测试审核阶段，测试人员正在对方案进行逻辑测试和业务验证。</p>
          <div class="tr-loading-tip">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/>
            </svg>
            <span>测试完成后将自动解析并展示报告：测试方式、受理号码、固费核查、计费核查、提醒核查、工单核查及验收结论。</span>
          </div>
        </div>

        <!-- 测试人员已上传并自动解析的报�?-->
        <template v-else>
          <!-- 报告概览 -->
          <div class="tr-overview">
            <div class="tr-overview-header">
              <div class="tr-overview-title">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                  <polyline points="14 2 14 8 20 8"/>
                  <line x1="16" y1="13" x2="8" y2="13"/>
                  <line x1="16" y1="17" x2="8" y2="17"/>
                  <polyline points="10 9 9 9 8 9"/>
                </svg>
                <span>测试报告</span>
              </div>
              <span class="tr-status-badge" :class="archiveTestReport.status === 'passed' ? 'badge-pass' : 'badge-fail'">
                {{ archiveTestReport.status === 'passed' ? '测试通过' : '测试未通过' }}
              </span>
            </div>
            <div class="tr-overview-grid">
              <div class="tr-overview-item">
                <span class="tr-label">报告编号</span>
                <span class="tr-value">{{ archiveTestReport.reportId }}</span>
              </div>
              <div class="tr-overview-item">
                <span class="tr-label">关联工单</span>
                <span class="tr-value">{{ archiveTestReport.workOrderId }}</span>
              </div>
              <div class="tr-overview-item">
                <span class="tr-label">资费名称</span>
                <span class="tr-value">{{ archiveTestReport.productName }}</span>
              </div>
              <div class="tr-overview-item">
                <span class="tr-label">测试人员</span>
                <span class="tr-value">{{ archiveTestReport.tester }}</span>
              </div>
              <div class="tr-overview-item">
                <span class="tr-label">测试日期</span>
                <span class="tr-value">{{ archiveTestReport.testDate }}</span>
              </div>
              <div class="tr-overview-item">
                <span class="tr-label">测试方式</span>
                <span class="tr-value">{{ archiveTestReport.testMethod }}</span>
              </div>
              <div class="tr-overview-item">
                <span class="tr-label">受理号码</span>
                <span class="tr-value tr-number">{{ archiveTestReport.acceptNumber }}</span>
              </div>
            </div>
          </div>

          <!-- 核查明细 -->
          <div v-for="section in archiveTestReport.sections" :key="section.key" class="tr-section">
            <div class="tr-section-header">
              <span class="tr-section-title">{{ section.title }}</span>
              <span class="tr-section-status" :class="section.status === 'passed' ? 'status-pass' : 'status-fail'">
                <svg v-if="section.status === 'passed'" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
                <svg v-else width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                  <line x1="18" y1="6" x2="6" y2="18"/>
                  <line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
                {{ section.status === 'passed' ? '通过' : '不通过' }}
              </span>
            </div>
            <div class="tr-section-body">
              <div v-for="(item, idx) in section.items" :key="idx" class="tr-check-item">
                <span class="tr-check-label">{{ item.label }}</span>
                <div class="tr-check-detail">
                  <div class="tr-check-row">
                    <span class="tr-check-key">预期</span>
                    <span class="tr-check-val">{{ item.expected }}</span>
                  </div>
                  <div class="tr-check-row">
                    <span class="tr-check-key">实际</span>
                    <span class="tr-check-val">{{ item.actual }}</span>
                  </div>
                </div>
                <span class="tr-check-result" :class="item.result === 'pass' ? 'result-pass' : 'result-fail'">
                  <svg v-if="item.result === 'pass'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                    <polyline points="20 6 9 17 4 12"/>
                  </svg>
                  <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                    <line x1="18" y1="6" x2="6" y2="18"/>
                    <line x1="6" y1="6" x2="18" y2="18"/>
                  </svg>
                </span>
              </div>
            </div>
          </div>

          <!-- 验收结论 -->
          <div class="tr-conclusion" :class="archiveTestReport.status === 'passed' ? 'conclusion-pass' : 'conclusion-fail'">
            <div class="tr-conclusion-header">
              <svg v-if="archiveTestReport.status === 'passed'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                <polyline points="22 4 12 14.01 9 11.01"/>
              </svg>
              <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <line x1="15" y1="9" x2="9" y2="15"/>
                <line x1="9" y1="9" x2="15" y2="15"/>
              </svg>
              <span class="tr-conclusion-title">验收结论：{{ archiveTestReport.conclusion }}</span>
            </div>
            <p class="tr-conclusion-desc">{{ archiveTestReport.conclusionDesc }}</p>
          </div>
        </template>
      </div>

    <!-- 备案公示 Tab —�?发起备案后展示备�?公示字段预览（全量视图） -->
    <div v-show="activeTab === 'filing'" class="tab-content filing-tab">
      <!-- 备案公示状态条 -->
      <div class="fl-status-bar">
        <div class="fl-status-left">
          <span class="fl-status-title">备案公示</span>
          <span class="config-status-tag" :class="archiveFiling.approved ? 'status-pass' : (archiveFiling.submitted ? 'status-filing' : 'status-tested')">
            {{ archiveFiling.approved ? '备案通过' : (archiveFiling.submitted ? '备案审核中' : '待提交备案') }}
          </span>
        </div>
        <div class="fl-legend">
          <span><i class="fl-dot dot-common"></i>共有</span>
          <span><i class="fl-dot dot-filing"></i>备案</span>
          <span><i class="fl-dot dot-public"></i>公示</span>
        </div>
      </div>

      <!-- 分区域字段展�?-->
      <div v-for="(section, si) in archiveFiling.sections" :key="si" class="form-section">
        <div class="section-header open">
          <span class="section-title">{{ section.title }}</span>
          <span v-if="si === 3" class="section-header-sub">资源共用</span>
        </div>
        <div class="section-content">
          <div v-for="(f, fi) in section.fields" :key="fi" class="form-row readonly" :class="'fl-field-' + f.category">
            <label class="form-label">
              <span class="label-text">{{ f.label }}</span>
              <span class="fl-field-tag" :class="'tag-' + f.category">
                {{ f.category === 'common' ? '共有' : (f.category === 'filing' ? '备案' : '公示') }}
              </span>
            </label>
            <div class="form-value">{{ f.value }}</div>
          </div>
        </div>
      </div>

      <!-- 备案核查 -->
      <div class="form-section">
        <div class="section-header open">
          <span class="section-title">备案核查</span>
        </div>
        <div class="section-content">
          <div v-for="(item, idx) in archiveFiling.checks" :key="idx" class="fl-check-item">
            <span class="fl-check-icon" :class="item.status">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
            </span>
            <div class="fl-check-text">
              <span class="fl-check-label">{{ item.label }}</span>
              <span class="fl-check-desc">{{ item.desc }}</span>
            </div>
            <span class="fl-check-status" :class="item.status">{{ item.status === 'passed' ? '通过' : '未通过' }}</span>
          </div>
        </div>
      </div>

      <!-- 公示预览 -->
      <div class="form-section">
        <div class="section-header open">
          <span class="section-title">公示预览</span>
        </div>
        <div class="section-content">
          <div class="fl-notice-card">
            <h4 class="fl-notice-heading">{{ archiveFiling.notice?.title }}</h4>
            <p class="fl-notice-row"><span>公示对象</span> {{ archiveFiling.notice?.target }}</p>
            <p class="fl-notice-body">{{ archiveFiling.notice?.content }}</p>
            <p class="fl-notice-row"><span>公示渠道</span> {{ archiveFiling.notice?.notifyChannels }}</p>
            <p class="fl-notice-row"><span>公示期</span> {{ archiveFiling.notice?.startDate }} 至 {{ archiveFiling.notice?.endDate }}（{{ archiveFiling.notice?.duration }}）</p>
          </div>
        </div>
      </div>

      <!-- 提交备案 -->
      <div class="fl-actions">
        <button
          v-if="!archiveFiling.submitted"
          class="fl-submit-btn"
          @click="onSubmitFiling"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="22" y1="2" x2="11" y2="13"/>
            <polygon points="22 2 15 22 11 13 2 9 22 2"/>
          </svg>
          提交备案
        </button>
        <div v-else class="fl-submitted-tip">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
            <polyline points="22 4 12 14.01 9 11.01"/>
          </svg>
          {{ archiveFiling.approved ? '备案已通过，请选择销售渠道' : '备案已提交，等待备案通过' }}
        </div>
      </div>
    </div>

    <!-- 销售渠�?Tab —�?备案通过后选择销售渠�?-->
    <div v-show="activeTab === 'channel'" class="tab-content channel-tab">
      <div class="ch-topbar">
        <div class="ch-title">销售渠道配置</div>
        <span class="fl-status-badge" :class="archiveChannel.submitted ? 'badge-pass' : 'badge-pending'">
          {{ archiveChannel.submitted ? '渠道已配置' : '待选择渠道' }}
        </span>
      </div>
      <p class="ch-desc">请选择该资费方案支持的销售渠道，可选多个渠道。勾选后点击右下角"提交渠道"完成配置。</p>

      <div v-for="(ch, idx) in archiveChannel.list" :key="ch.key" class="ch-item" :class="{ selected: (archiveChannel.selected || []).includes(ch.key) }" @click="toggleChannel(ch.key)">
        <span class="ch-check" :class="{ checked: (archiveChannel.selected || []).includes(ch.key) }">
          <svg v-if="(archiveChannel.selected || []).includes(ch.key)" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
            <polyline points="20 6 9 17 4 12"/>
          </svg>
        </span>
        <div class="ch-text">
          <span class="ch-label">{{ ch.label }}</span>
          <span class="ch-desc">{{ ch.desc }}</span>
        </div>
      </div>

      <div class="ch-actions">
        <button
          v-if="!archiveChannel.submitted"
          class="fl-submit-btn"
          :disabled="(archiveChannel.selected || []).length === 0"
          @click="onSubmitChannel"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="22" y1="2" x2="11" y2="13"/>
            <polygon points="22 2 15 22 11 13 2 9 22 2"/>
          </svg>
          提交渠道
        </button>
        <div v-else class="fl-submitted-tip">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
            <polyline points="22 4 12 14.01 9 11.01"/>
          </svg>
          销售渠道已配置，流程已进入上架销售阶段
        </div>
      </div>
    </div>
    </div>

    <!-- 底部操作�?-->
    <div class="config-footer">
      <!-- 批量配置导航：第x/共y + 上一个/下一个 -->
      <div v-if="batchMode" class="batch-nav">
        <button
          class="batch-nav-btn"
          :disabled="!batchContext.hasPrev || submitted"
          @click="$emit('go-prev')"
          :title="batchContext.hasPrev ? '上一个草稿产品' : '已是第一个'"
        >
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <polyline points="15 18 9 12 15 6"/>
          </svg>
          上一个
        </button>
        <span class="batch-nav-count">第 {{ batchContext.index }} / {{ batchContext.total }} 个</span>
        <button
          class="batch-nav-btn"
          :disabled="!batchContext.hasNext || submitted"
          @click="$emit('go-next')"
          :title="batchContext.hasNext ? '下一个草稿产品' : '已是最后一个'"
        >
          下一个
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
            <polyline points="9 18 15 12 9 6"/>
          </svg>
        </button>
      </div>
      <template v-if="!submitted">
        <div class="footer-left">
          <button class="footer-btn btn-draft" @click="saveDraft">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/>
              <polyline points="17 21 17 13 7 13 7 21"/>
              <polyline points="7 3 7 8 15 8"/>
            </svg>
            保存草稿
          </button>
          <button class="footer-btn btn-check" @click="showAudit">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 11l3 3L22 4"/>
              <path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
            </svg>
            智能稽核
          </button>
        </div>
        <div class="footer-right">
          <button class="btn-submit" @click="onSubmit">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="22" y1="2" x2="11" y2="13"/>
              <polygon points="22 2 15 22 11 13 2 9 22 2"/>
            </svg>
            提交配置
          </button>
        </div>
      </template>
      <div v-else class="footer-submitted" :class="currentStatus">
        <svg v-if="currentStatus === 'completed' || currentStatus === 'testing'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="20 6 9 17 4 12"/>
        </svg>
        <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <polyline points="12 6 12 12 16 14"/>
        </svg>
        <span class="footer-submitted-text">{{ footerSubmittedText }}</span>
        <span class="footer-submitted-id">工单编号：{{ productInfo.workOrderId }}</span>
      </div>
    </div>

    <!-- 智能稽核弹窗 -->
    <transition name="audit-fade">
      <div v-if="auditVisible" class="audit-overlay" @click.self="auditVisible = false">
        <div class="audit-modal">
          <div class="audit-modal-header">
            <span class="audit-modal-title">智能稽核</span>
            <button class="audit-close" @click="auditVisible = false">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="audit-modal-body">
            <div v-if="auditLoading" class="audit-loading">
              <div class="audit-spinner"></div>
              <p>{{ auditProgressText }}</p>
            </div>
            <div v-else class="audit-results">
              <div v-for="(item, idx) in auditResults" :key="idx" class="audit-item" :class="item.type">
                <div class="audit-icon">
                  <svg v-if="item.type === 'success'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="20 6 9 17 4 12"/>
                  </svg>
                  <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10"/>
                    <line x1="15" y1="9" x2="9" y2="15"/>
                    <line x1="9" y1="9" x2="15" y2="15"/>
                  </svg>
                </div>
                <div class="audit-text">
                  <div class="audit-item-title">{{ item.title }}</div>
                  <div class="audit-item-desc">{{ item.desc }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onUnmounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { launchProductConfig, testReport, filing, salesChannel } from '../config/launchOptimizeData.js'
import { campusProductConfig } from '../config/campusDraftData.js'

const emit = defineEmits(['close', 'submitted', 'submit-filing', 'submit-channel', 'go-prev', 'go-next', 'batch-submitted', 'copy-init', 'copy-rename', 'dialogue-rename'])

// 只读档案模式：传�?archiveSnapshot 时，本面板以"配置档案"形式只读展示某份归档工单的完整配�?// archiveSnapshot = { productInfo: { workOrderId, prodPrcName, templateLabel, status }, values: { 字段key:�?} }
const props = defineProps({
  archiveSnapshot: { type: Object, default: null },
  // 对话式配置：由 AI 从用户需求解析出的字段值（{ 字段key: 值 }）。传入了即以"对话式配置"模式渲染：
  // 仅预填已解析字段，其余字段置空，等待"一键智填"用 AI 推荐值填充。
  parsedFields: { type: Object, default: null },
  // 对话式配置专用工单号：工作台页签/底部工单号均以本值展示，避免读取产品运营助手全局 launch 单号
  workOrderId: { type: String, default: '' },
  // 文件导入批量配置：传入了则该工作台处于"草稿批量配置"模式，
  // 底部展示 第x/共y + 上/下一个 导航，提交后自动跳下一个草稿
  batchContext: { type: Object, default: null },
  // 场景专属配置 schema：对话式/批量场景可传入与 launchOptimizeData 同构的配置数据
  //（如校园青春卡 campusDraftData.campusProductConfig）。未传入时回落到全局 launchProductConfig。
  schemaConfig: { type: Object, default: null },
})

// 复制模式：基于某份存量产品/归档档案「复制」出的可编辑新产品（保持只读档案预填的字段值为模板数据）
const copyMode = ref(false)
// 复制模式下的新工单号（进入复制时生成一次）
const copyWorkOrderId = ref('')
const isArchive = computed(() => !!props.archiveSnapshot && !copyMode.value)
const dialogueMode = computed(() => !!props.parsedFields)
// 动态预览模式：对话式配置与只读档案（存量快照）都按 formData 实际值渲染手机预览/免填单/短信模板，
// 避免"畅享59元青春版"等 launch 静态示例文案泄漏到查询助手的档案调阅视图中
const dynamicPreview = computed(() => dialogueMode.value || isArchive.value)
// 批量配置模式：批量上下文存在且非只读档案
const batchMode = computed(() => !!props.batchContext && !isArchive.value)
// 对话式配置独立状态：仅在该场景内流转（草稿→待审核），不读写产品运营助手全局 launch 状态
const dialogueStatus = ref('draft')

// ========== 面板宽度拖拽 ==========
const MIN_WIDTH = 400
const MAX_WIDTH = 900
const DEFAULT_WIDTH = 560

const panelWidth = ref(DEFAULT_WIDTH)
const resizing = ref(false)

const startResize = (e) => {
  e.preventDefault()
  resizing.value = true
  const startX = e.touches ? e.touches[0].clientX : e.clientX
  const startWidth = panelWidth.value

  const onMove = (ev) => {
    const clientX = ev.touches ? ev.touches[0].clientX : ev.clientX
    const delta = startX - clientX
    panelWidth.value = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, startWidth + delta))
  }

  const onUp = () => {
    resizing.value = false
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    document.removeEventListener('touchmove', onMove)
    document.removeEventListener('touchend', onUp)
  }

  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
  document.addEventListener('touchmove', onMove, { passive: false })
  document.addEventListener('touchend', onUp)
}

onUnmounted(() => {
  resizing.value = false
})

// ========== Tab 切换 ==========
const baseTabs = [
  { key: 'base', label: '基础信息' },
  { key: 'resource', label: '资源配置' },
  { key: 'marketing', label: '营销资源' },
  { key: 'sales', label: '销售规则' },
  { key: 'summary', label: '信息预览' },
]
const tabs = computed(() => {
  // 只读档案模式：展示完整页签（含测试报告/备案公示/销售渠道，数据取自该工单自身快照）。
  // 待审批（pending）工单尚未进入测试/备案/上架阶段，仅展示基础配置类页签。
  if (isArchive.value) {
    const status = props.archiveSnapshot.productInfo?.status
    if (status === 'pending') {
      return baseTabs.slice()
    }
    return [...baseTabs, { key: 'testReport', label: '测试报告' }, { key: 'filing', label: '备案公示' }, { key: 'channel', label: '销售渠道' }]
  }
  if (currentStatus.value === 'testing' || currentStatus.value === 'tested') {
    return [...baseTabs, { key: 'testReport', label: '测试报告' }]
  }
  if (currentStatus.value === 'filing') {
    return [...baseTabs, { key: 'testReport', label: '测试报告' }, { key: 'filing', label: '备案公示' }]
  }
  if (currentStatus.value === 'channel' || salesChannel.submitted) {
    return [...baseTabs, { key: 'testReport', label: '测试报告' }, { key: 'filing', label: '备案公示' }, { key: 'channel', label: '销售渠道' }]
  }
  return baseTabs
})
const activeTab = ref('base')

// 切换到测试报告页签（供父组件�?查看测试报告"动作时调用）
const showTestReport = () => {
  if (currentStatus.value === 'channel' || currentStatus.value === 'filing' || currentStatus.value === 'testing' || testReport.uploaded || currentStatus.value === 'tested') {
    activeTab.value = 'testReport'
  }
}

// 切换到备案公示页签（供父组件�?发起备案公示"时调用）
const showFiling = () => {
  if (currentStatus.value === 'channel' || currentStatus.value === 'filing' || filing.started) {
    activeTab.value = 'filing'
  }
}

// 切换到销售渠道页签（供父组件�?备案通过/选择销售渠�?时调用）
const showChannel = () => {
  if (currentStatus.value === 'channel' || salesChannel.available) {
    activeTab.value = 'channel'
  }
}

// ========== 产品基本信息 ==========
const productInfo = computed(() => {
  if (isArchive.value && props.archiveSnapshot.productInfo) {
    return {
      workOrderId: props.archiveSnapshot.productInfo.workOrderId,
      prodPrcName: props.archiveSnapshot.productInfo.prodPrcName,
      templateLabel: props.archiveSnapshot.productInfo.templateLabel,
      status: props.archiveSnapshot.productInfo.status || 'listed',
    }
  }
  // 对话式配置：标题沿用用户解析出的套餐名称（若有）；未命名时用占位标题，
  // 工单号和状态使用本场景自己的值（不读写产品运营助手全局 launch 状态），保证该场景内自洽
  if (dialogueMode.value && props.parsedFields) {
    // 产品名优先取用户在表单中实际编辑后的名称，改名后面板标题/预览实时同步
    const editedName = String(formData.prodName ?? '').trim()
    const name = editedName || props.parsedFields.prodName || '校园套餐（待命名）'
    return {
      ...launchProductConfig.productInfo,
      prodPrcName: name,
      workOrderId: props.workOrderId || '',
      status: dialogueStatus.value,
    }
  }
  // 复制模式：基于存量产品/归档档案生成的新产品（草稿态、新工单号、产品名追加“（复制）”）
  if (copyMode.value) {
    const src = props.archiveSnapshot?.productInfo || {}
    const vals = props.archiveSnapshot?.values || {}
    // 产品名优先取用户在表单中实际编辑后的名称，保证改名后面板标题/预览实时同步
    const editedName = String(formData.prodName ?? '').trim()
    return {
      ...launchProductConfig.productInfo,
      workOrderId: copyWorkOrderId.value,
      prodPrcName: editedName || (vals.prodName || src.prodPrcName || '') + '（复制）',
      templateLabel: src.templateLabel || launchProductConfig.productInfo.templateLabel,
      status: copyStatus.value,
    }
  }
  return launchProductConfig.productInfo
})

const currentStatus = computed(() => {
  if (submitted.value) {
    return productInfo.value.status === 'completed' ? 'completed' :
           productInfo.value.status === 'listed' ? 'listed' :
           productInfo.value.status === 'testing' ? 'testing' :
           productInfo.value.status === 'tested' ? 'tested' :
           productInfo.value.status === 'filing' ? 'filing' :
           productInfo.value.status === 'channel' ? 'channel' : 'submitted'
  }
  return productInfo.value.status
})
const statusText = computed(() => {
  const map = { draft: '草稿', submitted: '待审核', completed: '审批通过', listed: '销售中', testing: '测试中', tested: '待备案公示', filing: '备案公示中', channel: '待选择销售渠道' }
  return map[currentStatus.value] || '草稿'
})
const statusClass = computed(() => {
  const map = { draft: 'status-draft', submitted: 'status-pending', completed: 'status-pass', listed: 'status-listed', testing: 'status-testing', tested: 'status-tested', filing: 'status-filing', channel: 'status-channel' }
  return map[currentStatus.value] || 'status-draft'
})

const footerSubmittedText = computed(() => {
  if (currentStatus.value === 'listed') return '已上架销售中'
  if (currentStatus.value === 'completed') return '已审批通过'
  if (currentStatus.value === 'testing') return '审批通过，进入测试'
  if (currentStatus.value === 'tested') return '测试完成，等待备案公示'
  if (currentStatus.value === 'filing') return filing.submitted ? '备案已提交，等待通过' : '备案公示中，待提交备案'
  if (currentStatus.value === 'channel') return '待选择销售渠道'
  return '已提交，进入审批阶段'
})

// ========== 表单数据 ==========
// 场景 schema：默认为 launchProductConfig；对话式/批量场景传入 schemaConfig 时（如校园青春卡）使用该场景专属配置，
// 保证各场景字段/AI推荐理由不同（尤其避免把"沿用原套餐"这类仅适用于存量复制/启动优化的表述带入新建套餐场景）
const config = computed(() => {
  // 仅对话式/批量（parsedFields 存在）且显式传入 schemaConfig 时切换场景 schema
  if (props.schemaConfig && !props.archiveSnapshot) return props.schemaConfig
  return launchProductConfig
})
const formData = reactive({})
const modifiedFields = reactive({})

// 归档（配置档案）模式：测试报�?备案公示/销售渠道三个页签改用该工单自身的历史快照，
// 而非全局"当前正在配置的套餐"状态；非归档模式回落到全局数据源。
const archiveTestReport = computed(() => (isArchive.value ? (props.archiveSnapshot.testReport || {}) : testReport))
const archiveFiling = computed(() => (isArchive.value ? (props.archiveSnapshot.filing || {}) : filing))
const archiveChannel = computed(() => (isArchive.value ? (props.archiveSnapshot.channel || {}) : salesChannel))

// 对话式配置：记录"用户已解析"字段（渲染为"已解析"彩色标签）
const parsedFilled = reactive({})
// 记录由 AI 自动补全的字段（渲染为"AI推荐"标签）
const aiFilled = reactive({})

// 初始化表单数据
const initFormData = () => {
  // 对话式配置模式：打开工作台即自动填入——已解析字段标"已解析"，未解析字段由 AI 按模板自动补全（标"复用"或"AI推荐"）
  if (dialogueMode.value) {
    const vals = props.parsedFields || {}
    const applyField = (key, field) => {
      if (vals[key] !== undefined && vals[key] !== '') {
        // 用户已解析字段：直接填入并标"已解析"
        formData[key] = vals[key]
        parsedFilled[key] = '根据用户需求解析得出'
        aiFilled[key] = undefined
      } else {
        // 未解析字段：AI 自动补全模板推荐值
        parsedFilled[key] = undefined
        // 产品名称未由用户给出时保持空，由标题占位展示，而非复用示例套餐名
        if (key === 'prodName' || key === 'prodPrcName') {
          formData[key] = ''
          aiFilled[key] = undefined
          return
        }
        if (field.tag === 'reuse') {
          // 对话式/批量配置无原套餐可复用，统一按"AI推荐"给出补全理由
          aiFilled[key] = `${field.label}由AI按业务模板自动补全（建议值：${field.value ?? '--'}）`
        } else {
          // 对话式/批量配置为全新产品（无原套餐参考），AI 推荐理由不得引用
          // "沿用原套餐/复用原套餐/基于原套餐"等仅适用「存量复制/启动优化」场景的表述
          const reason = field.tagReason || ''
          const tainted = /(沿用|复用|基于|继承|参考|参照)原套餐/.test(reason)
          aiFilled[key] = tainted
            ? `${field.label}由AI按业务模板自动补全（建议值：${field.value ?? '--'}）`
            : (reason || `${field.label}由AI按业务模板推荐`)
        }
        if (field.type === 'table') {
          formData[key] = (field.rows || []).map((r) => ({ ...r }))
        } else if (field.type === 'checkbox-group') {
          // 对话式配置默认勾选：渠道类型选中前四个渠道，适用地区选中四川全省
          if (key === 'channelType') {
            formData[key] = ['offline', 'online', 'local-online', 'cmcc-online']
          } else if (key === 'applicableArea') {
            formData[key] = ['sc']
          } else {
            formData[key] = Array.isArray(field.value) ? field.value.slice() : []
          }
        } else {
          // 累计订购次数：AI 推荐默认 1，避免留空
          formData[key] = key === 'totalOrderTimes'
            ? '1'
            : (field.value !== undefined ? field.value : '')
        }
      }
    }
    Object.entries(config.value.baseInfo || {}).forEach(([key, field]) => applyField(key, field))
    ;[
      ...(config.value.resourceSections || []).map((s) => s.fields),
      ...(config.value.marketingSections || []).map((s) => s.fields),
      ...(config.value.salesSections || []).map((s) => s.fields),
    ].forEach((fields) => {
      ;(fields || []).forEach((field) => applyField(field.key, field))
    })
    return
  }
  // 只读档案模式：用归档快照里的字段值填充（缺失字段回落到模板默认值）
  if (isArchive.value && props.archiveSnapshot.values) {
    const vals = props.archiveSnapshot.values
    // 基础信息是对象（key→field），其余 sections 是字段数组
    Object.entries(config.value.baseInfo || {}).forEach(([key, field]) => {
      formData[key] = (vals[key] !== undefined ? vals[key] : field.value)
    })
    ;[
      ...(config.value.resourceSections || []).map((s) => s.fields),
      ...(config.value.marketingSections || []).map((s) => s.fields),
      ...(config.value.salesSections || []).map((s) => s.fields),
    ].forEach((fields) => {
      ;(fields || []).forEach((field) => {
        if (field.type === 'table') {
          formData[field.key] = Array.isArray(vals[field.key])
            ? vals[field.key].map((r) => ({ ...r }))
            : (field.rows || []).map((r) => ({ ...r }))
        } else {
          formData[field.key] = (vals[field.key] !== undefined ? vals[field.key] : field.value)
        }
      })
    })
    return
  }
  // 基础信息
  Object.entries(config.value.baseInfo).forEach(([key, field]) => {
    formData[key] = field.value
  })
  // 资源配置
  config.value.resourceSections.forEach((section) => {
    section.fields.forEach((field) => {
      formData[field.key] = field.value
    })
  })
  // 营销资源
  config.value.marketingSections.forEach((section) => {
    section.fields.forEach((field) => {
      formData[field.key] = field.value
    })
  })
  // 销售规则
  config.value.salesSections.forEach((section) => {
    section.fields.forEach((field) => {
      if (field.type === 'table') {
        // 表格类型：深拷贝 rows 到 formData
        formData[field.key] = (field.rows || []).map((r) => ({ ...r }))
      } else {
        formData[field.key] = field.value
      }
    })
  })
}
// 对话式配置：字段初始空值（标量空串 / 多选空数组 / 表格空数组）
const emptyValue = (field) => {
  if (field.type === 'table') return []
  if (field.type === 'checkbox-group') return []
  return ''
}
initFormData()

// 字段标签：优先"用户已解析"；对话式配置场景下无原套餐可复用，其余字段统一标"AI推荐"；
// 只读档案（存量快照）为历史归档数据，不显示 AI推荐/复用标签；否则按"AI补全/原配置标签"
const fieldTag = (field) => {
  if (parsedFilled[field.key]) return 'parsed'
  if (isArchive.value) return null
  if (dialogueMode.value) return 'ai'
  if (aiFilled[field.key]) return 'ai'
  return field.tag || null
}

// 基础信息字段列表
const baseFields = computed(() =>
  Object.entries(config.value.baseInfo).map(([key, field]) => ({ key, ...field }))
)

// 资源配置 sections
const resourceSections = computed(() => config.value.resourceSections)

// 营销资源 sections
const marketingSections = computed(() => config.value.marketingSections)

// 销售规则 sections
const salesSections = computed(() => config.value.salesSections)

// ========== Section 折叠 ==========
const expandedSections = reactive({})
;[...config.value.resourceSections, ...config.value.marketingSections, ...config.value.salesSections].forEach((s) => {
  expandedSections[s.key] = true
})

const toggleSection = (key) => {
  expandedSections[key] = !expandedSections[key]
}

// ========== 字段修改标记 ==========
const markModified = (key) => {
  modifiedFields[key] = true
}

// ========== Checkbox 多选组 ==========
const checkboxValue = (key, val) => {
  const v = formData[key]
  if (Array.isArray(v)) return v.includes(val)
  if (typeof v === 'string') return v.split(',').includes(val)
  return false
}
const toggleCheckbox = (key, val) => {
  let arr = Array.isArray(formData[key]) ? [...formData[key]] : (formData[key] ? formData[key].split(',') : [])
  if (arr.includes(val)) {
    arr = arr.filter((v) => v !== val)
  } else {
    arr.push(val)
  }
  formData[key] = arr
  modifiedFields[key] = true
}

// ========== 表格行管理 ==========
const tableRows = (key) => {
  return Array.isArray(formData[key]) ? formData[key] : []
}

const addTableRow = (key, columns) => {
  const newRow = {}
  columns.forEach((col) => {
    if (col.type === 'auto') {
      newRow[col.key] = ''
    } else if (col.type === 'select') {
      if (col.dynamicOptions) {
        // 动态选项列：取关联字段值对应的第一个选项
        const refField = col.options[Object.keys(col.options)[0]]
        newRow[col.key] = refField && refField[0] ? refField[0].value : ''
      } else {
        newRow[col.key] = col.options && col.options[0] ? col.options[0].value : ''
      }
    } else {
      newRow[col.key] = ''
    }
  })
  if (!Array.isArray(formData[key])) {
    formData[key] = []
  }
  formData[key].push(newRow)
  modifiedFields[key] = true
}

const deleteTableRow = (key, rowIdx) => {
  if (Array.isArray(formData[key])) {
    formData[key].splice(rowIdx, 1)
    modifiedFields[key] = true
  }
}

// ========== 前项限制提示语自动生成 ==========
const labelMap = { age: '年龄', globalLevel: '全球通等级', baseMonthlyFee: '基础套餐月费' }
const ruleTypeMap = { gt: '大于', lt: '小于', ge: '大于等于', le: '小于等于', eq: '等于' }
const limitTypeMap = { allow: '允许办理', disallow: '不允许办理' }

const generatePreLimitTip = (key, rowIdx) => {
  const row = tableRows(key)[rowIdx]
  if (!row) return ''
  const label = labelMap[row.label] || row.label || '条件'
  const ruleType = ruleTypeMap[row.ruleType] || row.ruleType || ''
  const limitValue = row.limitValue || ''
  const limitType = limitTypeMap[row.limitType] || row.limitType || ''
  if (!limitValue) return `${label}${ruleType}___${limitType}`
  return `${label}${ruleType}${limitValue}${limitType}本套餐`
}

// ========== 后项限制动态选项 ==========
const getDynamicOptions = (col, row) => {
  if (!col.dynamicOptions || !col.options) return []
  // 后项限制业务名称列：依赖同行 ruleType 字段
  const refValue = row.ruleType || Object.keys(col.options)[0]
  return col.options[refValue] || col.options[Object.keys(col.options)[0]] || []
}

// 表格单元格变更时：如果修改的是被依赖的字段（如后项限制的规则类型），重置动态选项列的值
const onTableCellChange = (key, rowIdx) => {
  modifiedFields[key] = true
  // 后项限制：ruleType 变更时，检查 bizName 是否在新选项中，不在则重置
  if (key === 'postLimitTable') {
    const row = tableRows(key)[rowIdx]
    // 找到 bizName 列定义
    const section = config.value.salesSections.find((s) => s.key === 'post-limit')
    if (section) {
      const tableField = section.fields.find((f) => f.key === key)
      if (tableField) {
        const bizCol = tableField.columns.find((c) => c.key === 'bizName')
        if (bizCol && bizCol.dynamicOptions) {
          const newOptions = getDynamicOptions(bizCol, row)
          const stillValid = newOptions.some((o) => o.value === row.bizName)
          if (!stillValid && newOptions.length > 0) {
            row.bizName = newOptions[0].value
          }
        }
      }
    }
  }
}

// 动态选项变更时：更新当前单元格的值
const onDynamicOptionChange = (key, rowIdx, colKey, event) => {
  const row = tableRows(key)[rowIdx]
  if (row) {
    row[colKey] = event.target.value
  }
  modifiedFields[key] = true
}

// 普通 select 单元格变更
const onSelectCellChange = (key, rowIdx, colKey, event) => {
  const row = tableRows(key)[rowIdx]
  if (row) {
    row[colKey] = event.target.value
  }
  // 触发 onTableCellChange 逻辑（处理后项限制 ruleType 变更时重置 bizName）
  onTableCellChange(key, rowIdx)
}

// 普通 input 单元格变更
const onInputCellChange = (key, rowIdx, colKey, event) => {
  const row = tableRows(key)[rowIdx]
  if (row) {
    row[colKey] = event.target.value
  }
  modifiedFields[key] = true
  // 前项限制：输入限定值时自动刷新提示语（auto类型单元格依赖同行数据）
  if (key === 'preLimitTable') {
    // auto类型单元格是 computed 式渲染，row 更新后自动刷新
  }
}

// ========== 信息预览 - APP手机壳模�?==========
const previewSections = computed(() => config.value.previewSections || launchProductConfig.previewSections)

const phoneTab = ref('user')
const phoneTime = ref('9:41')

const phoneNavTitle = computed(() => {
  const map = { user: '产品详情', freefill: '受理免填单', sms: '短信通知' }
  return map[phoneTab.value] || '产品详情'
})

// ========== 信息预览 Tab 数据 —— 统一从 formData 取当前配置值，确保与对话式配置工作台一致 ==========
const previewFee = computed(() => {
  const v = formData.monthlyFee
  return v !== undefined && v !== '' ? String(v).replace(/\.00$/, '') : '59'
})
// 预览区产品名：优先取表单里用户实际编辑后的名称（改名后手机预览/免填单/短信模板同步更新）
const previewName = computed(() => {
  const v = String(formData.prodName ?? formData.prodPrcName ?? productInfo.value.prodPrcName ?? '').trim()
  return v || productInfo.value.prodPrcName
})
const previewFlowUnit = computed(() => formData.flowUnit || 'GB')
const previewFlowAmount = computed(() => formData.flowAmount || '40')
const previewVoiceAmount = computed(() => formData.voiceAmount || '100')
const previewSmsAmount = computed(() => formData.smsAmount || '')
const previewFlowOvercharge = computed(() => formData.flowOvercharge || '0.29元/MB')
const previewVoiceOvercharge = computed(() => formData.voiceOvercharge || '0.15元/分钟')
const previewSmsOvercharge = computed(() => formData.smsOvercharge || '')
// 权益表：对话式配置与只读档案（存量快照）按权益资源配置动态展示；产品运营助手(launch)工作台保持原始静态权益不变
const previewRights = computed(() =>
  dynamicPreview.value && Array.isArray(formData.rightsTable) ? formData.rightsTable : []
)

// 根据字段 key 查找配置定义（用于 value→label 映射）
const findField = (key) => {
  if (config.value.baseInfo && config.value.baseInfo[key]) return config.value.baseInfo[key]
  const all = [
    ...(config.value.resourceSections || []).flatMap((s) => s.fields),
    ...(config.value.marketingSections || []).flatMap((s) => s.fields),
    ...(config.value.salesSections || []).flatMap((s) => s.fields),
  ]
  return all.find((f) => f.key === key)
}
const optionLabel = (key, val) => {
  if (val === undefined || val === '' || val === null) return ''
  const f = findField(key)
  const opt = ((f && f.options) || []).find((o) => String(o.value) === String(val))
  return opt ? opt.label : String(val)
}

// 权益列表 —— 对话式/只读档案按权益配置表渲染，非对话式（产品运营助手 launch）保持原始静态权益列表
const equityList = computed(() => {
  if (!dynamicPreview.value) {
    return [
      { name: '腾讯视频月卡', desc: '热门综艺抢先看', short: '视', color: '#06a0ff' },
      { name: '爱奇艺月卡', desc: '热播剧集随心看', short: '奇', color: '#00be5f' },
      { name: '生日特权', desc: '生日当天专享福利', short: '生', color: '#f59e0b' },
      { name: '流量结转', desc: '当月未用流量转至次月', short: '流', color: '#8b5cf6' },
    ]
  }
  const rights = previewRights.value
  if (rights.length) {
    const palette = ['#06a0ff', '#00be5f', '#f59e0b', '#8b5cf6', '#ef4444', '#0ea5e9']
    return rights.map((r, i) => ({
      name: r.rightName,
      desc: r.obtainType === 'auto' ? '订购后自动发放，次月起生效' : '办理后手动领取',
      short: (r.rightName || '权').slice(0, 1),
      color: palette[i % palette.length],
    }))
  }
  return [
    { name: '腾讯视频月卡', desc: '热门综艺抢先看', short: '视', color: '#06a0ff' },
    { name: '爱奇艺月卡', desc: '热播剧集随心看', short: '奇', color: '#00be5f' },
  ]
})

// 免填单数据 —— 对话式/只读档案按实际配置值生成
const freeFillItems = computed(() => {
  if (dynamicPreview.value) {
    const name = previewName.value
    const area = (Array.isArray(formData.applicableArea) ? formData.applicableArea : [])
      .map((v) => optionLabel('applicableArea', v)).filter(Boolean).join('、') || '四川省'
    const includeRes = [
      previewFlowAmount.value + (previewFlowUnit.value || 'GB') + '流量',
      previewVoiceAmount.value + '分钟语音',
      previewSmsAmount.value ? previewSmsAmount.value + '条短信' : '',
      previewRights.value.map((r) => r.rightName).join('、'),
    ].filter(Boolean).join('+')
    return [
      { label: '资费名称', value: name },
      { label: '套餐月费', value: `${previewFee.value}元/月` },
      { label: '包含资源', value: includeRes || '--', highlight: true },
      { label: '超套收费标准', value: [previewFlowOvercharge.value, previewVoiceOvercharge.value, previewSmsOvercharge.value].filter(Boolean).join(' / ') || '--' },
      { label: '限定条件', value: `${area}范围内使用` },
      { label: '生效方式', value: optionLabel('effRule', formData.effRule) || '立即生效' },
      { label: '有效期', value: optionLabel('validity', formData.validity) || '长期有效' },
      { label: '续订方式', value: optionLabel('renewMethod', formData.renewMethod) || '自动续订' },
      { label: '同时订购', value: `${formData.orderTimes || '1'}次` },
      { label: '套餐固定费', value: `${formData.monthlyFee || '49'}元` },
    ]
  }
  const section = previewSections.value.find((s) => s.key === 'free-fill-preview')
  return section ? section.items : []
})

// 短信模板数据 —— 对话式/只读档案按实际配置值生成
const smsItems = computed(() => {
  if (dynamicPreview.value) {
    const name = previewName.value
    const fee = `${previewFee.value}元`
    const ress = [
      previewFlowAmount.value + (previewFlowUnit.value || 'GB') + '流量',
      previewVoiceAmount.value + '分钟语音',
      previewSmsAmount.value ? previewSmsAmount.value + '条短信' : '',
    ].filter(Boolean).join('和')
    return [
      { label: '办理成功短信', value: `恭喜您成功办理${name}，月费${fee}，含${ress}，感谢您的支持！` },
      { label: '预约生效短信', value: `您的${name}已生效，月费${fee}，生效当月起享受套餐权益。` },
      { label: '前台二确短信', value: `尊敬的客户，您正在办理${name}，月费${fee}，是否确认办理？` },
      { label: '退订短信', value: `您好，您的${name}已退订，如有疑问请致电10086。` },
    ]
  }
  const section = previewSections.value.find((s) => s.key === 'sms-preview')
  return section ? section.items : []
})

// ========== 底部操作 ==========
const saveDraft = () => {
  Object.keys(modifiedFields).forEach((k) => (modifiedFields[k] = false))
  ElMessage.success('草稿保存成功')
}

// ========== 智能稽核 ==========
const auditVisible = ref(false)
const auditLoading = ref(false)
const auditProgressText = ref('')
const auditResults = ref([])
// 只读档案模式强制为已提交（submitted=true），使所有字段禁用、底部显示归档状态
const submitted = ref(!!props.archiveSnapshot)
// 复制模式产品的状态（草稿→待审核），与全局 launch 状态隔离
const copyStatus = ref('draft')

// 复制：基于存量产品/归档档案生成可编辑新产品。保持只读档案预填字段值为模板数据，
// 改为草稿态可编辑，生成新工单号，产品名追加“（复制）”，并通知父组件登记「我的草稿」。
const onCopyFromArchive = () => {
  if (!props.archiveSnapshot) return
  const src = props.archiveSnapshot.productInfo || {}
  const vals = props.archiveSnapshot.values || {}
  copyWorkOrderId.value = `WO${Date.now().toString().slice(-8)}${Math.floor(Math.random() * 90 + 10)}`
  copyStatus.value = 'draft'
  copyMode.value = true
  submitted.value = false
  // 清空 AI/已解析标签，使预填字段按普通模板数据显示（可自由编辑）
  Object.keys(aiFilled).forEach((k) => { aiFilled[k] = undefined })
  Object.keys(parsedFilled).forEach((k) => { parsedFilled[k] = undefined })
  if (formData.prodName !== undefined) formData.prodName = String(formData.prodName) + '（复制）'
  if (formData.prodCode !== undefined) formData.prodCode = copyWorkOrderId.value
  activeTab.value = 'base'
  ElMessage.success('已复制存量产品配置，转为可编辑新产品')
  // 通知父组件在「我的草稿」登记该复制品（source='copy'）
  emit('copy-init', {
    workOrderId: copyWorkOrderId.value,
    prodName: (vals.prodName || src.prodPrcName || '') + '（复制）',
    templateLabel: src.templateLabel || launchProductConfig.productInfo.templateLabel,
    summary: `${vals.monthlyFee ? vals.monthlyFee + '元/月' : ''} · 复制自 ${src.prodPrcName || src.workOrderId || '存量产品'}`,
    values: { ...vals },
  })
}

// 复制模式/对话式配置下监听产品名编辑，实时同步给父组件（我的草稿/流程条/审批展示保持一致）
watch(() => formData.prodName, (name) => {
  if (copyMode.value && copyWorkOrderId.value && name) {
    emit('copy-rename', String(name).trim())
  }
  // 对话式配置：用户在工作台内命名/改名后，实时同步给父组件（流程 stepper 等联动）
  if (dialogueMode.value && !copyMode.value && props.workOrderId && name) {
    emit('dialogue-rename', String(name).trim())
  }
})

// 收集面板内用户实际编辑的全部表单值（含表格行），用于归档工单"查看完整工作台档案"只读回看
const collectFormValues = () => {
  const values = {}
  Object.keys(formData).forEach((k) => {
    const v = formData[k]
    values[k] = Array.isArray(v) ? v.map((r) => ({ ...r })) : v
  })
  return values
}

const showAudit = () => {
  auditVisible.value = true
  auditLoading.value = true
  auditResults.value = []

  const steps = [
    '正在执行智能稽核...',
    '基础信息完整性校验...',
    '必填字段检查...',
    '业务规则校验...',
    '短信模板合规性检查...',
  ]

  let stepIdx = 0
  auditProgressText.value = steps[0]

  const stepTimer = setInterval(() => {
    stepIdx++
    if (stepIdx < steps.length) {
      auditProgressText.value = steps[stepIdx]
    } else {
      clearInterval(stepTimer)
    }
  }, 800)

  setTimeout(() => {
    clearInterval(stepTimer)
    auditLoading.value = false

    const results = []
    results.push({ type: 'success', title: '必填项检查通过', desc: '所有必填字段已填写' })
    if (!formData.monthlyFee) {
      results.push({ type: 'error', title: '缺少套餐固定费', desc: '套餐固定费未填写，根据规范必填' })
    } else {
      results.push({ type: 'success', title: '月租配置合规', desc: '套餐固定费、税率配置完整' })
    }
    if (!formData.flowAmount) {
      results.push({ type: 'error', title: '缺少流量资源配置', desc: '流量资源配置未填写，根据规范必填' })
    } else {
      results.push({ type: 'success', title: '资源配额检查通过', desc: '流量、语音、短信资源配置完整' })
    }
    results.push({ type: 'success', title: '日期配置合规', desc: '销售日期配置符合业务规则' })
    results.push({ type: 'success', title: '短信模板合规', desc: '短信模板关键词检查通过' })

    auditResults.value = results

    const hasError = results.some((r) => r.type === 'error')
    if (!hasError) {
      setTimeout(() => {
        submitted.value = true
        // 对话式配置用本场景独立状态（草稿→待审核），不读写产品运营助手全局 launch 状态，避免跨场景串扰
        if (dialogueMode.value) {
          dialogueStatus.value = 'submitted'
        } else if (copyMode.value) {
          // 复制模式同样用独立状态，不覆盖全局 launch 配置
          copyStatus.value = 'submitted'
        } else {
          launchProductConfig.productInfo.status = 'submitted'
        }
        auditVisible.value = false
        ElMessage.success('稽核通过，配置已提交')
        emit('submitted', {
          workOrderId: productInfo.value.workOrderId,
          values: collectFormValues(),
          // 提交时回传面板内实际产品名（用户在复制/编辑后可能改名），供父组件同步审批/流程/回复
          productName: String(formData.prodName ?? formData.prodPrcName ?? productInfo.value.prodPrcName ?? '').trim(),
        })
        // 批量配置模式：提交后通知父组件标记草稿已提交并自动跳转下一个
        if (batchMode.value) {
          emit('batch-submitted')
        }
      }, 600)
    }
  }, 4200)
}

const onSubmit = () => {
  showAudit()
}

// 点击"提交备案"：触发父组件处理备案提交 �?自动模拟备案通过
const onSubmitFiling = () => {
  emit('submit-filing', { workOrderId: productInfo.value.workOrderId })
}

// 点击"提交销售渠道"：触发父组件处理流程推进到上架销售
const onSubmitChannel = () => {
  emit('submit-channel', { workOrderId: productInfo.value.workOrderId, values: collectFormValues() })
}

// 勾选/取消某个销售渠道
const toggleChannel = (key) => {
  if (salesChannel.submitted) return
  const idx = salesChannel.selected.indexOf(key)
  if (idx >= 0) {
    salesChannel.selected.splice(idx, 1)
  } else {
    salesChannel.selected.push(key)
  }
}

// tab 数量增加时自动向左扩展面板宽度，避免 tab 溢出（只扩不减，尊重用户手动拖窄）
watch(() => tabs.value.length, (count) => {
  const targetByCount = { 5: DEFAULT_WIDTH, 6: 640, 7: 720, 8: 800 }
  const target = targetByCount[count]
  if (target && target > panelWidth.value) {
    panelWidth.value = target
  }
})

// 对话式配置·增量字段更新：用户通过后续对话（如"产品叫xxx"/"月费改成xxx"）调整已打开面板的字段值。
// 效果等同用户在工作台内直接修改并标注为"已解析"（渲染彩色标签），并联动手机预览/免填单/短信模板/流程名称。
const applyFieldUpdate = (key, value) => {
  if (!key) return
  formData[key] = value
  // 标"已解析"、清除原有"AI推荐"标签
  parsedFilled[key] = '根据用户需求解析得出'
  aiFilled[key] = undefined
  // 产品名称/工单名联动：更新预览区与流程 stepper（productInfo computed 已优先取表单名称）
  if (key === 'prodName') {
    // 若同时存在产品档案名称（prodPrcName）字段也一并同步，保证标题/预览/免填单一致
    if ('prodPrcName' in config.value.baseInfo) formData.prodPrcName = value
  }
}

// 暴露提交方法，供外部（如快捷回复"智能稽核并提�?）调用，效果等同点击"提交配置"
defineExpose({
  submit: onSubmit,
  showTestReport,
  showFiling,
  showChannel,
  // 供历史会话恢复时同步"已提交"状态，使底部操作栏随流程阶段切换
  setSubmitted: (val) => { submitted.value = !!val },
  // 供对话式配置场景：用户追加对话增量更新面板字段
  applyFieldUpdate,
})
</script>

<style scoped>
.config-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  border-left: 1px solid #e5e7eb;
  background: #fff;
  flex-shrink: 0;
  position: relative;
}

/* 拖拽调宽�?*/
.config-panel-resizer {
  position: absolute;
  top: 0;
  left: -4px;
  width: 8px;
  height: 100%;
  cursor: col-resize;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
}
.resizer-line {
  width: 2px;
  height: 100%;
  background: transparent;
  transition: background 0.15s ease;
  border-radius: 1px;
}
.config-panel-resizer:hover .resizer-line {
  background: #3b82f6;
}
.config-panel-resizer:active .resizer-line {
  background: #2563eb;
}

/* 顶部标题�?*/
.config-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.config-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.config-header-icon {
  color: #3b82f6;
  flex-shrink: 0;
}
.config-header-text {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.config-scheme-name {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.config-template-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  background: #eff6ff;
  color: #3b82f6;
  font-weight: 500;
  white-space: nowrap;
}
.config-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.config-copy-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 12px;
  border: 1px solid #2563eb;
  border-radius: 6px;
  background: #2563eb;
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}
.config-copy-btn:hover {
  background: #1d4ed8;
}

.config-status-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 500;
}
.config-status-tag.status-draft {
  background: #f1f5f9;
  color: #64748b;
}
.config-status-tag.status-pending {
  background: #fef3c7;
  color: #d97706;
}
.config-status-tag.status-pass {
  background: #dcfce7;
  color: #16a34a;
}
.config-status-tag.status-testing {
  background: #dbeafe;
  color: #2563eb;
}
.config-status-tag.status-tested {
  background: #dcfce7;
  color: #15803d;
}
.config-status-tag.status-filing {
  background: #fef3c7;
  color: #b45309;
}
.config-status-tag.status-channel {
  background: #ede9fe;
  color: #7c3aed;
}
.config-status-tag.status-listed {
  background: #d1fae5;
  color: #047857;
}
.config-close-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  transition: all 0.15s;
}
.config-close-btn:hover {
  background: #f1f5f9;
  color: #1e293b;
}

/* Tab 导航 */
.config-tabs {
  display: flex;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
  padding: 0 8px;
}
.config-tab {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 10px 14px;
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 0.15s;
}
.config-tab:hover {
  color: #3b82f6;
}
.config-tab.active {
  color: #2563eb;
  border-bottom-color: #2563eb;
  font-weight: 600;
}
.config-tab svg {
  flex-shrink: 0;
}

/* Tab 内容�?*/
.config-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px;
}
.config-body::-webkit-scrollbar {
  width: 6px;
}
.config-body::-webkit-scrollbar-track {
  background: transparent;
}
.config-body::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 3px;
}

/* Form Section */
.form-section {
  margin-bottom: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  overflow: visible;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: #f8fafc;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
  border-radius: 10px 10px 0 0;
}
.section-header:not(.open) {
  border-radius: 10px;
}
.section-content {
  padding: 14px;
  border-radius: 0 0 10px 10px;
  background: #fff;
}
.section-header:hover {
  background: #f1f5f9;
}
.section-header.open {
  background: #eff6ff;
}
.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}
.section-chevron {
  color: #94a3b8;
  transition: transform 0.2s ease;
}
.section-header.open .section-chevron {
  transform: rotate(180deg);
}

/* 基础信息双列布局 */
.base-info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 24px;
}
.base-field-row {
  margin-bottom: 12px;
}
.base-field-row .form-label {
  width: 140px;
  flex-shrink: 0;
  flex-wrap: nowrap;
  white-space: nowrap;
  align-items: center;
}
.base-field-row .form-control {
  min-width: 0;
  flex: 1;
}

/* Form Row */
.form-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}
.form-row:last-child {
  margin-bottom: 0;
}
.form-label {
  width: 120px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  padding-top: 6px;
}
.required {
  color: #ef4444;
  font-weight: 600;
}
.label-text {
  font-size: 12px;
  color: #475569;
  font-weight: 500;
  white-space: nowrap;
}
.field-tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  font-weight: 500;
  cursor: help;
  white-space: nowrap;
  position: relative;
  display: inline-flex;
  align-items: center;
}
.field-tag::after {
  content: attr(data-reason);
  position: absolute;
  bottom: calc(100% + 6px);
  left: 0;
  background: #1e293b;
  color: #f1f5f9;
  font-size: 11px;
  font-weight: 400;
  line-height: 1.5;
  padding: 6px 10px;
  border-radius: 6px;
  white-space: normal;
  width: 200px;
  text-align: left;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.25);
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.2s ease, visibility 0.2s ease;
  z-index: 200;
  pointer-events: none;
}
.field-tag::before {
  content: '';
  position: absolute;
  bottom: calc(100% + 1px);
  left: 10px;
  border: 5px solid transparent;
  border-top-color: #1e293b;
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.2s ease, visibility 0.2s ease;
  z-index: 200;
  pointer-events: none;
}
.field-tag:hover::after,
.field-tag:hover::before {
  opacity: 1;
  visibility: visible;
}
.tag-ai {
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  color: #fff;
}
.tag-reuse {
  background: #f1f5f9;
  color: #64748b;
  border: 1px solid #e2e8f0;
}
.tag-parsed {
  background: linear-gradient(135deg, #10b981, #059669);
  color: #fff;
}
.form-control {
  flex: 1;
  min-width: 0;
}
.form-input {
  width: 100%;
  padding: 6px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 13px;
  color: #1e293b;
  background: #fff;
  transition: all 0.15s;
  box-sizing: border-box;
}
.form-input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}
.form-input.modified {
  border-color: #f59e0b;
  background: #fffbeb;
}
.form-input.highlight {
  border-color: #3b82f6;
  background: #eff6ff;
  font-weight: 600;
}
.form-select {
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%2364748b' stroke-width='2'%3E%3Cpolyline points='6 9 12 15 18 9'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 8px center;
  padding-right: 28px;
}
.form-textarea {
  resize: vertical;
  min-height: 60px;
  font-family: inherit;
  line-height: 1.5;
}
.input-with-unit {
  display: flex;
  align-items: center;
}
.input-with-unit .form-input {
  border-radius: 6px 0 0 6px;
}
.input-unit {
  padding: 6px 10px;
  border: 1px solid #e2e8f0;
  border-left: none;
  border-radius: 0 6px 6px 0;
  background: #f8fafc;
  color: #64748b;
  font-size: 12px;
  white-space: nowrap;
}

/* Checkbox 多选组 */
.checkbox-group {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.checkbox-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 12px;
  color: #475569;
  cursor: pointer;
  user-select: none;
  transition: all 0.15s;
}
.checkbox-item input {
  display: none;
}
.checkbox-item:hover {
  border-color: #93c5fd;
  background: #f0f9ff;
}
.checkbox-item.checked {
  border-color: #3b82f6;
  background: #eff6ff;
  color: #2563eb;
  font-weight: 500;
}

/* ========== 表格字段（前项限�?后项限制�?========== */
.table-field {
  margin-bottom: 16px;
}
.table-field-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.table-field-label {
  width: auto;
  padding-top: 0;
  gap: 6px;
}
.table-add-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  border: 1px solid #3b82f6;
  border-radius: 6px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
  flex-shrink: 0;
}
.table-add-btn:hover {
  background: #3b82f6;
  color: #fff;
}
.limit-table-wrap {
  overflow-x: auto;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}
.limit-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.limit-table thead th {
  background: #f8fafc;
  padding: 8px 10px;
  text-align: left;
  font-weight: 600;
  color: #475569;
  border-bottom: 1px solid #e2e8f0;
  white-space: nowrap;
}
.limit-table tbody td {
  padding: 6px 8px;
  border-bottom: 1px solid #f1f5f9;
  vertical-align: middle;
}
.limit-table tbody tr:last-child td {
  border-bottom: none;
}
.limit-table .col-action {
  width: 50px;
  text-align: center;
}
.table-cell-input {
  width: 100%;
  padding: 5px 8px;
  border: 1px solid transparent;
  border-radius: 4px;
  font-size: 12px;
  color: #1e293b;
  background: transparent;
  transition: all 0.15s;
  box-sizing: border-box;
}
.table-cell-input:focus {
  outline: none;
  border-color: #3b82f6;
  background: #fff;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.1);
}
.table-cell-select {
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='10' viewBox='0 0 24 24' fill='none' stroke='%2364748b' stroke-width='2'%3E%3Cpolyline points='6 9 12 15 18 9'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 6px center;
  padding-right: 22px;
}
.table-del-btn {
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 5px;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  transition: all 0.15s;
  margin: 0 auto;
}
.table-del-btn:hover {
  background: #fef2f2;
  color: #ef4444;
}
.table-empty {
  text-align: center;
  color: #94a3b8;
  padding: 20px 10px !important;
  font-size: 12px;
}
.table-cell-auto {
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
  padding: 5px 8px;
  background: #f8fafc;
  border-radius: 4px;
  border: 1px dashed #cbd5e1;
  min-height: 24px;
}

/* ========== 信息预览 - APP手机壳模�?========== */
.phone-preview-tab {
  display: flex;
  justify-content: center;
  padding: 16px 8px;
}

/* 手机外框 */
.phone-frame {
  width: 320px;
  height: 600px;
  background: #fff;
  border-radius: 36px;
  border: 3px solid #1e293b;
  box-shadow: 0 8px 40px rgba(15, 23, 42, 0.15), inset 0 0 0 2px #334155;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
  flex-shrink: 0;
}

/* 状态栏 */
.phone-statusbar {
  height: 28px;
  background: #1e293b;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px 0 24px;
  flex-shrink: 0;
}
.phone-time {
  font-size: 12px;
  font-weight: 600;
  color: #f1f5f9;
}
.phone-status-icons {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #f1f5f9;
}
.phone-battery {
  width: 22px;
  height: 11px;
  border: 1.5px solid #f1f5f9;
  border-radius: 3px;
  position: relative;
  display: flex;
  align-items: center;
  padding: 1px;
}
.phone-battery-bar {
  width: 75%;
  height: 100%;
  background: #4ade80;
  border-radius: 1px;
}
.phone-battery-cap {
  position: absolute;
  right: -3px;
  top: 3px;
  width: 2px;
  height: 5px;
  background: #f1f5f9;
  border-radius: 0 1px 1px 0;
}

/* APP标题�?*/
.phone-navbar {
  height: 40px;
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  flex-shrink: 0;
}
.phone-nav-back,
.phone-nav-more {
  color: #fff;
  flex-shrink: 0;
}
.phone-nav-more {
  cursor: pointer;
}
.phone-nav-title {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
}

/* APP内容�?*/
.phone-screen {
  flex: 1;
  overflow-y: auto;
  background: #f5f5f7;
  -webkit-overflow-scrolling: touch;
}
.phone-screen::-webkit-scrollbar {
  width: 0;
}

/* APP页面 */
.app-page {
  min-height: 100%;
  padding: 12px 12px 70px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 套餐横幅卡片 */
.app-banner-card {
  position: relative;
  border-radius: 14px;
  overflow: hidden;
  padding: 18px 16px;
}
.app-banner-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #1e3a8a 0%, #2563eb 50%, #3b82f6 100%);
}
.app-banner-bg::after {
  content: '';
  position: absolute;
  right: -20px;
  top: -30px;
  width: 120px;
  height: 120px;
  background: rgba(255,255,255,0.08);
  border-radius: 50%;
}
.app-banner-bg::before {
  content: '';
  position: absolute;
  right: 30px;
  bottom: -40px;
  width: 80px;
  height: 80px;
  background: rgba(255,255,255,0.06);
  border-radius: 50%;
}
.app-banner-content {
  position: relative;
  z-index: 1;
}
.app-banner-name {
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  margin-bottom: 6px;
}
.app-banner-fee {
  display: flex;
  align-items: baseline;
  gap: 2px;
  margin-bottom: 10px;
}
.fee-num {
  font-size: 32px;
  font-weight: 700;
  color: #fff;
  line-height: 1;
}
.fee-unit {
  font-size: 12px;
  color: rgba(255,255,255,0.8);
}
.app-banner-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.app-tag {
  font-size: 10px;
  padding: 3px 8px;
  border-radius: 10px;
  background: rgba(255,255,255,0.2);
  color: #fff;
  font-weight: 500;
  backdrop-filter: blur(4px);
}

/* 通用卡片 */
.app-card {
  background: #fff;
  border-radius: 12px;
  padding: 12px 14px;
}
.app-card-title {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.app-card-title::before {
  content: '';
  width: 3px;
  height: 12px;
  background: #3b82f6;
  border-radius: 2px;
}

/* 资源网格 */
.app-resource-grid {
  display: flex;
  justify-content: space-around;
}
.app-resource-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.app-resource-icon {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.app-resource-icon.flow { background: linear-gradient(135deg, #06a0ff, #3b82f6); }
.app-resource-icon.voice { background: linear-gradient(135deg, #10b981, #059669); }
.app-resource-icon.video { background: linear-gradient(135deg, #f59e0b, #ef4444); }
.app-resource-num {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
}
.app-resource-num .unit {
  font-size: 10px;
  font-weight: 400;
  color: #64748b;
  margin-left: 2px;
}
.app-resource-label {
  font-size: 10px;
  color: #64748b;
}

/* 权益列表 */
.app-equity-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.app-equity-item {
  display: flex;
  align-items: center;
  gap: 10px;
}
.app-equity-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}
.app-equity-info {
  flex: 1;
  min-width: 0;
}
.app-equity-name {
  font-size: 12px;
  font-weight: 600;
  color: #1e293b;
}
.app-equity-desc {
  font-size: 10px;
  color: #94a3b8;
}

/* 费用列表 */
.app-fee-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.app-fee-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #64748b;
  padding: 4px 0;
  border-bottom: 1px solid #f1f5f9;
}
.app-fee-row:last-child {
  border-bottom: none;
}
.app-fee-row.highlight {
  background: #eff6ff;
  margin: 0 -8px;
  padding: 6px 8px;
  border-radius: 6px;
  border-bottom: none;
}
.app-fee-row.highlight span {
  color: #2563eb;
  font-weight: 600;
}
.app-fee-val {
  font-weight: 500;
  color: #1e293b;
}

/* 规则列表 */
.app-rule-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.app-rule-item {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 11px;
  color: #64748b;
  line-height: 1.5;
}
.app-rule-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #3b82f6;
  margin-top: 6px;
  flex-shrink: 0;
}

/* 操作按钮�?*/
.app-action-bar {
  display: flex;
  gap: 8px;
  margin-top: 6px;
}
.app-btn-primary {
  flex: 1;
  height: 38px;
  border: none;
  border-radius: 19px;
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.3);
  transition: transform 0.1s;
}
.app-btn-primary:active {
  transform: scale(0.97);
}
.app-btn-primary.full {
  width: 100%;
}
.app-btn-secondary {
  flex: 1;
  height: 38px;
  border: 1.5px solid #e2e8f0;
  border-radius: 19px;
  background: #fff;
  color: #475569;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: transform 0.1s;
}
.app-btn-secondary:active {
  transform: scale(0.97);
}

/* 免填单列�?*/
.app-formfill-list {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.app-formfill-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid #f1f5f9;
  font-size: 12px;
}
.app-formfill-row:last-child {
  border-bottom: none;
}
.app-formfill-label {
  color: #64748b;
  flex-shrink: 0;
}
.app-formfill-value {
  color: #1e293b;
  font-weight: 500;
  text-align: right;
  word-break: break-all;
}
.app-formfill-value.highlight {
  color: #2563eb;
  font-weight: 600;
}

/* 优惠信息 */
.app-discount-box {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  background: linear-gradient(135deg, #fef3c7, #fde68a);
  border-radius: 8px;
}
.app-discount-tag {
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  background: #f59e0b;
  padding: 3px 8px;
  border-radius: 4px;
  flex-shrink: 0;
}
.app-discount-title {
  font-size: 12px;
  font-weight: 600;
  color: #92400e;
}
.app-discount-desc {
  font-size: 10px;
  color: #b45309;
}

/* 短信对话 */
.app-sms-chat {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 4px 0;
}
.app-sms-time {
  text-align: center;
  font-size: 10px;
  color: #94a3b8;
  margin-bottom: 2px;
}
.app-sms-msg {
  display: flex;
  gap: 8px;
}
.app-sms-avatar {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}
.app-sms-bubble-wrap {
  flex: 1;
  min-width: 0;
}
.app-sms-label {
  font-size: 10px;
  color: #94a3b8;
  margin-bottom: 3px;
}
.app-sms-bubble {
  background: #fff;
  border-radius: 2px 12px 12px 12px;
  padding: 10px 12px;
  font-size: 12px;
  color: #1e293b;
  line-height: 1.6;
  word-break: break-all;
}

/* 底部Tab导航 */
.phone-tabbar {
  height: 56px;
  background: #fff;
  border-top: 1px solid #e2e8f0;
  display: flex;
  flex-shrink: 0;
}
.phone-tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: #94a3b8;
  cursor: pointer;
  transition: color 0.15s;
}
.phone-tab-item span {
  font-size: 10px;
  font-weight: 500;
}
.phone-tab-item.active {
  color: #2563eb;
}

/* Home�?*/
.phone-home-indicator {
  height: 5px;
  width: 100px;
  background: #1e293b;
  border-radius: 3px;
  margin: 0 auto 6px;
  flex-shrink: 0;
}

/* 底部操作�?*/
.config-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #e5e7eb;
  background: #fff;
  flex-shrink: 0;
}
.batch-nav {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  width: 100%;
  padding: 6px 0 2px;
  margin-bottom: 2px;
  border-bottom: 1px dashed #fde68a;
}
.batch-nav-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  border: 1px solid #fcd34d;
  border-radius: 8px;
  background: #fffbeb;
  color: #b45309;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.batch-nav-btn:hover:not(:disabled) {
  background: #fef3c7;
  border-color: #f59e0b;
}
.batch-nav-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.batch-nav-count {
  font-size: 12px;
  font-weight: 600;
  color: #92400e;
  white-space: nowrap;
}
.footer-left {
  display: flex;
  gap: 8px;
}
.footer-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 7px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  color: #475569;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}
.footer-btn:hover {
  border-color: #93c5fd;
  color: #2563eb;
  background: #f0f9ff;
}
.btn-smart {
  border-color: #a5b4fc;
  color: #4f46e5;
  background: #eef2ff;
}
.btn-smart:hover {
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  border-color: #6366f1;
  color: #fff;
}
.btn-submit {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 7px 18px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
}
.btn-submit:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4);
}
.footer-submitted {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 14px;
  border-radius: 8px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  color: #15803d;
  font-size: 12px;
  transition: background 0.3s ease, border-color 0.3s ease, color 0.3s ease;
}
.footer-submitted.submitted {
  background: #eff6ff;
  border-color: #bfdbfe;
  color: #1d4ed8;
}
.footer-submitted.submitted .footer-submitted-id {
  color: #2563eb;
}
.footer-submitted.testing {
  background: #eff6ff;
  border-color: #bfdbfe;
  color: #1d4ed8;
}
.footer-submitted.testing .footer-submitted-id {
  color: #2563eb;
}
.footer-submitted.tested {
  background: #eff6ff;
  border-color: #c7d7fe;
  color: #1d4ed8;
}
.footer-submitted.tested .footer-submitted-id {
  color: #2563eb;
}
.footer-submitted-text {
  font-weight: 600;
}
.footer-submitted-id {
  color: #16a34a;
  margin-left: auto;
}
.form-input:disabled,
.form-select:disabled,
.form-textarea:disabled,
.table-cell-input:disabled,
.checkbox-item input:disabled {
  background: #f8fafc;
  color: #94a3b8;
  cursor: not-allowed;
}
.table-add-btn:disabled,
.table-del-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* 智能稽核弹窗 */
.audit-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.audit-modal {
  width: 420px;
  max-height: 80vh;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.2);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.audit-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid #e5e7eb;
}
.audit-modal-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}
.audit-close {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
}
.audit-close:hover {
  background: #f1f5f9;
}
.audit-modal-body {
  padding: 20px;
  overflow-y: auto;
}
.audit-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 20px 0;
}
.audit-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #e2e8f0;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.audit-loading p {
  font-size: 13px;
  color: #64748b;
}
.audit-results {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.audit-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
}
.audit-item.success {
  background: #f0fdf4;
}
.audit-item.error {
  background: #fef2f2;
}
.audit-icon {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.audit-item.success .audit-icon {
  color: #16a34a;
}
.audit-item.error .audit-icon {
  color: #ef4444;
}
.audit-text {
  flex: 1;
}
.audit-item-title {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 2px;
}
.audit-item-desc {
  font-size: 12px;
  color: #64748b;
}

/* 弹窗动画 */
.audit-fade-enter-active,
.audit-fade-leave-active {
  transition: opacity 0.2s ease;
}
.audit-fade-enter-from,
.audit-fade-leave-to {
  opacity: 0;
}

/* ========== 测试报告 Tab ========== */
.test-report-tab {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* 等待测试人员上传 */
.tr-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 48px 20px;
  text-align: center;
  border: 1px dashed #c7d7fe;
  border-radius: 14px;
  background: linear-gradient(135deg, #f8fafc 0%, #eff6ff 100%);
}
.tr-loading-spinner {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: #eff6ff;
  border: 1px solid #dbeafe;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #2563eb;
  animation: tr-breathe 1.8s ease-in-out infinite;
}
@keyframes tr-breathe {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.08); opacity: 0.75; }
}
.tr-loading-title {
  font-size: 15px;
  font-weight: 700;
  color: #1e3a8a;
}
.tr-loading-desc {
  font-size: 13px;
  color: #475569;
  line-height: 1.6;
  margin: 0;
  max-width: 380px;
}
.tr-loading-tip {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 11px;
  color: #64748b;
  line-height: 1.5;
  text-align: left;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px 10px;
  max-width: 380px;
}
.tr-loading-tip svg {
  flex-shrink: 0;
  color: #3b82f6;
  margin-top: 1px;
}

/* 报告概览 */
.tr-overview {
  background: linear-gradient(135deg, #f0f9ff 0%, #eff6ff 100%);
  border: 1px solid #dbeafe;
  border-radius: 12px;
  padding: 14px;
}
.tr-overview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.tr-overview-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 700;
  color: #1e40af;
}
.tr-overview-title svg {
  color: #3b82f6;
}
.tr-status-badge {
  font-size: 12px;
  font-weight: 700;
  padding: 3px 10px;
  border-radius: 999px;
}
.tr-status-badge.badge-pass {
  background: #d1fae5;
  color: #065f46;
}
.tr-status-badge.badge-fail {
  background: #fee2e2;
  color: #991b1b;
}
.tr-overview-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 12px;
}
.tr-overview-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.tr-label {
  font-size: 11px;
  color: #64748b;
  font-weight: 600;
}
.tr-value {
  font-size: 12px;
  color: #0f172a;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tr-value.tr-number {
  font-family: 'SF Mono', 'Consolas', monospace;
  letter-spacing: 0.5px;
  color: #2563eb;
}

/* 核查明细 */
.tr-section {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  overflow: hidden;
}
.tr-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}
.tr-section-title {
  font-size: 13px;
  font-weight: 700;
  color: #334155;
}
.tr-section-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 999px;
}
.tr-section-status.status-pass {
  background: #d1fae5;
  color: #065f46;
}
.tr-section-status.status-fail {
  background: #fee2e2;
  color: #991b1b;
}
.tr-section-body {
  display: flex;
  flex-direction: column;
}
.tr-check-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid #f1f5f9;
}
.tr-check-item:last-child {
  border-bottom: none;
}
.tr-check-label {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  flex-shrink: 0;
  width: 90px;
  line-height: 1.5;
  padding-top: 1px;
}
.tr-check-detail {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.tr-check-row {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 11px;
  line-height: 1.5;
}
.tr-check-key {
  color: #94a3b8;
  font-weight: 600;
  flex-shrink: 0;
}
.tr-check-val {
  color: #334155;
  word-break: break-all;
}
.tr-check-result {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  margin-top: 1px;
}
.tr-check-result.result-pass {
  background: #d1fae5;
  color: #10b981;
}
.tr-check-result.result-fail {
  background: #fee2e2;
  color: #ef4444;
}

/* 验收结论 */
.tr-conclusion {
  border-radius: 12px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.tr-conclusion.conclusion-pass {
  background: linear-gradient(135deg, #ecfdf5 0%, #f0fdf4 100%);
  border: 1px solid #bbf7d0;
}
.tr-conclusion.conclusion-fail {
  background: linear-gradient(135deg, #fef2f2 0%, #fff1f2 100%);
  border: 1px solid #fecaca;
}
.tr-conclusion-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tr-conclusion.conclusion-pass .tr-conclusion-header {
  color: #065f46;
}
.tr-conclusion.conclusion-fail .tr-conclusion-header {
  color: #991b1b;
}
.tr-conclusion-header svg {
  flex-shrink: 0;
}
.tr-conclusion-title {
  font-size: 14px;
  font-weight: 700;
}
.tr-conclusion-desc {
  font-size: 12px;
  color: #475569;
  line-height: 1.6;
  margin: 0;
}

/* ========== 备案公示 Tab ========== */
.filing-tab {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
/* 只读字段行（备案公示预览�?*/
.form-row.readonly {
  align-items: flex-start;
}
.form-value {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #1e293b;
  padding-top: 6px;
  word-break: break-all;
  line-height: 1.5;
}
.fl-field-tag {
  font-size: 10px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 4px;
  line-height: 1.4;
  white-space: nowrap;
}
.fl-field-tag.tag-common {
  background: #f1f5f9;
  color: #64748b;
}
.fl-field-tag.tag-filing {
  background: #fef3c7;
  color: #b45309;
}
.fl-field-tag.tag-public {
  background: #dbeafe;
  color: #2563eb;
}
.section-header-sub {
  margin-left: 8px;
  font-size: 11px;
  font-weight: 400;
  color: #94a3b8;
}
.fl-overview {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  overflow: hidden;
}
.fl-overview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: #f8fafc;
  border-bottom: 1px solid #eef2f7;
}
.fl-overview-title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}
.fl-overview-title svg {
  color: #b45309;
}
.fl-status-badge {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 999px;
}
.fl-status-badge.badge-pass {
  background: #dcfce7;
  color: #15803d;
}
.fl-status-badge.badge-pending {
  background: #fef3c7;
  color: #b45309;
}
.fl-overview-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 14px;
}
.fl-overview-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.fl-label {
  font-size: 11px;
  color: #94a3b8;
}
.fl-value {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}
.fl-section {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 14px;
}
.fl-section-header {
  margin-bottom: 10px;
}
.fl-section-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}
.fl-check-item {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 7px 0;
  border-bottom: 1px solid #f1f5f9;
}
.fl-check-item:last-child {
  border-bottom: none;
}
.fl-check-icon {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
  margin-top: 1px;
}
.fl-check-icon.passed {
  background: #10b981;
}
.fl-check-icon.failed {
  background: #ef4444;
}
.fl-check-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.fl-check-label {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}
.fl-check-desc {
  font-size: 11px;
  color: #94a3b8;
  line-height: 1.4;
}
.fl-check-status {
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}
.fl-check-status.passed {
  color: #16a34a;
}
.fl-check-status.failed {
  color: #dc2626;
}
.fl-notice {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 14px;
}
.fl-notice-header {
  margin-bottom: 10px;
}
.fl-notice-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}
.fl-notice-card {
  border: 1px dashed #d1d5db;
  border-radius: 8px;
  padding: 14px;
  background: #fdfbf5;
}
.fl-notice-heading {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 10px;
  text-align: center;
}
.fl-notice-row {
  font-size: 12px;
  color: #475569;
  line-height: 1.8;
  margin: 0;
}
.fl-notice-row span {
  color: #94a3b8;
  margin-right: 6px;
}
.fl-notice-body {
  font-size: 12px;
  color: #334155;
  line-height: 1.8;
  margin: 8px 0;
  text-align: justify;
}
.fl-actions {
  padding-top: 4px;
}
.fl-submit-btn {
  width: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 10px 0;
  border: none;
  border-radius: 8px;
  background: #b45309;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.2s;
}
.fl-submit-btn:hover {
  background: #92400e;
}
.fl-submitted-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 12px;
  background: #f0fdf4;
  color: #15803d;
  font-size: 13px;
  font-weight: 600;
  border-radius: 8px;
}

/* ========== 备案公示 Tab · 只读展示 ========== */
.fl-status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
}
.fl-status-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.fl-status-title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}
.fl-legend {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 11px;
  color: #64748b;
}
.fl-legend span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.fl-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}
.fl-dot.dot-common {
  background: #94a3b8;
}
.fl-dot.dot-filing {
  background: #b45309;
}
.fl-dot.dot-public {
  background: #2563eb;
}
.fl-section-sub {
  margin-left: 8px;
  font-size: 11px;
  font-weight: 400;
  color: #94a3b8;
}
.fl-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 16px;
}
.fl-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 0;
  border-bottom: 1px dashed #eef2f7;
}
.fl-field-label {
  display: flex;
  align-items: center;
  gap: 6px;
}
.fl-field-name {
  font-size: 11px;
  color: #94a3b8;
}
.fl-tag {
  font-size: 9px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 4px;
  line-height: 1.4;
}
.fl-tag.tag-common {
  background: #f1f5f9;
  color: #64748b;
}
.fl-tag.tag-filing {
  background: #fef3c7;
  color: #b45309;
}
.fl-tag.tag-public {
  background: #dbeafe;
  color: #2563eb;
}
.fl-field-value {
  font-size: 12px;
  font-weight: 600;
  color: #0f172a;
  word-break: break-all;
}

/* ========== 销售渠�?Tab ========== */
.channel-tab {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.ch-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.ch-title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}
.ch-desc {
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
  margin: 0;
}
.ch-item {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 12px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s;
  background: #fff;
}
.ch-item:hover {
  border-color: #c4b5fd;
  background: #faf9ff;
}
.ch-item.selected {
  border-color: #7c3aed;
  background: #f5f3ff;
}
.ch-check {
  width: 18px;
  height: 18px;
  border-radius: 5px;
  border: 1.5px solid #cbd5e1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
  background: #fff;
  transition: all 0.15s;
}
.ch-check.checked {
  background: #7c3aed;
  border-color: #7c3aed;
}
.ch-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.ch-label {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}
.ch-desc {
  font-size: 11px;
  color: #94a3b8;
}
.ch-actions {
  padding-top: 4px;
}
.ch-actions .fl-submit-btn:disabled {
  background: #cbd5e1;
  cursor: not-allowed;
}
</style>
