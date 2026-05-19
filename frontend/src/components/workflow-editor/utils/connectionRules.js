export const NODE_TYPES = {
  START: 'start',
  END: 'end',
  CONDITION: 'condition',
  LOOP: 'loop',
  PROMPT: 'prompt',
  LLM: 'llm',
  TOOL: 'tool',
  HTTP: 'http',
  CODE: 'code',
  VARIABLE: 'variable',
  PARSER: 'parser',
  KNOWLEDGE_BASE: 'knowledgeBase'
};

export const NODE_CONFIG = {
  [NODE_TYPES.START]: {
    name: '开始节点',
    canConnectFrom: [],
    canConnectTo: ['prompt', 'llm', 'tool', 'http', 'code', 'variable', 'condition', 'loop', 'parser', 'knowledgeBase'],
    maxInputs: 0,
    maxOutputs: 1,
    handles: [{ type: 'source', position: 'right', id: 'default' }]
  },
  [NODE_TYPES.END]: {
    name: '结束节点',
    canConnectFrom: ['prompt', 'llm', 'tool', 'http', 'code', 'variable', 'condition', 'loop', 'parser', 'knowledgeBase'],
    canConnectTo: [],
    maxInputs: 1,
    maxOutputs: 0,
    handles: [{ type: 'target', position: 'left', id: 'default' }]
  },
  [NODE_TYPES.CONDITION]: {
    name: '条件分支节点',
    canConnectFrom: ['start', 'prompt', 'llm', 'tool', 'http', 'code', 'variable', 'condition', 'loop', 'parser', 'knowledgeBase'],
    canConnectTo: ['prompt', 'llm', 'tool', 'http', 'code', 'variable', 'condition', 'loop', 'parser', 'end', 'knowledgeBase'],
    maxInputs: 1,
    maxOutputs: 2,
    handles: [
      { type: 'target', position: 'left', id: 'default' },
      { type: 'source', position: 'right', id: 'true', label: '满足' },
      { type: 'source', position: 'right', id: 'false', label: '不满足' }
    ]
  },
  [NODE_TYPES.LOOP]: {
    name: '循环节点',
    canConnectFrom: ['start', 'prompt', 'llm', 'tool', 'http', 'code', 'variable', 'condition', 'loop', 'parser', 'knowledgeBase'],
    canConnectTo: ['prompt', 'llm', 'tool', 'http', 'code', 'variable', 'condition', 'parser', 'end', 'knowledgeBase'],
    maxInputs: 2,
    maxOutputs: 2,
    handles: [
      { type: 'target', position: 'left', id: 'default', label: '入口' },
      { type: 'target', position: 'bottom', id: 'continue', label: '继续' },
      { type: 'source', position: 'right', id: 'body', label: '循环体' },
      { type: 'source', position: 'right', id: 'exit', label: '退出' }
    ]
  },
  [NODE_TYPES.PROMPT]: {
    name: '提示词节点',
    canConnectFrom: ['start', 'prompt', 'llm', 'tool', 'http', 'code', 'variable', 'condition', 'loop', 'parser', 'knowledgeBase'],
    canConnectTo: ['llm', 'tool', 'http', 'code', 'variable', 'condition', 'loop', 'parser', 'end', 'knowledgeBase'],
    maxInputs: 1,
    maxOutputs: 1,
    handles: [
      { type: 'target', position: 'left', id: 'default' },
      { type: 'source', position: 'right', id: 'default' }
    ]
  },
  [NODE_TYPES.LLM]: {
    name: 'LLM调用节点',
    canConnectFrom: ['start', 'prompt', 'llm', 'tool', 'http', 'code', 'variable', 'condition', 'loop', 'parser', 'knowledgeBase'],
    canConnectTo: ['prompt', 'llm', 'tool', 'http', 'code', 'variable', 'condition', 'loop', 'parser', 'end', 'knowledgeBase'],
    maxInputs: 1,
    maxOutputs: 1,
    handles: [
      { type: 'target', position: 'left', id: 'default' },
      { type: 'source', position: 'right', id: 'default' }
    ]
  },
  [NODE_TYPES.TOOL]: {
    name: '工具调用节点',
    canConnectFrom: ['start', 'prompt', 'llm', 'tool', 'http', 'code', 'variable', 'condition', 'loop', 'parser', 'knowledgeBase'],
    canConnectTo: ['prompt', 'llm', 'tool', 'http', 'code', 'variable', 'condition', 'loop', 'parser', 'end', 'knowledgeBase'],
    maxInputs: 1,
    maxOutputs: 1,
    handles: [
      { type: 'target', position: 'left', id: 'default' },
      { type: 'source', position: 'right', id: 'default' }
    ]
  },
  [NODE_TYPES.HTTP]: {
    name: 'HTTP请求节点',
    canConnectFrom: ['start', 'prompt', 'llm', 'tool', 'http', 'code', 'variable', 'condition', 'loop', 'parser', 'knowledgeBase'],
    canConnectTo: ['prompt', 'llm', 'tool', 'http', 'code', 'variable', 'condition', 'loop', 'parser', 'end', 'knowledgeBase'],
    maxInputs: 1,
    maxOutputs: 1,
    handles: [
      { type: 'target', position: 'left', id: 'default' },
      { type: 'source', position: 'right', id: 'default' }
    ]
  },
  [NODE_TYPES.CODE]: {
    name: '代码执行节点',
    canConnectFrom: ['start', 'prompt', 'llm', 'tool', 'http', 'code', 'variable', 'condition', 'loop', 'parser', 'knowledgeBase'],
    canConnectTo: ['prompt', 'llm', 'tool', 'http', 'code', 'variable', 'condition', 'loop', 'parser', 'end', 'knowledgeBase'],
    maxInputs: 1,
    maxOutputs: 1,
    handles: [
      { type: 'target', position: 'left', id: 'default' },
      { type: 'source', position: 'right', id: 'default' }
    ]
  },
  [NODE_TYPES.VARIABLE]: {
    name: '变量赋值节点',
    canConnectFrom: ['start', 'prompt', 'llm', 'tool', 'http', 'code', 'variable', 'condition', 'loop', 'parser', 'knowledgeBase'],
    canConnectTo: ['prompt', 'llm', 'tool', 'http', 'code', 'variable', 'condition', 'loop', 'parser', 'end', 'knowledgeBase'],
    maxInputs: 1,
    maxOutputs: 1,
    handles: [
      { type: 'target', position: 'left', id: 'default' },
      { type: 'source', position: 'right', id: 'default' }
    ]
  },
  [NODE_TYPES.PARSER]: {
    name: '输出解析节点',
    canConnectFrom: ['start', 'prompt', 'llm', 'tool', 'http', 'code', 'variable', 'condition', 'loop', 'parser', 'knowledgeBase'],
    canConnectTo: ['prompt', 'llm', 'tool', 'http', 'code', 'variable', 'condition', 'loop', 'parser', 'end', 'knowledgeBase'],
    maxInputs: 1,
    maxOutputs: 1,
    handles: [
      { type: 'target', position: 'left', id: 'default' },
      { type: 'source', position: 'right', id: 'default' }
    ]
  },
  [NODE_TYPES.KNOWLEDGE_BASE]: {
    name: '知识库节点',
    canConnectFrom: ['start', 'prompt', 'llm', 'tool', 'http', 'code', 'variable', 'condition', 'loop', 'parser', 'knowledgeBase'],
    canConnectTo: ['prompt', 'llm', 'tool', 'http', 'code', 'variable', 'condition', 'loop', 'parser', 'end', 'knowledgeBase'],
    maxInputs: 1,
    maxOutputs: 1,
    handles: [
      { type: 'target', position: 'left', id: 'default' },
      { type: 'source', position: 'right', id: 'default' }
    ]
  }
};

export const validateConnection = (connection, elements) => {
  const { source, target, sourceHandle, targetHandle } = connection;
  
  const sourceNode = elements.find(el => el.id === source && !el.source);
  const targetNode = elements.find(el => el.id === target && !el.source);
  
  if (!sourceNode || !targetNode) {
    return { valid: false, code: 'NODE_NOT_FOUND', message: '节点不存在', suggestion: '请确保两个节点都存在于画布上' };
  }
  
  const sourceConfig = NODE_CONFIG[sourceNode.type];
  const targetConfig = NODE_CONFIG[targetNode.type];
  
  if (!sourceConfig || !targetConfig) {
    return { valid: false, code: 'UNKNOWN_NODE_TYPE', message: `未知节点类型: ${sourceNode.type} 或 ${targetNode.type}`, suggestion: '请使用有效的节点类型' };
  }
  
  if (source === target) {
    return { valid: false, code: 'SELF_CONNECTION', message: '不能连接到自身', suggestion: '请选择不同的目标节点' };
  }
  
  if (sourceNode.type === NODE_TYPES.END) {
    return { valid: false, code: 'END_NODE_OUTPUT', message: '结束节点不能有输出连接', suggestion: '结束节点是工作流的终点，不能作为连接源' };
  }
  
  if (targetNode.type === NODE_TYPES.START) {
    return { valid: false, code: 'START_NODE_INPUT', message: '开始节点不能有输入连接', suggestion: '开始节点是工作流的起点，不能作为连接目标' };
  }
  
  if (!targetConfig.canConnectFrom.includes(sourceNode.type)) {
    return { 
      valid: false, 
      code: 'TYPE_MISMATCH', 
      message: `${sourceConfig.name}不能连接到${targetConfig.name}`, 
      suggestion: `${sourceConfig.name}只能连接到: ${targetConfig.canConnectFrom.map(t => NODE_CONFIG[t]?.name || t).join('、')}` 
    };
  }
  
  const existingEdges = elements.filter(el => el.source && el.target);
  
  const duplicateEdge = existingEdges.find(
    el => el.source === source && 
          el.target === target && 
          el.sourceHandle === sourceHandle &&
          el.targetHandle === targetHandle
  );
  if (duplicateEdge) {
    return { valid: false, code: 'DUPLICATE_CONNECTION', message: '连接已存在', suggestion: '相同的连接已经存在，请删除后重新创建' };
  }
  
  const targetInputEdges = existingEdges.filter(
    el => el.target === target && 
          (!targetHandle || el.targetHandle === targetHandle)
  );
  
  if (targetInputEdges.length >= targetConfig.maxInputs) {
    return { 
      valid: false, 
      code: 'MAX_INPUTS_EXCEEDED', 
      message: `${targetConfig.name}的输入连接已达上限`, 
      suggestion: `${targetConfig.name}最多只能有${targetConfig.maxInputs}个输入连接` 
    };
  }
  
  const sourceOutputEdges = existingEdges.filter(
    el => el.source === source && 
          (!sourceHandle || el.sourceHandle === sourceHandle)
  );
  
  if (sourceHandle) {
    const handleOutputEdges = existingEdges.filter(
      el => el.source === source && el.sourceHandle === sourceHandle
    );
    if (handleOutputEdges.length > 0) {
      return { 
        valid: false, 
        code: 'HANDLE_OUTPUT_EXISTS', 
        message: '该输出端口已有连接', 
        suggestion: '每个输出端口只能连接一个目标节点' 
      };
    }
  } else {
    if (sourceOutputEdges.length >= sourceConfig.maxOutputs) {
      return { 
        valid: false, 
        code: 'MAX_OUTPUTS_EXCEEDED', 
        message: `${sourceConfig.name}的输出连接已达上限`, 
        suggestion: `${sourceConfig.name}最多只能有${sourceConfig.maxOutputs}个输出连接` 
      };
    }
  }
  
  if (wouldCreateCycle(source, target, existingEdges)) {
    return { valid: false, code: 'CYCLE_DETECTED', message: '会形成循环依赖', suggestion: '请避免创建循环连接，可能导致死循环' };
  }
  
  if (sourceNode.type === NODE_TYPES.CONDITION) {
    if (!sourceHandle) {
      return { valid: false, code: 'CONDITION_MISSING_HANDLE', message: '条件节点必须选择输出分支', suggestion: '请选择"满足"或"不满足"分支' };
    }
    if (sourceHandle !== 'true' && sourceHandle !== 'false') {
      return { valid: false, code: 'INVALID_CONDITION_HANDLE', message: '无效的条件分支', suggestion: '条件节点只能选择"满足"或"不满足"分支' };
    }
  }
  
  if (sourceNode.type === NODE_TYPES.LOOP) {
    if (!sourceHandle) {
      return { valid: false, code: 'LOOP_MISSING_HANDLE', message: '循环节点必须选择输出端口', suggestion: '请选择"循环体"或"退出"端口' };
    }
    if (sourceHandle !== 'body' && sourceHandle !== 'exit') {
      return { valid: false, code: 'INVALID_LOOP_HANDLE', message: '无效的循环输出端口', suggestion: '循环节点只能选择"循环体"或"退出"端口' };
    }
  }
  
  if (targetNode.type === NODE_TYPES.LOOP && targetHandle === 'continue') {
    const continueEdges = existingEdges.filter(
      el => el.target === target && el.targetHandle === 'continue'
    );
    if (continueEdges.length > 0) {
      return { valid: false, code: 'LOOP_CONTINUE_EXISTS', message: '循环继续端口已有连接', suggestion: '循环继续端口只能有一个输入连接' };
    }
  }
  
  return { valid: true, code: 'VALID', message: '连接有效' };
};

const wouldCreateCycle = (source, target, edges) => {
  const visited = new Set();
  const inStack = new Set();
  
  const hasPath = (from, to) => {
    if (from === to) return true;
    if (visited.has(from)) return false;
    
    visited.add(from);
    inStack.add(from);
    
    const outgoingEdges = edges.filter(e => e.source === from);
    for (const edge of outgoingEdges) {
      if (hasPath(edge.target, to)) {
        return true;
      }
    }
    
    inStack.delete(from);
    return false;
  };
  
  return hasPath(target, source);
};

export const canConnect = (sourceNodeType, targetNodeType) => {
  const sourceConfig = NODE_CONFIG[sourceNodeType];
  const targetConfig = NODE_CONFIG[targetNodeType];
  
  if (!sourceConfig || !targetConfig) return false;
  if (sourceNodeType === NODE_TYPES.END) return false;
  if (targetNodeType === NODE_TYPES.START) return false;
  if (!targetConfig.canConnectFrom.includes(sourceNodeType)) return false;
  
  return true;
};

export const getNodeConfig = (nodeType) => {
  return NODE_CONFIG[nodeType] || null;
};

export const getValidTargetTypes = (sourceNodeType) => {
  const config = NODE_CONFIG[sourceNodeType];
  if (!config) return [];
  return config.canConnectTo;
};

export const getValidSourceTypes = (targetNodeType) => {
  const config = NODE_CONFIG[targetNodeType];
  if (!config) return [];
  return config.canConnectFrom;
};