export const nodeDisplayProps = {
  configMode: {
    type: Boolean,
    default: false
  },
  compact: {
    type: Boolean,
    default: false
  },
  anchorMode: {
    type: String,
    default: 'vertical',
    validator: (value) => ['vertical', 'horizontal'].includes(value)
  }
};
