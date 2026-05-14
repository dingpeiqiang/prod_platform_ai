export class KeyboardShortcuts {
  constructor() {
    this.handlers = {};
    this.enabled = true;
  }

  register(key, handler) {
    this.handlers[key] = handler;
  }

  unregister(key) {
    delete this.handlers[key];
  }

  enable() {
    this.enabled = true;
  }

  disable() {
    this.enabled = false;
  }

  handleEvent(event) {
    if (!this.enabled) return;

    const key = this.getKeyString(event);
    
    if (this.handlers[key]) {
      event.preventDefault();
      this.handlers[key](event);
      return true;
    }
    
    return false;
  }

  getKeyString(event) {
    const parts = [];
    
    if (event.ctrlKey || event.metaKey) {
      parts.push('ctrl');
    }
    if (event.shiftKey) {
      parts.push('shift');
    }
    if (event.altKey) {
      parts.push('alt');
    }
    
    const key = event.key.toLowerCase();
    if (key === 'control') return '';
    if (key === 'shift') return '';
    if (key === 'alt') return '';
    if (key === 'meta') return '';
    
    parts.push(key);
    
    return parts.join('+');
  }
}

export const defaultShortcuts = [
  { key: 'ctrl+z', description: '撤销' },
  { key: 'ctrl+y', description: '重做' },
  { key: 'ctrl+s', description: '保存' },
  { key: 'ctrl+c', description: '复制' },
  { key: 'ctrl+v', description: '粘贴' },
  { key: 'delete', description: '删除选中节点' },
  { key: 'backspace', description: '删除选中节点' },
  { key: 'ctrl+shift+a', description: '全选' },
  { key: 'escape', description: '取消选择' },
  { key: 'ctrl+g', description: '切换左侧面板' },
  { key: 'ctrl+l', description: '切换右侧面板' },
  { key: 'arrowup', description: '向上移动选中节点' },
  { key: 'arrowdown', description: '向下移动选中节点' },
  { key: 'arrowleft', description: '向左移动选中节点' },
  { key: 'arrowright', description: '向右移动选中节点' }
];