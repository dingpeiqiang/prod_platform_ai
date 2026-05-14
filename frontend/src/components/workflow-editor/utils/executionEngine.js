export class ExecutionEngine {
  constructor() {
    this.logs = [];
    this.isRunning = false;
    this.nodeStatus = {};
    this.onStatusChange = null;
    this.onLog = null;
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

    this.setNodeStatus(nodeId, 'running');
    this.addLog('node', `执行节点: ${node.data.label || node.type}`, `节点ID: ${node.id}`, { node });

    await this.delay(500);

    try {
      switch (node.type) {
        case 'start':
          // 将传入的参数设置到变量中，方便后续节点使用
          if (context.params && Object.keys(context.params).length > 0) {
            Object.assign(context.variables, context.params);
            this.addLog('info', '初始化参数到变量', null, context.variables);
          } else {
            this.addLog('info', '初始化工作流上下文', null, context);
          }
          break;

        case 'prompt': {
          const prompt = node.data.prompt || '请输入内容';
          context.input = prompt.replace(/\{\{(\w+)\}\}/g, (_, key) => context.variables[key] || '');
          this.addLog('info', '构建提示词', prompt, { result: context.input });
          break;
        }

        case 'llm': {
          const model = node.data.model || 'qwen-vl-plus';
          const temperature = node.data.temperature || 0.7;
          this.addLog('info', `调用 LLM 模型`, `模型: ${model}, 温度: ${temperature}`, { input: context.input });

          const mockResponse = `这是模拟的 LLM 响应。\n\n输入: ${context.input}\n\n模型: ${model}\n\n时间: ${new Date().toLocaleString()}`;
          context.output = mockResponse;
          this.addLog('info', 'LLM 响应完成', null, { output: mockResponse });
          break;
        }

        case 'tool': {
          this.addLog('info', '工具调用', `工具类型: ${node.data.toolType || '未知'}`, null);
          context.variables['toolResult'] = '模拟工具执行结果';
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
          this.addLog('info', `循环开始`, `${loopType} 循环，次数: ${loopCount}`, null);

          for (let i = 0; i < loopCount; i++) {
            context.variables['loopIndex'] = i;
            context.variables['loopCount'] = loopCount;
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
          context.variables[varName] = varValue;
          this.addLog('info', '变量赋值', `${varName} = ${varValue}`, context.variables);
          break;
        }

        case 'http': {
          this.addLog('info', 'HTTP 请求', `URL: ${node.data.url || '未配置'}`, null);
          context.variables['httpResult'] = { status: 200, data: '模拟响应数据' };
          break;
        }

        case 'code': {
          this.addLog('info', '代码执行', node.data.code || '无代码', null);
          context.variables['codeResult'] = '模拟代码执行结果';
          break;
        }

        case 'parser': {
          this.addLog('info', '输出解析', null, { input: context.output });
          context.parsed = context.output ? JSON.stringify({ text: context.output }, null, 2) : null;
          break;
        }

        case 'end': {
          this.addLog('info', '到达结束节点', null, null);
          this.setNodeStatus(nodeId, 'completed');
          return;
        }

        default:
          this.addLog('info', `执行节点类型: ${node.type}`, null, null);
      }

      const nextEdges = edges.filter(e => e.source === nodeId && !e.sourceHandle);
      for (const edge of nextEdges) {
        await this.executeNode(edge.target, nodes, edges, context);
      }
    } catch (error) {
      this.setNodeStatus(nodeId, 'error');
      throw error;
    } finally {
      if (node.type !== 'end') {
        const currentStatus = this.nodeStatus[nodeId];
        if (currentStatus === 'running') {
          this.setNodeStatus(nodeId, 'completed');
        }
      }
    }
  }

  delay(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
}