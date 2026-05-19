import StartNode from './nodes/StartNode.vue';
import EndNode from './nodes/EndNode.vue';
import PromptNode from './nodes/PromptNode.vue';
import LlmNode from './nodes/LlmNode.vue';
import ToolNode from './nodes/ToolNode.vue';
import ConditionNode from './nodes/ConditionNode.vue';
import LoopNode from './nodes/LoopNode.vue';
import VariableNode from './nodes/VariableNode.vue';
import HttpNode from './nodes/HttpNode.vue';
import CodeNode from './nodes/CodeNode.vue';
import ParserNode from './nodes/ParserNode.vue';
import FormNode from './nodes/FormNode.vue';
import KnowledgeNode from './nodes/KnowledgeNode.vue';
import UserInputNode from './nodes/UserInputNode.vue';

export const NODE_CONFIG_COMPONENTS = {
  start: StartNode,
  end: EndNode,
  prompt: PromptNode,
  llm: LlmNode,
  tool: ToolNode,
  condition: ConditionNode,
  loop: LoopNode,
  userInput: UserInputNode,
  variable: VariableNode,
  http: HttpNode,
  code: CodeNode,
  parser: ParserNode,
  form: FormNode,
  knowledgeBase: KnowledgeNode
};

export const NODE_TYPE_LABELS = {
  start: '开始',
  end: '结束',
  condition: '条件分支',
  loop: '循环',
  prompt: '提示词',
  llm: 'LLM调用',
  tool: '工具调用',
  http: 'HTTP请求',
  code: '代码执行',
  variable: '变量赋值',
  parser: '输出解析',
  form: '表单',
  knowledgeBase: '知识库',
  userInput: '用户输入'
};
