<template>
    <div class="ontology-manager">
        <div class="top-bar">
            <button class="back-btn" @click="goBack">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M19 12H5M12 19l-7-7 7-7"/>
                </svg>
                返回首页
            </button>
            <h2>本体管理</h2>
            <div class="header-actions">
                <select v-model="filterCategory" @change="loadOntologies" class="filter-select">
                    <option value="">全部分类</option>
                    <option v-for="cat in categories" :key="cat.code" :value="cat.code">{{ cat.name }}</option>
                </select>
                <button class="primary-btn" @click="openCreateModal">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
                    </svg>
                    新建本体
                </button>
            </div>
        </div>

        <div class="content-layout">
            <div class="ontology-list">
                <div v-if="loading" class="loading-state">
                    <div class="loading-spinner"></div>
                    <p>加载中...</p>
                </div>
                <template v-else>
                    <div
                        v-for="ontology in ontologies" :key="ontology.ontologyCode"
                        class="ontology-card"
                        :class="{ active: selectedCode === ontology.ontologyCode }"
                        @click="selectOntology(ontology)"
                    >
                        <div class="ontology-header">
                            <span class="ontology-category">{{ getCategoryName(ontology.category) }}</span>
                            <div class="ontology-status">
                                <span v-if="ontology.isActive" class="active-badge">启用</span>
                                <span class="ontology-version">v{{ ontology.version }}</span>
                            </div>
                        </div>
                        <h3 class="ontology-name">{{ ontology.ontologyName }}</h3>
                        <p class="ontology-desc">{{ ontology.description || '暂无描述' }}</p>
                        <div class="ontology-meta">
                            <span class="entity-count">
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                    <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                                    <line x1="3" y1="9" x2="21" y2="9"/>
                                    <line x1="9" y1="21" x2="9" y2="9"/>
                                </svg>
                                {{ ontology.entities?.length || 0 }} 个实体
                            </span>
                            <span>{{ formatDate(ontology.updatedAt) }}</span>
                        </div>
                        <div class="ontology-actions">
                            <button class="small-btn" @click.stop="toggleActive(ontology)">
                                {{ ontology.isActive ? '停用' : '启用' }}
                            </button>
                            <button class="small-btn danger" @click.stop="confirmDelete(ontology)">删除</button>
                        </div>
                    </div>
                    <div v-if="ontologies.length === 0" class="empty-state">
                        <div class="empty-icon">📝</div>
                        <h3>还没有本体数据</h3>
                        <p>点击"新建本体"来创建第一个！</p>
                    </div>
                </template>
            </div>

            <div v-if="selectedOntology && !loading" class="editor-panel">
                <div class="editor-header">
                    <div class="editor-title">
                        <h3>{{ selectedOntology.ontologyName }}</h3>
                        <span class="editor-code">{{ selectedOntology.ontologyCode }}</span>
                    </div>
                    <div class="editor-actions">
                        <button class="btn" @click="resetEditing" :disabled="saving">重置</button>
                        <button class="btn-primary" @click="saveOntology" :disabled="saving || !hasChanges">
                            {{ saving ? '保存中...' : '保存' }}
                        </button>
                    </div>
                </div>

                <div class="editor-content">
                    <div class="section">
                        <div class="section-title">基本信息</div>
                        <div class="form-grid">
                            <div class="form-item">
                                <label>名称 *</label>
                                <input v-model="editingData.ontologyName" placeholder="输入名称" />
                            </div>
                            <div class="form-item">
                                <label>分类</label>
                                <select v-model="editingData.category">
                                    <option v-for="cat in categories" :key="cat.code" :value="cat.code">{{ cat.name }}</option>
                                </select>
                            </div>
                            <div class="form-item full">
                                <label>描述</label>
                                <input v-model="editingData.description" placeholder="输入描述" />
                            </div>
                        </div>
                    </div>

                    <div class="section">
                        <div class="section-title">
                            <span>实体结构</span>
                            <button class="small-btn" @click="addEntity">+ 新增实体</button>
                        </div>

                        <div v-if="editingData.entities && editingData.entities.length > 0" class="entities-list">
                            <div v-for="(entity, entityIndex) in editingData.entities" :key="entityIndex" class="entity-editor">
                                <div class="entity-header">
                                    <div class="entity-header-left">
                                        <div class="entity-icon">
                                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                                <rect x="3" y="3" width="18" height="18" rx="2"/>
                                                <line x1="3" y1="9" x2="21" y2="9"/>
                                                <line x1="9" y1="21" x2="9" y2="9"/>
                                            </svg>
                                        </div>
                                        <input v-model="entity.entityCode" placeholder="编码" class="entity-code-input" />
                                        <input v-model="entity.entityName" placeholder="名称" class="entity-name-input" />
                                    </div>
                                    <div class="entity-header-right">
                                        <button class="icon-btn" @click="addField(entity)" title="新增字段">
                                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                                <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
                                            </svg>
                                        </button>
                                        <button class="icon-btn danger" @click="removeEntity(entityIndex)" title="删除实体">
                                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                                            </svg>
                                        </button>
                                    </div>
                                </div>

                                <div class="fields-section">
                                    <div class="fields-header">
                                        <span>字段列表 ({{ entity.fields?.length || 0 }})</span>
                                        <div class="view-toggle">
                                            <button 
                                                :class="['view-btn', { active: entity.viewMode === 'tree' }]"
                                                @click="entity.viewMode = 'tree'"
                                            >树视图</button>
                                            <button 
                                                :class="['view-btn', { active: entity.viewMode === 'table' }]"
                                                @click="entity.viewMode = 'table'"
                                            >表格</button>
                                        </div>
                                    </div>

                                    <div v-if="entity.viewMode === 'table'" class="fields-table">
                                        <table>
                                            <thead>
                                                <tr>
                                                    <th>编码</th>
                                                    <th>名称</th>
                                                    <th>类型</th>
                                                    <th>必填</th>
                                                    <th>规则</th>
                                                    <th>操作</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                <tr v-if="!entity.fields || entity.fields.length === 0">
                                                    <td colspan="6" class="empty-row">暂无字段，点击 + 新增</td>
                                                </tr>
                                                <tr v-else v-for="(field, fieldIndex) in entity.fields" :key="fieldIndex">
                                                    <td>
                                                        <input v-model="field.fieldCode" placeholder="字段编码" class="table-input" />
                                                    </td>
                                                    <td>
                                                        <input v-model="field.fieldName" placeholder="字段名称" class="table-input" />
                                                    </td>
                                                    <td>
                                                        <select v-model="field.fieldType" class="table-select" @change="handleTypeChange(field)">
                                                            <option value="string">字符串</option>
                                                            <option value="input">文本</option>
                                                            <option value="textarea">多行</option>
                                                            <option value="number">数字</option>
                                                            <option value="integer">整数</option>
                                                            <option value="boolean">布尔</option>
                                                            <option value="date">日期</option>
                                                            <option value="datetime">日期时间</option>
                                                            <option value="email">邮箱</option>
                                                            <option value="phone">手机</option>
                                                            <option value="enum">枚举</option>
                                                            <option value="object">对象</option>
                                                            <option value="array">数组</option>
                                                        </select>
                                                    </td>
                                                    <td>
                                                        <select v-model="field.required" class="table-select">
                                                            <option :value="false">否</option>
                                                            <option :value="true">是</option>
                                                        </select>
                                                    </td>
                                                    <td>
                                                        <input v-model="field.ruleDescription" placeholder="规则描述" class="table-input" />
                                                    </td>
                                                    <td class="actions-cell">
                                                        <button 
                                                            v-if="field.fieldType === 'object' || field.fieldType === 'array'"
                                                            class="small-btn"
                                                            @click="toggleFieldExpanded(field)"
                                                        >
                                                            {{ field.expanded ? '收起' : '展开' }}
                                                        </button>
                                                        <button class="icon-btn danger" @click="removeField(entity, fieldIndex)">
                                                            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                                                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                                                            </svg>
                                                        </button>
                                                    </td>
                                                </tr>
                                            </tbody>
                                        </table>
                                        <div v-if="hasExpandedFields(entity)" class="expanded-fields">
                                            <div v-for="(field, fieldIndex) in entity.fields" :key="fieldIndex" v-if="field.expanded">
                                                <div class="nested-fields-editor">
                                                    <div class="editor-title">{{ field.fieldName || field.fieldCode }} 配置</div>
                                                    <div v-if="field.fieldType === 'object'" class="object-editor">
                                                        <div class="section-subtitle">对象属性</div>
                                                        <button class="small-btn" @click="addObjectProperty(field)">+ 添加属性</button>
                                                        <div v-if="!field.properties || field.properties.length === 0" class="empty-note">
                                                            暂无属性，点击 + 添加
                                                        </div>
                                                        <div v-else class="properties-list">
                                                            <div v-for="(prop, propIndex) in field.properties" :key="propIndex" class="property-item">
                                                                <input v-model="prop.fieldCode" placeholder="编码" class="small-input" />
                                                                <input v-model="prop.fieldName" placeholder="名称" class="small-input" />
                                                                <select v-model="prop.fieldType" class="small-select">
                                                                    <option value="string">字符串</option>
                                                                    <option value="number">数字</option>
                                                                    <option value="boolean">布尔</option>
                                                                    <option value="object">对象</option>
                                                                </select>
                                                                <select v-model="prop.required" class="tiny-select">
                                                                    <option :value="false">否</option>
                                                                    <option :value="true">是</option>
                                                                </select>
                                                                <button class="icon-btn danger" @click="removeObjectProperty(field, propIndex)">
                                                                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                                                        <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                                                                    </svg>
                                                                </button>
                                                            </div>
                                                        </div>
                                                    </div>
                                                    <div v-if="field.fieldType === 'array'" class="array-editor">
                                                        <div class="section-subtitle">数组项配置</div>
                                                        <div class="array-config">
                                                            <label>类型：</label>
                                                            <select v-model="field.items.fieldType" class="small-select">
                                                                <option value="string">字符串</option>
                                                                <option value="number">数字</option>
                                                                <option value="integer">整数</option>
                                                                <option value="boolean">布尔</option>
                                                                <option value="object">对象</option>
                                                            </select>
                                                        </div>
                                                        <div v-if="field.items.fieldType === 'object'" class="array-object-config">
                                                            <div class="section-subtitle">对象属性</div>
                                                            <button class="small-btn" @click="addArrayItemProperty(field)">+ 添加属性</button>
                                                            <div v-if="!field.items.properties || field.items.properties.length === 0" class="empty-note">
                                                                暂无属性，点击 + 添加
                                                            </div>
                                                            <div v-else class="properties-list">
                                                                <div v-for="(prop, propIndex) in field.items.properties" :key="propIndex" class="property-item">
                                                                    <input v-model="prop.fieldCode" placeholder="编码" class="small-input" />
                                                                    <input v-model="prop.fieldName" placeholder="名称" class="small-input" />
                                                                    <select v-model="prop.fieldType" class="small-select">
                                                                        <option value="string">字符串</option>
                                                                        <option value="number">数字</option>
                                                                        <option value="boolean">布尔</option>
                                                                        <option value="object">对象</option>
                                                                    </select>
                                                                    <select v-model="prop.required" class="tiny-select">
                                                                        <option :value="false">否</option>
                                                                        <option :value="true">是</option>
                                                                    </select>
                                                                    <button class="icon-btn danger" @click="removeArrayItemProperty(field, propIndex)">
                                                                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                                                            <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                                                                        </svg>
                                                                    </button>
                                                                </div>
                                                            </div>
                                                        </div>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    </div>

                                    <div v-else class="fields-tree">
                                        <div v-if="!entity.fields || entity.fields.length === 0" class="empty-fields">
                                            暂无字段，点击 + 新增
                                        </div>
                                        <template v-else>
                                            <div v-for="(field, fieldIndex) in entity.fields" :key="fieldIndex" class="field-tree-node">
                                                <div class="field-header">
                                                    <span :class="['field-type-badge', field.fieldType]">{{ getFieldTypeLabel(field.fieldType) }}</span>
                                                    <div class="field-info">
                                                        <input v-model="field.fieldCode" placeholder="编码" class="small-input" />
                                                        <span class="separator">:</span>
                                                        <input v-model="field.fieldName" placeholder="名称" class="small-input" />
                                                    </div>
                                                    <div class="field-controls">
                                                        <select v-model="field.required" class="tiny-select">
                                                            <option :value="false">选填</option>
                                                            <option :value="true">必填</option>
                                                        </select>
                                                        <select v-model="field.fieldType" class="tiny-select" @change="handleTypeChange(field)">
                                                            <option value="string">字符串</option>
                                                            <option value="input">文本</option>
                                                            <option value="textarea">多行</option>
                                                            <option value="number">数字</option>
                                                            <option value="integer">整数</option>
                                                            <option value="boolean">布尔</option>
                                                            <option value="date">日期</option>
                                                            <option value="datetime">日期时间</option>
                                                            <option value="email">邮箱</option>
                                                            <option value="phone">手机</option>
                                                            <option value="enum">枚举</option>
                                                            <option value="object">对象</option>
                                                            <option value="array">数组</option>
                                                        </select>
                                                        <button 
                                                            v-if="field.fieldType === 'object' || field.fieldType === 'array'"
                                                            class="icon-btn"
                                                            @click="toggleTreeFieldExpanded(field)"
                                                            :title="field.expanded ? '收起' : '展开'"
                                                        >
                                                            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                                                <polyline :points="field.expanded ? '18 15 12 9 6 15' : '6 9 12 15 18 9'"/>
                                                            </svg>
                                                        </button>
                                                        <button class="icon-btn danger" @click="removeField(entity, fieldIndex)" title="删除">
                                                            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                                                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                                                            </svg>
                                                        </button>
                                                    </div>
                                                </div>
                                                <div v-if="field.expanded" class="field-content">
                                                    <div v-if="field.fieldType === 'object'" class="object-fields">
                                                        <div class="object-header">
                                                            <span>对象属性</span>
                                                            <button class="small-btn" @click="addObjectProperty(field)">+ 添加属性</button>
                                                        </div>
                                                        <div v-if="!field.properties || field.properties.length === 0" class="empty-note">
                                                            暂无属性，点击 + 添加
                                                        </div>
                                                        <div v-else class="nested-fields">
                                                            <div v-for="(prop, propIndex) in field.properties" :key="propIndex" class="field-tree-node">
                                                                <div class="field-header">
                                                                    <span :class="['field-type-badge', prop.fieldType]">{{ getFieldTypeLabel(prop.fieldType) }}</span>
                                                                    <div class="field-info">
                                                                        <input v-model="prop.fieldCode" placeholder="编码" class="small-input" />
                                                                        <span class="separator">:</span>
                                                                        <input v-model="prop.fieldName" placeholder="名称" class="small-input" />
                                                                    </div>
                                                                    <div class="field-controls">
                                                                        <select v-model="prop.required" class="tiny-select">
                                                                            <option :value="false">选填</option>
                                                                            <option :value="true">必填</option>
                                                                        </select>
                                                                        <select v-model="prop.fieldType" class="tiny-select">
                                                                            <option value="string">字符串</option>
                                                                            <option value="number">数字</option>
                                                                            <option value="boolean">布尔</option>
                                                                            <option value="object">对象</option>
                                                                        </select>
                                                                        <button class="icon-btn danger" @click="removeObjectProperty(field, propIndex)" title="删除">
                                                                            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                                                                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                                                                            </svg>
                                                                        </button>
                                                                    </div>
                                                                </div>
                                                            </div>
                                                        </div>
                                                    </div>
                                                    <div v-if="field.fieldType === 'array'" class="array-fields">
                                                        <div class="array-header">
                                                            <span>数组项类型</span>
                                                        </div>
                                                        <div class="array-item-config">
                                                            <label>类型：</label>
                                                            <select v-model="field.items.fieldType" class="small-select">
                                                                <option value="string">字符串</option>
                                                                <option value="number">数字</option>
                                                                <option value="integer">整数</option>
                                                                <option value="boolean">布尔</option>
                                                                <option value="object">对象</option>
                                                            </select>
                                                            <div v-if="field.items.fieldType === 'object'" class="object-in-array">
                                                                <div class="object-header">
                                                                    <span>对象属性</span>
                                                                    <button class="small-btn" @click="addArrayItemProperty(field)">+ 添加属性</button>
                                                                </div>
                                                                <div v-if="!field.items.properties || field.items.properties.length === 0" class="empty-note">
                                                                    暂无属性，点击 + 添加
                                                                </div>
                                                                <div v-else class="nested-fields">
                                                                    <div v-for="(prop, propIndex) in field.items.properties" :key="propIndex" class="field-tree-node">
                                                                        <div class="field-header">
                                                                            <span :class="['field-type-badge', prop.fieldType]">{{ getFieldTypeLabel(prop.fieldType) }}</span>
                                                                            <div class="field-info">
                                                                                <input v-model="prop.fieldCode" placeholder="编码" class="small-input" />
                                                                                <span class="separator">:</span>
                                                                                <input v-model="prop.fieldName" placeholder="名称" class="small-input" />
                                                                            </div>
                                                                            <div class="field-controls">
                                                                                <select v-model="prop.required" class="tiny-select">
                                                                                    <option :value="false">选填</option>
                                                                                    <option :value="true">必填</option>
                                                                                </select>
                                                                                <select v-model="prop.fieldType" class="tiny-select">
                                                                                    <option value="string">字符串</option>
                                                                                    <option value="number">数字</option>
                                                                                    <option value="boolean">布尔</option>
                                                                                    <option value="object">对象</option>
                                                                                </select>
                                                                                <button class="icon-btn danger" @click="removeArrayItemProperty(field, propIndex)" title="删除">
                                                                                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                                                                        <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                                                                                    </svg>
                                                                                </button>
                                                                            </div>
                                                                        </div>
                                                                    </div>
                                                                </div>
                                                            </div>
                                                        </div>
                                                    </div>
                                                </div>
                                            </div>
                                        </template>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div v-else class="empty-entities">
                            <div class="empty-entities-icon">📦</div>
                            <p>暂无实体，点击 + 新增实体</p>
                        </div>
                    </div>

                    <div class="section">
                        <div class="section-title">
                            <span>JSON 配置（高级）</span>
                            <button class="small-btn" @click="showJsonEditor = !showJsonEditor">
                                {{ showJsonEditor ? '收起' : '展开' }}
                            </button>
                        </div>
                        <div v-if="showJsonEditor" class="json-editor">
                            <textarea v-model="entitiesJson" rows="15" placeholder="JSON格式的实体结构..." />
                            <p class="json-hint">在此处直接编辑JSON，更改会同步到上面的表单中</p>
                        </div>
                    </div>
                </div>
            </div>

            <div v-else-if="!loading" class="empty-editor">
                <div class="empty-editor-icon">📋</div>
                <h3>请选择一个本体</h3>
                <p>在左侧列表中选择一个本体开始编辑</p>
            </div>
        </div>

        <div v-if="showModal" class="modal-overlay" @click.self="closeModal">
            <div class="modal" @click.stop>
                <div class="modal-header">
                    <h3>新建本体</h3>
                    <button class="close-btn" @click="closeModal">×</button>
                </div>
                <div class="modal-body">
                    <div class="form-item">
                        <label>编码 *</label>
                        <input v-model="createData.ontologyCode" placeholder="例如：customer_service" />
                    </div>
                    <div class="form-item">
                        <label>名称 *</label>
                        <input v-model="createData.ontologyName" placeholder="例如：客户服务" />
                    </div>
                    <div class="form-item">
                        <label>分类</label>
                        <select v-model="createData.category">
                            <option v-for="cat in categories" :key="cat.code" :value="cat.code">{{ cat.name }}</option>
                        </select>
                    </div>
                    <div class="form-item">
                        <label>描述</label>
                        <input v-model="createData.description" placeholder="简单描述用途" />
                    </div>
                </div>
                <div class="modal-footer">
                    <button class="btn" @click="closeModal" :disabled="saving">取消</button>
                    <button class="btn-primary" @click="confirmCreate" :disabled="saving">
                        {{ saving ? '创建中...' : '确定' }}
                    </button>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useLoadingStore } from '../stores/loading'
import * as ontologyApi from '../services/ontologyApi'

const emit = defineEmits(['goBack'])
const goBack = () => { emit('goBack') }

const loadingStore = useLoadingStore()

const loading = ref(false)
const saving = ref(false)
const ontologies = ref([])
const categories = ref([])
const filterCategory = ref('')
const selectedCode = ref(null)
const selectedOntology = ref(null)
const editingData = ref({})
const originalData = ref({})
const showJsonEditor = ref(false)
const showModal = ref(false)
const createData = ref({})

const hasChanges = computed(() => {
    if (!selectedOntology.value) return false
    return JSON.stringify(editingData.value) !== JSON.stringify(originalData.value)
})

const entitiesJson = computed({
    get() {
        if (editingData.value.entities) {
            return JSON.stringify(editingData.value.entities, null, 2)
        }
        return ''
    },
    set(val) {
        try {
            const parsed = JSON.parse(val)
            if (Array.isArray(parsed)) {
                editingData.value.entities = parsed
            } else {
                ElMessage.warning('JSON 格式错误：需要是数组格式')
            }
        } catch (e) {
            // 忽略解析错误，用户正在输入
        }
    }
})

const getCategoryName = (code) => {
    const cat = categories.value.find(c => c.code === code)
    return cat?.name || code
}

const formatDate = (dateStr) => {
    if (!dateStr) return ''
    const d = new Date(dateStr)
    return d.toLocaleDateString('zh-CN')
}

const getFieldTypeLabel = (type) => {
    const labels = {
        string: '字符串',
        input: '文本',
        textarea: '多行',
        number: '数字',
        integer: '整数',
        boolean: '布尔',
        date: '日期',
        datetime: '日期时间',
        email: '邮箱',
        phone: '手机',
        enum: '枚举',
        object: '对象',
        array: '数组'
    }
    return labels[type] || type
}

const loadCategories = async () => {
    try {
        const res = await ontologyApi.getOntologyCategories()
        if (res.success) categories.value = res.data
    } catch (e) {
        console.error('加载分类失败:', e)
    }
}

const loadOntologies = async () => {
    loading.value = true
    try {
        const res = await ontologyApi.listOntologies(filterCategory.value || undefined, undefined)
        if (res.success) ontologies.value = res.data || []
        else ElMessage.error(res.message || '加载本体列表失败')
    } catch (e) {
        ElMessage.error('加载本体列表失败')
        console.error(e)
    } finally {
        loading.value = false
    }
}

const selectOntology = async (ontology) => {
    if (hasChanges.value) {
        try {
            await ElMessageBox.confirm('当前有未保存的更改，切换将丢失更改。是否继续？', '确认切换', {
                confirmButtonText: '继续',
                cancelButtonText: '取消',
                type: 'warning'
            })
        } catch (e) {
            return
        }
    }

    selectedCode.value = ontology.ontologyCode
    loadingStore.show('加载本体详情...')
    try {
        const res = await ontologyApi.getOntology(ontology.ontologyCode)
        if (res.success) {
            const data = res.data
            if (data.entities) {
                data.entities.forEach(entity => {
                    entity.viewMode = 'table'
                    if (entity.fields) {
                        entity.fields.forEach(field => {
                            field.expanded = false
                        })
                    }
                })
            }
            selectedOntology.value = data
            editingData.value = {
                ...data,
                entities: data.entities ? JSON.parse(JSON.stringify(data.entities)) : []
            }
            originalData.value = {
                ...data,
                entities: data.entities ? JSON.parse(JSON.stringify(data.entities)) : []
            }
        } else {
            ElMessage.error(res.message || '加载本体详情失败')
        }
    } catch (e) {
        ElMessage.error('加载本体详情失败')
        console.error(e)
    } finally {
        loadingStore.hide()
    }
}

const resetEditing = () => {
    if (!originalData.value) return
    editingData.value = {
        ...originalData.value,
        entities: originalData.value.entities ? JSON.parse(JSON.stringify(originalData.value.entities)) : []
    }
    ElMessage.success('已重置')
}

const validateOntology = (data, isCreate = false) => {
    if (!data.ontologyName?.trim()) {
        ElMessage.warning('请输入名称')
        return false
    }
    if (isCreate && !data.ontologyCode?.trim()) {
        ElMessage.warning('请输入编码')
        return false
    }
    return true
}

const saveOntology = async () => {
    if (!validateOntology(editingData.value)) return
    
    saving.value = true
    try {
        const res = await ontologyApi.updateOntology(selectedCode.value, editingData.value)
        if (res.success) {
            ElMessage.success('保存成功')
            const savedData = res.data
            if (savedData.entities) {
                savedData.entities.forEach(entity => {
                    entity.viewMode = 'table'
                    if (entity.fields) {
                        entity.fields.forEach(field => {
                            field.expanded = false
                        })
                    }
                })
            }
            originalData.value = {
                ...savedData,
                entities: savedData.entities ? JSON.parse(JSON.stringify(savedData.entities)) : []
            }
            await loadOntologies()
            const updatedOntology = ontologies.value.find(o => o.ontologyCode === selectedCode.value)
            if (updatedOntology) selectedOntology.value = savedData
        } else {
            ElMessage.error(res.message || '保存失败')
        }
    } catch (e) {
        ElMessage.error('保存失败')
        console.error(e)
    } finally {
        saving.value = false
    }
}

const openCreateModal = () => {
    createData.value = {
        ontologyCode: '',
        ontologyName: '',
        description: '',
        category: 'general',
        entities: []
    }
    showModal.value = true
}

const closeModal = () => { 
    showModal.value = false 
}

const confirmCreate = async () => {
    if (!validateOntology(createData.value, true)) return
    
    saving.value = true
    try {
        const res = await ontologyApi.createOntology(createData.value)
        if (res.success) {
            ElMessage.success('创建成功')
            showModal.value = false
            await loadOntologies()
            await selectOntology(res.data)
        } else {
            ElMessage.error(res.message || '创建失败')
        }
    } catch (e) {
        ElMessage.error('创建失败')
        console.error(e)
    } finally {
        saving.value = false
    }
}

const confirmDelete = async (ontology) => {
    try {
        await ElMessageBox.confirm(
            `确定要删除本体「${ontology.ontologyName}」吗？此操作不可恢复。`,
            '确认删除',
            {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning',
                confirmButtonClass: 'btn-danger'
            }
        )
        await deleteOntology(ontology)
    } catch (e) {
        // 用户取消，不处理
    }
}

const deleteOntology = async (ontology) => {
    try {
        loadingStore.show('删除中...')
        const res = await ontologyApi.deleteOntology(ontology.ontologyCode)
        if (res.success) {
            ElMessage.success('删除成功')
            if (selectedCode.value === ontology.ontologyCode) {
                selectedCode.value = null
                selectedOntology.value = null
                editingData.value = {}
                originalData.value = {}
            }
            await loadOntologies()
        } else {
            ElMessage.error(res.message || '删除失败')
        }
    } catch (e) {
        ElMessage.error('删除失败')
        console.error(e)
    } finally {
        loadingStore.hide()
    }
}

const toggleActive = async (ontology) => {
    try {
        const res = await ontologyApi.toggleOntology(ontology.ontologyCode)
        if (res.success) {
            ElMessage.success(res.data.isActive ? '已启用' : '已停用')
            await loadOntologies()
            if (selectedCode.value === ontology.ontologyCode) {
                selectedOntology.value = res.data
                editingData.value.isActive = res.data.isActive
                originalData.value.isActive = res.data.isActive
            }
        } else {
            ElMessage.error(res.message || '操作失败')
        }
    } catch (e) {
        ElMessage.error('操作失败')
        console.error(e)
    }
}

const addEntity = () => {
    if (!editingData.value.entities) editingData.value.entities = []
    editingData.value.entities.push({
        entityCode: '',
        entityName: '',
        viewMode: 'table',
        fields: []
    })
}

const removeEntity = (index) => {
    editingData.value.entities.splice(index, 1)
}

const addField = (entity) => {
    if (!entity.fields) entity.fields = []
    entity.fields.push({
        fieldCode: '',
        fieldName: '',
        fieldType: 'string',
        required: false,
        ruleDescription: '',
        expanded: false,
        properties: [],
        items: { fieldType: 'string', properties: [] }
    })
}

const removeField = (entity, index) => {
    entity.fields.splice(index, 1)
}

const handleTypeChange = (field) => {
    if (field.fieldType === 'object' && !field.properties) {
        field.properties = []
    }
    if (field.fieldType === 'array' && !field.items) {
        field.items = { fieldType: 'string', properties: [] }
    }
}

const toggleFieldExpanded = (field) => {
    field.expanded = !field.expanded
    if (field.expanded) {
        if (field.fieldType === 'object' && !field.properties) {
            field.properties = []
        }
        if (field.fieldType === 'array' && !field.items) {
            field.items = { fieldType: 'string', properties: [] }
        }
    }
}

const toggleTreeFieldExpanded = (field) => {
    field.expanded = !field.expanded
    if (field.expanded) {
        if (field.fieldType === 'object' && !field.properties) {
            field.properties = []
        }
        if (field.fieldType === 'array' && !field.items) {
            field.items = { fieldType: 'string', properties: [] }
        }
    }
}

const addObjectProperty = (field) => {
    if (!field.properties) field.properties = []
    field.properties.push({
        fieldCode: '',
        fieldName: '',
        fieldType: 'string',
        required: false,
        ruleDescription: ''
    })
}

const removeObjectProperty = (field, index) => {
    field.properties.splice(index, 1)
}

const addArrayItemProperty = (field) => {
    if (!field.items) field.items = { fieldType: 'object', properties: [] }
    if (!field.items.properties) field.items.properties = []
    field.items.properties.push({
        fieldCode: '',
        fieldName: '',
        fieldType: 'string',
        required: false,
        ruleDescription: ''
    })
}

const removeArrayItemProperty = (field, index) => {
    if (field.items && field.items.properties) {
        field.items.properties.splice(index, 1)
    }
}

const hasExpandedFields = (entity) => {
    return entity.fields && entity.fields.some(f => f.expanded)
}

onMounted(async () => {
    await Promise.all([loadCategories(), loadOntologies()])
})
</script>

<style scoped>
.ontology-manager {
    padding: 24px;
    height: 100%;
    display: flex;
    flex-direction: column;
    background: var(--bg-tertiary);
}

.top-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px 24px;
    background: var(--bg-primary);
    border-radius: 12px;
    margin-bottom: 20px;
    box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

.back-btn {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 16px;
    border-radius: 8px;
    border: 1px solid var(--border-default);
    background: var(--bg-primary);
    cursor: pointer;
    font-size: 14px;
    color: var(--text-secondary);
    transition: all var(--transition-fast);
}

.back-btn:hover {
    background: var(--bg-secondary);
    color: var(--text-primary);
}

.top-bar h2 {
    margin: 0;
    font-size: 18px;
}

.header-actions {
    display: flex;
    gap: 12px;
    align-items: center;
}

.filter-select {
    padding: 8px 12px;
    border-radius: 6px;
    border: 1px solid var(--border-default);
    background: var(--bg-primary);
    color: var(--text-primary);
}

.primary-btn {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 10px 18px;
    background: var(--color-primary-500);
    color: white;
    border: none;
    border-radius: 8px;
    cursor: pointer;
    transition: background var(--transition-fast);
}

.primary-btn:hover:not(:disabled) {
    background: var(--color-primary-600);
}

.primary-btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
}

.content-layout {
    display: grid;
    grid-template-columns: 360px 1fr;
    gap: 20px;
    flex: 1;
    min-height: 0;
}

.ontology-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
    overflow-y: auto;
}

.loading-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 60px 20px;
    color: var(--text-muted);
}

.loading-spinner {
    width: 40px;
    height: 40px;
    border: 3px solid var(--border-default);
    border-top-color: var(--color-primary-500);
    border-radius: 50%;
    animation: spin 1s linear infinite;
}

@keyframes spin {
    to { transform: rotate(360deg); }
}

.ontology-card {
    background: var(--bg-primary);
    padding: 20px;
    border-radius: 12px;
    border: 2px solid transparent;
    cursor: pointer;
    box-shadow: 0 2px 8px rgba(0,0,0,0.06);
    transition: all 0.2s;
}

.ontology-card:hover {
    border-color: var(--color-primary-500);
    transform: translateY(-2px);
}

.ontology-card.active {
    border-color: var(--color-primary-500);
    background: var(--color-primary-50);
}

.ontology-header {
    display: flex;
    justify-content: space-between;
    margin-bottom: 12px;
}

.ontology-category {
    padding: 4px 10px;
    border-radius: 20px;
    font-size: 12px;
    background: var(--bg-secondary);
    color: var(--text-secondary);
}

.ontology-status {
    display: flex;
    gap: 8px;
    align-items: center;
}

.active-badge {
    padding: 2px 8px;
    border-radius: 10px;
    background: var(--color-success-500);
    color: white;
    font-size: 11px;
}

.ontology-version {
    color: var(--text-muted);
    font-size: 12px;
}

.ontology-name {
    font-size: 16px;
    margin: 0 0 8px 0;
}

.ontology-desc {
    font-size: 13px;
    color: var(--text-secondary);
    margin: 0 0 12px 0;
}

.ontology-meta {
    display: flex;
    align-items: center;
    gap: 16px;
    font-size: 12px;
    color: var(--text-muted);
    margin-bottom: 12px;
}

.entity-count {
    display: flex;
    align-items: center;
    gap: 4px;
}

.ontology-actions {
    display: flex;
    gap: 8px;
    padding-top: 12px;
    border-top: 1px solid var(--border-light);
}

.small-btn {
    padding: 6px 12px;
    background: var(--bg-secondary);
    border: none;
    border-radius: 6px;
    cursor: pointer;
    font-size: 12px;
    transition: background var(--transition-fast);
}

.small-btn:hover:not(:disabled) {
    background: var(--bg-tertiary);
}

.small-btn.danger {
    background: var(--color-error-50);
    color: var(--color-error-600);
}

.small-btn.danger:hover:not(:disabled) {
    background: var(--color-error-100);
}

.empty-state {
    text-align: center;
    padding: 40px;
    color: var(--text-muted);
    background: var(--bg-primary);
    border-radius: 12px;
}

.empty-icon {
    font-size: 40px;
    margin-bottom: 12px;
}

.editor-panel {
    background: var(--bg-primary);
    border-radius: 12px;
    padding: 24px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
}

.empty-editor {
    background: var(--bg-primary);
    border-radius: 12px;
    padding: 60px 24px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: var(--text-muted);
}

.empty-editor-icon {
    font-size: 48px;
    margin-bottom: 16px;
}

.editor-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;
    padding-bottom: 16px;
    border-bottom: 1px solid var(--border-default);
}

.editor-title h3 {
    margin: 0 0 4px 0;
}

.editor-code {
    font-size: 13px;
    color: var(--text-muted);
}

.editor-actions {
    display: flex;
    gap: 8px;
}

.btn {
    padding: 10px 20px;
    border: 1px solid var(--border-default);
    border-radius: 8px;
    cursor: pointer;
    background: var(--bg-primary);
    color: var(--text-primary);
    transition: background var(--transition-fast);
}

.btn:hover:not(:disabled) {
    background: var(--bg-secondary);
}

.btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
}

.btn-primary {
    padding: 10px 20px;
    background: var(--color-primary-500);
    color: white;
    border: none;
    border-radius: 8px;
    cursor: pointer;
    transition: background var(--transition-fast);
}

.btn-primary:hover:not(:disabled) {
    background: var(--color-primary-600);
}

.btn-primary:disabled {
    opacity: 0.6;
    cursor: not-allowed;
}

.editor-content {
    flex: 1;
}

.section {
    margin-bottom: 28px;
}

.section-title {
    font-weight: 600;
    margin-bottom: 16px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 15px;
}

.section-subtitle {
    font-weight: 500;
    margin-bottom: 12px;
    color: var(--text-secondary);
    font-size: 14px;
}

.form-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 16px;
}

.form-item {
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.form-item.full {
    grid-column: span 2;
}

.form-item label {
    font-size: 13px;
    font-weight: 500;
    color: var(--text-secondary);
}

.form-item input,
.form-item select,
.form-item textarea {
    padding: 10px 12px;
    border: 1px solid var(--border-default);
    border-radius: 6px;
    background: var(--bg-primary);
    color: var(--text-primary);
}

.form-item input:focus,
.form-item select:focus,
.form-item textarea:focus {
    outline: none;
    border-color: var(--color-primary-500);
}

.entities-list {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.entity-editor {
    border: 1px solid var(--border-default);
    border-radius: 12px;
    padding: 20px;
    background: var(--bg-secondary);
}

.entity-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
}

.entity-header-left {
    display: flex;
    align-items: center;
    gap: 12px;
}

.entity-icon {
    width: 40px;
    height: 40px;
    background: var(--color-primary-100);
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--color-primary-600);
}

.entity-code-input {
    width: 120px;
    padding: 6px 10px;
    border: 1px solid var(--border-default);
    border-radius: 6px;
    background: var(--bg-primary);
    color: var(--text-primary);
    font-family: monospace;
}

.entity-name-input {
    width: 150px;
    padding: 6px 10px;
    border: 1px solid var(--border-default);
    border-radius: 6px;
    background: var(--bg-primary);
    color: var(--text-primary);
}

.entity-header-right {
    display: flex;
    gap: 8px;
}

.icon-btn {
    padding: 6px;
    background: transparent;
    border: none;
    cursor: pointer;
    color: var(--text-secondary);
    border-radius: 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: background var(--transition-fast);
}

.icon-btn:hover {
    background: var(--bg-tertiary);
}

.icon-btn.danger:hover {
    background: var(--color-error-50);
    color: var(--color-error-600);
}

.fields-section {
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid var(--border-light);
}

.fields-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    font-size: 14px;
    color: var(--text-secondary);
}

.view-toggle {
    display: flex;
    gap: 4px;
    background: var(--bg-primary);
    padding: 2px;
    border-radius: 6px;
}

.view-btn {
    padding: 4px 12px;
    border: none;
    border-radius: 4px;
    cursor: pointer;
    font-size: 12px;
    background: transparent;
    color: var(--text-secondary);
    transition: all var(--transition-fast);
}

.view-btn:hover {
    color: var(--text-primary);
}

.view-btn.active {
    background: var(--color-primary-500);
    color: white;
}

.fields-table {
    background: var(--bg-primary);
    border-radius: 8px;
    overflow: hidden;
    border: 1px solid var(--border-default);
}

.fields-table table {
    width: 100%;
    border-collapse: collapse;
}

.fields-table th {
    background: var(--bg-tertiary);
    padding: 10px 12px;
    text-align: left;
    font-weight: 500;
    font-size: 13px;
    color: var(--text-secondary);
    border-bottom: 1px solid var(--border-default);
}

.fields-table td {
    padding: 8px 12px;
    border-bottom: 1px solid var(--border-light);
}

.table-input {
    width: 100%;
    padding: 6px 8px;
    border: 1px solid var(--border-default);
    border-radius: 4px;
    background: var(--bg-primary);
    color: var(--text-primary);
    font-size: 13px;
}

.table-input:focus {
    outline: none;
    border-color: var(--color-primary-500);
}

.table-select {
    padding: 6px 8px;
    border: 1px solid var(--border-default);
    border-radius: 4px;
    background: var(--bg-primary);
    color: var(--text-primary);
    font-size: 13px;
}

.actions-cell {
    display: flex;
    gap: 6px;
    align-items: center;
}

.empty-row {
    text-align: center;
    padding: 24px;
    color: var(--text-muted);
    font-size: 13px;
}

.expanded-fields {
    margin-top: 16px;
    padding: 16px;
    background: var(--bg-tertiary);
    border-radius: 8px;
}

.fields-tree {
    padding: 4px;
}

.field-tree-node {
    background: var(--bg-primary);
    border-radius: 8px;
    margin-bottom: 8px;
    border: 1px solid var(--border-default);
    overflow: hidden;
}

.field-header {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 12px;
    background: var(--bg-secondary);
}

.field-type-badge {
    padding: 2px 8px;
    border-radius: 4px;
    font-size: 11px;
    font-weight: 500;
}

.field-type-badge.string {
    background: var(--color-info-100);
    color: var(--color-info-600);
}

.field-type-badge.number,
.field-type-badge.integer {
    background: var(--color-warning-100);
    color: var(--color-warning-600);
}

.field-type-badge.boolean {
    background: var(--color-success-100);
    color: var(--color-success-600);
}

.field-type-badge.date,
.field-type-badge.datetime {
    background: var(--color-primary-100);
    color: var(--color-primary-600);
}

.field-type-badge.object,
.field-type-badge.array {
    background: var(--color-purple-100);
    color: var(--color-purple-600);
}

.field-info {
    display: flex;
    align-items: center;
    gap: 8px;
    flex: 1;
}

.small-input {
    padding: 6px 8px;
    border: 1px solid var(--border-default);
    border-radius: 4px;
    background: var(--bg-primary);
    color: var(--text-primary);
    font-size: 13px;
}

.separator {
    color: var(--text-muted);
}

.field-controls {
    display: flex;
    gap: 8px;
    align-items: center;
}

.tiny-select {
    padding: 4px 8px;
    border: 1px solid var(--border-default);
    border-radius: 4px;
    background: var(--bg-primary);
    color: var(--text-primary);
    font-size: 12px;
}

.field-content {
    padding: 16px;
    border-top: 1px solid var(--border-light);
}

.object-fields,
.array-fields {
    margin-left: 20px;
}

.object-header,
.array-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    font-weight: 500;
    color: var(--text-secondary);
}

.nested-fields {
    margin-left: 20px;
}

.empty-fields {
    text-align: center;
    padding: 32px;
    color: var(--text-muted);
    font-size: 13px;
}

.empty-entities {
    text-align: center;
    padding: 48px;
    color: var(--text-muted);
}

.empty-entities-icon {
    font-size: 48px;
    margin-bottom: 12px;
}

.empty-note {
    padding: 16px;
    color: var(--text-muted);
    font-size: 13px;
    text-align: center;
}

.nested-fields-editor {
    background: var(--bg-primary);
    border-radius: 8px;
    padding: 16px;
    margin-top: 8px;
}

.editor-title {
    font-weight: 500;
    margin-bottom: 12px;
    color: var(--text-secondary);
}

.properties-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.property-item {
    display: flex;
    gap: 8px;
    align-items: center;
    padding: 8px;
    background: var(--bg-tertiary);
    border-radius: 6px;
}

.small-select {
    padding: 4px 8px;
    border: 1px solid var(--border-default);
    border-radius: 4px;
    background: var(--bg-primary);
    color: var(--text-primary);
    font-size: 12px;
}

.object-editor,
.array-editor {
    margin-top: 12px;
}

.array-config {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 12px;
}

.array-item-config {
    margin-bottom: 12px;
}

.object-in-array {
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1px solid var(--border-light);
}

.array-object-config {
    margin-top: 8px;
}

.json-editor textarea {
    width: 100%;
    padding: 12px;
    border: 1px solid var(--border-default);
    border-radius: 8px;
    font-family: monospace;
    background: var(--bg-primary);
    color: var(--text-primary);
}

.json-hint {
    margin: 8px 0 0 0;
    font-size: 12px;
    color: var(--text-muted);
}

.modal-overlay {
    position: fixed;
    inset: 0;
    background: var(--bg-overlay);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: var(--z-modal);
}

.modal {
    background: var(--bg-primary);
    border-radius: 12px;
    width: 500px;
    max-height: 90vh;
    overflow: auto;
    box-shadow: 0 20px 40px rgba(0,0,0,0.15);
}

.modal-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 20px;
    border-bottom: 1px solid var(--border-default);
}

.close-btn {
    border: none;
    background: none;
    font-size: 24px;
    cursor: pointer;
    color: var(--text-secondary);
}

.modal-body {
    padding: 20px;
}

.modal-footer {
    display: flex;
    justify-content: flex-end;
    gap: 12px;
    padding: 20px;
    border-top: 1px solid var(--border-default);
}
</style>
