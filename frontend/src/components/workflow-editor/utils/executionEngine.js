export class ExecutionEngine {
  constructor() {
    this.logs = [];
    this.isRunning = false;
    this.nodeStatus = {};
    this.nodeExecutionData = {};  // 新增：存储每个节点的执行详情
    this.onStatusChange = null;
    this.onLog = null;
    this.isPaused = false;
    this.pendingInput = null;
    this.resumeCallback = null;
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
  }

  // 添加节点日志
  addNodeLog(nodeId, logEntry) {
    if (!this.nodeExecutionData[nodeId]) return;
    this.nodeExecutionData[nodeId].logs.push({
      ...logEntry,
      timestamp: logEntry.timestamp || Date.now()
    });
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

  setCallbacks(onStatusChange, onLog) {
    this.onStatusChange = onStatusChange;
    this.onLog = onLog;
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

  async execute(elements, inputParams = {}) {
    if (this.isRunning) return;

    this.isRunning = true;
    this.logs = [];
    this.clearNodeStatus();
    this.clearNodeExecutionData();

    try {
      const nodes = elements.filter(el => !el.source && !el.target);
      const edges = elements.filter(el => el.source && el.target);

      const startNode = nodes.find(n => n.type === 'start');
      if (!startNode) {
        throw new Error('未找到开始节点');
      }

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
    const node = nodes.find(n => n.id === nodeId);
    if (!node) return;

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
          
          this.updateNodeData(nodeId, {
            input: { prompt: context.input, model, temperature },
            config: { model, temperature, maxTokens: node.data.maxTokens },
            output: null  // 待填充
          });
          this.addNodeLog(nodeId, { type: 'info', message: `调用模型: ${model}, 温度: ${temperature}` });
          this.addNodeLog(nodeId, { type: 'debug', message: `输入: ${context.input}` });
          
          this.addLog('info', `调用 LLM 模型`, `模型: ${model}, 温度: ${temperature}`, { input: context.input });

          const mockResponse = `这是模拟的 LLM 响应。\n\n输入: ${context.input}\n\n模型: ${model}\n\n时间: ${new Date().toLocaleString()}`;
          context.output = mockResponse;
          
          this.updateNodeData(nodeId, { output: mockResponse });
          this.addNodeLog(nodeId, { type: 'info', message: 'LLM 响应完成' });
          this.addLog('info', 'LLM 响应完成', null, { output: mockResponse });
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

          return new Promise((resolve) => {
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
          const condition = node.data.condition || 'true';
          let result = true;
          try {
            const func = new Function('context', `return ${condition}`);
            result = func(context);
          } catch {
            result = true;
          }

          this.updateNodeData(nodeId, {
            input: { condition, context: { ...context.variables } },
            config: { condition },
            output: result,
            logs: [{ type: 'result', message: `条件结果: ${condition} = ${result}` }]
          });
          this.addNodeLog(nodeId, { type: 'info', message: `条件判断: ${condition} = ${result}` });
          this.addLog('info', '条件判断', `${condition} = ${result}`, { result });

          const resultEdges = edges.filter(e => e.source === nodeId && e.sourceHandle === (result ? 'true' : 'false'));
          for (const edge of resultEdges) {
            await this.executeNode(edge.target, nodes, edges, context);
          }
          this.setNodeStatus(nodeId, 'completed');
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
          const varName = node.data.variableName || 'result';
          const varValue = node.data.variableValue || context.output;
          
          this.updateNodeData(nodeId, {
            input: { varName, varValue: varValue },
            config: { varName, varValue: node.data.variableValue },
            output: { [varName]: varValue }
          });
          context.variables[varName] = varValue;
          this.addNodeLog(nodeId, { type: 'info', message: `赋值 ${varName} = ${varValue}` });
          this.addLog('info', '变量赋值', `${varName} = ${varValue}`, context.variables);
          break;
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

      const nextEdges = edges.filter(e => e.source === nodeId && !e.sourceHandle);
      for (const edge of nextEdges) {
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

  resume(userInputValue) {
    if (this.resumeCallback) {
      this.resumeCallback(userInputValue);
      return true;
    }
    return false;
  }

  getPendingInput() {
    return this.pendingInput;
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
}