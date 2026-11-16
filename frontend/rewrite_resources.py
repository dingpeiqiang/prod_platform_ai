import io

def process(path):
    with io.open(path, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    start = {}
    for i, ln in enumerate(lines):
        if ln.startswith('  resourceSections: ['):
            start['resource'] = i
        elif ln.startswith('  optionalSections:'):
            start['optional'] = i
        elif ln.startswith('  previewSections: ['):
            start['preview'] = i

    if 'resource' not in start:
        raise RuntimeError('resource marker missing in ' + path)

    res_start = start['resource']
    # keep from: optionalSections if present, else previewSections
    keep_from = start.get('optional', start.get('preview'))
    if keep_from is None:
        raise RuntimeError('no end marker in ' + path)

    out = lines[:res_start]
    new_res = (NEW_RESOURCE + '\n').splitlines(True)
    if not new_res[-1].endswith('\n'):
        new_res[-1] += '\n'
    out += new_res
    out += lines[keep_from:]

    with io.open(path, 'w', encoding='utf-8', newline='') as f:
        f.writelines(out)
    print('OK', path)

NEW_RESOURCE = """  resourceSections: [
    {
      key: 'data-resource',
      title: '數據資源',
      icon: 'fa-mobile-screen',
      fields: [
        {
          key: 'dataAmount',
          label: '資源量',
          value: '20',
          type: 'number',
          unit: 'GB',
          tag: 'ai',
          tagReason: '預付費套餐默認配置20GB數據資源，滿足基礎上網需求',
        },
        {
          key: 'dataRoamArea',
          label: '漫遊地',
          value: ['domestic', 'hk-mo-tw'],
          type: 'checkbox-group',
          tag: 'ai',
          tagReason: '默認開通大陸及港澳台漫遊',
          options: [
            { value: 'domestic', label: '大陸' },
            { value: 'hk-mo-tw', label: '港澳台' },
            { value: 'asia', label: '亞洲' },
            { value: 'europe', label: '歐洲' },
            { value: 'global', label: '全球' },
          ],
        },
        {
          key: 'throttlePolicy',
          label: '限速策略',
          value: 'pcrf',
          type: 'select',
          tag: 'ai',
          tagReason: '默認採用PCRF限速策略，保障網絡公平使用',
          options: [
            { value: 'pcrf', label: 'PCRF' },
            { value: 'gx', label: 'GX' },
          ],
        },
        {
          key: 'throttleTemplate',
          label: '限速模版',
          type: 'table',
          tag: 'ai',
          tagReason: '按業務模板默認配置限速規則（動態行，可新增）',
          rows: [
            { maxRate: '1Mbps', triggerAmount: '20GB', remark: '達量後限速1Mbps' },
            { maxRate: '0.5Mbps', triggerAmount: '50GB', remark: '高量用户限速0.5Mbps' },
          ],
          columns: [
            { key: 'maxRate', label: '限速速率', type: 'text', placeholder: '如 1Mbps' },
            { key: 'triggerAmount', label: '觸發資源量', type: 'text', placeholder: '如 20GB' },
            { key: 'remark', label: '說明', type: 'text', placeholder: '限速說明' },
          ],
        },
      ],
    },
    {
      key: 'voice-resource',
      title: '語音資源',
      icon: 'fa-phone',
      fields: [
        {
          key: 'voiceAmount',
          label: '資源量',
          value: '100',
          type: 'number',
          unit: '分鐘',
          tag: 'ai',
          tagReason: '默認配置100分鐘語音資源，便於國際間互通',
        },
        {
          key: 'voiceRoamArea',
          label: '漫遊地',
          value: ['domestic'],
          type: 'checkbox-group',
          tag: 'ai',
          tagReason: '語音漫遊默認僅限大陸使用',
          options: [
            { value: 'domestic', label: '大陸' },
            { value: 'hk-mo-tw', label: '港澳台' },
            { value: 'asia', label: '亞洲' },
            { value: 'global', label: '全球' },
          ],
        },
        {
          key: 'voicePeerArea',
          label: '對端歸屬地',
          value: ['domestic'],
          type: 'checkbox-group',
          tag: 'ai',
          tagReason: '對端歸屬地默認為大陸',
          options: [
            { value: 'domestic', label: '大陸' },
            { value: 'hk-mo-tw', label: '港澳台' },
            { value: 'asia', label: '亞洲' },
            { value: 'global', label: '全球' },
          ],
        },
      ],
    },
    {
      key: 'sms-resource',
      title: '短信資源',
      icon: 'fa-comment-dots',
      fields: [
        {
          key: 'smsAmount',
          label: '資源量',
          value: '50',
          type: 'number',
          unit: '條',
          tag: 'ai',
          tagReason: '默認配置50條短信資源',
        },
        {
          key: 'smsRoamArea',
          label: '漫遊地',
          value: ['domestic'],
          type: 'checkbox-group',
          tag: 'ai',
          tagReason: '短信漫遊默認僅限大陸',
          options: [
            { value: 'domestic', label: '大陸' },
            { value: 'hk-mo-tw', label: '港澳台' },
            { value: 'asia', label: '亞洲' },
            { value: 'global', label: '全球' },
          ],
        },
        {
          key: 'smsPeerArea',
          label: '對端歸屬地',
          value: ['domestic'],
          type: 'checkbox-group',
          tag: 'ai',
          tagReason: '對端歸屬地默認為大陸',
          options: [
            { value: 'domestic', label: '大陸' },
            { value: 'hk-mo-tw', label: '港澳台' },
            { value: 'asia', label: '亞洲' },
            { value: 'global', label: '全球' },
          ],
        },
      ],
    },
  ],"""

base = r"E:\思特奇\POS\联通国际交流0902\chat-skeleton\src\config"
process(base + r"\launchOptimizeData.js")
process(base + r"\campusDraftData.js")
