﻿<template>
  <div class="ontology-reasoning-manager">
    <div class="header">
      <div class="header-left">
        <el-button @click="goBack" class="back-btn">
          <ArrowLeft />
          返回
        </el-button>
        <h1 class="page-title">本体推理平台</h1>
        <span class="page-subtitle">LLM + 规则引擎 + 知识图谱 融合决策推理</span>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="clearAllResults">
          <Delete />
          清空结果
        </el-button>
      </div>
    </div>

    <div class="main-content">
      <div class="nav-sidebar">
        <div class="nav-section">
          <div class="nav-title">推理能力</div>
          <div class="nav-items">
            <button v-for="item in navItems" :key="item.key" class="nav-item" :class="{ active: activeTab === item.key }" @click="setActiveTab(item.key)">
              <span class="nav-icon">{{ item.icon }}</span>
              <span class="nav-label">{{ item.label }}</span>
            </button>
          </div>
        </div>
        <div class="nav-section">
          <div class="nav-title">常用工具</div>
          <div class="nav-items">
            <button class="nav-item" @click="setActiveTab('audit')"><span class="nav-icon">🔍</span><span class="nav-label">审计追踪</span></button>
            <button class="nav-item" @click="setActiveTab('explain')"><span class="nav-icon">💬</span><span class="nav-label">解释生成</span></button>
            <button class="nav-item" @click="setActiveTab('schema')"><span class="nav-icon">📚</span><span class="nav-label">Schema 浏览</span></button>
          </div>
        </div>
        <div class="nav-section">
          <div class="nav-title">本体管理</div>
          <div class="nav-items">
            <button class="nav-item" :class="{ active: activeTab === 'onto-overview' }" @click="activeTab = 'onto-overview'"><span class="nav-icon">📊</span><span class="nav-label">本体概览</span></button>
            <button class="nav-item" :class="{ active: activeTab === 'onto-classes' }" @click="activeTab = 'onto-classes'"><span class="nav-icon">🏷️</span><span class="nav-label">类结构管理</span></button>
            <button class="nav-item" :class="{ active: activeTab === 'onto-instances' }" @click="activeTab = 'onto-instances'"><span class="nav-icon">🧩</span><span class="nav-label">实例管理</span></button>
            <button class="nav-item" :class="{ active: activeTab === 'onto-sparql' }" @click="activeTab = 'onto-sparql'"><span class="nav-icon">🔎</span><span class="nav-label">SPARQL 查询</span></button>
          </div>
        </div>
      </div>

      <div class="content-area">
        <div v-if="activeTab === 'facts'" class="tab-content"><div class="tab-header"><h2>事实检索</h2><span class="tab-desc">从本体查询实体画像，支持同时查询多个实体</span></div><el-card class="form-card"><el-form :model="factsForm" label-width="100px"><el-form-item label="实体列表"><div class="entity-list"><div v-for="(entity, index) in factsForm.entities" :key="index" class="entity-row"><el-input v-model="entity.id" placeholder="实体ID" style="width: 160px;" /><el-select v-model="entity.type" placeholder="实体类型" style="width: 140px;"><el-option label="Customer" value="Customer" /><el-option label="Account" value="Account" /><el-option label="Invoice" value="Invoice" /><el-option label="Payment" value="Payment" /><el-option label="Order" value="Order" /><el-option label="Product" value="Product" /></el-select><el-select v-model="entity.source" placeholder="数据来源" style="width: 120px;"><el-option label="本体库" value="ontology" /><el-option label="CRM" value="crm" /><el-option label="CMDB" value="cmdb" /></el-select><el-button type="danger" size="small" @click="removeEntity(index)" v-if="factsForm.entities.length > 1"><Minus /></el-button></div></div><el-button type="primary" size="small" @click="addEntity" style="margin-top: 8px;"><Plus /> 添加实体</el-button></el-form-item><el-form-item label="查询范围"><el-select v-model="factsForm.intent.scope" style="width: 200px;"><el-option label="用户画像" value="profile" /><el-option label="账单画像" value="billing_profile" /><el-option label="全部属性" value="full" /></el-select></el-form-item><el-form-item><el-button type="primary" @click="handleRetrieveFacts" :loading="loading"><Search /> 检索事实</el-button></el-form-item></el-form></el-card><div v-if="factsResult" class="result-card"><div class="result-header"><span class="result-title">检索结果</span><span class="result-meta">快照ID: {{ factsResult.snapshot_id }}</span></div><div class="result-body"><pre class="result-json">{{ JSON.stringify(factsResult.facts_map, null, 2) }}</pre></div></div></div>

        <div v-else-if="activeTab === 'policy'" class="tab-content"><div class="tab-header"><h2>规则评估</h2><span class="tab-desc">执行 Drools 规则校验，返回裁决结果</span></div><el-card class="form-card"><el-form :model="policyForm" label-width="100px"><el-form-item label="策略集"><el-select v-model="policyForm.context.policy_set_id" style="width: 300px;" placeholder="选择策略集"><el-option v-for="ps in policySets" :key="ps.id" :label="ps.name" :value="ps.id" /></el-select></el-form-item><el-form-item label="评估类型"><el-select v-model="policyForm.context.expectation_type" style="width: 200px;"><el-option label="校验用户" value="validation" /><el-option label="校验候选方案" value="candidate_check" /></el-select></el-form-item><el-form-item label="事实数据"><el-input v-model="policyForm.factsJson" type="textarea" :rows="6" placeholder='{"vipLevel": "Gold", "annualSpend": 80000}' /></el-form-item><el-form-item><el-button type="primary" @click="handleEvaluatePolicy" :loading="loading"><Check /> 执行评估</el-button></el-form-item></el-form></el-card><div v-if="policyResult" class="result-card"><div class="result-header"><span class="result-title">评估结果</span><el-tag :type="getVerdictType(policyResult.decision?.verdict)" size="large">{{ getVerdictLabel(policyResult.decision?.verdict) }}</el-tag></div><div class="result-body"><el-descriptions :column="1" border><el-descriptions-item label="置信度">{{ policyResult.decision?.confidence || '-' }}</el-descriptions-item><el-descriptions-item label="裁决理由">{{ policyResult.decision?.reason || '-' }}</el-descriptions-item><el-descriptions-item label="触发规则"><el-tag v-for="rule in policyResult.decision?.triggered_rules" :key="rule" size="small">{{ rule }}</el-tag></el-descriptions-item></el-descriptions></div></div></div>

        <div v-else-if="activeTab === 'swrl'" class="tab-content"><div class="tab-header"><h2>SWRL 推理</h2><span class="tab-desc">执行语义网规则推理，从已知事实推导出新事实</span></div><el-card class="form-card"><el-form :model="swrlForm" label-width="100px"><el-form-item label="推理模式"><el-radio-group v-model="swrlForm.mode"><el-radio label="指定规则">指定规则</el-radio><el-radio label="指定模块">指定模块</el-radio><el-radio label="默认规则">默认规则</el-radio></el-radio-group></el-form-item><el-form-item v-if="swrlForm.mode === '指定规则'" label="规则列表"><div class="rule-list"><div v-for="(rule, index) in swrlForm.ruleRefs" :key="index" class="rule-row"><el-input v-model="rule.rule_id" placeholder="规则ID" style="width: 200px;" /><el-input v-model="rule.module" placeholder="模块（可选）" style="width: 150px;" /><el-button type="danger" size="small" @click="removeRule(index)" v-if="swrlForm.ruleRefs.length > 1"><Minus /></el-button></div></div><el-button type="primary" size="small" @click="addRule" style="margin-top: 8px;"><Plus /> 添加规则</el-button></el-form-item><el-form-item v-if="swrlForm.mode === '指定模块'" label="模块名称"><el-input v-model="swrlForm.rule_module" placeholder="例如: marketing_rules" /></el-form-item><el-form-item label="事实数据"><el-input v-model="swrlForm.factsJson" type="textarea" :rows="4" placeholder='{"vipLevel": "Gold", "annualSpend": 80000}' /></el-form-item><el-form-item><el-button type="primary" @click="handleEvaluateSwrl" :loading="loading"><Connection /> 执行推理</el-button></el-form-item></el-form></el-card><div v-if="swrlResult" class="result-card"><div class="result-header"><span class="result-title">推理结果</span><span class="result-meta">触发规则: {{ swrlResult.fired_rule_ids?.length || 0 }} 条</span></div><div class="result-body"><div v-for="result in swrlResult.results" :key="result.rule_id" class="rule-result-item"><div class="rule-header"><span class="rule-id">{{ result.rule_id }}</span><el-tag :type="result.fired ? 'success' : 'info'" size="small">{{ result.fired ? '已触发' : '未触发' }}</el-tag></div><div v-if="result.conclusions?.length" class="rule-conclusions"><span class="conclusion-label">推导出新事实:</span><pre>{{ JSON.stringify(result.conclusions, null, 2) }}</pre></div></div></div></div></div>

        <div v-else-if="activeTab === 'shacl'" class="tab-content"><div class="tab-header"><h2>SHACL 验证</h2><span class="tab-desc">使用 SHACL 验证数据是否符合预定义的形状约束</span></div><el-card class="form-card"><el-form :model="shaclForm" label-width="100px"><el-form-item label="Shape名称"><el-input v-model="shaclForm.shapes" placeholder="不指定则使用默认" /></el-form-item><el-form-item label="租户ID"><el-input v-model="shaclForm.tenant_id" placeholder="marketing_tenant" /></el-form-item><el-form-item label="验证数据"><el-input v-model="shaclForm.dataJson" type="textarea" :rows="6" placeholder='{"id": "Customer_Li", "vipLevel": "Gold", "email": "test@example.com"}' /></el-form-item><el-form-item><el-button type="primary" @click="handleValidateShacl" :loading="loading"><Check /> 执行验证</el-button></el-form-item></el-form></el-card><div v-if="shaclResult" class="result-card"><div class="result-header"><span class="result-title">验证结果</span><el-tag :type="shaclResult.conforms ? 'success' : 'danger'" size="large">{{ shaclResult.conforms ? '符合约束' : '不符合约束' }}</el-tag></div><div class="result-body"><div v-if="shaclResult.results?.length" class="violation-list"><div v-for="(violation, index) in shaclResult.results" :key="index" class="violation-item"><el-tag :type="getSeverityType(violation.severity)" size="small">{{ violation.severity }}</el-tag><span class="violation-path">{{ violation.path }}</span><span class="violation-message">{{ violation.message }}</span></div></div><div v-else class="empty-result">验证通过，无违规项</div></div></div></div>

        <div v-else-if="activeTab === 'compare'" class="tab-content"><div class="tab-header"><h2>假设推理（内存版）</h2><span class="tab-desc">基于已有的事实快照，在内存中应用补丁做假设推演</span></div><el-card class="form-card"><el-form :model="compareForm" label-width="100px"><el-form-item label="基准快照ID"><el-input v-model="compareForm.base_snapshot_id" placeholder="来自事实检索的快照ID" /></el-form-item><el-form-item label="策略集"><el-select v-model="compareForm.policy_set_id" style="width: 300px;" placeholder="选择策略集"><el-option v-for="ps in policySets" :key="ps.id" :label="ps.name" :value="ps.id" /></el-select></el-form-item><el-form-item label="假设场景"><div class="patch-list"><div v-for="(patch, index) in compareForm.patches" :key="index" class="patch-row"><el-input v-model="patch.description" placeholder="变更描述" style="width: 200px;" /><el-input v-model="patch.changesJson" placeholder='{"recommendedAction": "..."}' style="width: 280px;" /><el-button type="danger" size="small" @click="removePatch(index)" v-if="compareForm.patches.length > 1"><Minus /></el-button></div></div><el-button type="primary" size="small" @click="addPatch" style="margin-top: 8px;"><Plus /> 添加假设场景</el-button></el-form-item><el-form-item><el-button type="primary" @click="handleViewState" :loading="loading"><View /> 执行对比推理</el-button></el-form-item></el-form></el-card><div v-if="compareResult" class="result-card"><div class="result-header"><span class="result-title">对比结果</span><span class="result-meta">共对比 {{ compareResult.comparisons?.length || 0 }} 个场景</span></div><div class="result-body"><div v-for="(comp, index) in compareResult.comparisons" :key="index" class="comparison-item"><div class="comparison-header"><span class="comparison-index">方案 {{ index + 1 }}</span><span class="comparison-desc">{{ comp.patch_description }}</span><el-tag :type="getVerdictType(comp.evaluation?.verdict)" size="small">{{ getVerdictLabel(comp.evaluation?.verdict) }}</el-tag></div><div class="comparison-reason">{{ comp.evaluation?.reason }}</div></div></div></div></div>

        <div v-else-if="activeTab === 'hypothetical'" class="tab-content"><div class="tab-header"><h2>假设推理（本体版）</h2><span class="tab-desc">在本体中创建临时 Named Graph 做深度 what-if 分析</span></div><el-card class="form-card"><el-form :model="hypotheticalForm" label-width="100px"><el-form-item label="实体URI列表"><div class="entity-uri-list"><div v-for="(_, index) in hypotheticalForm.entity_ids" :key="index" class="entity-uri-row"><el-input v-model="hypotheticalForm.entity_ids[index]" placeholder="http://example.org/Customer_Li" /><el-button type="danger" size="small" @click="removeEntityUri(index)" v-if="hypotheticalForm.entity_ids.length > 1"><Minus /></el-button></div></div><el-button type="primary" size="small" @click="addEntityUri" style="margin-top: 8px;"><Plus /> 添加实体</el-button></el-form-item><el-form-item label="策略集"><el-select v-model="hypotheticalForm.policy_set_id" style="width: 300px;" placeholder="选择策略集"><el-option v-for="ps in policySets" :key="ps.id" :label="ps.name" :value="ps.id" /></el-select></el-form-item><el-form-item label="假设三元组"><div class="triple-list"><div v-for="(triple, index) in hypotheticalForm.triples" :key="index" class="triple-row"><el-input v-model="triple.subject" placeholder="主体" style="width: 120px;" /><el-input v-model="triple.predicate" placeholder="属性" style="width: 120px;" /><el-input v-model="triple.object" placeholder="值" style="width: 150px;" /><el-button type="danger" size="small" @click="removeTriple(index)" v-if="hypotheticalForm.triples.length > 1"><Minus /></el-button></div></div><el-button type="primary" size="small" @click="addTriple" style="margin-top: 8px;"><Plus /> 添加三元组</el-button></el-form-item><el-form-item><el-button type="primary" @click="handleHypotheticalEvaluate" :loading="loading"><Connection /> 执行本体假设推理</el-button></el-form-item></el-form></el-card><div v-if="hypotheticalResult" class="result-card"><div class="result-header"><span class="result-title">推理结果</span><el-tag :type="getVerdictType(hypotheticalResult.decision?.verdict)" size="large">{{ getVerdictLabel(hypotheticalResult.decision?.verdict) }}</el-tag></div><div class="result-body"><div class="facts-section"><span class="section-label">合并后事实:</span><pre>{{ JSON.stringify(hypotheticalResult.facts, null, 2) }}</pre></div><div class="decision-section"><span class="section-label">裁决理由:</span><p>{{ hypotheticalResult.decision?.reason }}</p></div></div></div></div>

        <div v-else-if="activeTab === 'nlquery'" class="tab-content"><div class="tab-header"><h2>自然语言查询</h2><span class="tab-desc">NL → SPARQL → NL，将自然语言问题转换为 SPARQL 查询</span></div><el-card class="form-card"><el-form :model="nlQueryForm" label-width="100px"><el-form-item label="问题"><el-input v-model="nlQueryForm.question" placeholder='例如：Customer_Li 的会员等级是什么？' /></el-form-item><el-form-item><el-button type="primary" @click="handleNlQuery" :loading="loading"><ChatDotRound /> 执行查询</el-button></el-form-item></el-form></el-card><div v-if="nlQueryResult" class="result-card"><div class="result-header"><span class="result-title">查询结果</span></div><div class="result-body"><div class="answer-section"><span class="section-label">回答:</span><p class="answer-text">{{ nlQueryResult.answer }}</p></div></div></div></div>

        <div v-else-if="activeTab === 'discover'" class="tab-content"><div class="tab-header"><h2>实体发现</h2><span class="tab-desc">自然语言查询发现目标实体，并获取这些实体的结构化画像</span></div><el-card class="form-card"><el-form :model="discoverForm" label-width="100px"><el-form-item label="问题"><el-input v-model="discoverForm.question" placeholder='例如：找出年消费超过5万的客户' /></el-form-item><el-form-item label="最大返回数"><el-input-number v-model="discoverForm.max_entities" :min="1" :max="20" /></el-form-item><el-form-item><el-button type="primary" @click="handleNlDiscover" :loading="loading"><Search /> 发现实体</el-button></el-form-item></el-form></el-card><div v-if="discoverResult" class="result-card"><div class="result-header"><span class="result-title">发现结果</span><span class="result-meta">发现 {{ discoverResult.entity_ids?.length || 0 }} 个实体</span></div><div class="result-body"><div class="answer-section"><span class="section-label">回答:</span><p class="answer-text">{{ discoverResult.nl_answer }}</p></div><div class="entities-section"><span class="section-label">实体列表:</span><div class="entity-tags"><el-tag v-for="entity in discoverResult.entity_ids" :key="entity" size="small">{{ entity }}</el-tag></div></div><div v-if="discoverResult.facts_flat" class="facts-section"><span class="section-label">实体画像:</span><pre>{{ JSON.stringify(discoverResult.facts_flat, null, 2) }}</pre></div></div></div></div>

        <div v-else-if="activeTab === 'audit'" class="tab-content"><div class="tab-header"><h2>审计追踪</h2><span class="tab-desc">获取完整审计日志，用于问题追溯和合规审计</span></div><el-card class="form-card"><el-form :model="auditForm" label-width="100px"><el-form-item label="追踪ID"><el-input v-model="auditForm.trace_id" placeholder="输入 trace_id" /></el-form-item><el-form-item label="租户ID"><el-input v-model="auditForm.tenant_id" placeholder="marketing_tenant" /></el-form-item><el-form-item><el-button type="primary" @click="handleGetTrace" :loading="loading"><Edit /> 查询日志</el-button></el-form-item></el-form></el-card><div v-if="auditResult" class="result-card"><div class="result-header"><span class="result-title">审计日志</span><span class="result-meta">共 {{ auditResult.total_steps }} 个步骤</span></div><div class="result-body"><el-timeline><el-timeline-item v-for="(step, index) in auditResult.steps" :key="index" :timestamp="formatTimestamp(step.timestamp)" :type="getStepType(step.step)"><div class="timeline-content"><span class="step-type">{{ getStepLabel(step.step) }}</span><pre class="step-details">{{ JSON.stringify(step, null, 2) }}</pre></div></el-timeline-item></el-timeline></div></div></div>

        <div v-else-if="activeTab === 'explain'" class="tab-content"><div class="tab-header"><h2>解释生成</h2><span class="tab-desc">根据审计追踪ID生成面向不同受众的自然语言解释</span></div><el-card class="form-card"><el-form :model="explainForm" label-width="100px"><el-form-item label="追踪ID"><el-input v-model="explainForm.trace_id" placeholder="输入 trace_id" /></el-form-item><el-form-item label="受众类型"><el-select v-model="explainForm.audience" style="width: 200px;"><el-option label="终端用户" value="end_user" /><el-option label="业务人员" value="business" /><el-option label="合规审计" value="audit" /></el-select></el-form-item><el-form-item label="租户ID"><el-input v-model="explainForm.tenant_id" placeholder="marketing_tenant" /></el-form-item><el-form-item><el-button type="primary" @click="handleExplain" :loading="loading"><Document /> 生成解释</el-button></el-form-item></el-form></el-card><div v-if="explainResult" class="result-card"><div class="result-header"><span class="result-title">解释结果</span></div><div class="result-body"><div class="explanation-section"><span class="section-label">自然语言解释:</span><p class="explanation-text">{{ explainResult.natural_language }}</p></div><div class="rules-section"><span class="section-label">引用规则:</span><div class="rule-tags"><el-tag v-for="rule in explainResult.referenced_rules" :key="rule" size="small">{{ rule }}</el-tag></div></div></div></div></div>

        <div v-else-if="activeTab === 'schema'" class="tab-content"><div class="tab-header"><h2>Schema 浏览</h2><span class="tab-desc">浏览本体结构和策略集信息</span></div><el-card class="info-card"><template #header><span class="card-title">本体类</span></template><div v-if="schemaCatalog" class="schema-list"><el-tag v-for="cls in schemaCatalog.classes" :key="cls" size="small" class="schema-tag">{{ cls }}</el-tag></div><div v-else class="empty-schema">点击下方按钮加载 Schema</div></el-card><el-card class="info-card"><template #header><span class="card-title">策略集</span></template><div v-if="policySets.length" class="policy-set-list"><div v-for="ps in policySets" :key="ps.id" class="policy-set-item"><span class="policy-set-id">{{ ps.id }}</span><span class="policy-set-name">{{ ps.name }}</span><span class="policy-set-desc">{{ ps.description }}</span></div></div><div v-else class="empty-schema">点击下方按钮加载策略集</div></el-card><div class="schema-actions"><el-button type="primary" @click="loadSchemaCatalog" :loading="loading"><Refresh /> 加载 Schema</el-button><el-button type="primary" @click="loadPolicySets" :loading="loading"><Refresh /> 加载策略集</el-button></div></div>

        <div v-else-if="activeTab === 'onto-overview'" class="tab-content"><div class="tab-header"><h2>本体概览</h2><span class="tab-desc">本体库统计信息和结构总览</span></div><div class="stats-grid"><el-card class="stat-card"><div class="stat-value">{{ ontoStats.classCount }}</div><div class="stat-label">类 (Class)</div></el-card><el-card class="stat-card"><div class="stat-value">{{ ontoStats.propertyCount }}</div><div class="stat-label">属性 (Property)</div></el-card><el-card class="stat-card"><div class="stat-value">{{ ontoStats.instanceCount }}</div><div class="stat-label">实例 (Instance)</div></el-card></div><div style="display: flex; gap: 20px; margin-top: 20px;"><el-card class="info-card" style="flex: 1;"><template #header><span class="card-title">本体类列表</span></template><div v-if="ontoStats.classes?.length" class="schema-list"><el-tag v-for="cls in ontoStats.classes" :key="cls" size="small" class="schema-tag">{{ cls }}</el-tag></div><div v-else class="empty-schema">暂无类</div></el-card><el-card class="info-card" style="flex: 1;"><template #header><span class="card-title">本体属性列表</span></template><div v-if="ontoStats.properties?.length" class="schema-list"><el-tag v-for="prop in ontoStats.properties" :key="prop" size="small" class="schema-tag">{{ prop }}</el-tag></div><div v-else class="empty-schema">暂无属性</div></el-card></div><div class="schema-actions" style="margin-top: 16px;"><el-button type="primary" @click="loadOntoStats" :loading="loading"><Refresh /> 刷新统计</el-button></div></div>

        <div v-else-if="activeTab === 'onto-classes'" class="tab-content"><div class="tab-header"><h2>类结构管理</h2><span class="tab-desc">管理本体类和属性定义</span></div><div style="display: flex; gap: 20px;"><el-card class="info-card" style="flex: 1;"><template #header><span class="card-title">已有类</span></template><div v-if="ontoStats.classes?.length" class="class-list"><div v-for="cls in ontoStats.classes" :key="cls" class="class-item"><span class="class-icon">📝</span><span class="class-name">{{ cls }}</span></div></div><div v-else class="empty-schema">暂无类</div></el-card><el-card class="info-card" style="flex: 1;"><template #header><span class="card-title">已有属性</span></template><div v-if="ontoStats.properties?.length" class="class-list"><div v-for="prop in ontoStats.properties" :key="prop" class="class-item"><span class="class-icon">🔆</span><span class="class-name">{{ prop }}</span></div></div><div v-else class="empty-schema">暂无属性</div></el-card></div><el-card class="form-card" style="margin-top: 20px;"><template #header><span class="card-title">创建新类</span></template><el-form :model="newClassForm" label-width="80px"><el-form-item label="类名"><el-input v-model="newClassForm.name" placeholder="例如: Order" /></el-form-item><el-form-item><el-button type="primary" @click="handleAddClass" :loading="addingClass"><Plus /> 创建类</el-button></el-form-item></el-form></el-card><el-card class="form-card" style="margin-top: 16px;"><template #header><span class="card-title">创建新属性</span></template><el-form :model="newPropertyForm" label-width="80px"><el-form-item label="属性名"><el-input v-model="newPropertyForm.name" placeholder="例如: orderAmount" /></el-form-item><el-form-item><el-button type="primary" @click="handleAddProperty" :loading="addingProperty"><Plus /> 创建属性</el-button></el-form-item></el-form></el-card></div>

        <div v-else-if="activeTab === 'onto-instances'" class="tab-content"><div class="tab-header"><h2>实例管理</h2><span class="tab-desc">浏览、创建、编辑和删除本体实例</span></div><el-card class="form-card"><template #header><div style="display: flex; justify-content: space-between; align-items: center;"><span class="card-title">实例列表</span><el-button type="primary" size="small" @click="showAddInstanceDialog = true"><Plus /> 新建实例</el-button></div></template><el-table :data="instances" style="width: 100%" v-loading="loadingInstances" max-height="400"><el-table-column prop="uri" label="URI" min-width="200" /><el-table-column prop="entityType" label="类型" width="120" /><el-table-column label="属性" min-width="200"><template #default="{ row }"><el-tag v-for="(val, key) in row" :key="key" size="small" style="margin: 2px" v-if="!['uri','entityId','entityType','source'].includes(key)">{{ key }}={{ val }}</el-tag></template></el-table-column><el-table-column label="操作" width="180" fixed="right"><template #default="{ row }"><el-button size="small" @click="editInstance(row)">编辑</el-button><el-button size="small" type="danger" @click="handleDeleteInstance(row.uri)">删除</el-button></template></el-table-column></el-table></el-card></div>

        <div v-else-if="activeTab === 'onto-sparql'" class="tab-content"><div class="tab-header"><h2>SPARQL 查询控制台</h2><span class="tab-desc">编写和执行 SPARQL 查询语句</span></div><el-card class="form-card"><el-form label-width="80px"><el-form-item label="查询语句"><el-input v-model="sparqlQueryText" type="textarea" :rows="6" placeholder="SELECT ?entity ?vipLevel WHERE { ?entity a &lt;http://example.org/Customer&gt; . ?entity vipLevel ?vipLevel }" style="font-family: monospace;" /></el-form-item><el-form-item><el-button type="primary" @click="handleSparqlQuery" :loading="queryingSparql"><Search /> 执行查询</el-button><el-button @click="sparqlQueryText = ''">清空</el-button></el-form-item></el-form></el-card><div v-if="sparqlResult" class="result-card"><div class="result-header"><span class="result-title">查询结果</span><span class="result-meta">返回 {{ sparqlResult.length }} 行</span></div><div class="result-body"><el-table :data="sparqlResult" style="width: 100%" max-height="400" v-if="sparqlResult.length"><el-table-column v-for="col in sparqlResultColumns" :key="col" :prop="col" :label="col" min-width="150" /></el-table><div v-else class="empty-result">查询结果为空</div></div></div></div>

        <div v-else class="tab-content tab-empty-safe"><div class="empty-state-card"><div class="empty-state-title">请选择一个功能模块</div><div class="empty-state-desc">如果右侧空白，通常是当前选中了未定义的标签。这里已增加默认空态兜底。</div></div></div>
      </div>
    </div>
  </div>

  <el-dialog v-model="showAddInstanceDialog" title="新建实例" width="500px">
    <el-form :model="newInstanceForm" label-width="80px">
      <el-form-item label="URI"><el-input v-model="newInstanceForm.uri" placeholder="http://example.org/Customer_Wang" /></el-form-item>
      <el-form-item label="类型"><el-select v-model="newInstanceForm.type" style="width: 100%;" placeholder="选择类型"><el-option v-for="cls in ontoStats.classes" :key="cls" :label="cls" :value="cls" /></el-select></el-form-item>
      <el-form-item label="属性"><div class="triple-list"><div v-for="(prop, index) in newInstanceForm.props" :key="index" class="triple-row"><el-input v-model="prop.key" placeholder="属性名" style="width: 120px;" /><el-input v-model="prop.value" placeholder="值" style="width: 150px;" /><el-button type="danger" size="small" @click="newInstanceForm.props.splice(index, 1)" v-if="newInstanceForm.props.length > 1"><Minus /></el-button></div></div><el-button type="primary" size="small" @click="newInstanceForm.props.push({ key: '', value: '' })" style="margin-top: 8px;"><Plus /> 添加属性</el-button></el-form-item>
    </el-form>
    <template #footer><el-button @click="showAddInstanceDialog = false">取消</el-button><el-button type="primary" @click="handleAddInstance" :loading="addingInstance">创建</el-button></template>
  </el-dialog>

  <el-dialog v-model="showEditInstanceDialog" title="编辑实例" width="500px">
    <el-form :model="editInstanceForm" label-width="80px">
      <el-form-item label="URI"><el-input v-model="editInstanceForm.uri" disabled /></el-form-item>
      <el-form-item label="属性"><div class="triple-list"><div v-for="(prop, index) in editInstanceForm.props" :key="index" class="triple-row"><el-input v-model="prop.key" placeholder="属性名" style="width: 120px;" /><el-input v-model="prop.value" placeholder="值" style="width: 150px;" /><el-button type="danger" size="small" @click="editInstanceForm.props.splice(index, 1)" v-if="editInstanceForm.props.length > 1"><Minus /></el-button></div></div><el-button type="primary" size="small" @click="editInstanceForm.props.push({ key: '', value: '' })" style="margin-top: 8px;"><Plus /> 添加属性</el-button></el-form-item>
    </el-form>
    <template #footer><el-button @click="showEditInstanceDialog = false">取消</el-button><el-button type="primary" @click="handleUpdateInstance" :loading="updatingInstance">保存</el-button></template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Delete, Plus, Minus, Search, Refresh, Check, Connection, ChatDotRound, Document, View, Edit } from '@element-plus/icons-vue'
import { retrieveFacts, evaluatePolicy, evaluateSwrl, validateShacl, compareState, hypotheticalEvaluate, explain, getTrace, nlQuery, nlDiscoverAndRetrieve, getSchemaCatalog, getPolicySets, getOntologyStats, addOntologyClass, addOntologyProperty, getAllInstances, addInstance, updateInstance, deleteInstance, sparqlQuery } from '../services/ontologyReasoningApi.js'

const emit = defineEmits(['go-back'])
const goBack = () => emit('go-back')
const activeTab = ref('facts')
const setActiveTab = (key) => { activeTab.value = key }
const tabKeys = new Set(['facts', 'policy', 'swrl', 'shacl', 'compare', 'hypothetical', 'nlquery', 'discover', 'audit', 'explain', 'schema', 'onto-overview', 'onto-classes', 'onto-instances', 'onto-sparql'])
const ensureValidTab = () => { if (!tabKeys.has(activeTab.value)) activeTab.value = 'facts' }
watch(activeTab, ensureValidTab, { immediate: true })
const loading = ref(false)
const navItems = [
  { key: 'facts', icon: '📊', label: '事实检索' },
  { key: 'policy', icon: '🛠️', label: '规则评估' },
  { key: 'swrl', icon: '✨', label: 'SWRL推理' },
  { key: 'shacl', icon: '✅', label: 'SHACL验证' },
  { key: 'compare', icon: '📧', label: '假设推理(内存)' },
  { key: 'hypothetical', icon: '🧪', label: '假设推理(本体)' },
  { key: 'nlquery', icon: '💬', label: '自然语言查询' },
  { key: 'discover', icon: '🔎', label: '实体发现' },
]

const factsForm = reactive({ entities: [{ id: 'Customer_Li', type: 'Customer', source: 'ontology' }], intent: { scope: 'profile' }, trace_context: { tenant_id: 'marketing_tenant' } })
const policyForm = reactive({ factsJson: '{"vipLevel": "Gold", "annualSpend": 80000, "creditScore": 750}', context: { policy_set_id: '', expectation_type: 'validation' }, trace_context: { tenant_id: 'marketing_tenant' } })
const swrlForm = reactive({ mode: '指定规则', ruleRefs: [{ rule_id: '', module: '' }], rule_module: '', factsJson: '{"vipLevel": "Gold", "annualSpend": 80000}' })
const shaclForm = reactive({ dataJson: '{"id": "Customer_Li", "vipLevel": "Gold", "annualSpend": 80000, "email": "test@example.com"}', shapes: '', tenant_id: 'marketing_tenant' })
const compareForm = reactive({ base_snapshot_id: '', policy_set_id: '', patches: [{ description: '', changesJson: '{}' }], trace_context: { tenant_id: 'marketing_tenant' } })
const hypotheticalForm = reactive({ entity_ids: ['http://example.org/Customer_Li'], triples: [{ subject: 'Customer_Li', predicate: '', object: '' }], policy_set_id: '', tenant_id: 'marketing_tenant' })
const nlQueryForm = reactive({ question: '' })
const discoverForm = reactive({ question: '', max_entities: 5 })
const auditForm = reactive({ trace_id: '', tenant_id: 'marketing_tenant' })
const explainForm = reactive({ trace_id: '', audience: 'end_user', tenant_id: 'marketing_tenant' })

const factsResult = ref(null), policyResult = ref(null), swrlResult = ref(null), shaclResult = ref(null), compareResult = ref(null), hypotheticalResult = ref(null), nlQueryResult = ref(null), discoverResult = ref(null), auditResult = ref(null), explainResult = ref(null), schemaCatalog = ref(null), policySets = ref([])
const ontoStats = reactive({ classCount: 0, propertyCount: 0, instanceCount: 0, classes: [], properties: [] })
const instances = ref([])
const loadingInstances = ref(false), addingClass = ref(false), addingProperty = ref(false), addingInstance = ref(false), updatingInstance = ref(false), queryingSparql = ref(false)
const showAddInstanceDialog = ref(false), showEditInstanceDialog = ref(false)
const newClassForm = reactive({ name: '' }), newPropertyForm = reactive({ name: '' }), newInstanceForm = reactive({ uri: '', type: '', props: [{ key: '', value: '' }] }), editInstanceForm = reactive({ uri: '', props: [] })
const sparqlQueryText = ref(''), sparqlResult = ref(null)
const sparqlResultColumns = computed(() => !sparqlResult.value?.length ? [] : Object.keys(sparqlResult.value[0]))

const loadOntoStats = async () => { const stats = await getOntologyStats(); ontoStats.classCount = stats.classCount || 0; ontoStats.propertyCount = stats.propertyCount || 0; ontoStats.instanceCount = stats.instanceCount || 0; ontoStats.classes = stats.classes || []; ontoStats.properties = stats.properties || [] }
const loadInstances = async () => { loadingInstances.value = true; try { instances.value = await getAllInstances() } finally { loadingInstances.value = false } }
const handleAddClass = async () => { if (!newClassForm.name.trim()) { ElMessage.warning('请输入类名'); return } addingClass.value = true; try { const res = await addOntologyClass(newClassForm.name.trim()); if (res.success !== false) { ElMessage.success(res.message || '创建成功'); newClassForm.name = ''; await loadOntoStats() } else ElMessage.error(res.message || '创建失败') } catch { ElMessage.error('创建失败') } finally { addingClass.value = false } }
const handleAddProperty = async () => { if (!newPropertyForm.name.trim()) { ElMessage.warning('请输入属性名'); return } addingProperty.value = true; try { const res = await addOntologyProperty(newPropertyForm.name.trim()); if (res.success !== false) { ElMessage.success(res.message || '创建成功'); newPropertyForm.name = ''; await loadOntoStats() } else ElMessage.error(res.message || '创建失败') } catch { ElMessage.error('创建失败') } finally { addingProperty.value = false } }
const handleAddInstance = async () => { if (!newInstanceForm.uri.trim()) { ElMessage.warning('请输入 URI'); return } addingInstance.value = true; try { const facts = {}; newInstanceForm.props.forEach(p => { if (p.key.trim()) facts[p.key.trim()] = p.value }); const res = await addInstance(newInstanceForm.uri.trim(), newInstanceForm.type, facts); if (res.success !== false) { ElMessage.success(res.message || '创建成功'); showAddInstanceDialog.value = false; newInstanceForm.uri = ''; newInstanceForm.type = ''; newInstanceForm.props = [{ key: '', value: '' }]; await loadInstances(); await loadOntoStats() } else ElMessage.error(res.message || '创建失败') } catch { ElMessage.error('创建失败') } finally { addingInstance.value = false } }
const editInstance = (row) => { editInstanceForm.uri = row.uri || ''; const props = []; Object.entries(row).forEach(([key, val]) => { if (!['uri', 'entityId', 'entityType', 'source'].includes(key)) props.push({ key, value: String(val) }) }); editInstanceForm.props = props.length ? props : [{ key: '', value: '' }]; showEditInstanceDialog.value = true }
const handleUpdateInstance = async () => { updatingInstance.value = true; try { const facts = {}; editInstanceForm.props.forEach(p => { if (p.key.trim()) facts[p.key.trim()] = p.value }); const res = await updateInstance(editInstanceForm.uri, facts); if (res.success !== false) { ElMessage.success(res.message || '更新成功'); showEditInstanceDialog.value = false; await loadInstances() } else ElMessage.error(res.message || '更新失败') } catch { ElMessage.error('更新失败') } finally { updatingInstance.value = false } }
const handleDeleteInstance = async (uri) => { try { await ElMessageBox.confirm(`确定删除实例 ${uri} 吗？`, '确认删除', { type: 'warning' }); const res = await deleteInstance(uri); if (res.success !== false) { ElMessage.success(res.message || '删除成功'); await loadInstances(); await loadOntoStats() } else ElMessage.error(res.message || '删除失败') } catch { } }
const handleSparqlQuery = async () => { if (!sparqlQueryText.value.trim()) { ElMessage.warning('请输入 SPARQL 查询语句'); return } queryingSparql.value = true; try { const res = await sparqlQuery(sparqlQueryText.value); sparqlResult.value = res.results || []; if (sparqlResult.value.length) ElMessage.success(`查询完成，返回 ${sparqlResult.value.length} 行`); else ElMessage.info('查询结果为空') } catch { ElMessage.error('查询失败') } finally { queryingSparql.value = false } }
const addEntity = () => factsForm.entities.push({ id: '', type: 'Customer', source: 'ontology' })
const removeEntity = (index) => factsForm.entities.splice(index, 1)
const addRule = () => swrlForm.ruleRefs.push({ rule_id: '', module: '' })
const removeRule = (index) => swrlForm.ruleRefs.splice(index, 1)
const addPatch = () => compareForm.patches.push({ description: '', changesJson: '{}' })
const removePatch = (index) => compareForm.patches.splice(index, 1)
const addEntityUri = () => hypotheticalForm.entity_ids.push('')
const removeEntityUri = (index) => hypotheticalForm.entity_ids.splice(index, 1)
const addTriple = () => hypotheticalForm.triples.push({ subject: '', predicate: '', object: '' })
const removeTriple = (index) => hypotheticalForm.triples.splice(index, 1)
const getVerdictType = (verdict) => ({ allow: 'success', deny: 'danger', review: 'warning', rank: 'info' }[verdict] || 'info')
const getVerdictLabel = (verdict) => ({ allow: '通过', deny: '拒绝', review: '需审核', rank: '排名' }[verdict] || verdict || '-')
const getSeverityType = (severity) => ({ violation: 'danger', warning: 'warning', info: 'info' }[severity] || 'info')
const getStepType = (step) => step?.includes('fact') ? 'primary' : step?.includes('policy') ? 'success' : step?.includes('swrl') ? 'warning' : step?.includes('shacl') ? 'info' : 'primary'
const getStepLabel = (step) => ({ 'fact.retrieve': '事实检索', 'policy.evaluate': '规则评估', 'policy.evaluate_with_facts': '合并评估', 'swrl.evaluate': 'SWRL推理', 'shacl.validate': 'SHACL验证' }[step] || step || '-')
const formatTimestamp = (timestamp) => !timestamp ? '-' : new Date(timestamp * 1000).toLocaleString('zh-CN')
const clearAllResults = () => { factsResult.value = null; policyResult.value = null; swrlResult.value = null; shaclResult.value = null; compareResult.value = null; hypotheticalResult.value = null; nlQueryResult.value = null; discoverResult.value = null; auditResult.value = null; explainResult.value = null; ElMessage.success('已清空所有结果') }

const handleRetrieveFacts = async () => { if (!factsForm.entities.some(e => e.id)) { ElMessage.warning('请输入至少一个实体ID'); return } loading.value = true; try { const req = { entities: factsForm.entities.map(e => ({ id: e.id, type: e.type, source: e.source })), intent: factsForm.intent, trace_context: factsForm.trace_context }; const result = await retrieveFacts(req); if (result.success !== false) { factsResult.value = result; ElMessage.success('检索成功') } else ElMessage.error(result.message || '检索失败') } catch { ElMessage.error('检索失败') } finally { loading.value = false } }
const handleEvaluatePolicy = async () => { if (!policyForm.context.policy_set_id) { ElMessage.warning('请选择策略集'); return } loading.value = true; try { let facts = {}; try { facts = JSON.parse(policyForm.factsJson) } catch { ElMessage.error('事实数据格式不正确'); loading.value = false; return } const req = { facts: { root: facts }, context: policyForm.context, trace_context: policyForm.trace_context }; const result = await evaluatePolicy(req); if (result.success !== false) { policyResult.value = result; ElMessage.success('评估完成') } else ElMessage.error(result.message || '评估失败') } catch { ElMessage.error('评估失败') } finally { loading.value = false } }
const handleEvaluateSwrl = async () => { loading.value = true; try { let facts = {}; try { facts = JSON.parse(swrlForm.factsJson) } catch { ElMessage.error('事实数据格式不正确'); loading.value = false; return } const req = { facts: { root: facts }, trace_context: { tenant_id: 'marketing_tenant' } }; if (swrlForm.mode === '指定规则') req.rule_refs = swrlForm.ruleRefs.filter(r => r.rule_id).map(r => ({ rule_id: r.rule_id, module: r.module })); else if (swrlForm.mode === '指定模块') req.rule_module = swrlForm.rule_module; const result = await evaluateSwrl(req); if (result.success !== false) { swrlResult.value = result; ElMessage.success('推理完成') } else ElMessage.error(result.message || '推理失败') } catch { ElMessage.error('推理失败') } finally { loading.value = false } }
const handleValidateShacl = async () => { loading.value = true; try { let data = {}; try { data = JSON.parse(shaclForm.dataJson) } catch { ElMessage.error('验证数据格式不正确'); loading.value = false; return } const req = { data, shapes: shaclForm.shapes || undefined, tenant_id: shaclForm.tenant_id }; const result = await validateShacl(req); if (result.success !== false) { shaclResult.value = result; ElMessage.success('验证完成') } else ElMessage.error(result.message || '验证失败') } catch { ElMessage.error('验证失败') } finally { loading.value = false } }
const handleViewState = async () => { if (!compareForm.base_snapshot_id) { ElMessage.warning('请输入基准快照ID'); return } if (!compareForm.policy_set_id) { ElMessage.warning('请选择策略集'); return } loading.value = true; try { const patches = compareForm.patches.map(p => { let changes = {}; try { changes = JSON.parse(p.changesJson) } catch {} return { target_entity: { id: 'http://example.org/Customer_Li', type: 'Customer' }, changes: { root: changes }, description: p.description } }); const req = { base_snapshot_id: compareForm.base_snapshot_id, patches, policy_set_id: compareForm.policy_set_id, trace_context: compareForm.trace_context }; const result = await compareState(req); if (result.success !== false) { compareResult.value = result; ElMessage.success('对比推理完成') } else ElMessage.error(result.message || '对比推理失败') } catch { ElMessage.error('对比推理失败') } finally { loading.value = false } }
const handleHypotheticalEvaluate = async () => { if (!hypotheticalForm.policy_set_id) { ElMessage.warning('请选择策略集'); return } loading.value = true; try { const req = { entity_ids: hypotheticalForm.entity_ids.filter(e => e), triples: hypotheticalForm.triples.filter(t => t.subject && t.predicate && t.object), policy_set_id: hypotheticalForm.policy_set_id, tenant_id: hypotheticalForm.tenant_id }; const result = await hypotheticalEvaluate(req); if (result.success !== false) { hypotheticalResult.value = result; ElMessage.success('本体假设推理完成') } else ElMessage.error(result.message || '推理失败') } catch { ElMessage.error('推理失败') } finally { loading.value = false } }
const handleNlQuery = async () => { if (!nlQueryForm.question.trim()) { ElMessage.warning('请输入问题'); return } loading.value = true; try { const result = await nlQuery(nlQueryForm.question); if (result.success !== false) { nlQueryResult.value = result; ElMessage.success('查询完成') } else ElMessage.error(result.message || '查询失败') } catch { ElMessage.error('查询失败') } finally { loading.value = false } }
const handleNlDiscover = async () => { if (!discoverForm.question.trim()) { ElMessage.warning('请输入问题'); return } loading.value = true; try { const result = await nlDiscoverAndRetrieve(discoverForm.question, discoverForm.max_entities); if (result.success !== false) { discoverResult.value = result; ElMessage.success('发现完成') } else ElMessage.error(result.message || '发现失败') } catch { ElMessage.error('发现失败') } finally { loading.value = false } }
const handleGetTrace = async () => { if (!auditForm.trace_id) { ElMessage.warning('请输入追踪ID'); return } loading.value = true; try { const result = await getTrace(auditForm.trace_id, auditForm.tenant_id); if (result.success !== false) { auditResult.value = result; ElMessage.success('查询完成') } else ElMessage.error(result.message || '查询失败') } catch { ElMessage.error('查询失败') } finally { loading.value = false } }
const handleExplain = async () => { if (!explainForm.trace_id) { ElMessage.warning('请输入追踪ID'); return } loading.value = true; try { const req = { trace_id: explainForm.trace_id, audience: explainForm.audience, tenant_id: explainForm.tenant_id }; const result = await explain(req); if (result.success !== false) { explainResult.value = result; ElMessage.success('解释生成完成') } else ElMessage.error(result.message || '生成失败') } catch { ElMessage.error('生成失败') } finally { loading.value = false } }
const loadSchemaCatalog = async () => { loading.value = true; try { const result = await getSchemaCatalog(); if (result.success !== false) { schemaCatalog.value = result; ElMessage.success('Schema加载完成') } else ElMessage.error(result.message || '加载失败') } catch { ElMessage.error('加载失败') } finally { loading.value = false } }
const loadPolicySets = async () => { loading.value = true; try { const result = await getPolicySets(); if (result.success !== false) { policySets.value = result.policy_sets || []; ElMessage.success('策略集加载完成') } else ElMessage.error(result.message || '加载失败') } catch { ElMessage.error('加载失败') } finally { loading.value = false } }
onMounted(() => { loadPolicySets(); loadOntoStats(); loadInstances() })
</script>

<style scoped>
.ontology-reasoning-manager { flex: 1; background: var(--bg-primary); display: flex; flex-direction: column; min-height: 0; }
.header { flex-shrink: 0; display: flex; justify-content: space-between; align-items: center; padding: 16px 24px; background: var(--bg-elevated); box-shadow: var(--shadow-sm); gap: 24px; }
.header-left { display: flex; align-items: center; gap: 16px; }
.back-btn { display: flex; align-items: center; }
.page-title { margin: 0; font-size: 20px; font-weight: 600; }
.page-subtitle { font-size: 14px; color: var(--text-tertiary); }
.main-content { flex: 1; display: flex; flex-direction: row; align-items: stretch; overflow: hidden; min-height: 0; }
.nav-sidebar { flex: 0 0 220px; width: 220px; background: var(--bg-secondary); padding: 16px 0; overflow-y: auto; border-right: 1px solid var(--border-light); box-sizing: border-box; }
.nav-section { margin-bottom: 24px; }
.nav-title { font-size: 12px; font-weight: 600; color: var(--text-tertiary); padding: 8px 16px; text-transform: uppercase; letter-spacing: 0.5px; }
.nav-items { display: flex; flex-direction: column; }
.nav-item { display: flex; align-items: center; gap: 10px; padding: 10px 16px; border: none; background: transparent; color: var(--text-secondary); font-size: 14px; cursor: pointer; transition: all 0.2s; text-align: left; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.nav-item:hover { background: rgba(99, 102, 241, 0.08); color: var(--text-primary); }
.nav-item.active { background: rgba(99, 102, 241, 0.12); color: #6366f1; font-weight: 500; }
.nav-icon { font-size: 16px; }
.content-area { flex: 1; min-width: 0; padding: 20px; overflow: hidden; width: 100%; box-sizing: border-box; display: flex; flex-direction: column; min-height: 0; }
.tab-content { flex: 1; overflow-y: auto; min-height: 0; }
.tab-empty-safe { display: flex; align-items: center; justify-content: center; }
.empty-state-card { width: min(720px, 100%); padding: 28px; background: var(--bg-elevated); border: 1px dashed var(--border-default); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); text-align: center; }
.empty-state-title { font-size: 18px; font-weight: 600; margin-bottom: 8px; }
.empty-state-desc { color: var(--text-tertiary); line-height: 1.7; }
.tab-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 20px; }
.tab-header h2 { margin: 0; font-size: 18px; font-weight: 600; }
.tab-desc { font-size: 13px; color: var(--text-tertiary); }
.form-card { margin-bottom: 20px; }
.entity-list,.rule-list,.patch-list,.entity-uri-list,.triple-list { display: flex; flex-direction: column; gap: 8px; }
.entity-row,.rule-row,.patch-row,.entity-uri-row,.triple-row { display: flex; align-items: center; gap: 8px; }
.result-card { background: var(--bg-elevated); border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); border: 1px solid var(--border-light); padding: 16px; }
.result-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; padding-bottom: 12px; border-bottom: 1px solid var(--border-light); }
.result-title { font-size: 15px; font-weight: 600; }
.result-meta { font-size: 13px; color: var(--text-tertiary); }
.result-body { max-height: 400px; overflow-y: auto; }
.result-json { font-family: var(--font-mono); font-size: 13px; line-height: 1.6; color: var(--text-primary); white-space: pre-wrap; word-break: break-all; }
.rule-result-item { padding: 12px; background: var(--bg-secondary); border-radius: var(--radius-md); margin-bottom: 12px; }
.rule-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.rule-id { font-weight: 600; font-family: var(--font-mono); }
.rule-conclusions { margin-top: 8px; }
.conclusion-label { display: block; font-size: 12px; color: var(--text-tertiary); margin-bottom: 4px; }
.violation-list { display: flex; flex-direction: column; gap: 8px; }
.violation-item { display: flex; align-items: center; gap: 8px; padding: 10px 12px; background: var(--bg-secondary); border-radius: var(--radius-md); }
.violation-path { font-family: var(--font-mono); font-size: 13px; color: var(--text-secondary); }
.violation-message { flex: 1; font-size: 13px; color: var(--text-primary); }
.empty-result { text-align: center; color: var(--text-tertiary); padding: 20px; }
.comparison-item { padding: 12px; background: var(--bg-secondary); border-radius: var(--radius-md); margin-bottom: 12px; }
.comparison-header { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.comparison-index { font-weight: 600; }
.comparison-desc { flex: 1; }
.comparison-reason { font-size: 13px; color: var(--text-secondary); }
.facts-section,.decision-section,.answer-section,.entities-section,.explanation-section,.rules-section { margin-bottom: 16px; }
.section-label { display: block; font-size: 12px; color: var(--text-tertiary); margin-bottom: 8px; }
.answer-text,.explanation-text { font-size: 14px; line-height: 1.6; color: var(--text-primary); }
.entity-tags,.rule-tags { display: flex; flex-wrap: wrap; gap: 8px; }
.timeline-content { margin-top: 8px; }
.step-type { font-weight: 600; margin-right: 8px; }
.step-details { font-family: var(--font-mono); font-size: 12px; line-height: 1.5; color: var(--text-secondary); }
.info-card { margin-bottom: 16px; }
.card-title { font-weight: 600; font-size: 15px; }
.schema-list { display: flex; flex-wrap: wrap; gap: 8px; }
.schema-tag { font-family: var(--font-mono); }
.empty-schema { color: var(--text-tertiary); padding: 20px; text-align: center; }
.policy-set-list { display: flex; flex-direction: column; gap: 12px; }
.policy-set-item { padding: 12px; background: var(--bg-secondary); border-radius: var(--radius-md); }
.policy-set-id { font-family: var(--font-mono); font-weight: 600; display: block; }
.policy-set-name { display: block; font-size: 14px; margin: 4px 0; }
.policy-set-desc { font-size: 12px; color: var(--text-tertiary); }
.schema-actions { display: flex; gap: 12px; }
.stats-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.stat-card { text-align: center; padding: 8px; }
.stat-value { font-size: 36px; font-weight: 700; color: #6366f1; line-height: 1.2; }
.stat-label { font-size: 14px; color: var(--text-tertiary); margin-top: 4px; }
.class-list { display: flex; flex-direction: column; gap: 8px; }
.class-item { display: flex; align-items: center; gap: 10px; padding: 8px 12px; background: var(--bg-secondary); border-radius: var(--radius-md); }
.class-icon { font-size: 16px; }
.class-name { font-family: var(--font-mono); font-size: 14px; }
</style>
