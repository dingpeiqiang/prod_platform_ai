function extractJson(text) {
  if (!text || typeof text !== 'string') {
    return null;
  }

  let cleaned = text.trim();

  const codeBlockMatch = cleaned.match(/```(?:json)?\s*([\s\S]*?)```/i);
  if (codeBlockMatch) {
    cleaned = codeBlockMatch[1].trim();
  }

  cleaned = cleaned.replace(/[“”]/g, '"').replace(/[‘’]/g, "'");

  const start = cleaned.indexOf('{');
  const end = cleaned.lastIndexOf('}') + 1;
  if (start === -1 || end <= start) {
    return null;
  }

  let jsonStr = cleaned.substring(start, end);
  jsonStr = jsonStr.replace(/,\s*([}\]])/g, '$1');

  try {
    return JSON.parse(jsonStr);
  } catch {
    try {
      let inString = false;
      let escapeNext = false;
      const stack = [];
      for (const ch of jsonStr) {
        if (escapeNext) {
          escapeNext = false;
          continue;
        }
        if (ch === '\\' && inString) {
          escapeNext = true;
          continue;
        }
        if (ch === '"' && !escapeNext) {
          inString = !inString;
          continue;
        }
        if (inString) continue;
        if (ch === '{' || ch === '[') {
          stack.push(ch);
        } else if (ch === '}' && stack.length && stack[stack.length - 1] === '{') {
          stack.pop();
        } else if (ch === ']' && stack.length && stack[stack.length - 1] === '[') {
          stack.pop();
        }
      }
      if (inString) {
        jsonStr += '"';
      }
      while (stack.length) {
        const opener = stack.pop();
        jsonStr = jsonStr.replace(/,\s*$/, '');
        jsonStr += opener === '{' ? '}' : ']';
      }
      return JSON.parse(jsonStr);
    } catch {
      return null;
    }
  }
}

export class ExecutionEngine {
  constructor() {
    this.logs = [];
    this.isRunning = false;
    this.workflowId = null;
    this.nodeStatus = {};
    this.nodeExecutionData = {};  // 新增：存储每个节点的执行详情
    this.onStatusChange = null;
    this.onLog = null;
    this.onNodeDataChange = null;  // 新增：节点数据变化回调，用于流式更新UI
    this.isPaused = false;
    this.pendingInput = null;
    this.resumeCallback = null;
    this.pendingForm = null;
    this.cancelCallback = null;
  }

  // 初始化节点执行数据记录
  initNodeExecution(nodeId, nodeType, nodeLabel) {
    this.nodeExecutionData[nodeId] = {
      nodeId,
      nodeType,
      nodeLabel: nodeLabel || nodeType,
      status: 'running',
      startTime: Date.now(),
      endTime: null,
      duration: null,
      input: null,      // 输入数据
      config: null,     // 节点配置
      output: null,     // 输出数据
      logs: [],         // 执行日志
      error: null
    };
  }

  // 更新节点执行数据
  updateNodeData(nodeId, data) {
    if (!this.nodeExecutionData[nodeId]) return;
    Object.assign(this.nodeExecutionData[nodeId], data);
    // 触发节点数据变化回调，实现流式更新
    if (this.onNodeDataChange) {
      this.onNodeDataChange(this.getNodeExecutionData());
    }
  }

  // 添加节点日志
  addNodeLog(nodeId, logEntry) {
    if (!this.nodeExecutionData[nodeId]) return;
    this.nodeExecutionData[nodeId].logs.push({
      ...logEntry,
      timestamp: logEntry.timestamp || Date.now()
    });
    // 触发节点数据变化回调，实现流式更新
    if (this.onNodeDataChange) {
      this.onNodeDataChange(this.getNodeExecutionData());
    }
  }

  // 完成节点执行
  completeNodeExecution(nodeId, status = 'completed') {
    if (!this.nodeExecutionData[nodeId]) return;
    const nodeData = this.nodeExecutionData[nodeId];
    nodeData.status = status;
    nodeData.endTime = Date.now();
    nodeData.duration = nodeData.endTime - nodeData.startTime;
  }

  // 获取所有节点的执行数据（用于ExecutionPanel）
  getNodeExecutionData() {
    return Object.values(this.nodeExecutionData);
  }

  setCallbacks(onStatusChange, onLog, onNodeDataChange = null) {
    this.onStatusChange = onStatusChange;
    this.onLog = onLog;
    this.onNodeDataChange = onNodeDataChange;
  }

  setNodeStatus(nodeId, status) {
    this.nodeStatus[nodeId] = status;
    if (this.onStatusChange) {
      this.onStatusChange({ ...this.nodeStatus });
    }
  }

  clearNodeStatus() {
    this.nodeStatus = {};
    if (this.onStatusChange) {
      this.onStatusChange({});
    }
  }

  clearNodeExecutionData() {
    this.nodeExecutionData = {};
  }

  addLog(type, message, detail, data) {
    const timeStr = new Date().toLocaleTimeString('zh-CN');
    const log = { type, message, detail, data, time: timeStr };
    this.logs.push(log);
    if (this.onLog) {
      this.onLog(log);
    }
  }

  async execute(elements, inputParams = {}, workflowId = null) {
    console.log('[WORKFLOW DEBUG] ==================== 工作流执行开始 ====================');
    console.log('[WORKFLOW DEBUG] 输入参数:', JSON.stringify(inputParams, null, 2));
    console.log('[WORKFLOW DEBUG] 所有元素数量:', elements.length);
    
    this.workflowId = workflowId || `wf_${Date.now()}`;
    console.log('[WORKFLOW DEBUG] 工作流ID:', this.workflowId);
    
    if (this.isRunning) {
      console.log('[WORKFLOW DEBUG] 工作流正在运行中，跳过执行');
      return;
    }

    this.isRunning = true;
    this.isPaused = false;
    this.pendingInput = null;
    this.resumeCallback = null;
    this.pendingForm = null;
    this.logs = [];
    this.clearNodeStatus();
    this.clearNodeExecutionData();
    
    if (this.onLog) {
      this.onLog({ type: 'clear' });
    }

    try {
      const nodes = elements.filter(el => !el.source && !el.target);
      const edges = elements.filter(el => el.source && el.target);
      
      console.log('[WORKFLOW DEBUG] 节点数量:', nodes.length);
      console.log('[WORKFLOW DEBUG] 边数量:', edges.length);
      console.log('[WORKFLOW DEBUG] 所有节点:', JSON.stringify(nodes.map(n => ({ id: n.id, type: n.type, data: n.data })), null, 2));
      console.log('[WORKFLOW DEBUG] 所有边:', JSON.stringify(edges, null, 2));

      const startNode = nodes.find(n => n.type === 'start');
      if (!startNode) {
        console.log('[WORKFLOW ERROR] 未找到开始节点');
        throw new Error('未找到开始节点');
      }
      console.log('[WORKFLOW DEBUG] 开始节点:', startNode.id);

      const context = {
        input: '',
        variables: {},
        params: inputParams || {}  // 添加传入参数到上下文
      };

      // 记录输入参数
      if (Object.keys(inputParams).length > 0) {
        this.addLog('info', '接收到执行参数', null, { params: inputParams });
      }

      this.addLog('start', '开始执行工作流', null, null);
      console.log('[WORKFLOW DEBUG] 开始执行节点:', startNode.id);
      await this.executeNode(startNode.id, nodes, edges, context);

      this.addLog('success', '工作流执行完成', null, {
        context,
        timestamp: new Date().toISOString()
      });

      return {
        status: 'success',
        context,
        timestamp: new Date().toISOString()
      };
    } catch (error) {
      this.addLog('error', '工作流执行失败', error.message, null);
      return {
        status: 'error',
        error: error.message,
        timestamp: new Date().toISOString()
      };
    } finally {
      this.isRunning = false;
    }
  }

  async executeNode(nodeId, nodes, edges, context) {
    console.log('[NODE DEBUG] ---------- 开始执行节点 ----------');
    console.log('[NODE DEBUG] 节点ID:', nodeId);
    
    const node = nodes.find(n => n.id === nodeId);
    if (!node) {
      console.log('[NODE ERROR] 未找到节点:', nodeId);
      return;
    }
    
    console.log('[NODE DEBUG] 节点类型:', node.type);
    console.log('[NODE DEBUG] 节点数据:', JSON.stringify(node.data, null, 2));

    const nodeLabel = node.data.label || node.type;
    this.setNodeStatus(nodeId, 'running');
    
    // 初始化节点执行数据记录
    this.initNodeExecution(nodeId, node.type, nodeLabel);
    
    // 记录节点开始执行日志
    this.addLog('node_start', `开始执行: ${nodeLabel}`, `节点ID: ${node.id}`, { 
      nodeId, nodeType: node.type, nodeLabel 
    });
    this.addNodeLog(nodeId, { type: 'info', message: '节点开始执行' });

    await this.delay(300);

    try {
      switch (node.type) {
        case 'start': {
          // 将传入的参数设置到变量中，方便后续节点使用
          const inputParams = { ...context.params };
          this.updateNodeData(nodeId, { 
            input: inputParams,
            config: node.data.parameters || [],
            output: Object.keys(inputParams).length > 0 ? inputParams : null
          });
          
          if (Object.keys(inputParams).length > 0) {
            Object.assign(context.variables, inputParams);
            this.addNodeLog(nodeId, { type: 'info', message: `初始化参数: ${JSON.stringify(inputParams)}` });
          }
          this.addLog('info', '初始化参数到变量', null, inputParams);
          break;
        }

        case 'prompt': {
          const promptTemplate = node.data.prompt || '请输入内容';
          const resolvedPrompt = promptTemplate.replace(/\{\{(\w+)\}\}/g, (_, key) => context.variables[key] || '');
          
          this.updateNodeData(nodeId, {
            input: { template: promptTemplate, variables: context.variables },
            config: { template: promptTemplate },
            output: resolvedPrompt
          });
          this.addNodeLog(nodeId, { type: 'info', message: `解析变量后的提示词: ${resolvedPrompt}` });
          
          context.input = resolvedPrompt;
          this.addLog('info', '构建提示词', promptTemplate, { resolved: resolvedPrompt });
          break;
        }

        case 'llm': {
          const model = node.data.model || 'qwen-vl-plus';
          const temperature = node.data.temperature || 0.7;
          const systemPrompt = node.data.systemPrompt || '';
          const nodePrompt = node.data.prompt || '';
          
          let resolvedPrompt = context.input || '';
          
          if (nodePrompt) {
            let renderedPrompt = nodePrompt.replace(/\{\{(\w+)\}\}/g, (_, key) => context.variables[key] || '');
            if (resolvedPrompt && resolvedPrompt.trim()) {
              resolvedPrompt = renderedPrompt.includes("{input}") ? renderedPrompt.replace("{input}", resolvedPrompt) : renderedPrompt + "\n" + resolvedPrompt;
            } else {
              resolvedPrompt = renderedPrompt;
            }
          }
          
          this.updateNodeData(nodeId, {
            input: { prompt: resolvedPrompt, systemPrompt, model, temperature },
            config: { model, temperature, maxTokens: node.data.maxTokens, systemPrompt, prompt: nodePrompt },
            output: null  // 待填充
          });
          this.addNodeLog(nodeId, { type: 'info', message: `调用模型: ${model}, 温度: ${temperature}` });
          this.addNodeLog(nodeId, { type: 'debug', message: `输入: ${resolvedPrompt}` });
          
          this.addLog('info', `调用 LLM 模型`, `模型: ${model}, 温度: ${temperature}`, { input: resolvedPrompt });

          try {
            const response = await fetch('/api/v1/chat/completion', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                model: model,
                prompt: resolvedPrompt,
                system_prompt: systemPrompt,
                temperature: parseFloat(temperature) || 0.7,
                max_tokens: Math.round(parseFloat(node.data.maxTokens) || 1024),
                top_k: Math.round(parseFloat(node.data.topK) || 0),
                top_p: parseFloat(node.data.topP) || 1.0
              })
            });

            if (response.ok) {
              const data = await response.json();
              if (!data.result && !data.content) {
                throw new Error('LLM 响应为空');
              }
              context.output = data.result || data.content;
            } else {
              const errorData = await response.json();
              throw new Error(errorData.message || 'LLM 调用失败');
            }
          } catch (error) {
            this.addNodeLog(nodeId, { type: 'error', message: error.message });
            this.addLog('error', 'LLM 调用失败', error.message, null);
            throw error;
          }
          
          let finalOutput = context.output;
          
          const outputs = node.data.outputParams || [];
          const needJsonParse = outputs.some(param => param && param.type === 'json');
          
          if (needJsonParse && context.output && typeof context.output === 'string') {
            const parsedJson = extractJson(context.output);
            if (parsedJson) {
              finalOutput = parsedJson;
              this.addNodeLog(nodeId, { type: 'info', message: '已解析 JSON 输出' });
            }
          }
          
          context.output = finalOutput;
          context.variables['llmOutput'] = finalOutput;
          
          if (node.data.outputVar) {
            context.variables[node.data.outputVar] = finalOutput;
          }
          
          this.updateNodeData(nodeId, { output: finalOutput });
          this.addNodeLog(nodeId, { type: 'info', message: 'LLM 响应完成' });
          this.addLog('info', 'LLM 响应完成', null, { output: finalOutput });
          break;
        }

        case 'userInput': {
          const prompt = node.data.prompt || '请输入：';
          const inputType = node.data.inputType || 'text';
          const options = node.data.options ? node.data.options.split('\n').filter(opt => opt.trim()) : [];
          const required = node.data.required ?? true;
          const outputVar = node.data.outputVar || 'user_input';

          this.updateNodeData(nodeId, {
            input: { prompt, inputType, required },
            config: { prompt, inputType, options, required, outputVar }
          });
          this.addNodeLog(nodeId, { type: 'info', message: `等待用户输入: ${prompt}` });

          this.addLog('info', '用户输入节点', `提示: ${prompt}, 类型: ${inputType}`, { prompt, inputType, options, required });

          this.isPaused = true;
          this.pendingInput = {
            nodeId: nodeId,
            prompt: prompt,
            inputType: inputType,
            options: options,
            required: required,
            outputVar: outputVar
          };

          this.addLog('info', '工作流暂停等待用户输入', null, this.pendingInput);

          const userInputPromise = new Promise((resolve) => {
            this.resumeCallback = (userInputValue) => {
              context.variables[outputVar] = userInputValue;
              context.output = userInputValue;
              this.updateNodeData(nodeId, { output: userInputValue });
              this.addNodeLog(nodeId, { type: 'info', message: `用户输入: ${userInputValue}` });
              
              this.isPaused = false;
              this.pendingInput = null;
              this.resumeCallback = null;
              this.addLog('info', '收到用户输入', null, { [outputVar]: userInputValue });
              resolve();
            };
          });

          await userInputPromise;

          const userInputNextEdges = edges.filter(e => e.source === nodeId && (!e.sourceHandle || e.sourceHandle === 'source'));
          for (const edge of userInputNextEdges) {
            await this.executeNode(edge.target, nodes, edges, context);
          }
          this.setNodeStatus(nodeId, 'completed');
          this.completeNodeExecution(nodeId, 'completed');
          return;
        }

        case 'tool': {
          const toolType = node.data.toolType || '未知';
          this.updateNodeData(nodeId, {
            input: context.variables,
            config: { toolType, toolName: node.data.toolName },
            output: '模拟工具执行结果'
          });
          context.variables['toolResult'] = '模拟工具执行结果';
          this.addNodeLog(nodeId, { type: 'info', message: `工具类型: ${toolType}` });
          this.addLog('info', '工具调用', `工具类型: ${toolType}`, null);
          break;
        }

        case 'condition': {
          console.log('[DEBUG] ==================== 条件节点执行开始 ====================');
          console.log('[DEBUG] 节点ID:', nodeId);
          console.log('[DEBUG] 节点数据:', JSON.stringify(node.data, null, 2));
          console.log('[DEBUG] 当前上下文变量:', JSON.stringify(context.variables, null, 2));
          
          let result = false;
          let conditionStr = 'false';
          
          let branch = null;
          let branchIndex = 0;
          const branchLogs = [];
          
          if (node.data.branches && node.data.branches.length > 0) {
            const branches = node.data.branches;
            console.log('[DEBUG] 分支数据:', JSON.stringify(branches, null, 2));
            
            for (let i = 0; i < branches.length; i++) {
              console.log('[DEBUG] ---------- 评估分支 ' + i + ' ----------');
              branch = branches[i];
              branchIndex = i;
              
              console.log('[DEBUG] 分支类型:', branch.type);
              console.log('[DEBUG] 分支handle:', branch.handle);
              console.log('[DEBUG] 分支条件:', JSON.stringify(branch.conditions, null, 2));
              
              let currentConditionStr = '';
              let currentResult = false;
              
              if (branch.type === 'else') {
                console.log('[DEBUG] 检测到 ELSE 分支，直接匹配');
                currentConditionStr = 'else';
                currentResult = true;
                result = true;
                conditionStr = 'else';
              } else if (branch.conditions && branch.conditions.length > 0) {
                const conditions = branch.conditions;
                let branchResult = true;
                
                console.log('[DEBUG] 开始评估条件，共 ' + conditions.length + ' 个条件');
                
                for (let j = 0; j < conditions.length; j++) {
                  const cond = conditions[j];
                  console.log('[DEBUG] 条件 ' + j + ':');
                  console.log('[DEBUG]   变量名:', cond.variable);
                  console.log('[DEBUG]   操作符:', cond.operator);
                  console.log('[DEBUG]   值类型:', cond.valueType);
                  console.log('[DEBUG]   比较值:', cond.value);
                  
                  if (!cond.variable || !cond.operator) {
                    console.log('[DEBUG]   跳过：变量或操作符为空');
                    continue;
                  }
                  
                  const varValue = context.variables[cond.variable];
                  console.log('[DEBUG]   变量实际值:', varValue, '(类型:', typeof varValue + ')');
                  
                  let compareValue = cond.value;
                  if (cond.valueType === 'reference' && cond.value) {
                    compareValue = context.variables[cond.value] || cond.value;
                    console.log('[DEBUG]   引用变量解析后的值:', compareValue);
                  }
                  
                  console.log('[DEBUG]   执行比较:', varValue, cond.operator, compareValue);
                  branchResult = this.evaluateCondition(varValue, cond.operator, compareValue);
                  console.log('[DEBUG]   比较结果:', branchResult);
                  
                  if (!branchResult) {
                    console.log('[DEBUG]   条件不满足，跳出循环');
                    break;
                  }
                }
                
                currentResult = branchResult;
                currentConditionStr = this.formatBranchConditions(branch);
                
                if (branchResult) {
                  console.log('[DEBUG] 分支条件全部满足，设置结果为 true');
                  result = true;
                  conditionStr = currentConditionStr;
                }
              } else {
                console.log('[DEBUG] 分支没有条件，跳过');
              }
              
              let branchLabel = '';
              if (branch?.type === 'else') {
                branchLabel = `分支 ${i + 1}: 否则`;
              } else if (i === 0) {
                branchLabel = `分支 ${i + 1}: 如果`;
              } else {
                branchLabel = `分支 ${i + 1}: 否则如果`;
              }
              
              const logEntry = { type: currentResult ? 'result' : 'info', message: `${branchLabel}: ${currentConditionStr} = ${currentResult}` };
              branchLogs.push(logEntry);
              console.log('[DEBUG] 添加日志:', logEntry);
              console.log('[DEBUG] 当前 result 值:', result);
              
              if (result) {
                console.log('[DEBUG] 找到匹配分支，跳出循环');
                break;
              }
            }
          } else {
            console.log('[DEBUG] 使用旧格式 condition 字段');
            conditionStr = node.data.condition || 'true';
            console.log('[DEBUG] 条件表达式:', conditionStr);
            try {
              const func = new Function('context', `return ${conditionStr}`);
              result = func(context);
              console.log('[DEBUG] 旧格式执行结果:', result);
            } catch (e) {
              console.log('[DEBUG] 旧格式执行出错:', e.message);
              result = true;
            }
          }

          let branchLabel = '';
          if (branch?.type === 'else') {
            branchLabel = `分支 ${branchIndex + 1}: 否则`;
          } else if (branchIndex === 0) {
            branchLabel = `分支 ${branchIndex + 1}: 如果`;
          } else {
            branchLabel = `分支 ${branchIndex + 1}: 否则如果`;
          }
          
          const resultMsg = `${branchLabel}: ${conditionStr} = ${result}`;
          console.log('[DEBUG] 最终结果消息:', resultMsg);
          
          this.updateNodeData(nodeId, {
            input: { condition: conditionStr, context: { ...context.variables } },
            config: { branches: node.data.branches, condition: node.data.condition },
            output: result,
            logs: branchLogs.length > 0 ? branchLogs : [{ type: 'result', message: resultMsg }]
          });
          this.addLog('info', '条件判断', resultMsg, { result, branchType: branch?.type });

          const currentBranch = branch;
          
          // 如果没有任何分支匹配，记录错误并返回
          if (!result) {
            console.log('[ERROR] 没有找到匹配的分支条件');
            this.addLog('error', '条件分支执行失败', '没有找到匹配的分支条件', { branches: node.data.branches });
            this.addNodeLog(nodeId, { type: 'error', message: '没有找到匹配的分支条件' });
            this.setNodeStatus(nodeId, 'completed');
            console.log('[DEBUG] ==================== 条件节点执行结束（无匹配分支） ====================');
            return;
          }
          
          // 使用分支的 handle 属性作为唯一标识符
          // handle 是在分支创建时生成的唯一 ID，不会因分支重排序而变化
          const branchHandle = currentBranch?.handle;
          if (!branchHandle) {
            console.log('[ERROR] 分支没有 handle 属性:', currentBranch);
            this.addLog('error', '条件分支执行失败', '分支配置不完整，缺少 handle 属性', { branch: currentBranch });
            this.addNodeLog(nodeId, { type: 'error', message: '分支配置不完整，缺少 handle 属性' });
            this.setNodeStatus(nodeId, 'completed');
            console.log('[DEBUG] ==================== 条件节点执行结束（分支配置错误） ====================');
            return;
          }
          console.log('[DEBUG] 匹配分支的 handle:', branchHandle);
          
          // 输出所有从当前节点出发的边
          const allOutgoingEdges = edges.filter(e => e.source === nodeId);
          console.log('[DEBUG] 从当前节点出发的所有边:', JSON.stringify(allOutgoingEdges, null, 2));
          
          const resultEdges = edges.filter(e => e.source === nodeId && e.sourceHandle === branchHandle);
          console.log('[DEBUG] 匹配的边数量:', resultEdges.length);
          console.log('[DEBUG] 匹配的边:', JSON.stringify(resultEdges, null, 2));
          
          if (resultEdges.length === 0) {
            console.log('[ERROR] 未找到 handle 为 "' + branchHandle + '" 的输出边');
            this.addLog('error', '条件分支执行失败', `未找到 handle 为 "${branchHandle}" 的输出边`, { branchHandle, edges: allOutgoingEdges });
            this.addNodeLog(nodeId, { type: 'error', message: `未找到 handle 为 "${branchHandle}" 的输出边` });
            this.setNodeStatus(nodeId, 'completed');
            console.log('[DEBUG] ==================== 条件节点执行结束（无输出边） ====================');
            return;
          }
          
          console.log('[DEBUG] 开始执行后续节点');
          for (const edge of resultEdges) {
            console.log('[DEBUG] 执行节点:', edge.target);
            await this.executeNode(edge.target, nodes, edges, context);
          }
          this.setNodeStatus(nodeId, 'completed');
          console.log('[DEBUG] ==================== 条件节点执行结束（成功） ====================');
          return;
        }

        case 'loop': {
          const loopType = node.data.loopType || 'for';
          const loopCount = parseInt(node.data.loopCount) || 3;
          
          this.updateNodeData(nodeId, {
            input: { loopType, loopCount },
            config: { loopType, loopCount },
            output: null
          });
          this.addNodeLog(nodeId, { type: 'info', message: `${loopType} 循环，次数: ${loopCount}` });
          this.addLog('info', `循环开始`, `${loopType} 循环，次数: ${loopCount}`, null);

          for (let i = 0; i < loopCount; i++) {
            context.variables['loopIndex'] = i;
            context.variables['loopCount'] = loopCount;
            this.addNodeLog(nodeId, { type: 'info', message: `迭代 ${i + 1}/${loopCount}` });
            this.addLog('info', `循环迭代 ${i + 1}/${loopCount}`, null, { loopIndex: i, loopCount });

            const bodyEdges = edges.filter(e => e.source === nodeId && e.sourceHandle === 'body');
            for (const edge of bodyEdges) {
              await this.executeNode(edge.target, nodes, edges, { ...context });
            }
          }

          const endEdges = edges.filter(e => e.source === nodeId && e.sourceHandle === 'end');
          for (const edge of endEdges) {
            await this.executeNode(edge.target, nodes, edges, context);
          }
          this.setNodeStatus(nodeId, 'completed');
          return;
        }

        case 'variable': {
          const varName = node.data.varName || node.data.variableName || 'result';
          let rawValue = node.data.varValue || node.data.variableValue || context.output;
          
          let varValue = rawValue;
          if (rawValue && typeof rawValue === 'string') {
            varValue = this.evaluateExpression(rawValue, context.variables);
          }
          
          this.updateNodeData(nodeId, {
            input: { varName, varValue: varValue },
            config: { varName, varValue: node.data.varValue || node.data.variableValue },
            output: { [varName]: varValue }
          });
          context.variables[varName] = varValue;
          this.addNodeLog(nodeId, { type: 'info', message: `赋值 ${varName} = ${varValue}` });
          this.addLog('info', '变量赋值', `${varName} = ${varValue}`, context.variables);
          break;
        }

        case 'form': {
          const ontologyCode = node.data.ontologyCode || '';
          const toolName = node.data.toolType || node.data.toolName || '';
          const enableValidation = node.data.enableValidation || false;
          const model = node.data.model || '';
          const temperature = node.data.temperature || 0.3;
          const validationPrompt = node.data.validationPrompt || '';
          const inputVariable = node.data.inputVariable || '';
          
          this.updateNodeData(nodeId, {
            input: { ...context.variables },
            config: {
              ontologyCode,
              toolName,
              enableValidation,
              model,
              temperature,
              validationPrompt,
              inputVariable
            },
            output: null
          });
          
          this.addNodeLog(nodeId, { type: 'info', message: `表单节点配置: 本体=${ontologyCode}, 工具=${toolName || '未配置'}` });
          this.addLog('info', '表单节点', `本体: ${ontologyCode}, 工具: ${toolName || '未配置'}`, { ontologyCode, toolName, enableValidation, model });
          
          try {
            const response = await fetch('/api/workflows/execute-form-node', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                ontology_code: ontologyCode,
                tool_name: toolName,
                enable_validation: enableValidation,
                model: model,
                temperature: temperature,
                validation_prompt: validationPrompt,
                input_variable: inputVariable,
                input_data: context.variables
              })
            });
            
            if (!response.ok) {
              const errorData = await response.json();
              throw new Error(errorData.message || '表单节点执行失败');
            }
            
            const data = await response.json();
            
            if (data.success === false) {
              const errorMessage = data.message || data.error || '表单节点执行失败';
              this.addNodeLog(nodeId, { type: 'error', message: errorMessage });
              this.addLog('error', '表单节点执行失败', errorMessage, data);
              throw new Error(errorMessage);
            }
            
            context.variables['formResult'] = data;
            
            if (data.form_schema) {
              context.variables['formSchema'] = data.form_schema;
            }
            if (data.form_data) {
              context.variables['formData'] = data.form_data;
            }
            
            this.updateNodeData(nodeId, { output: data });
            this.addNodeLog(nodeId, { type: 'info', message: '获取表单 Schema 成功，准备暂停等待用户提交' });
            this.addLog('info', '表单节点暂停', '等待用户提交表单数据', data);
            
            this.isPaused = true;
            this.pendingForm = {
              ...(data.form_schema || {}),
              nodeId: nodeId,
              ontologyCode: ontologyCode,
              toolName: toolName,
              enableValidation: enableValidation,
              model: model,
              temperature: temperature,
              validationPrompt: validationPrompt,
              inputVariable: inputVariable,
              contextVariables: { ...context.variables },
              formData: data.form_data || {}
            };
            
            console.log('[executionEngine] pendingForm 已设置:', this.pendingForm);
            
            const formSubmitPromise = new Promise((resolve, reject) => {
              this.resumeCallback = async (submittedFormData) => {
                try {
                  if (submittedFormData === null || submittedFormData === undefined) {
                    throw new Error('用户取消了表单提交');
                  }
                  
                  this.addNodeLog(nodeId, { type: 'info', message: '收到用户提交的表单数据，正在处理...' });
                  this.addLog('info', '收到表单提交数据', null, submittedFormData);
                  
                  const submitResponse = await fetch('/api/workflows/resume', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                      workflow_id: this.workflowId,
                      form_data: submittedFormData,
                      form_code: ontologyCode,
                      form_name: this.pendingForm?.formName || '表单'
                    })
                  });
                  
                  if (!submitResponse.ok) {
                    const errorData = await submitResponse.json();
                    throw new Error(errorData.message || '表单提交失败');
                  }
                  
                  const submitResult = await submitResponse.json();
                  
                  context.output = submitResult;
                  context.variables['formSubmitResult'] = submitResult;
                  
                  if (submitResult.form_validation) {
                    context.variables['formValidation'] = submitResult.form_validation;
                  }
                  if (submitResult.form_data) {
                    context.variables['formData'] = submitResult.form_data;
                  }
                  
                  this.updateNodeData(nodeId, { output: submitResult });
                  this.addNodeLog(nodeId, { type: 'info', message: '表单提交成功' });
                  this.addLog('info', '表单提交成功', null, submitResult);
                  
                  this.isPaused = false;
                  this.pendingForm = null;
                  this.resumeCallback = null;
                  
                  resolve(submitResult);
                } catch (error) {
                  this.isPaused = false;
                  this.pendingForm = null;
                  this.resumeCallback = null;
                  this.addNodeLog(nodeId, { type: 'error', message: error.message });
                  reject(error);
                }
              };
              
              this.cancelCallback = () => {
                this.isPaused = false;
                this.pendingForm = null;
                this.resumeCallback = null;
                this.addNodeLog(nodeId, { type: 'info', message: '表单提交已取消' });
                this.addLog('info', '表单提交已取消', null, null);
              };
            });
            
            await formSubmitPromise;
            
            const formNextEdges = edges.filter(e => e.source === nodeId && (!e.sourceHandle || e.sourceHandle === 'source'));
            for (const edge of formNextEdges) {
              await this.executeNode(edge.target, nodes, edges, context);
            }
            this.setNodeStatus(nodeId, 'completed');
            this.completeNodeExecution(nodeId, 'completed');
            return;
          } catch (error) {
            console.error('表单节点执行失败:', error);
            this.addNodeLog(nodeId, { type: 'error', message: error.message });
            throw error;
          }
        }

        case 'http': {
          const url = node.data.url || '未配置';
          const method = node.data.method || 'GET';
          
          this.updateNodeData(nodeId, {
            input: { url, method, variables: context.variables },
            config: { url, method, headers: node.data.headers },
            output: { status: 200, data: '模拟响应数据' }
          });
          context.variables['httpResult'] = { status: 200, data: '模拟响应数据' };
          this.addNodeLog(nodeId, { type: 'info', message: `HTTP ${method} ${url}` });
          this.addLog('info', 'HTTP 请求', `URL: ${url}`, null);
          break;
        }

        case 'code': {
          const code = node.data.code || '无代码';
          
          this.updateNodeData(nodeId, {
            input: { code, variables: context.variables },
            config: { language: node.data.language || 'javascript' },
            output: '模拟代码执行结果'
          });
          context.variables['codeResult'] = '模拟代码执行结果';
          this.addNodeLog(nodeId, { type: 'info', message: `执行代码 (${node.data.language || 'javascript'})` });
          this.addLog('info', '代码执行', code, null);
          break;
        }

        case 'parser': {
          const inputData = context.output;
          
          this.updateNodeData(nodeId, {
            input: { rawOutput: inputData },
            config: { parseType: node.data.parseType },
            output: inputData ? JSON.stringify({ text: inputData }, null, 2) : null
          });
          context.parsed = inputData ? JSON.stringify({ text: inputData }, null, 2) : null;
          this.addNodeLog(nodeId, { type: 'info', message: '解析输出数据' });
          this.addLog('info', '输出解析', null, { input: inputData });
          break;
        }

        case 'knowledgeBase': {
          const kbId = node.data.knowledgeBase || '';
          const queryMode = node.data.queryMode || 'retrieve';
          const queryText = node.data.queryText || '';
          
          const resolvedQuery = queryText.replace(/\{\{(\w+)\}\}/g, (_, key) => context.variables[key] || '');
          
          this.updateNodeData(nodeId, {
            input: { knowledgeBase: kbId, queryMode, queryText: resolvedQuery },
            config: { knowledgeBase: kbId, queryMode, queryText },
            output: null  // 待填充
          });
          this.addNodeLog(nodeId, { type: 'info', message: `查询知识库: ${kbId}, 模式: ${queryMode}` });
          this.addNodeLog(nodeId, { type: 'debug', message: `查询内容: ${resolvedQuery}` });

          const mockResults = {
            documents: [
              { id: 'doc1', content: '知识库文档内容示例1...', score: 0.95 },
              { id: 'doc2', content: '知识库文档内容示例2...', score: 0.88 }
            ],
            answer: queryMode === 'qa' ? '这是模拟的知识库问答响应。' : null,
            summary: queryMode === 'summarize' ? '这是模拟的文档摘要。' : null
          };

          context.variables['kbResult'] = mockResults;
          
          if (node.data.outputVar) {
            context.variables[node.data.outputVar] = mockResults;
          }
          
          this.updateNodeData(nodeId, { output: mockResults });
          this.addNodeLog(nodeId, { type: 'info', message: `查询完成，返回 ${mockResults.documents?.length || 0} 条结果` });
          this.addLog('info', '知识库查询完成', null, { result: mockResults });
          break;
        }

        case 'end': {
          this.updateNodeData(nodeId, {
            input: context.variables,
            output: '工作流结束'
          });
          this.addNodeLog(nodeId, { type: 'info', message: '工作流执行完毕' });
          this.addLog('info', '到达结束节点', null, null);
          this.setNodeStatus(nodeId, 'completed');
          return;
        }

        default:
          this.updateNodeData(nodeId, { config: { nodeType: node.type } });
          this.addLog('info', `执行节点类型: ${node.type}`, null, null);
      }

      // 查找没有 sourceHandle 或者 sourceHandle 为 "source" 的边（普通节点的输出边）
      // Vue Flow 默认会给边添加 sourceHandle: "source" 属性
      const nextEdges = edges.filter(e => e.source === nodeId && (!e.sourceHandle || e.sourceHandle === 'source'));
      console.log('[NODE DEBUG] 查找后续节点 - 当前节点ID:', nodeId);
      console.log('[NODE DEBUG] 所有从当前节点出发的边:', JSON.stringify(edges.filter(e => e.source === nodeId), null, 2));
      console.log('[NODE DEBUG] 匹配的后续边数量:', nextEdges.length);
      console.log('[NODE DEBUG] 匹配的后续边:', JSON.stringify(nextEdges, null, 2));
      
      if (nextEdges.length === 0 && node.type !== 'end') {
        console.log('[NODE WARNING] 当前节点没有后续节点:', nodeId, '节点类型:', node.type);
      }
      
      for (const edge of nextEdges) {
        console.log('[NODE DEBUG] 执行后续节点:', edge.target);
        await this.executeNode(edge.target, nodes, edges, context);
      }
    } catch (error) {
      this.setNodeStatus(nodeId, 'error');
      this.updateNodeData(nodeId, { status: 'error', error: error.message });
      this.addNodeLog(nodeId, { type: 'error', message: error.message });
      throw error;
    } finally {
      if (node.type !== 'end') {
        const currentStatus = this.nodeStatus[nodeId];
        if (currentStatus === 'running') {
          this.setNodeStatus(nodeId, 'completed');
          this.completeNodeExecution(nodeId, 'completed');
        }
      } else {
        this.completeNodeExecution(nodeId, 'completed');
      }
    }
  }

  delay(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  evaluateExpression(expression, variables) {
    if (!expression) return expression;
    
    try {
      const expr = expression.trim();
      
      if (/^[0-9.+\-*/%<>!=&|]+$/.test(expr)) {
        const func = new Function(`return ${expr}`);
        return func();
      }
      
      const funcMap = {
        now: () => new Date().toISOString(),
        uuid: () => 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
          const r = Math.random() * 16 | 0;
          const v = c === 'x' ? r : (r & 0x3 | 0x8);
          return v.toString(16);
        }),
        len: (str) => typeof str === 'string' ? str.length : 0,
        trim: (str) => typeof str === 'string' ? str.trim() : str,
        upper: (str) => typeof str === 'string' ? str.toUpperCase() : str,
        lower: (str) => typeof str === 'string' ? str.toLowerCase() : str,
        json: (obj) => JSON.stringify(obj),
        parseJson: (str) => JSON.parse(str),
        env: (key) => process.env[key] || '',
        random: () => Math.random()
      };
      
      const hasVariables = expr.includes('{{') && expr.includes('}}');
      if (hasVariables) {
        let result = expr;
        
        result = result.replace(/\{\{(\w+)\(\)\}\}/g, (_, funcName) => {
          if (funcMap[funcName]) {
            return funcMap[funcName]();
          }
          return `{{${funcName}()}}`;
        });
        
        result = result.replace(/\{\{([\w.]+)\}\}/g, (_, path) => {
          let value = variables;
          const keys = path.split('.');
          for (const key of keys) {
            if (value === undefined || value === null) break;
            value = value[key];
          }
          if (value === undefined || value === null) return '';
          return String(value);
        });
        
        if (result !== expr) {
          return result;
        }
      }
      
    } catch (error) {
      console.warn('Expression evaluation error:', error);
    }
    
    return expression;
  }

  evaluateCondition(varValue, operator, compareValue) {
    try {
      const numVarValue = parseFloat(varValue);
      const numCompareValue = parseFloat(compareValue);
      
      switch (operator) {
        case '==': return String(varValue) === String(compareValue);
        case '!=': return String(varValue) !== String(compareValue);
        case '>': return numVarValue > numCompareValue;
        case '<': return numVarValue < numCompareValue;
        case '>=': return numVarValue >= numCompareValue;
        case '<=': return numVarValue <= numCompareValue;
        case 'contains': 
          return typeof varValue === 'string' && varValue.includes(compareValue);
        case 'not_contains':
          return typeof varValue !== 'string' || !varValue.includes(compareValue);
        case 'starts_with':
          return typeof varValue === 'string' && varValue.startsWith(compareValue);
        case 'ends_with':
          return typeof varValue === 'string' && varValue.endsWith(compareValue);
        case 'matches':
          return typeof varValue === 'string' && new RegExp(compareValue).test(varValue);
        case 'is_empty':
          return !varValue || String(varValue).trim() === '';
        default: return true;
      }
    } catch {
      return false;
    }
  }

  formatBranchConditions(branch) {
    if (!branch.conditions || branch.conditions.length === 0) {
      return branch.type === 'else' ? 'else' : 'true';
    }
    
    return branch.conditions.map(cond => {
      const operatorLabel = {
        '==': '等于',
        '!=': '不等于',
        '>': '大于',
        '<': '小于',
        '>=': '大于等于',
        '<=': '小于等于',
        'contains': '包含',
        'not_contains': '不包含',
        'starts_with': '以...开头',
        'ends_with': '以...结尾',
        'matches': '匹配正则',
        'is_empty': '为空'
      }[cond.operator] || cond.operator;
      
      const displayValue = cond.value || '(空)';
      return `${cond.variable} ${operatorLabel} ${cond.valueType === 'reference' ? `{{${displayValue}}}` : displayValue}`;
    }).join(' && ');
  }

  async submitFormData(formData) {
    try {
      const response = await fetch('/api/workflows/execute-form-node', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ontology_code: this.pendingForm?.ontologyCode || '',
          tool_name: this.pendingForm?.toolName || '',
          enable_validation: this.pendingForm?.enableValidation || false,
          model: this.pendingForm?.model || '',
          temperature: this.pendingForm?.temperature || 0.3,
          validation_prompt: this.pendingForm?.validationPrompt || '',
          input_variable: this.pendingForm?.inputVariable || '',
          input_data: this.pendingForm?.contextVariables || {}
        })
      });
      
      if (response.ok) {
        return await response.json();
      } else {
        const errorData = await response.json();
        throw new Error(errorData.message || '表单节点执行失败');
      }
    } catch (error) {
      throw error;
    }
  }

  resume(userInputValue) {
    console.log('[executionEngine] resume 被调用:', userInputValue);
    console.log('[executionEngine] pendingForm:', this.pendingForm);
    console.log('[executionEngine] resumeCallback:', !!this.resumeCallback);
    
    if (this.pendingForm && this.resumeCallback) {
      this.resumeCallback(userInputValue);
      return true;
    }
    
    if (this.resumeCallback) {
      this.resumeCallback(userInputValue);
      return true;
    }
    return false;
  }

  getPendingInput() {
    return this.pendingInput;
  }

  getPendingForm() {
    return this.pendingForm;
  }

  getWorkflowId() {
    return this.workflowId;
  }

  isExecutionPaused() {
    return this.isPaused;
  }

  stop() {
    this.isRunning = false;
    this.isPaused = false;
    this.pendingInput = null;
    this.resumeCallback = null;
    this.logs = [];
    this.clearNodeStatus();
    this.clearNodeExecutionData();
  }

  async executeSingleNode(node, inputData = {}) {
    if (this.isRunning) {
      throw new Error('工作流正在执行中');
    }

    this.isRunning = true;
    this.isPaused = false;
    this.pendingInput = null;
    this.resumeCallback = null;
    this.logs = [];
    this.clearNodeStatus();
    this.clearNodeExecutionData();
    
    if (this.onLog) {
      this.onLog({ type: 'clear' });
    }

    try {
      const nodeId = node.id;
      const nodeLabel = node.data.label || node.type;
      
      this.setNodeStatus(nodeId, 'running');
      this.initNodeExecution(nodeId, node.type, nodeLabel);
      
      this.addLog('node_start', `开始执行单节点: ${nodeLabel}`, `节点ID: ${node.id}`, { 
        nodeId, nodeType: node.type, nodeLabel 
      });
      this.addNodeLog(nodeId, { type: 'info', message: '节点开始执行' });

      await this.delay(300);

      let result = null;

      switch (node.type) {
        case 'llm': {
          const model = node.data.model || 'qwen-vl-plus';
          const temperature = node.data.temperature || 0.7;
          const systemPrompt = node.data.systemPrompt || '';
          const nodePrompt = node.data.prompt || '';
          
          const inputContent = inputData && inputData.input ? inputData.input : '';
          let resolvedPrompt = inputContent || '';
          
          if (nodePrompt) {
            let renderedPrompt = nodePrompt.replace(/\{\{(\w+)\}\}/g, (_, key) => inputData[key] || '');
            if (resolvedPrompt && resolvedPrompt.trim()) {
              resolvedPrompt = renderedPrompt.includes("{input}") ? renderedPrompt.replace("{input}", resolvedPrompt) : renderedPrompt + "\n" + resolvedPrompt;
            } else {
              resolvedPrompt = renderedPrompt;
            }
          }
          
          this.updateNodeData(nodeId, {
            input: { prompt: resolvedPrompt, systemPrompt, model, temperature, ...inputData },
            config: { model, temperature, maxTokens: node.data.maxTokens, systemPrompt, prompt: nodePrompt },
            output: null
          });
          this.addNodeLog(nodeId, { type: 'info', message: `调用模型: ${model}, 温度: ${temperature}` });
          
          this.addNodeLog(nodeId, { type: 'debug', message: `输入提示词: ${resolvedPrompt}` });
          this.addLog('info', `调用 LLM 模型`, `模型: ${model}, 温度: ${temperature}`, { input: resolvedPrompt });

          try {
            const response = await fetch('/api/v1/chat/completion', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                model: model,
                prompt: resolvedPrompt,
                system_prompt: systemPrompt,
                temperature: parseFloat(temperature) || 0.7,
                max_tokens: Math.round(parseFloat(node.data.maxTokens) || 1024),
                top_k: Math.round(parseFloat(node.data.topK) || 0),
                top_p: parseFloat(node.data.topP) || 1.0
              })
            });

            if (response.ok) {
              const data = await response.json();
              if (!data.result && !data.content) {
                throw new Error('LLM 响应为空');
              }
              result = data.result || data.content;
            } else {
              const errorData = await response.json();
              throw new Error(errorData.message || 'LLM 调用失败');
            }
          } catch (error) {
            console.error('LLM 调用失败:', error);
            throw error;
          }

          this.updateNodeData(nodeId, { output: result });
          this.addNodeLog(nodeId, { type: 'info', message: 'LLM 响应完成' });
          this.addLog('info', 'LLM 响应完成', null, { output: result });
          break;
        }

        case 'prompt': {
          const promptTemplate = node.data.prompt || '请输入内容';
          let resolvedPrompt = promptTemplate;
          
          if (inputData) {
            resolvedPrompt = promptTemplate.replace(/\{\{(\w+)\}\}/g, (_, key) => inputData[key] || '');
          }
          
          this.updateNodeData(nodeId, {
            input: { template: promptTemplate, variables: inputData },
            config: { template: promptTemplate },
            output: resolvedPrompt
          });
          this.addNodeLog(nodeId, { type: 'info', message: `解析变量后的提示词: ${resolvedPrompt}` });
          result = resolvedPrompt;
          this.addLog('info', '构建提示词', promptTemplate, { resolved: resolvedPrompt });
          break;
        }

        case 'http': {
          const url = node.data.url || '未配置';
          const method = node.data.method || 'GET';
          let requestData = {};
          
          if (inputData) {
            requestData = { ...inputData };
          }
          
          this.updateNodeData(nodeId, {
            input: { url, method, data: requestData },
            config: { url, method, headers: node.data.headers },
            output: { status: 200, data: '模拟响应数据' }
          });
          result = { status: 200, data: '模拟响应数据' };
          this.addNodeLog(nodeId, { type: 'info', message: `HTTP ${method} ${url}` });
          this.addLog('info', 'HTTP 请求', `URL: ${url}`, null);
          break;
        }

        case 'code': {
          const code = node.data.code || '无代码';
          
          this.updateNodeData(nodeId, {
            input: { code, variables: inputData },
            config: { language: node.data.language || 'javascript' },
            output: '模拟代码执行结果'
          });
          result = '模拟代码执行结果';
          this.addNodeLog(nodeId, { type: 'info', message: `执行代码 (${node.data.language || 'javascript'})` });
          this.addLog('info', '代码执行', code, null);
          break;
        }

        case 'knowledgeBase': {
          const kbId = node.data.knowledgeBase || '';
          const queryMode = node.data.queryMode || 'retrieve';
          const queryText = node.data.queryText || '';
          
          let resolvedQuery = queryText;
          if (inputData) {
            resolvedQuery = queryText.replace(/\{\{(\w+)\}\}/g, (_, key) => inputData[key] || '');
          }
          
          this.updateNodeData(nodeId, {
            input: { knowledgeBase: kbId, queryMode, queryText: resolvedQuery },
            config: { knowledgeBase: kbId, queryMode, queryText },
            output: null
          });
          this.addNodeLog(nodeId, { type: 'info', message: `查询知识库: ${kbId}, 模式: ${queryMode}` });
          this.addNodeLog(nodeId, { type: 'debug', message: `查询内容: ${resolvedQuery}` });

          const mockResults = {
            documents: [
              { id: 'doc1', content: '知识库文档内容示例1...', score: 0.95 },
              { id: 'doc2', content: '知识库文档内容示例2...', score: 0.88 }
            ],
            answer: queryMode === 'qa' ? '这是模拟的知识库问答响应。' : null,
            summary: queryMode === 'summarize' ? '这是模拟的文档摘要。' : null
          };
          
          result = mockResults;
          this.updateNodeData(nodeId, { output: mockResults });
          this.addNodeLog(nodeId, { type: 'info', message: `查询完成，返回 ${mockResults.documents?.length || 0} 条结果` });
          this.addLog('info', '知识库查询完成', null, { result: mockResults });
          break;
        }

        case 'variable': {
          const varName = node.data.varName || node.data.variableName || 'result';
          let rawValue = node.data.varValue || node.data.variableValue || '';
          
          const sourceValue = node.data.varValue || node.data.variableValue;
          if (inputData && sourceValue) {
            rawValue = sourceValue.replace(/\{\{(\w+)\}\}/g, (_, key) => inputData[key] || '');
          } else if (inputData && inputData[varName]) {
            rawValue = inputData[varName];
          }
          
          let varValue = rawValue;
          if (rawValue && typeof rawValue === 'string') {
            varValue = this.evaluateExpression(rawValue, inputData || {});
          }
          
          this.updateNodeData(nodeId, {
            input: { varName, varValue: varValue },
            config: { varName, varValue: sourceValue },
            output: { [varName]: varValue }
          });
          result = { [varName]: varValue };
          this.addNodeLog(nodeId, { type: 'info', message: `赋值 ${varName} = ${varValue}` });
          this.addLog('info', '变量赋值', `${varName} = ${varValue}`, { [varName]: varValue });
          break;
        }

        case 'form': {
          const ontologyCode = node.data.ontologyCode || '';
          const toolName = node.data.toolType || node.data.toolName || '';
          const enableValidation = node.data.enableValidation || false;
          const model = node.data.model || '';
          const temperature = node.data.temperature || 0.3;
          const validationPrompt = node.data.validationPrompt || '';
          const inputVariable = node.data.inputVariable || '';
          
          this.updateNodeData(nodeId, {
            input: { ...inputData },
            config: {
              ontologyCode,
              toolName,
              enableValidation,
              model,
              temperature,
              validationPrompt,
              inputVariable
            },
            output: null
          });
          
          this.addNodeLog(nodeId, { type: 'info', message: `表单节点配置: 本体=${ontologyCode}, 工具=${toolName || '未配置'}` });
          this.addLog('info', '表单节点', `本体: ${ontologyCode}, 工具: ${toolName || '未配置'}`, { ontologyCode, toolName, enableValidation, model });
          
          // 单节点执行时，调用后端 API 执行表单节点
           try {
             const response = await fetch('/api/workflows/execute-form-node', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                ontology_code: ontologyCode,
                tool_name: toolName,
                enable_validation: enableValidation,
                model: model,
                temperature: temperature,
                validation_prompt: validationPrompt,
                input_variable: inputVariable,
                input_data: inputData || {}
              })
            });
            
            if (response.ok) {
              const data = await response.json();
              result = data;
              this.updateNodeData(nodeId, { output: result });
              this.addNodeLog(nodeId, { type: 'info', message: '表单节点执行完成' });
              this.addLog('info', '表单节点执行完成', null, result);
            } else {
              const errorData = await response.json();
              throw new Error(errorData.message || '表单节点执行失败');
            }
          } catch (error) {
            console.error('表单节点执行失败:', error);
            this.addNodeLog(nodeId, { type: 'error', message: error.message });
            throw error;
          }
          break;
        }

        default: {
          this.updateNodeData(nodeId, { 
            config: { nodeType: node.type },
            output: `单节点运行不支持该节点类型: ${node.type}`
          });
          result = `单节点运行不支持该节点类型: ${node.type}`;
          this.addLog('info', `执行节点类型: ${node.type}`, null, null);
        }
      }

      this.addLog('success', '单节点执行完成', null, {
        result,
        timestamp: new Date().toISOString()
      });

      return {
        status: 'success',
        result,
        nodeExecutionData: this.getNodeExecutionData(),
        timestamp: new Date().toISOString()
      };

    } catch (error) {
      this.setNodeStatus(node.id, 'error');
      this.updateNodeData(node.id, { status: 'error', error: error.message });
      this.addLog('error', '单节点执行失败', error.message, null);
      return {
        status: 'error',
        error: error.message,
        timestamp: new Date().toISOString()
      };
    } finally {
      this.isRunning = false;
    }
  }
}