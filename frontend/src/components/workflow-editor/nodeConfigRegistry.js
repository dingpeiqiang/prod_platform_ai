import StartNode from './nodes/StartNode.vue';
import EndNode from './nodes/EndNode.vue';
import LlmNode from './nodes/LlmNode.vue';
import ToolNode from './nodes/ToolNode.vue';
import ConditionNode from './nodes/ConditionNode.vue';
import LoopNode from './nodes/LoopNode.vue';
import HttpNode from './nodes/HttpNode.vue';
import CodeNode from './nodes/CodeNode.vue';
import ParserNode from './nodes/ParserNode.vue';
import FormNode from './nodes/FormNode.vue';
import KnowledgeNode from './nodes/KnowledgeNode.vue';
import UserInputNode from './nodes/UserInputNode.vue';
import ValidateNode from './nodes/ValidateNode.vue';

export const NODE_CONFIG_COMPONENTS = {
  start: StartNode,
  end: EndNode,
  llm: LlmNode,
  tool: ToolNode,
  condition: ConditionNode,
  loop: LoopNode,
  userInput: UserInputNode,
  http: HttpNode,
  code: CodeNode,
  parser: ParserNode,
  form: FormNode,
  knowledgeBase: KnowledgeNode,
  validate: ValidateNode
};

export const NODE_TYPE_LABELS = {
  start: '开始',
  end: '结束',
  condition: '条件分支',
  loop: '循环',
  llm: 'LLM调用',
  tool: 'MCP工具',
  http: 'HTTP请求',
  code: '代码执行',
  parser: '输出解析',
  form: '表单',
  knowledgeBase: '知识库',
  userInput: '用户输入',
  validate: '数据验证'
};
