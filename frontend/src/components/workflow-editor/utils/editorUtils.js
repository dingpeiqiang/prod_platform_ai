export const debounce = (fn, delay) => {
  let timer = null;
  return function (...args) {
    clearTimeout(timer);
    timer = setTimeout(() => fn.apply(this, args), delay);
  };
};

export const validateWorkflow = (elements) => {
  const errors = [];
  const warnings = [];
  const nodes = elements.filter(el => !el.source && !el.target);
  const edges = elements.filter(el => el.source && el.target);
  
  if (nodes.length === 0) {
    errors.push({ type: 'empty', message: '工作流为空', suggestion: '请从左侧面板拖拽节点到画布' });
    return { errors, warnings };
  }
  
  const startNodes = nodes.filter(n => n.type === 'start');
  const endNodes = nodes.filter(n => n.type === 'end');
  
  if (startNodes.length === 0) {
    errors.push({ type: 'missing_start', message: '缺少开始节点', suggestion: '请添加一个开始节点作为工作流入口' });
  } else if (startNodes.length > 1) {
    errors.push({ type: 'multiple_start', message: `存在 ${startNodes.length} 个开始节点`, suggestion: '工作流只能有一个开始节点，请删除多余的' });
  }
  
  if (endNodes.length === 0) {
    errors.push({ type: 'missing_end', message: '缺少结束节点', suggestion: '请添加一个结束节点作为工作流出口' });
  }
  
  if (edges.length === 0) {
    errors.push({ type: 'no_edges', message: '没有连接线', suggestion: '请连接节点形成完整的工作流' });
  }
  
  const nodeIds = nodes.map(n => n.id);
  const sourceNodes = edges.map(e => e.source);
  const targetNodes = edges.map(e => e.target);
  
  nodes.forEach(node => {
    if (node.type !== 'start' && !targetNodes.includes(node.id)) {
      warnings.push({ type: 'unconnected_input', message: `节点 "${node.data.label || node.type}" 没有输入连接`, nodeId: node.id, suggestion: '请连接上游节点到该节点' });
    }
    
    if (node.type !== 'end') {
      const outgoingEdges = edges.filter(e => e.source === node.id);
      if (outgoingEdges.length === 0) {
        warnings.push({ type: 'no_output', message: `节点 "${node.data.label || node.type}" 没有输出连接`, nodeId: node.id, suggestion: '请从该节点连接到下游节点' });
      }
    }
    
    if (node.type === 'condition') {
      const trueEdges = edges.filter(e => e.source === node.id && e.sourceHandle === 'true');
      const falseEdges = edges.filter(e => e.source === node.id && e.sourceHandle === 'false');
      if (trueEdges.length === 0 && falseEdges.length === 0) {
        warnings.push({ type: 'condition_no_output', message: `条件节点 "${node.data.label}" 缺少输出连接`, nodeId: node.id, suggestion: '请连接满足和不满足两个分支' });
      }
    }
    
    if (node.type === 'loop') {
      const bodyEdges = edges.filter(e => e.source === node.id && e.sourceHandle === 'body');
      if (bodyEdges.length === 0) {
        warnings.push({ type: 'loop_no_body', message: `循环节点 "${node.data.label}" 缺少循环体连接`, nodeId: node.id, suggestion: '请连接循环体输出' });
      }
    }
    
    if (!sourceNodes.includes(node.id) && !targetNodes.includes(node.id) && node.type !== 'start') {
      warnings.push({ type: 'isolated_node', message: `节点 "${node.data.label || node.type}" 完全孤立`, nodeId: node.id, suggestion: '请连接该节点到工作流中' });
    }
  });
  
  edges.forEach(edge => {
    if (!nodeIds.includes(edge.source)) {
      errors.push({ type: 'invalid_source', message: `连接线源节点不存在`, suggestion: '请删除无效的连接线' });
    }
    if (!nodeIds.includes(edge.target)) {
      errors.push({ type: 'invalid_target', message: `连接线目标节点不存在`, suggestion: '请删除无效的连接线' });
    }
  });
  
  if (edges.length > 0 && startNodes.length === 1) {
    const visited = new Set();
    const hasCycle = detectCycle(startNodes[0].id, edges, visited, new Set());
    if (hasCycle) {
      warnings.push({ type: 'cycle_detected', message: '检测到循环依赖', suggestion: '请检查是否存在死循环' });
    }
  }
  
  return { errors, warnings };
};

const detectCycle = (nodeId, edges, visited, inStack) => {
  if (inStack.has(nodeId)) return true;
  if (visited.has(nodeId)) return false;
  
  visited.add(nodeId);
  inStack.add(nodeId);
  
  const outgoingEdges = edges.filter(e => e.source === nodeId);
  for (const edge of outgoingEdges) {
    if (detectCycle(edge.target, edges, visited, inStack)) {
      return true;
    }
  }
  
  inStack.delete(nodeId);
  return false;
};

export const alignNodes = (nodes, alignType) => {
  if (nodes.length < 2) return;
  
  switch (alignType) {
    case 'left': {
      const minX = Math.min(...nodes.map(n => n.position.x));
      nodes.forEach(node => { node.position.x = minX; });
      break;
    }
    case 'center': {
      const xs = nodes.map(n => n.position.x);
      const minX = Math.min(...xs);
      const maxX = Math.max(...xs);
      const centerX = (minX + maxX) / 2;
      nodes.forEach(node => { node.position.x = centerX; });
      break;
    }
    case 'right': {
      const maxX = Math.max(...nodes.map(n => n.position.x));
      nodes.forEach(node => { node.position.x = maxX; });
      break;
    }
    case 'top': {
      const minY = Math.min(...nodes.map(n => n.position.y));
      nodes.forEach(node => { node.position.y = minY; });
      break;
    }
    case 'middle': {
      const ys = nodes.map(n => n.position.y);
      const minY = Math.min(...ys);
      const maxY = Math.max(...ys);
      const centerY = (minY + maxY) / 2;
      nodes.forEach(node => { node.position.y = centerY; });
      break;
    }
    case 'bottom': {
      const maxY = Math.max(...nodes.map(n => n.position.y));
      nodes.forEach(node => { node.position.y = maxY; });
      break;
    }
  }
};

export const distributeNodes = (nodes, distributeType) => {
  if (nodes.length < 3) return;
  
  if (distributeType === 'horizontal') {
    const sortedNodes = [...nodes].sort((a, b) => a.position.x - b.position.x);
    const minX = sortedNodes[0].position.x;
    const maxX = sortedNodes[sortedNodes.length - 1].position.x;
    const totalWidth = maxX - minX;
    const gap = totalWidth / (sortedNodes.length - 1);
    sortedNodes.forEach((node, index) => { node.position.x = minX + index * gap; });
  } else if (distributeType === 'vertical') {
    const sortedNodes = [...nodes].sort((a, b) => a.position.y - b.position.y);
    const minY = sortedNodes[0].position.y;
    const maxY = sortedNodes[sortedNodes.length - 1].position.y;
    const totalHeight = maxY - minY;
    const gap = totalHeight / (sortedNodes.length - 1);
    sortedNodes.forEach((node, index) => { node.position.y = minY + index * gap; });
  }
};

export const autoLayoutNodes = (nodes, edges) => {
  if (nodes.length === 0) return;
  
  const NODE_WIDTH = 180;
  const NODE_HEIGHT = 80;
  const HORIZONTAL_GAP = 200;
  const VERTICAL_GAP = 100;
  const START_X = 50;
  const START_Y = 200;
  
  const nodeMap = new Map();
  nodes.forEach(node => nodeMap.set(node.id, node));
  
  const inDegree = new Map();
  const outEdges = new Map();
  nodes.forEach(node => {
    inDegree.set(node.id, 0);
    outEdges.set(node.id, []);
  });
  
  edges.forEach(edge => {
    inDegree.set(edge.target, (inDegree.get(edge.target) || 0) + 1);
    outEdges.get(edge.source)?.push(edge.target);
  });
  
  const levels = [];
  const visited = new Set();
  const queue = [];
  
  nodes.forEach(node => {
    if (inDegree.get(node.id) === 0 || node.type === 'start') {
      queue.push(node.id);
      visited.add(node.id);
    }
  });
  
  if (queue.length === 0 && nodes.length > 0) {
    queue.push(nodes[0].id);
    visited.add(nodes[0].id);
  }
  
  while (queue.length > 0) {
    const levelSize = queue.length;
    const currentLevel = [];
    
    for (let i = 0; i < levelSize; i++) {
      const nodeId = queue.shift();
      currentLevel.push(nodeId);
      
      const targets = outEdges.get(nodeId) || [];
      targets.forEach(targetId => {
        if (!visited.has(targetId)) {
          const newInDegree = (inDegree.get(targetId) || 0) - 1;
          inDegree.set(targetId, newInDegree);
          if (newInDegree <= 0) {
            visited.add(targetId);
            queue.push(targetId);
          }
        }
      });
    }
    
    if (currentLevel.length > 0) {
      levels.push(currentLevel);
    }
  }
  
  const remainingNodes = nodes.filter(node => !visited.has(node.id));
  if (remainingNodes.length > 0) {
    levels.push(remainingNodes.map(node => node.id));
  }
  
  levels.forEach((level, levelIndex) => {
    const totalLevelHeight = level.length * NODE_HEIGHT + (level.length - 1) * VERTICAL_GAP;
    const startY = START_Y - totalLevelHeight / 2 + NODE_HEIGHT / 2;
    
    level.forEach((nodeId, nodeIndex) => {
      const node = nodeMap.get(nodeId);
      if (node) {
        node.position.x = START_X + levelIndex * (NODE_WIDTH + HORIZONTAL_GAP);
        node.position.y = startY + nodeIndex * (NODE_HEIGHT + VERTICAL_GAP);
      }
    });
  });
};