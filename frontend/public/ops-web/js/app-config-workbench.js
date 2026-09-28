(function(global){
  const { ref, reactive, computed, onMounted, onUnmounted, watch } = global.Vue;
  const ElMessage = global.ElMessage;
  



const MIN_WIDTH = 400
const MAX_WIDTH = 900
const DEFAULT_WIDTH = 560


const { launchProductConfig, testReport, campusProductConfig } = window.__CFG;
const ConfigWorkbench = {
  __name: 'ProductConfigPanel',
  props: {
  archiveSnapshot: { type: Object, default: null },
  // 瀵硅瘽寮忛厤缃細鐢?AI 浠庣敤鎴烽渶姹傝В鏋愬嚭鐨勫瓧娈靛€硷紙{ 瀛楁key: 鍊?}锛夈€備紶鍏ヤ簡鍗充互"瀵硅瘽寮忛厤缃?妯″紡娓叉煋锛?
  // 浠呴濉凡瑙ｆ瀽瀛楁锛屽叾浣欏瓧娈电疆绌猴紝绛夊緟"涓€閿櫤濉?鐢?AI 鎺ㄨ崘鍊煎～鍏呫€?
  parsedFields: { type: Object, default: null },
  // 瀵硅瘽寮忛厤缃笓鐢ㄥ伐鍗曞彿锛氬伐浣滃彴椤电/搴曢儴宸ュ崟鍙峰潎浠ユ湰鍊煎睍绀猴紝閬垮厤璇诲彇浜у搧杩愯惀鍔╂墜鍏ㄥ眬 launch 鍗曞彿
  workOrderId: { type: String, default: '' },
  // 鏂囦欢瀵煎叆鎵归噺閰嶇疆锛氫紶鍏ヤ簡鍒欒宸ヤ綔鍙板浜?鑽夌鎵归噺閰嶇疆"妯″紡锛?
  // 搴曢儴灞曠ず 绗瑇/鍏眣 + 涓?涓嬩竴涓?瀵艰埅锛屾彁浜ゅ悗鑷姩璺充笅涓€涓崏绋?
  batchContext: { type: Object, default: null },
  // 鍦烘櫙涓撳睘閰嶇疆 schema锛氬璇濆紡/鎵归噺鍦烘櫙鍙紶鍏ヤ笌 launchOptimizeData 鍚屾瀯鐨勯厤缃暟鎹?
  //锛堝鏍″洯闈掓槬鍗?campusDraftData.campusProductConfig锛夈€傛湭浼犲叆鏃跺洖钀藉埌鍏ㄥ眬 launchProductConfig銆?
  schemaConfig: { type: Object, default: null },
  // 澶氬晢鍝侊細鍙€夊晢鍝佸垪琛ㄤ笌褰撳墠閫変腑鍟嗗搧 id锛堢敱鐖剁粍浠?App 娉ㄥ叆锛夛紝鐢ㄤ簬澶撮儴鍟嗗搧鍚嶄笅鎷夊垏鎹?
  products: { type: Array, default: () => [] },
  activeProductId: { type: String, default: '' },
},
  emits: ['close', 'submitted', 'go-prev', 'go-next', 'batch-submitted', 'copy-init', 'copy-rename', 'dialogue-rename', 'panel-action', 'select-product'],
  setup(__props, { expose: __expose, emit: __emit }) {

const emit = __emit

// 鍙妗ｆ妯″紡锛氫紶锟?archiveSnapshot 鏃讹紝鏈潰鏉夸互"閰嶇疆妗ｆ"褰㈠紡鍙灞曠ず鏌愪唤褰掓。宸ュ崟鐨勫畬鏁撮厤锟?// archiveSnapshot = { productInfo: { workOrderId, prodPrcName, templateLabel, status }, values: { 瀛楁key:锟?} }
const props = __props

// 澶嶅埗妯″紡锛氬熀浜庢煇浠藉瓨閲忓晢鍝?褰掓。妗ｆ銆屽鍒躲€嶅嚭鐨勫彲缂栬緫鏂板晢鍝侊紙淇濇寔鍙妗ｆ棰勫～鐨勫瓧娈靛€间负妯℃澘鏁版嵁锛?
const copyMode = ref(false)
// 澶嶅埗妯″紡涓嬬殑鏂板伐鍗曞彿锛堣繘鍏ュ鍒舵椂鐢熸垚涓€娆★級
const copyWorkOrderId = ref('')
const isArchive = computed(() => !!props.archiveSnapshot && !copyMode.value)
const dialogueMode = computed(() => !!props.parsedFields)
// 鍔ㄦ€侀瑙堟ā寮忥細瀵硅瘽寮忛厤缃笌鍙妗ｆ锛堝瓨閲忓揩鐓э級閮芥寜 formData 瀹為檯鍊兼覆鏌撴墜鏈洪瑙?鍏嶅～鍗?鐭俊妯℃澘锛?
// 閬垮厤"鐣呬韩59鍏冮潚鏄ョ増"绛?launch 闈欐€佺ず渚嬫枃妗堟硠婕忓埌鏌ヨ鍔╂墜鐨勬。妗堣皟闃呰鍥句腑
const dynamicPreview = computed(() => dialogueMode.value || isArchive.value)
// 鎵归噺閰嶇疆妯″紡锛氭壒閲忎笂涓嬫枃瀛樺湪涓旈潪鍙妗ｆ
const batchMode = computed(() => !!props.batchContext && !isArchive.value)
// 瀵硅瘽寮忛厤缃嫭绔嬬姸鎬侊細浠呭湪璇ュ満鏅唴娴佽浆锛堣崏绋库啋寰呭鏍革級锛屼笉璇诲啓浜у搧杩愯惀鍔╂墜鍏ㄥ眬 launch 鐘舵€?
const dialogueStatus = ref('draft')

// ========== 闈㈡澘瀹藉害鎷栨嫿 ==========
const panelWidth = ref(DEFAULT_WIDTH)
const resizing = ref(false)

// ========== 澶氬晢鍝佸垏鎹紙澶撮儴妯悜骞抽摵锛屾孩鍑烘敹杩涒€滄洿澶氣€濓級 ==========
// 鍟嗗搧鍦ㄥご閮ㄦí鍚戝钩閾哄睍绀猴紝瀹藉害瓒冲鏃跺叏閮ㄥ苟鎺掞紱鏀句笉涓嬬殑鑷姩鏀惰繘鈥滄洿澶氣€濅笅鎷夊垪琛?
const products = computed(() => props.products || [])
const activeProductId = computed(() => props.activeProductId || '')
const productMenuOpen = ref(false)
const ppStripRef = ref(null)
const ppVisibleCount = ref(Infinity)

const onSelectProduct = (p) => {
  productMenuOpen.value = false
  if (p && p.id !== activeProductId.value) emit('select-product', p)
}
const toggleProductMenu = () => { productMenuOpen.value = !productMenuOpen.value }

// 鍙钩閾哄睍绀虹殑鍟嗗搧锛堟湭婧㈠嚭閮ㄥ垎锛?
const visibleProducts = computed(() => {
  const list = products.value
  if (ppVisibleCount.value >= list.length) return list
  return list.slice(0, Math.max(0, ppVisibleCount.value))
})
const measureChips = () => {
  const strip = ppStripRef.value
  if (!strip) return
  const list = products.value
  const nodes = strip.querySelectorAll('.pp-chip-slot')
  const gap = 8
  const available = strip.clientWidth
  let used = 0
  let count = 0
  for (let i = 0; i < nodes.length; i++) {
    const w = nodes[i].offsetWidth + (count > 0 ? gap : 0)
    if (used + w <= available) { used += w; count++ } else { break }
  }
  ppVisibleCount.value = count >= list.length ? list.length : count
}

const onDocClick = (e) => {
  if (productMenuOpen.value && !e.target.closest('.product-picker')) {
    productMenuOpen.value = false
  }
}
let ppResizeObserver = null
onMounted(() => {
  document.addEventListener('click', onDocClick)
  requestAnimationFrame(measureChips)
  if (window.ResizeObserver && ppStripRef.value) {
    ppResizeObserver = new ResizeObserver(() => measureChips())
    ppResizeObserver.observe(ppStripRef.value)
  }
  window.addEventListener('resize', measureChips)
})
onUnmounted(() => {
  document.removeEventListener('click', onDocClick)
  window.removeEventListener('resize', measureChips)
  if (ppResizeObserver) { ppResizeObserver.disconnect(); ppResizeObserver = null }
})
watch(products, () => requestAnimationFrame(measureChips))

const startResize = (e) => {
  e.preventDefault()
  resizing.value = true
  const startX = e.touches ? e.touches[0].clientX : e.clientX
  const startWidth = panelWidth.value

  const onMove = (ev) => {
    const clientX = ev.touches ? ev.touches[0].clientX : ev.clientX
    const delta = startX - clientX
    panelWidth.value = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, startWidth + delta))
  }

  const onUp = () => {
    resizing.value = false
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    document.removeEventListener('touchmove', onMove)
    document.removeEventListener('touchend', onUp)
  }

  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
  document.addEventListener('touchmove', onMove, { passive: false })
  document.addEventListener('touchend', onUp)
}

onUnmounted(() => {
  resizing.value = false
})

// ========== Tab 鍒囨崲 ==========
const baseTabs = [
  { key: 'demand', label: '闇€姹傛彁鎶? },
  { key: 'scheme', label: '閰嶇疆鏂规' },
  { key: 'config', label: '閰嶇疆淇℃伅' },
  { key: 'summary', label: '淇℃伅棰勮' },
  { key: 'audit', label: '绋芥牳鎶ュ憡' },
  { key: 'calibrate', label: '鏍″噯鎶ュ憡' },
  { key: 'testrep', label: '娴嬭瘯鎶ュ憡' },
  { key: 'approval', label: '瀹℃壒鐭╅樀' },
  { key: 'monitor', label: '鐩戞帶杩愮淮' },
]
const tabs = computed(() => baseTabs)
// 宸ヤ綔娴佺幆鑺?-> 榛樿椤电锛? 鐜妭鍙ｅ緞锛夛細
// 闇€姹傛彁鎶モ啋闇€姹傛彁鎶?/ 闇€姹傚垎鏋愨啋閰嶇疆鏂规 / 鏅鸿兘閰嶇疆鈫掗厤缃俊鎭€佷俊鎭瑙?/ 瑙勬牸绋芥牳鈫掔ń鏍告姤鍛?/
// 璧勮垂鏍″噯鈫掓牎鍑嗘姤鍛?/ 鑷姩鍖栨祴璇曗啋娴嬭瘯鎶ュ憡 / 涓婄嚎瀹℃壒鈫掑鎵圭煩闃?/ 鐩戞帶杩愮淮鈫掔洃鎺ц繍缁?
const DEFAULT_TAB_BY_STAGE = { '00':'demand', '01':'scheme', '02':'config', '03':'audit', '05':'calibrate', '04':'testrep', '06':'approval', '07':'monitor', '08':'approval', '09':'monitor', '10':'monitor' }
const activeTab = ref((window.__WB_QUERY && DEFAULT_TAB_BY_STAGE[window.__WB_QUERY.stage]) || 'config')

// 椤电鑱斿姩锛氭寜宸ヤ綔娴佺幆鑺傚垏鎹㈠埌瀵瑰簲椤甸潰tab
// 鑷姩鍖栨祴璇曪紙04锛夆啋 娴嬭瘯鎶ュ憡
const showTestReport = () => {
  if (currentStatus.value === 'testing' || currentStatus.value === 'tested' || testReport.uploaded) {
    activeTab.value = 'testrep'
  }
}

// 涓婄嚎瀹℃壒锛?6锛夆啋 瀹℃壒鐭╅樀
const showApproval = () => {
  activeTab.value = 'approval'
}

// 鐩戞帶杩愮淮锛?7/09/10锛夆啋 鐩戞帶杩愮淮
const showMonitor = () => {
  activeTab.value = 'monitor'
}

// ========== 鍟嗗搧鍩烘湰淇℃伅 ==========
const productInfo = computed(() => {
  if (isArchive.value && props.archiveSnapshot.productInfo) {
    return {
      workOrderId: props.archiveSnapshot.productInfo.workOrderId,
      prodPrcName: props.archiveSnapshot.productInfo.prodPrcName,
      templateLabel: props.archiveSnapshot.productInfo.templateLabel,
      status: props.archiveSnapshot.productInfo.status || 'listed',
    }
  }
  // 瀵硅瘽寮忛厤缃細鏍囬娌跨敤鐢ㄦ埛瑙ｆ瀽鍑虹殑濂楅鍚嶇О锛堣嫢鏈夛級锛涙湭鍛藉悕鏃剁敤鍗犱綅鏍囬锛?
  // 宸ュ崟鍙峰拰鐘舵€佷娇鐢ㄦ湰鍦烘櫙鑷繁鐨勫€硷紙涓嶈鍐欎骇鍝佽繍钀ュ姪鎵嬪叏灞€ launch 鐘舵€侊級锛屼繚璇佽鍦烘櫙鍐呰嚜娲?
  if (dialogueMode.value && props.parsedFields) {
    // 鍟嗗搧鍚嶄紭鍏堝彇鐢ㄦ埛鍦ㄨ〃鍗曚腑瀹為檯缂栬緫鍚庣殑鍚嶇О锛屾敼鍚嶅悗闈㈡澘鏍囬/棰勮瀹炴椂鍚屾
    const editedName = String(formData.prodName ?? '').trim()
    const name = editedName || props.parsedFields.prodName || '鏍″洯濂楅锛堝緟鍛藉悕锛?
    return {
      ...launchProductConfig.productInfo,
      prodPrcName: name,
      workOrderId: props.workOrderId || '',
      status: dialogueStatus.value,
    }
  }
  // 澶嶅埗妯″紡锛氬熀浜庡瓨閲忓晢鍝?褰掓。妗ｆ鐢熸垚鐨勬柊鍟嗗搧锛堣崏绋挎€併€佹柊宸ュ崟鍙枫€佸晢鍝佸悕杩藉姞鈥滐紙澶嶅埗锛夆€濓級
  if (copyMode.value) {
    const src = props.archiveSnapshot?.productInfo || {}
    const vals = props.archiveSnapshot?.values || {}
    // 鍟嗗搧鍚嶄紭鍏堝彇鐢ㄦ埛鍦ㄨ〃鍗曚腑瀹為檯缂栬緫鍚庣殑鍚嶇О锛屼繚璇佹敼鍚嶅悗闈㈡澘鏍囬/棰勮瀹炴椂鍚屾
    const editedName = String(formData.prodName ?? '').trim()
    return {
      ...launchProductConfig.productInfo,
      workOrderId: copyWorkOrderId.value,
      prodPrcName: editedName || (vals.prodName || src.prodPrcName || '') + '锛堝鍒讹級',
      templateLabel: src.templateLabel || launchProductConfig.productInfo.templateLabel,
      status: copyStatus.value,
    }
  }
  return launchProductConfig.productInfo
})

const currentStatus = computed(() => {
  if (submitted.value) {
    return productInfo.value.status === 'completed' ? 'completed' :
           productInfo.value.status === 'listed' ? 'listed' :
           productInfo.value.status === 'testing' ? 'testing' :
           productInfo.value.status === 'tested' ? 'tested' :
           productInfo.value.status === 'filing' ? 'filing' :
           productInfo.value.status === 'channel' ? 'channel' : 'submitted'
  }
  return productInfo.value.status
})

const footerSubmittedText = computed(() => {
  if (currentStatus.value === 'listed') return '宸蹭笂鏋堕攢鍞腑'
  if (currentStatus.value === 'completed') return '宸插鎵归€氳繃'
  if (currentStatus.value === 'testing') return '瀹℃壒閫氳繃锛岃繘鍏ユ祴璇?
  if (currentStatus.value === 'tested') return '娴嬭瘯瀹屾垚锛岀瓑寰呭妗堝叕绀?
  if (currentStatus.value === 'filing') return filing.submitted ? '澶囨宸叉彁浜わ紝绛夊緟閫氳繃' : '澶囨鍏ず涓紝寰呮彁浜ゅ妗?
  if (currentStatus.value === 'channel') return '寰呴€夋嫨閿€鍞笭閬?
  return '宸叉彁浜わ紝杩涘叆瀹℃壒闃舵'
})

// ========== 琛ㄥ崟鏁版嵁 ==========
// 鍦烘櫙 schema锛氶粯璁や负 launchProductConfig锛涘璇濆紡/鎵归噺鍦烘櫙浼犲叆 schemaConfig 鏃讹紙濡傛牎鍥潚鏄ュ崱锛変娇鐢ㄨ鍦烘櫙涓撳睘閰嶇疆锛?
// 淇濊瘉鍚勫満鏅瓧娈?AI鎺ㄨ崘鐞嗙敱涓嶅悓锛堝挨鍏堕伩鍏嶆妸"娌跨敤鍘熷椁?杩欑被浠呴€傜敤浜庡瓨閲忓鍒?鍚姩浼樺寲鐨勮〃杩板甫鍏ユ柊寤哄椁愬満鏅級
const config = computed(() => {
  // 浠呭璇濆紡/鎵归噺锛坧arsedFields 瀛樺湪锛変笖鏄惧紡浼犲叆 schemaConfig 鏃跺垏鎹㈠満鏅?schema
  if (props.schemaConfig && !props.archiveSnapshot) return props.schemaConfig
  return launchProductConfig
})
const formData = reactive({})
const modifiedFields = reactive({})

// 褰掓。锛堥厤缃。妗堬級妯″紡锛氭祴璇曟姤锟?澶囨鍏ず/閿€鍞笭閬撲笁涓〉绛炬敼鐢ㄨ宸ュ崟鑷韩鐨勫巻鍙插揩鐓э紝
// 鑰岄潪鍏ㄥ眬"褰撳墠姝ｅ湪閰嶇疆鐨勫椁?鐘舵€侊紱闈炲綊妗ｆā寮忓洖钀藉埌鍏ㄥ眬鏁版嵁婧愩€?
const archiveTestReport = computed(() => (isArchive.value ? (props.archiveSnapshot.testReport || {}) : testReport))

// 瀵硅瘽寮忛厤缃細璁板綍"鐢ㄦ埛宸茶В鏋?瀛楁锛堟覆鏌撲负"宸茶В鏋?褰╄壊鏍囩锛?
const parsedFilled = reactive({})
// 璁板綍鐢?AI 鑷姩琛ュ叏鐨勫瓧娈碉紙娓叉煋涓?AI鎺ㄨ崘"鏍囩锛?
const aiFilled = reactive({})

// 鍒濆鍖栬〃鍗曟暟鎹?
const initFormData = () => {
  // 瀵硅瘽寮忛厤缃ā寮忥細鎵撳紑宸ヤ綔鍙板嵆鑷姩濉叆鈥斺€斿凡瑙ｆ瀽瀛楁鏍?宸茶В鏋?锛屾湭瑙ｆ瀽瀛楁鐢?AI 鎸夋ā鏉胯嚜鍔ㄨˉ鍏紙鏍?澶嶇敤"鎴?AI鎺ㄨ崘"锛?
  if (dialogueMode.value) {
    const vals = props.parsedFields || {}
    const applyField = (key, field) => {
      if (vals[key] !== undefined && vals[key] !== '') {
        // 鐢ㄦ埛宸茶В鏋愬瓧娈碉細鐩存帴濉叆骞舵爣"宸茶В鏋?
        formData[key] = vals[key]
        parsedFilled[key] = '鏍规嵁鐢ㄦ埛闇€姹傝В鏋愬緱鍑?
        aiFilled[key] = undefined
      } else {
        // 鏈В鏋愬瓧娈碉細AI 鑷姩琛ュ叏妯℃澘鎺ㄨ崘鍊?
        parsedFilled[key] = undefined
        // 鍟嗗搧鍚嶇О鏈敱鐢ㄦ埛缁欏嚭鏃朵繚鎸佺┖锛岀敱鏍囬鍗犱綅灞曠ず锛岃€岄潪澶嶇敤绀轰緥濂楅鍚?
        if (key === 'prodName' || key === 'prodPrcName') {
          formData[key] = ''
          aiFilled[key] = undefined
          return
        }
        if (field.tag === 'reuse') {
          // 瀵硅瘽寮?鎵归噺閰嶇疆鏃犲師濂楅鍙鐢紝缁熶竴鎸?AI鎺ㄨ崘"缁欏嚭琛ュ叏鐞嗙敱
          aiFilled[key] = `${field.label}鐢盇I鎸変笟鍔℃ā鏉胯嚜鍔ㄨˉ鍏紙寤鸿鍊硷細${field.value ?? '--'}锛塦
        } else {
          // 瀵硅瘽寮?鎵归噺閰嶇疆涓哄叏鏂板晢鍝侊紙鏃犲師濂楅鍙傝€冿級锛孉I 鎺ㄨ崘鐞嗙敱涓嶅緱寮曠敤
          // "娌跨敤鍘熷椁?澶嶇敤鍘熷椁?鍩轰簬鍘熷椁?绛変粎閫傜敤銆屽瓨閲忓鍒?鍚姩浼樺寲銆嶅満鏅殑琛ㄨ堪
          const reason = field.tagReason || ''
          const tainted = /(娌跨敤|澶嶇敤|鍩轰簬|缁ф壙|鍙傝€億鍙傜収)鍘熷椁?.test(reason)
          aiFilled[key] = tainted
            ? `${field.label}鐢盇I鎸変笟鍔℃ā鏉胯嚜鍔ㄨˉ鍏紙寤鸿鍊硷細${field.value ?? '--'}锛塦
            : (reason || `${field.label}鐢盇I鎸変笟鍔℃ā鏉挎帹鑽恅)
        }
        if (field.type === 'table') {
          formData[key] = (field.rows || []).map((r) => ({ ...r }))
        } else if (field.type === 'checkbox-group') {
          // 瀵硅瘽寮忛厤缃粯璁ゅ嬀閫夛細娓犻亾绫诲瀷閫変腑鍓嶅洓涓笭閬擄紝閫傜敤鍦板尯閫変腑鍥涘窛鍏ㄧ渷
          if (key === 'channelType') {
            formData[key] = ['offline', 'online', 'local-online', 'cmcc-online']
          } else if (key === 'applicableArea') {
            formData[key] = ['sc']
          } else {
            formData[key] = Array.isArray(field.value) ? field.value.slice() : []
          }
        } else {
          // 绱璁㈣喘娆℃暟锛欰I 鎺ㄨ崘榛樿 1锛岄伩鍏嶇暀绌?
          formData[key] = key === 'totalOrderTimes'
            ? '1'
            : (field.value !== undefined ? field.value : '')
        }
      }
    }
    Object.entries(config.value.baseInfo || {}).forEach(([key, field]) => applyField(key, field))
    ;[
      ...(config.value.resourceSections || []).map((s) => s.fields),
      ...(config.value.marketingSections || []).map((s) => s.fields),
      ...(config.value.salesSections || []).map((s) => s.fields),
    ].forEach((fields) => {
      ;(fields || []).forEach((field) => applyField(field.key, field))
    })
    return
  }
  // 鍙妗ｆ妯″紡锛氱敤褰掓。蹇収閲岀殑瀛楁鍊煎～鍏咃紙缂哄け瀛楁鍥炶惤鍒版ā鏉块粯璁ゅ€硷級
  if (isArchive.value && props.archiveSnapshot.values) {
    const vals = props.archiveSnapshot.values
    // 鍩虹淇℃伅鏄璞★紙key鈫抐ield锛夛紝鍏朵綑 sections 鏄瓧娈垫暟缁?
    Object.entries(config.value.baseInfo || {}).forEach(([key, field]) => {
      formData[key] = (vals[key] !== undefined ? vals[key] : field.value)
    })
    ;[
      ...(config.value.resourceSections || []).map((s) => s.fields),
      ...(config.value.marketingSections || []).map((s) => s.fields),
      ...(config.value.salesSections || []).map((s) => s.fields),
    ].forEach((fields) => {
      ;(fields || []).forEach((field) => {
        if (field.type === 'table') {
          formData[field.key] = Array.isArray(vals[field.key])
            ? vals[field.key].map((r) => ({ ...r }))
            : (field.rows || []).map((r) => ({ ...r }))
        } else {
          formData[field.key] = (vals[field.key] !== undefined ? vals[field.key] : field.value)
        }
      })
    })
    return
  }
  // 鍩虹淇℃伅
  Object.entries(config.value.baseInfo).forEach(([key, field]) => {
    formData[key] = field.value
  })
  // 璧勬簮閰嶇疆
  config.value.resourceSections.forEach((section) => {
    section.fields.forEach((field) => {
      formData[field.key] = field.value
    })
  })
  // 钀ラ攢璧勬簮
  config.value.marketingSections.forEach((section) => {
    section.fields.forEach((field) => {
      formData[field.key] = field.value
    })
  })
  // 閿€鍞鍒?
  config.value.salesSections.forEach((section) => {
    section.fields.forEach((field) => {
      if (field.type === 'table') {
        // 琛ㄦ牸绫诲瀷锛氭繁鎷疯礉 rows 鍒?formData
        formData[field.key] = (field.rows || []).map((r) => ({ ...r }))
      } else {
        formData[field.key] = field.value
      }
    })
  })
  // 澶氬晢鍝侊細浠ュ綋鍓嶉€変腑鍟嗗搧鐨勮韩浠?璧勬簮鍊艰鐩栨ā鏉块粯璁ゅ€硷紝浣垮悇椤电闅忓晢鍝佸垏鎹㈣仈鍔?
  applyProductSeed()
}

// 鐢?window.__WB_PRODUCT锛堝綋鍓嶉€変腑鍟嗗搧锛夎鐩栬韩浠戒笌璧勬簮瀛楁锛屾湭鎻愪緵鐨勫瓧娈典繚鎸佹ā鏉块粯璁ゅ€?
const applyProductSeed = () => {
  const p = window.__WB_PRODUCT
  if (!p) return
  const seed = {
    prodName: p.name,
    prodPrcName: p.name,
    prodCode: p.code,
    monthlyFee: p.fee,
    flowAmount: p.flow,
    flowUnit: p.flowUnit,
    voiceAmount: p.voice,
    smsAmount: p.sms,
  }
  Object.keys(seed).forEach((key) => {
    if (seed[key] !== undefined && seed[key] !== '' && key in formData) {
      formData[key] = seed[key]
    }
  })
}
// 瀵硅瘽寮忛厤缃細瀛楁鍒濆绌哄€硷紙鏍囬噺绌轰覆 / 澶氶€夌┖鏁扮粍 / 琛ㄦ牸绌烘暟缁勶級
const emptyValue = (field) => {
  if (field.type === 'table') return []
  if (field.type === 'checkbox-group') return []
  return ''
}
initFormData()

// 瀛楁鏍囩锛氫紭鍏?鐢ㄦ埛宸茶В鏋?锛涘璇濆紡閰嶇疆鍦烘櫙涓嬫棤鍘熷椁愬彲澶嶇敤锛屽叾浣欏瓧娈电粺涓€鏍?AI鎺ㄨ崘"锛?
// 鍙妗ｆ锛堝瓨閲忓揩鐓э級涓哄巻鍙插綊妗ｆ暟鎹紝涓嶆樉绀?AI鎺ㄨ崘/澶嶇敤鏍囩锛涘惁鍒欐寜"AI琛ュ叏/鍘熼厤缃爣绛?
const fieldTag = (field) => {
  if (parsedFilled[field.key]) return 'parsed'
  if (isArchive.value) return null
  if (dialogueMode.value) return 'ai'
  if (aiFilled[field.key]) return 'ai'
  return field.tag || null
}

// 鍩虹淇℃伅瀛楁鍒楄〃
const baseFields = computed(() =>
  Object.entries(config.value.baseInfo).map(([key, field]) => ({ key, ...field }))
)

// 璧勬簮閰嶇疆 sections
const resourceSections = computed(() => config.value.resourceSections)

// 钀ラ攢璧勬簮 sections
const marketingSections = computed(() => config.value.marketingSections)

// 閿€鍞鍒?sections
const salesSections = computed(() => config.value.salesSections)

// ========== Section 鎶樺彔 ==========
const expandedSections = reactive({})
;[...config.value.resourceSections, ...config.value.marketingSections, ...config.value.salesSections].forEach((s) => {
  expandedSections[s.key] = true
})

const toggleSection = (key) => {
  expandedSections[key] = !expandedSections[key]
}

// ========== 闈㈡澘鎿嶄綔 鈫?瀵硅瘽闀滃儚 ==========
// 鍙充晶闈㈡澘涓婄殑鎿嶄綔浼氬疄鏃舵槧灏勪负宸︿晶瀵硅瘽涓殑涓€鏉＄敤鎴锋秷鎭紝鐢ㄤ簬璁板綍鐢ㄦ埛鎿嶄綔杩囩▼銆?
// 姣忔鎿嶄綔鐢?emit('panel-action', { text, category, timestamp }) 閫氱煡鐖剁粍浠讹紙ChatPage锛夎拷鍔犵敤鎴锋秷鎭€?
const cloneVal = (v) => (Array.isArray(v) ? v.map((r) => ({ ...r })) : v)

// 璁板綍鍚勫瓧娈电殑涓婁竴娆″€硷紝鐢ㄤ簬璁＄畻"浠庘€︿慨鏀逛负鈥?
const prevValues = reactive({})
const snapshotPrevValues = () => {
  Object.keys(formData).forEach((k) => { prevValues[k] = cloneVal(formData[k]) })
}
snapshotPrevValues()

// 鐢ㄦ埛渚ф秷鎭唴瀹?鈫?灞曠ず鍊硷紙select/checkbox 鐢?label锛屾暟缁?琛ㄦ牸鎷兼帴锛屽叾浣欏師鏍凤級
const toDisplay = (key, val) => {
  if (val === undefined || val === null || val === '') return ''
  const f = findField(key)
  if (f && f.options) return optionLabel(key, val)
  if (Array.isArray(val)) {
    if (val.length === 0) return ''
    if (val.some((v) => typeof v === 'object')) {
      return val.map((r) => Object.values(r || {}).filter((x) => x !== '' && x !== null && x !== undefined).join(' ')).join('锛?)
    }
    return val.map((v) => String(v)).join('銆?)
  }
  return String(val)
}

// 缁熶竴鍑哄彛锛氬彂鍑洪潰鏉挎搷浣滆褰?
const emitPanelAction = (payload) => {
  const text = (payload && payload.text) || ''
  if (!text) return
  emit('panel-action', { text, category: payload.category || 'action', timestamp: Date.now() })
}

// 鏂囨湰杈撳叆闃叉姈锛氬仠姝㈣緭鍏ュ悗涓€娆℃€ц褰曪紝閬垮厤姣忎釜鎸夐敭鐢熸垚涓€鏉℃秷鎭?
const fieldDebounceTimers = {}
const scheduleRecordFieldChange = (key) => {
  if (fieldDebounceTimers[key]) clearTimeout(fieldDebounceTimers[key])
  fieldDebounceTimers[key] = setTimeout(() => {
    delete fieldDebounceTimers[key]
    recordFieldChange(key)
  }, 500)
}

// 璁板綍鍗曚釜瀛楁鍊艰淇敼锛堜粠鈥︽敼涓衡€︼紝浠呭湪灞曠ず鍊肩湡姝ｅ彉鍖栨椂璁板綍锛?
const recordFieldChange = (key) => {
  const field = findField(key)
  const label = field ? (field.label || key) : key
  const newVal = toDisplay(key, formData[key])
  const oldVal = toDisplay(key, prevValues[key])
  prevValues[key] = cloneVal(formData[key])
  if (!label || oldVal === newVal) return
  let text = `宸插皢銆?{label}銆嶄慨鏀逛负銆?{newVal}銆峘
  if (oldVal) text += `锛堝師涓恒€?{oldVal}銆嶏級`
  emitPanelAction({ text, category: 'field' })
}

// ========== 瀛楁淇敼鏍囪 ==========
const markModified = (key) => {
  modifiedFields[key] = true
  const field = findField(key)
  if (field && field.options) {
    // 涓嬫媺閫夋嫨锛氱鏁ｄ慨鏀癸紝绔嬪嵆璁板綍
    recordFieldChange(key)
  } else {
    // 鏂囨湰妗?鏂囨湰鍩燂細闃叉姈璁板綍锛岄伩鍏嶉€愬瓧鍒峰睆
    scheduleRecordFieldChange(key)
  }
}

// ========== Checkbox 澶氶€夌粍 ==========
const checkboxValue = (key, val) => {
  const v = formData[key]
  if (Array.isArray(v)) return v.includes(val)
  if (typeof v === 'string') return v.split(',').includes(val)
  return false
}
const toggleCheckbox = (key, val) => {
  let arr = Array.isArray(formData[key]) ? [...formData[key]] : (formData[key] ? formData[key].split(',') : [])
  if (arr.includes(val)) {
    arr = arr.filter((v) => v !== val)
  } else {
    arr.push(val)
  }
  formData[key] = arr
  modifiedFields[key] = true
  const field = findField(key)
  const label = field ? (field.label || key) : key
  const opts = (field && field.options) || []
  const selectedLabels = arr.map((v) => {
    const o = opts.find((x) => String(x.value) === String(v))
    return o ? o.label : String(v)
  })
  emitPanelAction({ text: `宸叉洿鏂般€?{label}銆嶅嬀閫変负锛?{selectedLabels.join('銆?) || '锛堟棤锛?}`, category: 'field' })
}

// ========== 琛ㄦ牸琛岀鐞?==========
const tableRows = (key) => {
  return Array.isArray(formData[key]) ? formData[key] : []
}

const addTableRow = (key, columns) => {
  const newRow = {}
  columns.forEach((col) => {
    if (col.type === 'auto') {
      newRow[col.key] = ''
    } else if (col.type === 'select') {
      if (col.dynamicOptions) {
        // 鍔ㄦ€侀€夐」鍒楋細鍙栧叧鑱斿瓧娈靛€煎搴旂殑绗竴涓€夐」
        const refField = col.options[Object.keys(col.options)[0]]
        newRow[col.key] = refField && refField[0] ? refField[0].value : ''
      } else {
        newRow[col.key] = col.options && col.options[0] ? col.options[0].value : ''
      }
    } else {
      newRow[col.key] = ''
    }
  })
  if (!Array.isArray(formData[key])) {
    formData[key] = []
  }
  formData[key].push(newRow)
  modifiedFields[key] = true
  const fLabel = findField(key)?.label || key
  emitPanelAction({ text: `宸插湪銆?{fLabel}銆嶄腑鏂板涓€琛岄厤缃甡, category: 'table' })
}

const deleteTableRow = (key, rowIdx) => {
  if (Array.isArray(formData[key])) {
    formData[key].splice(rowIdx, 1)
    modifiedFields[key] = true
    const fLabel = findField(key)?.label || key
    emitPanelAction({ text: `宸插湪銆?{fLabel}銆嶄腑鍒犻櫎涓€琛岄厤缃甡, category: 'table' })
  }
}

// ========== 鍓嶉」闄愬埗鎻愮ず璇嚜鍔ㄧ敓鎴?==========
const labelMap = { age: '骞撮緞', globalLevel: '鍏ㄧ悆閫氱瓑绾?, baseMonthlyFee: '鍩虹濂楅鏈堣垂' }
const ruleTypeMap = { gt: '澶т簬', lt: '灏忎簬', ge: '澶т簬绛変簬', le: '灏忎簬绛変簬', eq: '绛変簬' }
const limitTypeMap = { allow: '鍏佽鍔炵悊', disallow: '涓嶅厑璁稿姙鐞? }

const generatePreLimitTip = (key, rowIdx) => {
  const row = tableRows(key)[rowIdx]
  if (!row) return ''
  const label = labelMap[row.label] || row.label || '鏉′欢'
  const ruleType = ruleTypeMap[row.ruleType] || row.ruleType || ''
  const limitValue = row.limitValue || ''
  const limitType = limitTypeMap[row.limitType] || row.limitType || ''
  if (!limitValue) return `${label}${ruleType}___${limitType}`
  return `${label}${ruleType}${limitValue}${limitType}鏈椁恅
}

// ========== 鍚庨」闄愬埗鍔ㄦ€侀€夐」 ==========
const getDynamicOptions = (col, row) => {
  if (!col.dynamicOptions || !col.options) return []
  // 鍚庨」闄愬埗涓氬姟鍚嶇О鍒楋細渚濊禆鍚岃 ruleType 瀛楁
  const refValue = row.ruleType || Object.keys(col.options)[0]
  return col.options[refValue] || col.options[Object.keys(col.options)[0]] || []
}

// 琛ㄦ牸鍗曞厓鏍煎彉鏇存椂锛氬鏋滀慨鏀圭殑鏄渚濊禆鐨勫瓧娈碉紙濡傚悗椤归檺鍒剁殑瑙勫垯绫诲瀷锛夛紝閲嶇疆鍔ㄦ€侀€夐」鍒楃殑鍊?
const onTableCellChange = (key, rowIdx) => {
  modifiedFields[key] = true
  // 鍚庨」闄愬埗锛歳uleType 鍙樻洿鏃讹紝妫€鏌?bizName 鏄惁鍦ㄦ柊閫夐」涓紝涓嶅湪鍒欓噸缃?
  if (key === 'postLimitTable') {
    const row = tableRows(key)[rowIdx]
    // 鎵惧埌 bizName 鍒楀畾涔?
    const section = config.value.salesSections.find((s) => s.key === 'post-limit')
    if (section) {
      const tableField = section.fields.find((f) => f.key === key)
      if (tableField) {
        const bizCol = tableField.columns.find((c) => c.key === 'bizName')
        if (bizCol && bizCol.dynamicOptions) {
          const newOptions = getDynamicOptions(bizCol, row)
          const stillValid = newOptions.some((o) => o.value === row.bizName)
          if (!stillValid && newOptions.length > 0) {
            row.bizName = newOptions[0].value
          }
        }
      }
    }
  }
}

// 鍔ㄦ€侀€夐」鍙樻洿鏃讹細鏇存柊褰撳墠鍗曞厓鏍肩殑鍊?
const onDynamicOptionChange = (key, rowIdx, colKey, event) => {
  const row = tableRows(key)[rowIdx]
  if (row) {
    row[colKey] = event.target.value
  }
  modifiedFields[key] = true
  recordFieldChange(key)
}

// 鏅€?select 鍗曞厓鏍煎彉鏇?
const onSelectCellChange = (key, rowIdx, colKey, event) => {
  const row = tableRows(key)[rowIdx]
  if (row) {
    row[colKey] = event.target.value
  }
  // 瑙﹀彂 onTableCellChange 閫昏緫锛堝鐞嗗悗椤归檺鍒?ruleType 鍙樻洿鏃堕噸缃?bizName锛?
  onTableCellChange(key, rowIdx)
  recordFieldChange(key)
}

// 鏅€?input 鍗曞厓鏍煎彉鏇?
const onInputCellChange = (key, rowIdx, colKey, event) => {
  const row = tableRows(key)[rowIdx]
  if (row) {
    row[colKey] = event.target.value
  }
  modifiedFields[key] = true
  // 鍓嶉」闄愬埗锛氳緭鍏ラ檺瀹氬€兼椂鑷姩鍒锋柊鎻愮ず璇紙auto绫诲瀷鍗曞厓鏍间緷璧栧悓琛屾暟鎹級
  if (key === 'preLimitTable') {
    // auto绫诲瀷鍗曞厓鏍兼槸 computed 寮忔覆鏌擄紝row 鏇存柊鍚庤嚜鍔ㄥ埛鏂?
  }
  scheduleRecordFieldChange(key)
}

// ========== 淇℃伅棰勮 - APP鎵嬫満澹虫ā锟?==========
const previewSections = computed(() => config.value.previewSections || launchProductConfig.previewSections)

const phoneTab = ref('user')
const phoneTime = ref('9:41')

const phoneNavTitle = computed(() => {
  const map = { user: '鍟嗗搧璇︽儏', freefill: '鍙楃悊鍏嶅～鍗?, sms: '鐭俊閫氱煡' }
  return map[phoneTab.value] || '鍟嗗搧璇︽儏'
})

// ========== 淇℃伅棰勮 Tab 鏁版嵁 鈥斺€?缁熶竴浠?formData 鍙栧綋鍓嶉厤缃€硷紝纭繚涓庡璇濆紡閰嶇疆宸ヤ綔鍙颁竴鑷?==========
const previewFee = computed(() => {
  const v = formData.monthlyFee
  return v !== undefined && v !== '' ? String(v).replace(/\.00$/, '') : '59'
})
// 棰勮鍖哄晢鍝佸悕锛氫紭鍏堝彇琛ㄥ崟閲岀敤鎴峰疄闄呯紪杈戝悗鐨勫悕绉帮紙鏀瑰悕鍚庢墜鏈洪瑙?鍏嶅～鍗?鐭俊妯℃澘鍚屾鏇存柊锛?
const previewName = computed(() => {
  const v = String(formData.prodName ?? formData.prodPrcName ?? productInfo.value.prodPrcName ?? '').trim()
  return v || productInfo.value.prodPrcName
})
const previewFlowUnit = computed(() => formData.flowUnit || 'GB')
const previewFlowAmount = computed(() => formData.flowAmount || '40')
const previewVoiceAmount = computed(() => formData.voiceAmount || '100')
const previewSmsAmount = computed(() => formData.smsAmount || '')
const previewFlowOvercharge = computed(() => formData.flowOvercharge || '0.29鍏?MB')
const previewVoiceOvercharge = computed(() => formData.voiceOvercharge || '0.15鍏?鍒嗛挓')
const previewSmsOvercharge = computed(() => formData.smsOvercharge || '')
// 鏉冪泭琛細瀵硅瘽寮忛厤缃笌鍙妗ｆ锛堝瓨閲忓揩鐓э級鎸夋潈鐩婅祫婧愰厤缃姩鎬佸睍绀猴紱浜у搧杩愯惀鍔╂墜(launch)宸ヤ綔鍙颁繚鎸佸師濮嬮潤鎬佹潈鐩婁笉鍙?
const previewRights = computed(() =>
  dynamicPreview.value && Array.isArray(formData.rightsTable) ? formData.rightsTable : []
)

// 鏍规嵁瀛楁 key 鏌ユ壘閰嶇疆瀹氫箟锛堢敤浜?value鈫抣abel 鏄犲皠锛?
const findField = (key) => {
  if (config.value.baseInfo && config.value.baseInfo[key]) return config.value.baseInfo[key]
  const all = [
    ...(config.value.resourceSections || []).flatMap((s) => s.fields),
    ...(config.value.marketingSections || []).flatMap((s) => s.fields),
    ...(config.value.salesSections || []).flatMap((s) => s.fields),
  ]
  return all.find((f) => f.key === key)
}
const optionLabel = (key, val) => {
  if (val === undefined || val === '' || val === null) return ''
  const f = findField(key)
  const opt = ((f && f.options) || []).find((o) => String(o.value) === String(val))
  return opt ? opt.label : String(val)
}

// 鏉冪泭鍒楄〃 鈥斺€?瀵硅瘽寮?鍙妗ｆ鎸夋潈鐩婇厤缃〃娓叉煋锛岄潪瀵硅瘽寮忥紙浜у搧杩愯惀鍔╂墜 launch锛変繚鎸佸師濮嬮潤鎬佹潈鐩婂垪琛?
const equityList = computed(() => {
  if (!dynamicPreview.value) { return [
      { name: '鍥藉唴閫氱敤娴侀噺', desc: '120GB 路 褰撴湀鏈敤鑷姩缁撹浆鑷虫鏈?, short: '娴?, color: '#06a0ff' },
      { name: '鍥藉唴閫氳瘽', desc: '1000鍒嗛挓 路 鍥藉唴涓诲彨', short: '璇?, color: '#10b981' },
      { name: '5G-A缃戠粶鏉冪泭', desc: '5G-A缃戠粶瑕嗙洊涓嬩韩鍙楁瀬鑷撮€熺巼浣撻獙', short: '5G', color: '#8b5cf6' }
    ]
  }
  const rights = previewRights.value
  if (rights.length) {
    const palette = ['#06a0ff', '#00be5f', '#f59e0b', '#8b5cf6', '#ef4444', '#0ea5e9']
    return rights.map((r, i) => ({
      name: r.rightName,
      desc: r.obtainType === 'auto' ? '璁㈣喘鍚庤嚜鍔ㄥ彂鏀撅紝娆℃湀璧风敓鏁? : '鍔炵悊鍚庢墜鍔ㄩ鍙?,
      short: (r.rightName || '鏉?).slice(0, 1),
      color: palette[i % palette.length],
    }))
  }
  return [
    { name: '鑵捐瑙嗛鏈堝崱', desc: '鐑棬缁艰壓鎶㈠厛鐪?, short: '瑙?, color: '#06a0ff' },
    { name: '鐖卞鑹烘湀鍗?, desc: '鐑挱鍓ч泦闅忓績鐪?, short: '濂?, color: '#00be5f' },
  ]
})

// 鍏嶅～鍗曟暟鎹?鈥斺€?瀵硅瘽寮?鍙妗ｆ鎸夊疄闄呴厤缃€肩敓鎴?
const freeFillItems = computed(() => {
  if (dynamicPreview.value) {
    const name = previewName.value
    const area = (Array.isArray(formData.applicableArea) ? formData.applicableArea : [])
      .map((v) => optionLabel('applicableArea', v)).filter(Boolean).join('銆?) || '鍥涘窛鐪?
    const includeRes = [
      previewFlowAmount.value + (previewFlowUnit.value || 'GB') + '娴侀噺',
      previewVoiceAmount.value + '鍒嗛挓璇煶',
      previewSmsAmount.value ? previewSmsAmount.value + '鏉＄煭淇? : '',
      previewRights.value.map((r) => r.rightName).join('銆?),
    ].filter(Boolean).join('+')
    return [
      { label: '璧勮垂鍚嶇О', value: name },
      { label: '濂楅鏈堣垂', value: `${previewFee.value}鍏?鏈坄 },
      { label: '鍖呭惈璧勬簮', value: includeRes || '--', highlight: true },
      { label: '瓒呭鏀惰垂鏍囧噯', value: [previewFlowOvercharge.value, previewVoiceOvercharge.value, previewSmsOvercharge.value].filter(Boolean).join(' / ') || '--' },
      { label: '闄愬畾鏉′欢', value: `${area}鑼冨洿鍐呬娇鐢╜ },
      { label: '鐢熸晥鏂瑰紡', value: optionLabel('effRule', formData.effRule) || '绔嬪嵆鐢熸晥' },
      { label: '鏈夋晥鏈?, value: optionLabel('validity', formData.validity) || '闀挎湡鏈夋晥' },
      { label: '缁鏂瑰紡', value: optionLabel('renewMethod', formData.renewMethod) || '鑷姩缁' },
      { label: '鍚屾椂璁㈣喘', value: `${formData.orderTimes || '1'}娆 },
      { label: '濂楅鍥哄畾璐?, value: `${formData.monthlyFee || '49'}鍏僠 },
    ]
  }
  const section = previewSections.value.find((s) => s.key === 'free-fill-preview')
  return section ? section.items : []
})

// 鐭俊妯℃澘鏁版嵁 鈥斺€?瀵硅瘽寮?鍙妗ｆ鎸夊疄闄呴厤缃€肩敓鎴?
const smsItems = computed(() => {
  if (dynamicPreview.value) {
    const name = previewName.value
    const fee = `${previewFee.value}鍏僠
    const ress = [
      previewFlowAmount.value + (previewFlowUnit.value || 'GB') + '娴侀噺',
      previewVoiceAmount.value + '鍒嗛挓璇煶',
      previewSmsAmount.value ? previewSmsAmount.value + '鏉＄煭淇? : '',
    ].filter(Boolean).join('鍜?)
    return [
      { label: '鍔炵悊鎴愬姛鐭俊', value: `鎭枩鎮ㄦ垚鍔熷姙鐞?{name}锛屾湀璐?{fee}锛屽惈${ress}锛屾劅璋㈡偍鐨勬敮鎸侊紒` },
      { label: '棰勭害鐢熸晥鐭俊', value: `鎮ㄧ殑${name}宸茬敓鏁堬紝鏈堣垂${fee}锛岀敓鏁堝綋鏈堣捣浜彈濂楅鏉冪泭銆俙 },
      { label: '鍓嶅彴浜岀‘鐭俊', value: `灏婃暚鐨勫鎴凤紝鎮ㄦ鍦ㄥ姙鐞?{name}锛屾湀璐?{fee}锛屾槸鍚︾‘璁ゅ姙鐞嗭紵` },
      { label: '閫€璁㈢煭淇?, value: `鎮ㄥソ锛屾偍鐨?{name}宸查€€璁紝濡傛湁鐤戦棶璇疯嚧鐢?0086銆俙 },
    ]
  }
  const section = previewSections.value.find((s) => s.key === 'sms-preview')
  return section ? section.items : []
})

// ========== 搴曢儴鎿嶄綔 ==========
const saveDraft = () => {
  Object.keys(modifiedFields).forEach((k) => (modifiedFields[k] = false))
  ElMessage.success('鑽夌淇濆瓨鎴愬姛')
  emitPanelAction({ text: `淇濆瓨褰撳墠閰嶇疆涓鸿崏绋匡紙${productInfo.value.prodPrcName}锛塦, category: 'action' })
}

// ========== 鏅鸿兘绋芥牳 ==========
const auditVisible = ref(false)
const auditLoading = ref(false)
const auditProgressText = ref('')
const auditResults = ref([])
// 鍙妗ｆ妯″紡寮哄埗涓哄凡鎻愪氦锛坰ubmitted=true锛夛紝浣挎墍鏈夊瓧娈电鐢ㄣ€佸簳閮ㄦ樉绀哄綊妗ｇ姸鎬?
const submitted = ref(!!props.archiveSnapshot)
// 澶嶅埗妯″紡鍟嗗搧鐨勭姸鎬侊紙鑽夌鈫掑緟瀹℃牳锛夛紝涓庡叏灞€ launch 鐘舵€侀殧绂?
const copyStatus = ref('draft')

// 澶嶅埗锛氬熀浜庡瓨閲忓晢鍝?褰掓。妗ｆ鐢熸垚鍙紪杈戞柊鍟嗗搧銆備繚鎸佸彧璇绘。妗堥濉瓧娈靛€间负妯℃澘鏁版嵁锛?
// 鏀逛负鑽夌鎬佸彲缂栬緫锛岀敓鎴愭柊宸ュ崟鍙凤紝鍟嗗搧鍚嶈拷鍔犫€滐紙澶嶅埗锛夆€濓紝骞堕€氱煡鐖剁粍浠剁櫥璁般€屾垜鐨勮崏绋裤€嶃€?
const onCopyFromArchive = () => {
  if (!props.archiveSnapshot) return
  const src = props.archiveSnapshot.productInfo || {}
  const vals = props.archiveSnapshot.values || {}
  copyWorkOrderId.value = `WO${Date.now().toString().slice(-8)}${Math.floor(Math.random() * 90 + 10)}`
  copyStatus.value = 'draft'
  copyMode.value = true
  submitted.value = false
  // 娓呯┖ AI/宸茶В鏋愭爣绛撅紝浣块濉瓧娈垫寜鏅€氭ā鏉挎暟鎹樉绀猴紙鍙嚜鐢辩紪杈戯級
  Object.keys(aiFilled).forEach((k) => { aiFilled[k] = undefined })
  Object.keys(parsedFilled).forEach((k) => { parsedFilled[k] = undefined })
  if (formData.prodName !== undefined) formData.prodName = String(formData.prodName) + '锛堝鍒讹級'
  if (formData.prodCode !== undefined) formData.prodCode = copyWorkOrderId.value
  activeTab.value = 'base'
  ElMessage.success('宸插鍒跺瓨閲忓晢鍝侀厤缃紝杞负鍙紪杈戞柊鍟嗗搧')
  emitPanelAction({ text: `鍩轰簬瀛橀噺鍟嗗搧銆?{src.prodPrcName || src.workOrderId || '瀛橀噺鍟嗗搧'}銆嶅鍒剁敓鎴愭柊鍟嗗搧閰嶇疆`, category: 'action' })
  // 閫氱煡鐖剁粍浠跺湪銆屾垜鐨勮崏绋裤€嶇櫥璁拌澶嶅埗鍝侊紙source='copy'锛?
  emit('copy-init', {
    workOrderId: copyWorkOrderId.value,
    prodName: (vals.prodName || src.prodPrcName || '') + '锛堝鍒讹級',
    templateLabel: src.templateLabel || launchProductConfig.productInfo.templateLabel,
    summary: `${vals.monthlyFee ? vals.monthlyFee + '鍏?鏈? : ''} 路 澶嶅埗鑷?${src.prodPrcName || src.workOrderId || '瀛橀噺鍟嗗搧'}`,
    values: { ...vals },
  })
}

// 澶嶅埗妯″紡/瀵硅瘽寮忛厤缃笅鐩戝惉鍟嗗搧鍚嶇紪杈戯紝瀹炴椂鍚屾缁欑埗缁勪欢锛堟垜鐨勮崏绋?娴佺▼鏉?瀹℃壒灞曠ず淇濇寔涓€鑷达級
watch(() => formData.prodName, (name) => {
  if (copyMode.value && copyWorkOrderId.value && name) {
    emit('copy-rename', String(name).trim())
  }
  // 瀵硅瘽寮忛厤缃細鐢ㄦ埛鍦ㄥ伐浣滃彴鍐呭懡鍚?鏀瑰悕鍚庯紝瀹炴椂鍚屾缁欑埗缁勪欢锛堟祦绋?stepper 绛夎仈鍔級
  if (dialogueMode.value && !copyMode.value && props.workOrderId && name) {
    emit('dialogue-rename', String(name).trim())
  }
})

// 鏀堕泦闈㈡澘鍐呯敤鎴峰疄闄呯紪杈戠殑鍏ㄩ儴琛ㄥ崟鍊硷紙鍚〃鏍艰锛夛紝鐢ㄤ簬褰掓。宸ュ崟"鏌ョ湅瀹屾暣宸ヤ綔鍙版。妗?鍙鍥炵湅
const collectFormValues = () => {
  const values = {}
  Object.keys(formData).forEach((k) => {
    const v = formData[k]
    values[k] = Array.isArray(v) ? v.map((r) => ({ ...r })) : v
  })
  return values
}

const showAudit = () => {
  emitPanelAction({ text: `鐐瑰嚮銆屾櫤鑳界ń鏍搞€嶏紝瀵瑰綋鍓嶉厤缃繘琛屽悎瑙勬€ф鏌, category: 'action' })
  auditVisible.value = true
  auditLoading.value = true
  auditResults.value = []

  const steps = [
    '姝ｅ湪鎵ц鏅鸿兘绋芥牳...',
    '鍩虹淇℃伅瀹屾暣鎬ф牎楠?..',
    '蹇呭～瀛楁妫€鏌?..',
    '涓氬姟瑙勫垯鏍￠獙...',
    '鐭俊妯℃澘鍚堣鎬ф鏌?..',
  ]

  let stepIdx = 0
  auditProgressText.value = steps[0]

  const stepTimer = setInterval(() => {
    stepIdx++
    if (stepIdx < steps.length) {
      auditProgressText.value = steps[stepIdx]
    } else {
      clearInterval(stepTimer)
    }
  }, 800)

  setTimeout(() => {
    clearInterval(stepTimer)
    auditLoading.value = false

    const results = []
    results.push({ type: 'success', title: '蹇呭～椤规鏌ラ€氳繃', desc: '鎵€鏈夊繀濉瓧娈靛凡濉啓' })
    if (!formData.monthlyFee) {
      results.push({ type: 'error', title: '缂哄皯濂楅鍥哄畾璐?, desc: '濂楅鍥哄畾璐规湭濉啓锛屾牴鎹鑼冨繀濉? })
    } else {
      results.push({ type: 'success', title: '鏈堢閰嶇疆鍚堣', desc: '濂楅鍥哄畾璐广€佺◣鐜囬厤缃畬鏁? })
    }
    if (!formData.flowAmount) {
      results.push({ type: 'error', title: '缂哄皯娴侀噺璧勬簮閰嶇疆', desc: '娴侀噺璧勬簮閰嶇疆鏈～鍐欙紝鏍规嵁瑙勮寖蹇呭～' })
    } else {
      results.push({ type: 'success', title: '璧勬簮閰嶉妫€鏌ラ€氳繃', desc: '娴侀噺銆佽闊炽€佺煭淇¤祫婧愰厤缃畬鏁? })
    }
    results.push({ type: 'success', title: '鏃ユ湡閰嶇疆鍚堣', desc: '閿€鍞棩鏈熼厤缃鍚堜笟鍔¤鍒? })
    results.push({ type: 'success', title: '鐭俊妯℃澘鍚堣', desc: '鐭俊妯℃澘鍏抽敭璇嶆鏌ラ€氳繃' })

    auditResults.value = results

    const hasError = results.some((r) => r.type === 'error')
    if (!hasError) {
      setTimeout(() => {
        submitted.value = true
        // 瀵硅瘽寮忛厤缃敤鏈満鏅嫭绔嬬姸鎬侊紙鑽夌鈫掑緟瀹℃牳锛夛紝涓嶈鍐欎骇鍝佽繍钀ュ姪鎵嬪叏灞€ launch 鐘舵€侊紝閬垮厤璺ㄥ満鏅覆鎵?
        if (dialogueMode.value) {
          dialogueStatus.value = 'submitted'
        } else if (copyMode.value) {
          // 澶嶅埗妯″紡鍚屾牱鐢ㄧ嫭绔嬬姸鎬侊紝涓嶈鐩栧叏灞€ launch 閰嶇疆
          copyStatus.value = 'submitted'
        } else {
          launchProductConfig.productInfo.status = 'submitted'
        }
        auditVisible.value = false
        ElMessage.success('绋芥牳閫氳繃锛岄厤缃凡鎻愪氦')
        emit('submitted', {
          workOrderId: productInfo.value.workOrderId,
          values: collectFormValues(),
          // 鎻愪氦鏃跺洖浼犻潰鏉垮唴瀹為檯鍟嗗搧鍚嶏紙鐢ㄦ埛鍦ㄥ鍒?缂栬緫鍚庡彲鑳芥敼鍚嶏級锛屼緵鐖剁粍浠跺悓姝ュ鎵?娴佺▼/鍥炲
          productName: String(formData.prodName ?? formData.prodPrcName ?? productInfo.value.prodPrcName ?? '').trim(),
        })
        emitPanelAction({ text: `鏅鸿兘绋芥牳閫氳繃锛岄厤缃凡鎻愪氦锛堝伐鍗曞彿锛?{productInfo.value.workOrderId}锛塦, category: 'action' })
        // 鎵归噺閰嶇疆妯″紡锛氭彁浜ゅ悗閫氱煡鐖剁粍浠舵爣璁拌崏绋垮凡鎻愪氦骞惰嚜鍔ㄨ烦杞笅涓€涓?
        if (batchMode.value) {
          emit('batch-submitted')
        }
      }, 600)
    }
  }, 4200)
}

const onSubmit = () => {
  emitPanelAction({ text: `鐐瑰嚮銆屾彁浜ら厤缃€嶏紝鍙戣捣鏅鸿兘绋芥牳骞舵彁浜わ紙${productInfo.value.prodPrcName}锛塦, category: 'action' })
  showAudit()
}

// tab 鏁伴噺澧炲姞鏃惰嚜鍔ㄥ悜宸︽墿灞曢潰鏉垮搴︼紝閬垮厤 tab 婧㈠嚭锛堝彧鎵╀笉鍑忥紝灏婇噸鐢ㄦ埛鎵嬪姩鎷栫獎锛?
watch(() => tabs.value.length, (count) => {
  const targetByCount = { 5: DEFAULT_WIDTH, 6: 640, 7: 720, 8: 800 }
  const target = targetByCount[count]
  if (target && target > panelWidth.value) {
    panelWidth.value = target
  }
})

// 瀵硅瘽寮忛厤缃峰閲忓瓧娈垫洿鏂帮細鐢ㄦ埛閫氳繃鍚庣画瀵硅瘽锛堝"鍟嗗搧鍙玿xx"/"鏈堣垂鏀规垚xxx"锛夎皟鏁村凡鎵撳紑闈㈡澘鐨勫瓧娈靛€笺€?
// 鏁堟灉绛夊悓鐢ㄦ埛鍦ㄥ伐浣滃彴鍐呯洿鎺ヤ慨鏀瑰苟鏍囨敞涓?宸茶В鏋?锛堟覆鏌撳僵鑹叉爣绛撅級锛屽苟鑱斿姩鎵嬫満棰勮/鍏嶅～鍗?鐭俊妯℃澘/娴佺▼鍚嶇О銆?
const applyFieldUpdate = (key, value) => {
  if (!key) return
  formData[key] = value
  // 鏍?宸茶В鏋?銆佹竻闄ゅ師鏈?AI鎺ㄨ崘"鏍囩
  parsedFilled[key] = '鏍规嵁鐢ㄦ埛闇€姹傝В鏋愬緱鍑?
  aiFilled[key] = undefined
  // 鍟嗗搧鍚嶇О/宸ュ崟鍚嶈仈鍔細鏇存柊棰勮鍖轰笌娴佺▼ stepper锛坧roductInfo computed 宸蹭紭鍏堝彇琛ㄥ崟鍚嶇О锛?
  if (key === 'prodName') {
    // 鑻ュ悓鏃跺瓨鍦ㄥ晢鍝佹。妗堝悕绉帮紙prodPrcName锛夊瓧娈典篃涓€骞跺悓姝ワ紝淇濊瘉鏍囬/棰勮/鍏嶅～鍗曚竴鑷?
    if ('prodPrcName' in config.value.baseInfo) formData.prodPrcName = value
  }
}

// 鏆撮湶鎻愪氦鏂规硶锛屼緵澶栭儴锛堝蹇嵎鍥炲"鏅鸿兘绋芥牳骞舵彁锟?锛夎皟鐢紝鏁堟灉绛夊悓鐐瑰嚮"鎻愪氦閰嶇疆"
__expose({
  submit: onSubmit,
  showTestReport,
  showApproval,
  showMonitor,
  // 渚涘巻鍙蹭細璇濇仮澶嶆椂鍚屾"宸叉彁浜?鐘舵€侊紝浣垮簳閮ㄦ搷浣滄爮闅忔祦绋嬮樁娈靛垏鎹?
  setSubmitted: (val) => { submitted.value = !!val },
  // 渚涘璇濆紡閰嶇疆鍦烘櫙锛氱敤鎴疯拷鍔犲璇濆閲忔洿鏂伴潰鏉垮瓧娈?
  applyFieldUpdate,
})

// ===== 鍟嗗搧瀹炰緥鏁版嵁婧愶紙鍗曚竴鏉ユ簮锛夛細鎵€鏈夐〉绛剧殑鏁版嵁缁熶竴鐢卞畠娲剧敓 =====
// 瀹炰緥韬唤鍙栬嚜 鈶燯RL 澶栭摼锛坥ffer_id/name/req_id锛夆憽宸查厤缃殑琛ㄥ崟鍊硷紙formData锛夆憿鍏ㄥ眬 launch 妗ｆ銆?
// 鍒囨崲涓嶅悓鍟嗗搧锛堟崲 URL 鍙傛暟 / 淇敼琛ㄥ崟锛夊嵆鍏ㄩ噺鑱斿姩鍒濆鍖栧悇椤电锛岃€岄潪鍚勯〉绛惧悇鑷竴灏忔挳婕旂ず鏁版嵁銆?
const productInstance = computed(() => {
  const q = window.__WB_QUERY || {}
  const p = window.__WB_PRODUCT || {}
  const num = (v) => (v === undefined || v === null || v === '' ? '' : String(v))
  const name = String(formData.prodName ?? formData.prodPrcName ?? productInfo.value.prodPrcName ?? '').trim()
    || p.name || q.name || q.offer_name || '寰呭懡鍚嶅晢鍝?
  const code = num(formData.prodCode) || num(p.code) || num(productInfo.value.workOrderId) || num(q.offer_id) || 'SP' + Date.now().toString().slice(-8)
  const fee = num(formData.monthlyFee) || num(p.fee) || '49'
  const flow = num(formData.flowAmount) || num(p.flow) || ''
  const flowUnit = num(formData.flowUnit) || num(p.flowUnit) || 'GB'
  const voice = num(formData.voiceAmount) || num(p.voice) || ''
  const sms = num(formData.smsAmount) || num(p.sms) || ''
  const applicant = '浜ч攢鍝佸煙 / 浜у晢鍝佷腑蹇?
  const department = '浜ч攢鍝佸煙 / 浜у晢鍝佷腑蹇?
  const today = new Date().toISOString().slice(0, 10).replace(/-/g, '')
  const now = new Date().toISOString().slice(0, 19).replace('T', ' ')
  const resources = [flow ? flow + flowUnit + '娴侀噺' : '', voice ? voice + '鍒嗛挓璇煶' : '', sms ? sms + '鏉＄煭淇? : ''].filter(Boolean).join('銆?)
  return { name, code, fee, flow, flowUnit, voice, sms, applicant, department, resources, today, now }
})

// 闇€姹傛彁鎶ラ〉绛?鈥斺€?鐢卞疄渚嬫暟鎹姩鎬佺敓鎴?
const demandView = computed(() => ({
  no: 'XQ-' + ((window.__WB_QUERY && window.__WB_QUERY.req_id) || '') + '-' + productInstance.value.today,
  name: productInstance.value.name,
  applicant: productInstance.value.applicant,
  department: productInstance.value.department,
  date: productInstance.value.today,
  target: '闈㈠悜鐩爣瀹㈢兢鎺ㄥ嚭銆? + productInstance.value.name + '銆? + (productInstance.value.fee ? '锛屾湀璐? + productInstance.value.fee + '鍏? : '') + '锛屼富鎵撴祦閲忎笌閫氳瘽浣撻獙',
  require: '鎺ㄥ嚭' + productInstance.value.name + '锛屽惈' + (productInstance.value.resources || '鍩虹璧勬簮') + '锛涢鏈堟寜澶╂姌绠楋紝娆℃湀璧锋寜鏈堟敹鍙栵紱鏀拺钀ヤ笟鍘?APP娓犻亾閿€鍞€?,
  effective: '2026-10-01',
  channels: ['钀ヤ笟鍘?, 'APP'],
}))

// 鍔犺浇鏂规椤电 鈥斺€?鐢卞疄渚嬫暟鎹姩鎬佺敓鎴?
const schemeView = computed(() => ({
  no: 'SCH-' + productInstance.value.today,
  requireNo: demandView.value.no,
  version: 'V1.0',
  name: productInstance.value.name + '鍔犺浇鏂规',
  author: '浜у晢鍝佷腑蹇冿紙CRM_POS锛?,
  status: '宸茬‘璁?,
  summary: [
    ['璧勮垂鍚嶇О', productInstance.value.name],
    ['鏈堢', (productInstance.value.fee ? productInstance.value.fee + '鍏?鏈? : '--') + '锛堥鏈堟寜澶╂姌绠楋紝娆℃湀璧锋寜鏈堟敹鍙栵級'],
    ['鍖呭惈璧勬簮', productInstance.value.resources || '--'],
    ['濂楀鏍囧噯', '娴侀噺3鍏?GB銆佽闊?.15鍏?鍒嗛挓銆佺煭淇?.1鍏?鏉?],
    ['鐢熸晥鏂瑰紡', '璁㈣喘绔嬪嵆鐢熸晥 / 閫€璁㈤绾︾敓鏁?],
    ['鍙戝竷鑼冨洿', '鍏ㄧ渷22涓湴甯傦紝钀ヤ笟鍘呫€丄PP娓犻亾'],
  ],
  items: ['鍩虹淇℃伅', '鏈堢', '璇煶璧勬簮', '娴侀噺璧勬簮', '鐭俊璧勬簮', '鍓嶉」闄愬埗', '鍚庨」闄愬埗'],
}))

// 绋芥牳鎶ュ憡椤电 鈥斺€?鐢卞疄渚嬫暟鎹姩鎬佺敓鎴?
const auditView = computed(() => ({
  object: 'offer_id = ' + productInstance.value.code,
  status: '閫氳繃',
  time: productInstance.value.today,
  conclusion: '绋芥牳閫氳繃锛氶厤缃鍚堟湰娆¤惤鍦伴攢鍞搧 鏂板閿€鍞搧' + productInstance.value.code + ' 瑙勫垯銆?,
  checks: [
    ['鍩虹淇℃伅瀹屾暣鎬?, '閫氳繃'],
    ['璧勬簮閰嶇疆瀹屾暣鎬?, '閫氳繃'],
    ['濂楀璧勮垂瀹屾暣鎬?, '閫氳繃'],
    ['瀛楁鏍煎紡瑙勮寖鎬?, '閫氳繃'],
    ['瀛楁鏋氫妇鍚堟硶鎬?, '閫氳繃'],
    ['閰嶇疆椤瑰叧鑱斾竴鑷存€?, '閫氳繃'],
    ['涓氬姟瑙勫垯瀹屾暣鎬?, '閫氳繃'],
  ],
}))

// 鏍″噯鎶ュ憡椤电 鈥斺€?鐢卞疄渚嬫暟鎹姩鎬佺敓鎴?
const calibrateView = computed(() => ({
  object: productInstance.value.code,
  status: '閫氳繃',
  time: productInstance.value.today,
  conclusion: '璧勮垂鏍″噯閫氳繃锛氳璐归厤缃笌鏂规闇€姹備繚鎸佷竴鑷达紝鏈彂鐜板彔鍔?浜掓枼鍐茬獊銆?,
  rows: [
    ['濂楅鍥哄畾璐?, productInstance.value.fee + '鍏?, productInstance.value.fee + '鍏?鏈?, '涓€鑷?],
    ['鍥藉唴閫氱敤娴侀噺', (productInstance.value.flow || '--') + productInstance.value.flowUnit, (productInstance.value.flow || '--') + productInstance.value.flowUnit, '涓€鑷?],
    ['鍥藉唴閫氳瘽', (productInstance.value.voice || '--') + '鍒嗛挓', (productInstance.value.voice || '--') + '鍒嗛挓', '涓€鑷?],
    ['濂楀娴侀噺鏍囧噯', '3鍏?GB', '3鍏?GB', '涓€鑷?],
    ['濂楀璇煶璐圭巼', '0.15鍏?鍒嗛挓', '0.15鍏?鍒嗛挓', '涓€鑷?],
    ['濂楀鐭俊璐圭巼', '0.1鍏?鏉?, '0.1鍏?鏉?, '涓€鑷?],
  ],
}))

// 娴嬭瘯鎶ュ憡椤电 鈥斺€?鐢卞疄渚嬫暟鎹姩鎬佺敓鎴?
const testRepView = computed(() => ({
  no: 'TEST-REP-' + productInstance.value.today + '-' + productInstance.value.code.slice(-3),
  name: productInstance.value.name,
  total: 30,
  pass: 30,
  rate: '100%',
  conclusion: '寤鸿涓婄嚎',
  dims: [
    ['鍙楃悊楠岃瘉 (ACC)', '10', '10', '閫氳繃'],
    ['璁¤垂楠岃瘉 (BILL)', '10', '10', '閫氳繃'],
    ['瀹㈡湇楠岃瘉 (CUST)', '10', '10', '閫氳繃'],
  ],
  cases: [
    ['ACC-001', '閿€鍞搧鍩虹鍑嗗叆瑙勫垯鏍￠獙', 'P0', '閫氳繃'],
    ['ACC-004', '璁㈣喘鎿嶄綔鑳藉姏鏍￠獙', 'P0', '閫氳繃'],
    ['ACC-011', '妯℃嫙璁㈣喘鎺ュ彛棰勬祴璇?, 'P0', '閫氳繃'],
    ['BILL-001', '鍩虹璧勮垂閲戦鍚堟硶鎬ф牎楠?, 'P0', '閫氳繃'],
    ['BILL-004', '璧勬簮鎵ｅ噺瑙勫垯鏍￠獙', 'P0', '閫氳繃'],
    ['BILL-010', '璧勮垂鐢熸晥澶辨晥鑱斿姩鏍￠獙', 'P0', '閫氳繃'],
    ['CUST-001', '瀹㈡湇浜у搧鍩虹瑙嗗浘瀹屾暣鎬?, 'P0', '閫氳繃'],
    ['CUST-004', '浜у搧璧勮垂瀵瑰璇存槑璇濇湳鏍￠獙', 'P0', '閫氳繃'],
    ['CUST-008', '瀵瑰灞曠ず淇℃伅鍚堣鏍￠獙', 'P0', '閫氳繃'],
  ],
}))

// 楠岃瘉鎶ュ憡椤电 鈥斺€?鐢卞疄渚嬫暟鎹姩鎬佺敓鎴?
const verifyView = computed(() => ({
  orderId: 'ORD' + productInstance.value.today.replace(/-/g, '') + Date.now().toString().slice(-6),
  offerInstId: 'OI' + productInstance.value.today.replace(/-/g, '') + Date.now().toString().slice(-6),
  status: '閫氳繃',
  time: productInstance.value.today,
  scenes: [
    ['濂楅鏂拌 (S_O_TC)', '閫氳繃'],
    ['鍓崱鍔犺 (S_ADD_CARD)', '閫氳繃'],
    ['濂楅閫€璁?(S_U_TC)', '閫氳繃'],
  ],
  points: [
    ['P_EFF_DATE 鐢熸晥鏃堕棿', '閫氳繃'],
    ['P_EXP_DATE 澶辨晥鏃堕棿', '閫氳繃'],
    ['P_STATUS 瀹炰緥鐘舵€?, '閫氳繃'],
    ['P_ORD_CNT 璁㈣喘鏁伴噺', '閫氳繃'],
  ],
}))

// 瀹℃壒鐭╅樀椤电 鈥斺€?鐢卞疄渚嬫暟鎹姩鎬佺敓鎴?
const approvalView = computed(() => ({
  no: 'SP-' + productInstance.value.today + '-005',
  name: productInstance.value.name,
  status: '宸查€氳繃',
  nodes: [
    ['浜у搧缁忕悊瀹℃牳', '浜у搧缁忕悊', '宸查€氳繃', '瀹℃牳閫氳繃锛屽悓鎰忎笂鏋?, productInstance.value.now],
    ['璧勮垂涓荤瀹℃牳', '璧勮垂涓荤', '宸查€氳繃', '瀹℃牳閫氳繃锛屽悓鎰忎笂鏋?, productInstance.value.now],
    ['杩愯惀瀹℃牳', '杩愯惀涓撳憳', '宸查€氳繃', '瀹℃牳閫氳繃锛屽悓鎰忎笂鏋?, productInstance.value.now],
    ['涓婄嚎瀹℃壒', 'IT鏀拺', '宸查€氳繃', '瀹℃牳閫氳繃锛屽悓鎰忎笂鏋?, productInstance.value.now],
  ],
  note: '瀹℃壒宸查€氳繃锛岄攢鍞搧涓婃灦瀹屾垚 鉁咃紙瀹℃壒閫氳繃鍗宠涓洪攢鍞搧宸茶嚜鍔ㄤ笂绾匡紝鏃犻渶浜屾纭锛夈€?,
}))

// 杩愯惀瑙嗗浘椤电姒傝 鈥斺€?鍚嶇О/缂栫爜闅忓疄渚嬭仈鍔紙瀹屾暣 ECharts 鏁版嵁闈㈡澘鍦ㄧ嫭绔嬫ā鍧楋紝淇濈暀婕旂ず鏁版嵁锛?
const monitorView = computed(() => ({
  single: {
    name: productInstance.value.name,
    status: '鍦ㄥ敭涓?,
    cards: [
      ['绱璁㈣喘閲?, '3,842', '鎴?],
      ['鏈湀鏂板', '1,206', '鎴?],
      ['鏈湀閫€璁?, '86', '鎴?],
      ['鍑€澧?, '1,120', '鎴?],
      ['鍦ㄥ敭鏀跺叆', '76.4', '涓囧厓'],
      ['璧勬簮鍒╃敤鐜?, '68%', '鈥?],
    ],
    warn: '褰撳墠鏃犲紓甯稿憡璀︼紱閫氱敤娴侀噺璧勬簮鍒╃敤鐜囨帴杩戦槇鍊硷紝寤鸿鍏虫敞鍚庣画瓒嬪娍銆?,
  },
  total: {
    cards: [
      ['鍦ㄥ敭閿€鍞搧', '48', '涓?],
      ['鏈湀鏂板璁㈣喘', '2.4涓?, '鎴?],
      ['鎬诲湪鍞敹鍏?, '1,820', '涓囧厓'],
      ['鏁翠綋鍋ュ悍搴?, '鑹ソ', '鈥?],
    ],
    risks: [
      ['娴侀噺璧勬簮鍒╃敤鐜囧亸楂?, '涓?, '澶氭閿€鍞搧鍐呴€氱敤娴侀噺鍖呴鍙戞斁杈冮泦涓?],
      ['閮ㄥ垎鑰佸椁愰€€璁笂鍗?, '浣?, '闇€鍏虫敞鍚?G-A绛夋柊濂楅鐨勮縼绉诲紩瀵?],
    ],
  },
}))

const __returned__ = { emit, props, products, activeProductId, copyMode, copyWorkOrderId, isArchive, dialogueMode, dynamicPreview, batchMode, dialogueStatus, panelWidth, resizing, startResize, productMenuOpen, toggleProductMenu, onSelectProduct, ppStripRef, visibleProducts, MIN_WIDTH, MAX_WIDTH, DEFAULT_WIDTH, baseTabs, tabs, activeTab, showTestReport, showApproval, showMonitor, productInfo, currentStatus, footerSubmittedText, config, formData, modifiedFields, archiveTestReport, parsedFilled, aiFilled, initFormData, emptyValue, fieldTag, baseFields, resourceSections, marketingSections, salesSections, expandedSections, toggleSection, cloneVal, prevValues, snapshotPrevValues, toDisplay, emitPanelAction, fieldDebounceTimers, scheduleRecordFieldChange, recordFieldChange, markModified, checkboxValue, toggleCheckbox, tableRows, addTableRow, deleteTableRow, labelMap, ruleTypeMap, limitTypeMap, generatePreLimitTip, getDynamicOptions, onTableCellChange, onDynamicOptionChange, onSelectCellChange, onInputCellChange, previewSections, phoneTab, phoneTime, phoneNavTitle, previewFee, previewName, previewFlowUnit, previewFlowAmount, previewVoiceAmount, previewSmsAmount, previewFlowOvercharge, previewVoiceOvercharge, previewSmsOvercharge, previewRights, findField, optionLabel, equityList, freeFillItems, smsItems, saveDraft, auditVisible, auditLoading, auditProgressText, auditResults, submitted, copyStatus, onCopyFromArchive, collectFormValues, showAudit, onSubmit, applyFieldUpdate, productInstance, demandView, schemeView, auditView, calibrateView, testRepView, verifyView, approvalView, monitorView, ref, reactive, computed, onUnmounted, watch, get ElMessage() { return ElMessage }, get launchProductConfig() { return launchProductConfig }, get testReport() { return testReport }, get campusProductConfig() { return campusProductConfig } }
Object.assign(__returned__, { demand: demandView, scheme: schemeView, audit: auditView, calibrate: calibrateView, testRep: testRepView, verify: verifyView, approval: approvalView, monitor: monitorView, monitorTab: ref('single') })
return __returned__
}

}
  // Attach the SFC template (runtime+compiler build compiles it at runtime)
  ConfigWorkbench.template = "\r\n  <div class=\"config-panel\" :style=\"{ width: panelWidth + 'px' }\">\r\n    <!-- 鎷栨嫿璋冨锟?-->\r\n    <div\r\n      class=\"config-panel-resizer\"\r\n      @mousedown=\"startResize\"\r\n      @touchstart.passive=\"startResize\"\r\n    >\r\n      <div class=\"resizer-line\"></div>\r\n    </div>\r\n\r\n    <!-- 椤堕儴鏍囬锟?-->\r\n    <div class=\"config-header\">\r\n      <div class=\"config-header-left\">\r\n        <svg class=\"config-header-icon\" width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n          <path d=\"M12 2L2 7l10 5 10-5-10-5z\"/>\r\n          <path d=\"M2 17l10 5 10-5\"/>\r\n          <path d=\"M2 12l10 5 10-5\"/>\r\n        </svg>\r\n        <div class=\"config-header-text\">\r\n          <div class=\"pp-strip\" ref=\"ppStripRef\">\r\n            <button\r\n              v-for=\"p in visibleProducts\"\r\n              :key=\"p.id\"\r\n              class=\"pp-chip pp-chip-slot\"\r\n              :class=\"{ active: p.id === activeProductId }\"\r\n              @click.stop=\"onSelectProduct(p)\"\r\n              :title=\"p.name\"\r\n            >\r\n              <span class=\"pp-chip-name\">{{ p.name }}</span>\r\n            </button>\r\n          </div>\r\n        </div>\r\n      </div>\r\n      <div class=\"config-header-actions\">\r\n        <button\r\n          class=\"pp-list-btn\"\r\n          :class=\"{ active: productMenuOpen }\"\r\n          @click.stop=\"toggleProductMenu\"\r\n          title=\"鏌ョ湅鍟嗗搧鍒楄〃\"\r\n        >\r\n          <svg width=\"15\" height=\"15\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><line x1=\"8\" y1=\"6\" x2=\"21\" y2=\"6\"/><line x1=\"8\" y1=\"12\" x2=\"21\" y2=\"12\"/><line x1=\"8\" y1=\"18\" x2=\"21\" y2=\"18\"/><line x1=\"3\" y1=\"6\" x2=\"3.01\" y2=\"6\"/><line x1=\"3\" y1=\"12\" x2=\"3.01\" y2=\"12\"/><line x1=\"3\" y1=\"18\" x2=\"3.01\" y2=\"18\"/></svg>\r\n          <span class=\"pp-chip-name\">鏌ョ湅鍟嗗搧鍒楄〃</span>\r\n          <span class=\"pp-count\">{{ products.length }}</span>\r\n        </button>\r\n        <div v-if=\"productMenuOpen\" class=\"product-picker product-picker-menu\">\r\n          <div class=\"pp-menu-head\">鍏ㄩ儴鍟嗗搧<span class=\"pp-menu-count\">鍏?{{ products.length }} 涓?/span></div>\r\n          <div class=\"pp-menu-body\">\r\n            <div v-for=\"p in products\" :key=\"p.id\" class=\"pp-menu-item\" :class=\"{ active: p.id === activeProductId }\" @click.stop=\"onSelectProduct(p)\" :title=\"p.name\">\r\n              <div class=\"pp-menu-item-top\">\r\n                <span class=\"pp-menu-name\">{{ p.name }}</span>\r\n                <span class=\"pp-menu-status\">{{ p.status }}</span>\r\n              </div>\r\n              <div class=\"pp-menu-item-sub\">\r\n                <span class=\"pp-menu-code\">{{ p.code }}</span>\r\n                <span class=\"pp-menu-fee\" v-if=\"p.fee\">{{ p.fee }}鍏?鏈?/span>\r\n                <svg v-if=\"p.id === activeProductId\" class=\"pp-menu-check\" width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"3\"><polyline points=\"20 6 9 17 4 12\"/></svg>\r\n              </div>\r\n            </div>\r\n          </div>\r\n        </div>\r\n        <button v-if=\"isArchive && !copyMode\" class=\"config-copy-btn\" @click=\"onCopyFromArchive\" title=\"鍩轰簬璇ュ瓨閲忓晢鍝佸鍒舵柊濂楅\">\r\n          <svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n            <rect x=\"9\" y=\"9\" width=\"13\" height=\"13\" rx=\"2\"/>\r\n            <path d=\"M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1\"/>\r\n          </svg>\r\n          <span>澶嶅埗</span>\r\n        </button>\r\n      </div>\r\n    </div>\r\n\r\n    <!-- Tab 瀵艰埅 -->\r\n    <div class=\"config-tabs\">\r\n      <button\r\n        v-for=\"tab in tabs\"\r\n        :key=\"tab.key\"\r\n        class=\"config-tab\"\r\n        :class=\"{ active: activeTab === tab.key }\"\r\n        @click=\"activeTab = tab.key\"\r\n      >\r\n        <svg v-if=\"tab.key === 'base'\" width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n          <path d=\"M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z\"/>\r\n          <polyline points=\"14 2 14 8 20 8\"/>\r\n        </svg>\r\n        <svg v-else-if=\"tab.key === 'resource'\" width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n          <path d=\"M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z\"/>\r\n          <polyline points=\"3.27 6.96 12 12.01 20.73 6.96\"/>\r\n          <line x1=\"12\" y1=\"22.08\" x2=\"12\" y2=\"12\"/>\r\n        </svg>\r\n        <svg v-else-if=\"tab.key === 'marketing'\" width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n          <line x1=\"12\" y1=\"1\" x2=\"12\" y2=\"23\"/>\r\n          <path d=\"M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6\"/>\r\n        </svg>\r\n        <svg v-else-if=\"tab.key === 'sales'\" width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n          <path d=\"M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2\"/>\r\n          <rect x=\"8\" y=\"2\" width=\"8\" height=\"4\" rx=\"1\"/>\r\n          <path d=\"M9 14l2 2 4-4\"/>\r\n        </svg>\r\n        <svg v-else width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n          <path d=\"M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z\"/>\r\n          <path d=\"M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z\"/>\r\n        </svg>\r\n        {{ tab.label }}\r\n      </button>\r\n    </div>\r\n\r\n    <!-- Tab 鍐呭锟?-->\r\n    <div class=\"config-body\">\r\n      <!-- 鍩虹淇℃伅 Tab -->\r\n            <!-- 閰嶇疆淇℃伅 Tab锛堝悎骞讹細鍩虹淇℃伅/鏈堢/璇煶/娴侀噺/鐭俊/鍓嶉」闄愬埗/鍚庨」闄愬埗锛?-->\n      <div v-show=\"activeTab === 'config'\" class=\"tab-content\">\n<div class=\"form-section\">\r\n          <div class=\"section-header open\">\r\n            <span class=\"section-title\">鍩虹淇℃伅</span>\r\n          </div>\r\n          <div class=\"section-content base-info-grid\">\r\n            <div class=\"form-row base-field-row\" v-for=\"field in baseFields\" :key=\"field.key\">\r\n              <label class=\"form-label\">\r\n                <span v-if=\"field.required\" class=\"required\">*</span>\r\n                <span class=\"label-text\">{{ field.label }}</span>\r\n                <span v-if=\"fieldTag(field) === 'ai'\" class=\"field-tag tag-ai\" :data-reason=\"aiFilled[field.key] || field.tagReason\">AI鎺ㄨ崘</span>\r\n                <span v-else-if=\"fieldTag(field) === 'reuse'\" class=\"field-tag tag-reuse\" :data-reason=\"field.tagReason\">澶嶇敤</span>\r\n                <span v-else-if=\"fieldTag(field) === 'parsed'\" class=\"field-tag tag-parsed\" :data-reason=\"parsedFilled[field.key] || '鏍规嵁鐢ㄦ埛闇€姹傝В鏋愬緱鍑?\">宸茶В鏋?/span>\r\n              </label>\r\n              <div class=\"form-control\">\r\n                <select v-if=\"field.options\" v-model=\"formData[field.key]\" class=\"form-input form-select\" :class=\"{ modified: modifiedFields[field.key] }\" :disabled=\"submitted\" @change=\"markModified(field.key)\">\r\n                  <option v-for=\"opt in field.options\" :key=\"opt.value\" :value=\"opt.value\">{{ opt.label }}</option>\r\n                </select>\r\n                <input\r\n                  v-else\r\n                  :type=\"field.type || 'text'\"\r\n                  v-model=\"formData[field.key]\"\r\n                  class=\"form-input\"\r\n                  :class=\"{ modified: modifiedFields[field.key] }\"\r\n                  :disabled=\"submitted\"\r\n                  @input=\"markModified(field.key)\"\r\n                />\r\n              </div>\r\n            </div>\r\n          </div>\r\n        </div>\n<div\r\n          v-for=\"section in marketingSections\"\r\n          :key=\"section.key\"\r\n          class=\"form-section\"\r\n        >\r\n          <div class=\"section-header\" :class=\"{ open: expandedSections[section.key] }\" @click=\"toggleSection(section.key)\">\r\n            <span class=\"section-title\">{{ section.title }}</span>\r\n            <svg class=\"section-chevron\" width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n              <polyline points=\"6 9 12 15 18 9\"/>\r\n            </svg>\r\n          </div>\r\n          <div v-show=\"expandedSections[section.key]\" class=\"section-content\">\r\n            <div class=\"form-row\" v-for=\"field in section.fields\" :key=\"field.key\">\r\n              <label class=\"form-label\">\r\n                <span v-if=\"field.required\" class=\"required\">*</span>\r\n                <span class=\"label-text\">{{ field.label }}</span>\r\n                <span v-if=\"fieldTag(field) === 'ai'\" class=\"field-tag tag-ai\" :data-reason=\"aiFilled[field.key] || field.tagReason\">AI鎺ㄨ崘</span>\r\n                <span v-else-if=\"fieldTag(field) === 'reuse'\" class=\"field-tag tag-reuse\" :data-reason=\"field.tagReason\">澶嶇敤</span>\r\n                <span v-else-if=\"fieldTag(field) === 'parsed'\" class=\"field-tag tag-parsed\" :data-reason=\"parsedFilled[field.key] || '鏍规嵁鐢ㄦ埛闇€姹傝В鏋愬緱鍑?\">宸茶В鏋?/span>\r\n              </label>\r\n              <div class=\"form-control\">\r\n                <div v-if=\"field.unit\" class=\"input-with-unit\">\r\n                  <input\r\n                    :type=\"field.type || 'text'\"\r\n                    v-model=\"formData[field.key]\"\r\n                    class=\"form-input\"\r\n                    :class=\"{ modified: modifiedFields[field.key], highlight: field.highlight }\"\r\n                    :disabled=\"submitted\"\r\n                    @input=\"markModified(field.key)\"\r\n                  />\r\n                  <span class=\"input-unit\">{{ field.unit }}</span>\r\n                </div>\r\n                <select\r\n                  v-else-if=\"field.type === 'select'\"\r\n                  v-model=\"formData[field.key]\"\r\n                  class=\"form-input form-select\"\r\n                  :class=\"{ modified: modifiedFields[field.key] }\"\r\n                  :disabled=\"submitted\"\r\n                  @change=\"markModified(field.key)\"\r\n                >\r\n                  <option v-for=\"opt in field.options\" :key=\"opt.value\" :value=\"opt.value\">{{ opt.label }}</option>\r\n                </select>\r\n                <textarea\r\n                  v-else-if=\"field.type === 'textarea'\"\r\n                  v-model=\"formData[field.key]\"\r\n                  class=\"form-input form-textarea\"\r\n                  :class=\"{ modified: modifiedFields[field.key], highlight: field.highlight }\"\r\n                  :disabled=\"submitted\"\r\n                  rows=\"3\"\r\n                  @input=\"markModified(field.key)\"\r\n                ></textarea>\r\n                <input\r\n                  v-else\r\n                  :type=\"field.type || 'text'\"\r\n                  v-model=\"formData[field.key]\"\r\n                  class=\"form-input\"\r\n                  :class=\"{ modified: modifiedFields[field.key], highlight: field.highlight }\"\r\n                  :disabled=\"submitted\"\r\n                  @input=\"markModified(field.key)\"\r\n                />\r\n              </div>\r\n            </div>\r\n          </div>\r\n        </div>\n<div\r\n          v-for=\"section in resourceSections\"\r\n          :key=\"section.key\"\r\n          class=\"form-section\"\r\n        >\r\n          <div class=\"section-header\" :class=\"{ open: expandedSections[section.key] }\" @click=\"toggleSection(section.key)\">\r\n            <span class=\"section-title\">{{ section.title }}</span>\r\n            <svg class=\"section-chevron\" width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n              <polyline points=\"6 9 12 15 18 9\"/>\r\n            </svg>\r\n          </div>\r\n          <div v-show=\"expandedSections[section.key]\" class=\"section-content\">\r\n            <!-- table 绫诲瀷锛氳〃鏍?+ 娣诲姞鎸夐挳 -->\r\n            <template v-for=\"field in section.fields\" :key=\"field.key\">\r\n              <div v-if=\"field.type === 'table'\" class=\"table-field\">\r\n                <div class=\"table-field-header\">\r\n                  <label class=\"form-label table-field-label\">\r\n                    <span class=\"label-text\">{{ field.label }}</span>\r\n                    <span v-if=\"fieldTag(field) === 'ai'\" class=\"field-tag tag-ai\" :data-reason=\"aiFilled[field.key] || field.tagReason\">AI鎺ㄨ崘</span>\r\n                    <span v-else-if=\"fieldTag(field) === 'reuse'\" class=\"field-tag tag-reuse\" :data-reason=\"field.tagReason\">澶嶇敤</span>\r\n                <span v-else-if=\"fieldTag(field) === 'parsed'\" class=\"field-tag tag-parsed\" :data-reason=\"parsedFilled[field.key] || '鏍规嵁鐢ㄦ埛闇€姹傝В鏋愬緱鍑?\">宸茶В鏋?/span>\r\n                  </label>\r\n                  <button class=\"table-add-btn\" :disabled=\"submitted\" @click=\"addTableRow(field.key, field.columns)\">\r\n                    <svg width=\"12\" height=\"12\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\">\r\n                      <line x1=\"12\" y1=\"5\" x2=\"12\" y2=\"19\"/>\r\n                      <line x1=\"5\" y1=\"12\" x2=\"19\" y2=\"12\"/>\r\n                    </svg>\r\n                    娣诲姞\r\n                  </button>\r\n                </div>\r\n                <div class=\"limit-table-wrap\">\r\n                  <table class=\"limit-table\">\r\n                    <thead>\r\n                      <tr>\r\n                        <th v-for=\"col in field.columns\" :key=\"col.key\">{{ col.label }}</th>\r\n                        <th class=\"col-action\">鎿嶄綔</th>\r\n                      </tr>\r\n                    </thead>\r\n                    <tbody>\r\n                      <tr v-for=\"(row, rowIdx) in tableRows(field.key)\" :key=\"rowIdx\">\r\n                        <td v-for=\"col in field.columns\" :key=\"col.key\">\r\n                          <div v-if=\"col.type === 'auto'\" class=\"table-cell-auto\">\r\n                            {{ generatePreLimitTip(field.key, rowIdx) }}\r\n                          </div>\r\n                          <select\r\n                            v-else-if=\"col.type === 'select' && col.dynamicOptions\"\r\n                            :value=\"row[col.key]\"\r\n                            class=\"table-cell-input table-cell-select\"\r\n                            :disabled=\"submitted\"\r\n                            @change=\"onDynamicOptionChange(field.key, rowIdx, col.key, $event)\"\r\n                          >\r\n                            <option v-for=\"opt in getDynamicOptions(col, row)\" :key=\"opt.value\" :value=\"opt.value\">{{ opt.label }}</option>\r\n                          </select>\r\n                          <select\r\n                            v-else-if=\"col.type === 'select'\"\r\n                            :value=\"row[col.key]\"\r\n                            class=\"table-cell-input table-cell-select\"\r\n                            :disabled=\"submitted\"\r\n                            @change=\"onSelectCellChange(field.key, rowIdx, col.key, $event)\"\r\n                          >\r\n                            <option v-for=\"opt in col.options\" :key=\"opt.value\" :value=\"opt.value\">{{ opt.label }}</option>\r\n                          </select>\r\n                          <input\r\n                            v-else\r\n                            type=\"text\"\r\n                            :value=\"row[col.key]\"\r\n                            class=\"table-cell-input\"\r\n                            :placeholder=\"col.placeholder || ''\"\r\n                            :disabled=\"submitted\"\r\n                            @input=\"onInputCellChange(field.key, rowIdx, col.key, $event)\"\r\n                          />\r\n                        </td>\r\n                        <td class=\"col-action\">\r\n                          <button class=\"table-del-btn\" :disabled=\"submitted\" @click=\"deleteTableRow(field.key, rowIdx)\" title=\"鍒犻櫎\">\r\n                            <svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n                              <polyline points=\"3 6 5 6 21 6\"/>\r\n                              <path d=\"M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2\"/>\r\n                            </svg>\r\n                          </button>\r\n                        </td>\r\n                      </tr>\r\n                      <tr v-if=\"tableRows(field.key).length === 0\">\r\n                        <td :colspan=\"field.columns.length + 1\" class=\"table-empty\">鏆傛棤鏁版嵁锛岀偣鍑籠"娣诲姞\"鎸夐挳鏂板</td>\r\n                      </tr>\r\n                    </tbody>\r\n                  </table>\r\n                </div>\r\n              </div>\r\n              <!-- 甯歌瀛楁 -->\r\n              <div v-else class=\"form-row\">\r\n              <label class=\"form-label\">\r\n                <span v-if=\"field.required\" class=\"required\">*</span>\r\n                <span class=\"label-text\">{{ field.label }}</span>\r\n                <span v-if=\"fieldTag(field) === 'ai'\" class=\"field-tag tag-ai\" :data-reason=\"aiFilled[field.key] || field.tagReason\">AI鎺ㄨ崘</span>\r\n                <span v-else-if=\"fieldTag(field) === 'reuse'\" class=\"field-tag tag-reuse\" :data-reason=\"field.tagReason\">澶嶇敤</span>\r\n                <span v-else-if=\"fieldTag(field) === 'parsed'\" class=\"field-tag tag-parsed\" :data-reason=\"parsedFilled[field.key] || '鏍规嵁鐢ㄦ埛闇€姹傝В鏋愬緱鍑?\">宸茶В鏋?/span>\r\n              </label>\r\n              <div class=\"form-control\">\r\n                <div v-if=\"field.unit\" class=\"input-with-unit\">\r\n                  <input\r\n                    :type=\"field.type || 'text'\"\r\n                    v-model=\"formData[field.key]\"\r\n                    class=\"form-input\"\r\n                    :class=\"{ modified: modifiedFields[field.key], highlight: field.highlight }\"\r\n                    :disabled=\"submitted\"\r\n                    @input=\"markModified(field.key)\"\r\n                  />\r\n                  <span class=\"input-unit\">{{ field.unit }}</span>\r\n                </div>\r\n                <select\r\n                  v-else-if=\"field.type === 'select'\"\r\n                  v-model=\"formData[field.key]\"\r\n                  class=\"form-input form-select\"\r\n                  :class=\"{ modified: modifiedFields[field.key] }\"\r\n                  :disabled=\"submitted\"\r\n                  @change=\"markModified(field.key)\"\r\n                >\r\n                  <option v-for=\"opt in field.options\" :key=\"opt.value\" :value=\"opt.value\">{{ opt.label }}</option>\r\n                </select>\r\n                <textarea\r\n                  v-else-if=\"field.type === 'textarea'\"\r\n                  v-model=\"formData[field.key]\"\r\n                  class=\"form-input form-textarea\"\r\n                  :class=\"{ modified: modifiedFields[field.key], highlight: field.highlight }\"\r\n                  :disabled=\"submitted\"\r\n                  rows=\"3\"\r\n                  @input=\"markModified(field.key)\"\r\n                ></textarea>\r\n                <input\r\n                  v-else\r\n                  :type=\"field.type || 'text'\"\r\n                  v-model=\"formData[field.key]\"\r\n                  class=\"form-input\"\r\n                  :class=\"{ modified: modifiedFields[field.key], highlight: field.highlight }\"\r\n                  :disabled=\"submitted\"\r\n                  @input=\"markModified(field.key)\"\r\n                />\r\n              </div>\r\n              </div>\r\n            </template>\r\n          </div>\r\n        </div>\n<div\r\n          v-for=\"section in salesSections\"\r\n          :key=\"section.key\"\r\n          class=\"form-section\"\r\n        >\r\n          <div class=\"section-header\" :class=\"{ open: expandedSections[section.key] }\" @click=\"toggleSection(section.key)\">\r\n            <span class=\"section-title\">{{ section.title }}</span>\r\n            <svg class=\"section-chevron\" width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n              <polyline points=\"6 9 12 15 18 9\"/>\r\n            </svg>\r\n          </div>\r\n          <div v-show=\"expandedSections[section.key]\" class=\"section-content\">\r\n            <!-- 閬嶅巻 section 鍐呭瓧锟?-->\r\n            <template v-for=\"field in section.fields\" :key=\"field.key\">\r\n              <!-- table 绫诲瀷锛氳〃锟?+ 娣诲姞鎸夐挳 -->\r\n              <div v-if=\"field.type === 'table'\" class=\"table-field\">\r\n                <div class=\"table-field-header\">\r\n                  <label class=\"form-label table-field-label\">\r\n                    <span class=\"label-text\">{{ field.label }}</span>\r\n                    <span v-if=\"fieldTag(field) === 'ai'\" class=\"field-tag tag-ai\" :data-reason=\"aiFilled[field.key] || field.tagReason\">AI鎺ㄨ崘</span>\r\n                    <span v-else-if=\"fieldTag(field) === 'reuse'\" class=\"field-tag tag-reuse\" :data-reason=\"field.tagReason\">澶嶇敤</span>\r\n                <span v-else-if=\"fieldTag(field) === 'parsed'\" class=\"field-tag tag-parsed\" :data-reason=\"parsedFilled[field.key] || '鏍规嵁鐢ㄦ埛闇€姹傝В鏋愬緱鍑?\">宸茶В鏋?/span>\r\n                  </label>\r\n                  <button class=\"table-add-btn\" :disabled=\"submitted\" @click=\"addTableRow(field.key, field.columns)\">\r\n                    <svg width=\"12\" height=\"12\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\">\r\n                      <line x1=\"12\" y1=\"5\" x2=\"12\" y2=\"19\"/>\r\n                      <line x1=\"5\" y1=\"12\" x2=\"19\" y2=\"12\"/>\r\n                    </svg>\r\n                    娣诲姞\r\n                  </button>\r\n                </div>\r\n                <div class=\"limit-table-wrap\">\r\n                  <table class=\"limit-table\">\r\n                    <thead>\r\n                      <tr>\r\n                        <th v-for=\"col in field.columns\" :key=\"col.key\">{{ col.label }}</th>\r\n                        <th class=\"col-action\">鎿嶄綔</th>\r\n                      </tr>\r\n                    </thead>\r\n                    <tbody>\r\n                      <tr v-for=\"(row, rowIdx) in tableRows(field.key)\" :key=\"rowIdx\">\r\n                        <td v-for=\"col in field.columns\" :key=\"col.key\">\r\n                          <!-- auto 绫诲瀷锛氬彧璇伙紝鏍规嵁鍚岃鍏朵粬鍗曞厓鏍艰嚜鍔ㄧ敓鎴愭彁绀鸿 -->\r\n                          <div v-if=\"col.type === 'auto'\" class=\"table-cell-auto\">\r\n                            {{ generatePreLimitTip(field.key, rowIdx) }}\r\n                          </div>\r\n                          <!-- dynamicOptions 绫诲瀷锛氶€夐」渚濊禆鍚岃鍙︿竴涓瓧娈电殑锟?-->\r\n                          <select\r\n                            v-else-if=\"col.type === 'select' && col.dynamicOptions\"\r\n                            :value=\"row[col.key]\"\r\n                            class=\"table-cell-input table-cell-select\"\r\n                            :disabled=\"submitted\"\r\n                            @change=\"onDynamicOptionChange(field.key, rowIdx, col.key, $event)\"\r\n                          >\r\n                            <option v-for=\"opt in getDynamicOptions(col, row)\" :key=\"opt.value\" :value=\"opt.value\">{{ opt.label }}</option>\r\n                          </select>\r\n                          <!-- 鏅€歴elect -->\r\n                          <select\r\n                            v-else-if=\"col.type === 'select'\"\r\n                            :value=\"row[col.key]\"\r\n                            class=\"table-cell-input table-cell-select\"\r\n                            :disabled=\"submitted\"\r\n                            @change=\"onSelectCellChange(field.key, rowIdx, col.key, $event)\"\r\n                          >\r\n                            <option v-for=\"opt in col.options\" :key=\"opt.value\" :value=\"opt.value\">{{ opt.label }}</option>\r\n                          </select>\r\n                          <!-- 鏅€歩nput -->\r\n                          <input\r\n                            v-else\r\n                            type=\"text\"\r\n                            :value=\"row[col.key]\"\r\n                            class=\"table-cell-input\"\r\n                            :placeholder=\"col.placeholder || ''\"\r\n                            :disabled=\"submitted\"\r\n                            @input=\"onInputCellChange(field.key, rowIdx, col.key, $event)\"\r\n                          />\r\n                        </td>\r\n                        <td class=\"col-action\">\r\n                          <button class=\"table-del-btn\" :disabled=\"submitted\" @click=\"deleteTableRow(field.key, rowIdx)\" title=\"鍒犻櫎\">\r\n                            <svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n                              <polyline points=\"3 6 5 6 21 6\"/>\r\n                              <path d=\"M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2\"/>\r\n                            </svg>\r\n                          </button>\r\n                        </td>\r\n                      </tr>\r\n                      <tr v-if=\"tableRows(field.key).length === 0\">\r\n                        <td :colspan=\"field.columns.length + 1\" class=\"table-empty\">鏆傛棤鏁版嵁锛岀偣鍑籠"娣诲姞\"鎸夐挳鏂板</td>\r\n                      </tr>\r\n                    </tbody>\r\n                  </table>\r\n                </div>\r\n              </div>\r\n              <!-- 甯歌瀛楁锛歝heckbox-group / select / input -->\r\n              <div v-else class=\"form-row\">\r\n                <label class=\"form-label\">\r\n                  <span v-if=\"field.required\" class=\"required\">*</span>\r\n                  <span class=\"label-text\">{{ field.label }}</span>\r\n                  <span v-if=\"fieldTag(field) === 'ai'\" class=\"field-tag tag-ai\" :data-reason=\"aiFilled[field.key] || field.tagReason\">AI鎺ㄨ崘</span>\r\n                  <span v-else-if=\"fieldTag(field) === 'reuse'\" class=\"field-tag tag-reuse\" :data-reason=\"field.tagReason\">澶嶇敤</span>\r\n                <span v-else-if=\"fieldTag(field) === 'parsed'\" class=\"field-tag tag-parsed\" :data-reason=\"parsedFilled[field.key] || '鏍规嵁鐢ㄦ埛闇€姹傝В鏋愬緱鍑?\">宸茶В鏋?/span>\r\n                </label>\r\n                <div class=\"form-control\">\r\n                  <!-- checkbox-group: 澶氶€夋锟?-->\r\n                  <div v-if=\"field.type === 'checkbox-group'\" class=\"checkbox-group\">\r\n                    <label\r\n                      v-for=\"opt in field.options\"\r\n                      :key=\"opt.value\"\r\n                      class=\"checkbox-item\"\r\n                      :class=\"{ checked: checkboxValue(field.key, opt.value) }\"\r\n                    >\r\n                      <input\r\n                        type=\"checkbox\"\r\n                        :value=\"opt.value\"\r\n                        :checked=\"checkboxValue(field.key, opt.value)\"\r\n                        :disabled=\"submitted\"\r\n                        @change=\"toggleCheckbox(field.key, opt.value)\"\r\n                      />\r\n                      <span>{{ opt.label }}</span>\r\n                    </label>\r\n                  </div>\r\n                  <div v-else-if=\"field.unit\" class=\"input-with-unit\">\r\n                    <input\r\n                      :type=\"field.type || 'text'\"\r\n                      v-model=\"formData[field.key]\"\r\n                      class=\"form-input\"\r\n                      :class=\"{ modified: modifiedFields[field.key], highlight: field.highlight }\"\r\n                      :disabled=\"submitted\"\r\n                      @input=\"markModified(field.key)\"\r\n                    />\r\n                    <span class=\"input-unit\">{{ field.unit }}</span>\r\n                  </div>\r\n                  <select\r\n                    v-else-if=\"field.type === 'select'\"\r\n                    v-model=\"formData[field.key]\"\r\n                    class=\"form-input form-select\"\r\n                    :class=\"{ modified: modifiedFields[field.key] }\"\r\n                    :disabled=\"submitted\"\r\n                    @change=\"markModified(field.key)\"\r\n                  >\r\n                    <option v-for=\"opt in field.options\" :key=\"opt.value\" :value=\"opt.value\">{{ opt.label }}</option>\r\n                  </select>\r\n                  <textarea\r\n                    v-else-if=\"field.type === 'textarea'\"\r\n                    v-model=\"formData[field.key]\"\r\n                    class=\"form-input form-textarea\"\r\n                    :class=\"{ modified: modifiedFields[field.key], highlight: field.highlight }\"\r\n                    :disabled=\"submitted\"\r\n                    rows=\"3\"\r\n                    @input=\"markModified(field.key)\"\r\n                  ></textarea>\r\n                  <input\r\n                    v-else\r\n                    :type=\"field.type || 'text'\"\r\n                    v-model=\"formData[field.key]\"\r\n                    class=\"form-input\"\r\n                    :class=\"{ modified: modifiedFields[field.key], highlight: field.highlight }\"\r\n                    :disabled=\"submitted\"\r\n                    @input=\"markModified(field.key)\"\r\n                  />\r\n                </div>\r\n              </div>\r\n            </template>\r\n          </div>\r\n        </div>\n      </div>\n<!-- 闇€姹傛彁鎶ュ崟 Tab -->\n      <div v-show=\"activeTab === 'demand'\" class=\"tab-content rv-wrap\">\n        <div class=\"rv-card\">\n          <div class=\"rv-head\">\n            <div class=\"rv-title\">\n              <svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z\"/><polyline points=\"14 2 14 8 20 8\"/></svg>\n              <span>闇€姹傛彁鎶ュ崟</span>\n            </div>\n            <span class=\"rv-badge badge-blue\">{{ demand.name }}</span>\n          </div>\n          <div class=\"rv-grid grid-2\">\n            <div class=\"rv-cell\"><span class=\"rv-k\">鎻愭姤缂栧彿</span><span class=\"rv-v\">{{ demand.no }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">鎻愭姤浜?/span><span class=\"rv-v\">{{ demand.applicant }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">鎻愭姤閮ㄩ棬</span><span class=\"rv-v\">{{ demand.department }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">鎻愭姤鏃ユ湡</span><span class=\"rv-v\">{{ demand.date }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">鐩爣瀹㈡埛缇?/span><span class=\"rv-v\">{{ demand.target }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">鏈熸湜鐢熸晥鏃ユ湡</span><span class=\"rv-v\">{{ demand.effective }}</span></div>\n          </div>\n          <div class=\"rv-block\">\n            <div class=\"rv-k\">涓氬姟闇€姹傛杩?/div>\n            <div class=\"rv-p\">{{ demand.require }}</div>\n          </div>\n          <div class=\"rv-block\">\n            <div class=\"rv-k\">閿€鍞笭閬?/div>\n            <div class=\"rv-tags\"><span v-for=\"c in demand.channels\" :key=\"c\" class=\"rv-tag\">{{ c }}</span></div>\n          </div>\n        </div>\n      </div>\n\n      <!-- 鍔犺浇鏂规 Tab -->\n      <div v-show=\"activeTab === 'scheme'\" class=\"tab-content rv-wrap\">\n        <div class=\"rv-card\">\n          <div class=\"rv-head\">\n            <div class=\"rv-title\">\n              <svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z\"/><polyline points=\"14 2 14 8 20 8\"/></svg>\n              <span>鍔犺浇鏂规</span>\n            </div>\n            <span class=\"rv-badge badge-green\">{{ scheme.status }}</span>\n          </div>\n          <div class=\"rv-grid grid-2\">\n            <div class=\"rv-cell\"><span class=\"rv-k\">鏂规缂栧彿</span><span class=\"rv-v\">{{ scheme.no }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">鍏宠仈闇€姹傚崟</span><span class=\"rv-v\">{{ scheme.requireNo }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">鏂规鍚嶇О</span><span class=\"rv-v\">{{ scheme.name }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">鏂规鐗堟湰 / 缂栧埗</span><span class=\"rv-v\">{{ scheme.version }} 路 {{ scheme.author }}</span></div>\n          </div>\n          <div class=\"rv-block\">\n            <div class=\"rv-k\">鏂规瑕佺偣</div>\n            <div class=\"rv-summary\">\n              <div v-for=\"(row,i) in scheme.summary\" :key=\"i\" class=\"rv-sum-row\">\n                <span class=\"rv-sum-k\">{{ row[0] }}</span>\n                <span class=\"rv-sum-v\">{{ row[1] }}</span>\n              </div>\n            </div>\n          </div>\n          <div class=\"rv-block\">\n            <div class=\"rv-k\">鍔犺浇閰嶇疆妯″潡</div>\n            <div class=\"rv-tags\"><span v-for=\"it in scheme.items\" :key=\"it\" class=\"rv-tag\">{{ it }}</span></div>\n          </div>\n        </div>\n      </div>\n\n      <!-- 绋芥牳鎶ュ憡 Tab -->\n      <div v-show=\"activeTab === 'audit'\" class=\"tab-content rv-wrap\">\n        <div class=\"rv-card\">\n          <div class=\"rv-head\">\n            <div class=\"rv-title\">\n              <svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M9 12l2 2 4-4\"/><circle cx=\"12\" cy=\"12\" r=\"10\"/></svg>\n              <span>閰嶇疆瑙勬牸绋芥牳鎶ュ憡</span>\n            </div>\n            <span class=\"rv-badge badge-green\">绋芥牳閫氳繃</span>\n          </div>\n          <div class=\"rv-strip\">\n            <div class=\"rv-cell\"><span class=\"rv-k\">绋芥牳瀵硅薄</span><span class=\"rv-v mono\">{{ audit.object }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">绋芥牳鏃堕棿</span><span class=\"rv-v\">{{ audit.time }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">绋芥牳缁撴灉</span><span class=\"rv-v\" style=\"color:#16a34a;font-weight:600\">{{ audit.status }}</span></div>\n          </div>\n          <div class=\"rv-block\">\n            <div class=\"rv-k\">绋芥牳妫€鏌ラ」</div>\n            <table class=\"rv-table\">\n              <thead><tr><th>妫€鏌ラ」</th><th style=\"width:120px\">缁撴灉</th></tr></thead>\n              <tbody>\n                <tr v-for=\"(c,i) in audit.checks\" :key=\"i\">\n                  <td>{{ c[0] }}</td>\n                  <td><span class=\"rv-badge badge-green\">{{ c[1] }}</span></td>\n                </tr>\n              </tbody>\n            </table>\n          </div>\n          <div class=\"rv-concl concl-pass\">\n            <span class=\"rv-cc-icon\">鉁?/span>\n            <span>{{ audit.conclusion }}</span>\n          </div>\n        </div>\n      </div>\n\n      <!-- 鏍″噯鎶ュ憡 Tab -->\n      <div v-show=\"activeTab === 'calibrate'\" class=\"tab-content rv-wrap\">\n        <div class=\"rv-card\">\n          <div class=\"rv-head\">\n            <div class=\"rv-title\">\n              <svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><circle cx=\"12\" cy=\"12\" r=\"10\"/><path d=\"M12 6v6l4 2\"/></svg>\n              <span>璧勮垂鏍″噯鎶ュ憡</span>\n            </div>\n            <span class=\"rv-badge badge-green\">鏍″噯閫氳繃</span>\n          </div>\n          <div class=\"rv-strip\">\n            <div class=\"rv-cell\"><span class=\"rv-k\">鏍″噯瀵硅薄</span><span class=\"rv-v mono\">{{ calibrate.object }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">鏍″噯鏃堕棿</span><span class=\"rv-v\">{{ calibrate.time }}</span></div>\n          </div>\n          <div class=\"rv-block\">\n            <div class=\"rv-k\">姣斿鏄庣粏锛堝椁愰渶姹?vs 璁¤垂閰嶇疆锛?/div>\n            <table class=\"rv-table\">\n              <thead><tr><th>椤圭洰</th><th>濂楅鎻忚堪锛堥渶姹傦級</th><th>璁¤垂閰嶇疆鎻忚堪锛堢郴缁燂級</th><th style=\"width:100px\">姣斿缁撴灉</th></tr></thead>\n              <tbody>\n                <tr v-for=\"(r,i) in calibrate.rows\" :key=\"i\">\n                  <td>{{ r[0] }}</td><td>{{ r[1] }}</td><td>{{ r[2] }}</td>\n                  <td><span class=\"rv-badge badge-green\">{{ r[3] }}</span></td>\n                </tr>\n              </tbody>\n            </table>\n          </div>\n          <div class=\"rv-concl concl-pass\">\n            <span class=\"rv-cc-icon\">鉁?/span>\n            <span>{{ calibrate.conclusion }}</span>\n          </div>\n        </div>\n      </div>\n\n      <!-- 娴嬭瘯鎶ュ憡 Tab -->\n      <div v-show=\"activeTab === 'testrep'\" class=\"tab-content rv-wrap\">\n        <div class=\"rv-card\">\n          <div class=\"rv-head\">\n            <div class=\"rv-title\">\n              <svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><polygon points=\"12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26\"/></svg>\n              <span>閿€鍞搧鑷姩鍖栨祴璇曟姤鍛?/span>\n            </div>\n            <span class=\"rv-badge badge-blue\">寤鸿涓婄嚎</span>\n          </div>\n          <div class=\"rv-metrics\">\n            <div class=\"rv-metric\"><span class=\"rv-m-num\">{{ testRep.total }}</span><span class=\"rv-m-tt\">鎬荤敤渚嬫暟</span></div>\n            <div class=\"rv-metric\"><span class=\"rv-m-num\" style=\"color:#16a34a\">{{ testRep.pass }}</span><span class=\"rv-m-tt\">閫氳繃鐢ㄤ緥</span></div>\n            <div class=\"rv-metric\"><span class=\"rv-m-num\" style=\"color:#16a34a\">{{ testRep.rate }}</span><span class=\"rv-m-tt\">閫氳繃鐜?/span></div>\n            <div class=\"rv-metric\"><span class=\"rv-m-num\" style=\"color:#2563eb\">{{ testRep.conclusion }}</span><span class=\"rv-m-tt\">鏁翠綋缁撹</span></div>\n          </div>\n          <div class=\"rv-block\">\n            <div class=\"rv-k\">涓夊ぇ楠岃瘉缁村害</div>\n            <table class=\"rv-table\">\n              <thead><tr><th>楠岃瘉缁村害</th><th>鐢ㄤ緥鏁?/th><th>閫氳繃</th><th style=\"width:100px\">缁撴灉</th></tr></thead>\n              <tbody>\n                <tr v-for=\"(d,i) in testRep.dims\" :key=\"i\">\n                  <td>{{ d[0] }}</td><td>{{ d[1] }}</td><td>{{ d[2] }}</td>\n                  <td><span class=\"rv-badge badge-green\">{{ d[3] }}</span></td>\n                </tr>\n              </tbody>\n            </table>\n          </div>\n          <div class=\"rv-block\">\n            <div class=\"rv-k\">閲嶇偣鐢ㄤ緥锛堣妭閫夛級</div>\n            <table class=\"rv-table\">\n              <thead><tr><th>鐢ㄤ緥ID</th><th>鐢ㄤ緥鍚嶇О</th><th style=\"width:80px\">绛夌骇</th><th style=\"width:100px\">缁撴灉</th></tr></thead>\n              <tbody>\n                <tr v-for=\"(c,i) in testRep.cases\" :key=\"i\">\n                  <td>{{ c[0] }}</td><td>{{ c[1] }}</td><td>{{ c[2] }}</td>\n                  <td><span class=\"rv-badge badge-green\">{{ c[3] }}</span></td>\n                </tr>\n              </tbody>\n            </table>\n          </div>\n          <div class=\"rv-block\">\n            <div class=\"rv-k\">鍙楃悊楠岃瘉鎶ュ憡锛堝苟鍏ヨ嚜鍔ㄥ寲娴嬭瘯锛?/div>\n            <div class=\"rv-strip\" style=\"margin-bottom:10px\">\n              <div class=\"rv-cell\"><span class=\"rv-k\">鍙楃悊鍑瘉 orderId</span><span class=\"rv-v mono\">{{ verify.orderId }}</span></div>\n              <div class=\"rv-cell\"><span class=\"rv-k\">offerInstId</span><span class=\"rv-v mono\">{{ verify.offerInstId }}</span></div>\n              <div class=\"rv-cell\"><span class=\"rv-k\">楠岃瘉鏃堕棿</span><span class=\"rv-v\">{{ verify.time }}</span></div>\n            </div>\n            <table class=\"rv-table\">\n              <thead><tr><th>鍙楃悊鍦烘櫙</th><th style=\"width:120px\">缁撴灉</th></tr></thead>\n              <tbody>\n                <tr v-for=\"(s,i) in verify.scenes\" :key=\"i\">\n                  <td>{{ s[0] }}</td><td><span class=\"rv-badge badge-green\">{{ s[1] }}</span></td>\n                </tr>\n              </tbody>\n            </table>\n            <div class=\"rv-k\" style=\"margin-top:12px\">鍏抽敭娴嬬偣</div>\n            <div class=\"rv-tags\"><span v-for=\"(p,i) in verify.points\" :key=\"i\" class=\"rv-tag tag-ok\">鉁?{{ p[0] }} 路 {{ p[1] }}</span></div>\n          </div>\n        </div>\n      </div>\n\n      <!-- 瀹℃壒鐭╅樀 Tab -->\n      <div v-show=\"activeTab === 'approval'\" class=\"tab-content rv-wrap\">\n        <div class=\"rv-card\">\n          <div class=\"rv-head\">\n            <div class=\"rv-title\">\n              <svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M22 11.08V12a10 10 0 1 1-5.93-9.14\"/><polyline points=\"22 4 12 14.01 9 11.01\"/></svg>\n              <span>涓婄嚎瀹℃壒鐭╅樀</span>\n            </div>\n            <span class=\"rv-badge badge-green\">宸查€氳繃</span>\n          </div>\n          <div class=\"rv-strip\">\n            <div class=\"rv-cell\"><span class=\"rv-k\">瀹℃壒鍗曞彿</span><span class=\"rv-v mono\">{{ approval.no }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">鍏宠仈閿€鍞搧</span><span class=\"rv-v\">{{ approval.name }}</span></div>\n            <div class=\"rv-cell\"><span class=\"rv-k\">鐘舵€?/span><span class=\"rv-v\" style=\"color:#16a34a;font-weight:600\">{{ approval.status }}</span></div>\n          </div>\n          <div class=\"rv-block\">\n            <div class=\"rv-k\">瀹℃壒娴佽浆鐭╅樀</div>\n            <table class=\"rv-table\">\n              <thead><tr><th style=\"width:60px\">搴忓彿</th><th>瀹℃壒鑺傜偣</th><th>瀹℃壒瑙掕壊</th><th style=\"width:90px\">鐘舵€?/th><th>瀹℃壒鎰忚</th><th style=\"width:170px\">鏇存柊鏃堕棿</th></tr></thead>\n              <tbody>\n                <tr v-for=\"(n,i) in approval.nodes\" :key=\"i\">\n                  <td>{{ i + 1 }}</td><td>{{ n[0] }}</td><td>{{ n[1] }}</td>\n                  <td><span class=\"rv-badge badge-green\">{{ n[2] }}</span></td>\n                  <td>{{ n[3] }}</td>\n                  <td>{{ n[4] }}</td>\n                </tr>\n              </tbody>\n            </table>\n          </div>\n          <div class=\"rv-concl concl-pass\">\n            <span class=\"rv-cc-icon\">鉁?/span>\n            <span>{{ approval.note }}</span>\n          </div>\n        </div>\n      </div>\n\n      \n      <!-- 杩愯惀瑙嗗浘锛氭暣椤?ECharts 澶х洏锛堝悓鏂囨。鍐呰仈锛孋SS 浣滅敤鍩?#ops-view-root锛屾寜姣斾緥缂╂斁閫傞厤锛?-->\n      <div v-show=\"activeTab === 'monitor'\" class=\"tab-content rv-wrap\">\n        <div id=\"ops-view-root\">\n          <div class=\"ops-scaler\">\n<!-- ===== 椤堕儴瀵艰埅 ===== -->\r\n<div class=\"topbar\">\r\n  <div class=\"logo\">\r\n    <div class=\"logo-mark\">鐨?/div>\r\n    浜у搧杩愯惀绠＄悊骞冲彴\r\n  </div>\r\n  <div class=\"nav-tabs\"></div>\r\n  <div class=\"topbar-right\">\r\n    <span>2026-09-15</span>\r\n    <div class=\"avatar\">寮?/div>\r\n    <span>绠＄悊鍛?/span>\r\n  </div>\r\n</div>\r\n\r\n<div class=\"layout\">\r\n  <main class=\"main\">\r\n\r\n    <!-- ======================================================\r\n         瑙嗗浘涓€锛氬崟鍝佽繍钀ラ厤缃紙鍒氫笂绾?路 鏃犳暟鎹級\r\n    ======================================================= -->\r\n    <section id=\"view-single\" class=\"view active\">\r\n\r\n      <div class=\"card\" style=\"padding:16px 20px;\">\r\n        <div style=\"display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:12px;\">\r\n          <div>\r\n            <div style=\"font-size:17px;font-weight:700;display:flex;align-items:center;gap:10px;\">\r\n              5G-A濂楅159鍏僜r\n              <span style=\"display:inline-flex;align-items:center;gap:5px;font-size:11.5px;font-weight:600;padding:3px 11px;border-radius:20px;background:var(--green-bg);color:var(--green);\">\r\n                <span style=\"width:6px;height:6px;border-radius:50%;background:currentColor;\"></span>\r\n                宸蹭笂绾?路 鐩戞帶鍒濆鍖栦腑\r\n              </span>\r\n            </div>\r\n            <div style=\"font-size:11.5px;color:var(--text-lt);margin-top:4px;\">\r\n              浜у搧缂栫爜锛?00102308 &nbsp;路&nbsp; 涓婄嚎鏃堕棿锛?026-09-15 10:24 &nbsp;路&nbsp; 褰掑睘锛氱渷鍏徃甯傚満閮╘r\n            </div>\r\n          </div>\r\n          <div style=\"display:flex;gap:8px;\">\r\n            <button class=\"btn btn-sm btn-primary\" onclick=\"switchView('board')\">鏌ョ湅杩愯惀瑙嗗浘</button>\r\n          </div>\r\n        </div>\r\n      </div>\r\n\r\n      <div class=\"card\" style=\"padding:14px 18px;background:#FBFCFE;border:1px solid #D3DEEC;\">\r\n        <div style=\"display:flex;gap:12px;font-size:12.5px;line-height:1.6;color:#00704D;\">\r\n          <div style=\"width:20px;height:20px;border-radius:5px;background:var(--green);color:#fff;display:flex;align-items:center;justify-content:center;font-size:11px;font-weight:700;flex-shrink:0;\">鉁?/div>\r\n          <div>\r\n            <b>閰嶇疆銆佺ń鏍搞€佹牎鍑嗐€佹祴璇曘€佸彈鐞嗛獙璇佸凡鍏ㄩ儴閫氳繃</b>锛孫A 涓婄嚎瀹℃壒宸插畬鎴愩€俓r\n            褰撳墠浜у搧杩涘叆鐩戞帶杩愮淮闃舵锛屽洜鍒氫笂绾挎殏鏃犲疄闄呮暟鎹紝浠ヤ笅涓?<b>閫氱敤缁忚惀鎸囨爣鍙ｅ緞</b> 涓?<b>棰勮鎺ㄩ€佽鍒?/b>锛岀‘璁ゅ悗绯荤粺灏嗚嚜 T+1 鏃ュ嚭璐﹁捣鑷姩閲囬泦骞惰Е鍙戠洃鎺с€俓r\n          </div>\r\n        </div>\r\n      </div>\r\n\r\n      <!-- 鎸囨爣瀛楀吀涓庨璀﹁鍒?-->\r\n      \r\n\r\n      \r\n    </section>\r\n\r\n    <!-- ======================================================\r\n         瑙嗗浘浜岋細椹鹃┒鑸遍鏍艰皟搴﹀ぇ鐩橈紙浠呴€氱敤缁忚惀鎸囨爣锛塡r\n    ======================================================= -->\r\n    <section id=\"view-board\" class=\"view\">\r\n\r\n      <!-- 椤堕儴锛氶潰鍖呭睉 + 鏈堜唤閫夋嫨锛堝弬鑰冮椤靛伐鍏锋爮锛?-->\r\n      <div style=\"display:flex;align-items:center;justify-content:space-between;height:44px;margin-bottom:6px;\">\r\n        <div style=\"font-size:13px;\">\r\n          <span style=\"color:#4b5563;font-weight:500;\">鍏ㄧ渷澶х洏</span>\r\n        </div>\r\n        <div style=\"position:relative;display:inline-flex;align-items:center;\">\r\n          <input type=\"month\" id=\"boardMonthPicker\" value=\"2026-09\" title=\"閫夋嫨鏈堜唤\"\r\n                 style=\"padding:8px 36px 8px 12px;border:1px solid #d1d5db;border-radius:6px;font-size:13px;color:#374151;background:#fff;appearance:none;-webkit-appearance:none;font-family:inherit;\">\r\n          <span style=\"position:absolute;right:10px;color:#9ca3af;pointer-events:none;font-size:13px;\">馃搮</span>\r\n        </div>\r\n      </div>\r\n\r\n      <!-- 绗竴鎺掞細宸?= 鍏ㄧ渷绯诲垪鏀跺叆锛?/3锛夛紝鍙?= 鍋ュ悍搴﹀垎鏋愶紙1/3锛?-->\r\n      <div class=\"main-grid\">\r\n        <!-- 宸︼細绯诲垪鏀跺叆鎬诲崱 -->\r\n        <div class=\"market-revenue-card\">\r\n          <div class=\"section-title\">甯傚満鏀跺叆</div>\r\n          <div class=\"market-revenue-cols\">\r\n            <!-- 绱鏀跺叆 -->\r\n            <div class=\"market-revenue-col\">\r\n              <div style=\"display:flex;align-items:flex-start;gap:12px;\">\r\n                <div class=\"mr-icon mr-icon-blue\">馃枼</div>\r\n                <div>\r\n                  <div style=\"font-size:13px;color:#4b5563;font-weight:500;\">绱鏀跺叆</div>\r\n                  <div class=\"mr-value-line\">\r\n                    <div class=\"mr-value\">1023.21<span class=\"unit\">涓囧厓</span></div>\r\n                    <div style=\"font-size:12.5px;display:flex;align-items:center;gap:6px;white-space:nowrap;\">\r\n                      <span style=\"color:#374151;\">鏈堝悓姣?/span>\r\n                      <span class=\"yoy-down\">-10.1% <i class=\"caret\">鈻?/i></span>\r\n                    </div>\r\n                  </div>\r\n                </div>\r\n              </div>\r\n            </div>\r\n            <!-- 褰撴湀鏀跺叆 -->\r\n            <div class=\"market-revenue-col\">\r\n              <div style=\"display:flex;align-items:flex-start;gap:12px;\">\r\n                <div class=\"mr-icon mr-icon-amber\">馃懁</div>\r\n                <div>\r\n                  <div style=\"font-size:13px;color:#4b5563;font-weight:500;\">褰撴湀鏀跺叆</div>\r\n                  <div class=\"mr-value-line\">\r\n                    <div class=\"mr-value\">980.55<span class=\"unit\">涓囧厓</span></div>\r\n                    <div style=\"font-size:12.5px;display:flex;align-items:center;gap:6px;white-space:nowrap;\">\r\n                      <span style=\"color:#374151;\">鏈堝悓姣?/span>\r\n                      <span class=\"yoy-up\">3.6% <i class=\"caret\">鈻?/i></span>\r\n                    </div>\r\n                  </div>\r\n                </div>\r\n              </div>\r\n            </div>\r\n          </div>\r\n\r\n          <!-- 鍐呭祵涓夌郴鍒楁敹鍏ワ細閫氫俊鏈嶅姟鏀跺叆鈫?G-A绯诲垪 / 绠楀姏鏈嶅姟鏀跺叆鈫掓潈鐩婇殢蹇冮€夌郴鍒?/ 鏅鸿兘鏈嶅姟鏀跺叆鈫掕瀺鍚堝椁愮郴鍒?-->\r\n          <div class=\"series-revenue-inner\">\r\n            <div class=\"series-grid\">\r\n              <!-- 5G-A绯诲垪鏀跺叆 -->\r\n              <div class=\"series-col\">\r\n                <div class=\"series-head\">\r\n                  <div class=\"series-head-icon\" style=\"background:#dbeafe;color:#2563eb;\">馃摱</div>\r\n                  <div class=\"series-head-name\">5G-A绯诲垪鏀跺叆</div>\r\n                </div>\r\n                <div class=\"series-pair\">\r\n                  <div>\r\n                    <div class=\"sp-label\">绱鏀跺叆</div>\r\n                    <div class=\"sp-value\">613.45<span class=\"unit\">涓?/span></div>\r\n                    <div class=\"sp-yoy\"><span class=\"k\">鏈堝悓姣?/span><span class=\"yoy-up\">2.7% <i class=\"caret\">鈻?/i></span></div>\r\n                  </div>\r\n                  <div>\r\n                    <div class=\"sp-label\">褰撴湀鏀跺叆</div>\r\n                    <div class=\"sp-value\">420.18<span class=\"unit\">涓?/span></div>\r\n                    <div class=\"sp-yoy\"><span class=\"k\">鏈堝悓姣?/span><span class=\"yoy-down\">-5.2% <i class=\"caret\">鈻?/i></span></div>\r\n                  </div>\r\n                </div>\r\n              </div>\r\n              <!-- 鏉冪泭闅忓績閫夌郴鍒楁敹鍏?-->\r\n              <div class=\"series-col\">\r\n                <div class=\"series-head\">\r\n                  <div class=\"series-head-icon\" style=\"background:#d1fae5;color:#059669;\">馃帥</div>\r\n                  <div class=\"series-head-name\">鏉冪泭闅忓績閫夌郴鍒楁敹鍏?/div>\r\n                </div>\r\n                <div class=\"series-pair\">\r\n                  <div>\r\n                    <div class=\"sp-label\">绱鏀跺叆</div>\r\n                    <div class=\"sp-value\">245.67<span class=\"unit\">涓?/span></div>\r\n                    <div class=\"sp-yoy\"><span class=\"k\">鏈堝悓姣?/span><span class=\"yoy-down\">-6.2% <i class=\"caret\">鈻?/i></span></div>\r\n                  </div>\r\n                  <div>\r\n                    <div class=\"sp-label\">褰撴湀鏀跺叆</div>\r\n                    <div class=\"sp-value\">180.34<span class=\"unit\">涓?/span></div>\r\n                    <div class=\"sp-yoy\"><span class=\"k\">鏈堝悓姣?/span><span class=\"yoy-up\">1.9% <i class=\"caret\">鈻?/i></span></div>\r\n                  </div>\r\n                </div>\r\n              </div>\r\n              <!-- 铻嶅悎濂楅绯诲垪鏀跺叆 -->\r\n              <div class=\"series-col\">\r\n                <div class=\"series-head\">\r\n                  <div class=\"series-head-icon\" style=\"background:#ffedd5;color:#ea580c;\">馃З</div>\r\n                  <div class=\"series-head-name\">铻嶅悎濂楅绯诲垪鏀跺叆</div>\r\n                </div>\r\n                <div class=\"series-pair\">\r\n                  <div>\r\n                    <div class=\"sp-label\">绱鏀跺叆</div>\r\n                    <div class=\"sp-value\">188.22<span class=\"unit\">涓?/span></div>\r\n                    <div class=\"sp-yoy\"><span class=\"k\">鏈堝悓姣?/span><span class=\"yoy-down\">-4.8% <i class=\"caret\">鈻?/i></span></div>\r\n                  </div>\r\n                  <div>\r\n                    <div class=\"sp-label\">褰撴湀鏀跺叆</div>\r\n                    <div class=\"sp-value\">150.91<span class=\"unit\">涓?/span></div>\r\n                    <div class=\"sp-yoy\"><span class=\"k\">鏈堝悓姣?/span><span class=\"yoy-up\">2.3% <i class=\"caret\">鈻?/i></span></div>\r\n                  </div>\r\n                </div>\r\n              </div>\r\n            </div>\r\n          </div>\r\n        </div>\r\n\r\n        <!-- 鍙筹細鍋ュ悍搴﹀垎鏋?-->\r\n        <div class=\"health-analysis-card\">\r\n          <div class=\"section-title\">鍋ュ悍搴﹀垎鏋?/div>\r\n          <div class=\"health-analysis\">\r\n            <div class=\"health-count-list\">\r\n              <div class=\"health-count-row health-count-row--total\">\r\n                <span>濂楅鎬绘暟</span>\r\n                <span class=\"value\">10涓?/span>\r\n              </div>\r\n              <div class=\"health-count-row health-count-row--warn\" data-dropdown=\"dropdown-warn\">\r\n                <span>棰勮濂楅鏁?/span>\r\n                <span><span class=\"value\">4涓?/span><i class=\"trigger\">鈻?/i></span>\r\n                <div id=\"dropdown-warn\" class=\"health-warn-menu\">\r\n                  <div class=\"item\"><span>5G-A濂楅鍗曞搧299鍏?/span><span class=\"sc sc-yellow\">59</span></div>\r\n                  <div class=\"item\"><span>5G-A铻嶅悎濂楅199鍏?/span><span class=\"sc sc-red\">52</span></div>\r\n                  <div class=\"item\"><span>鏉冪泭闅忓績閫?9鍏?/span><span class=\"sc sc-red\">58</span></div>\r\n                  <div class=\"item\"><span>鏉冪泭闅忓績閫?9鍏?/span><span class=\"sc sc-red\">48</span></div>\r\n                </div>\r\n              </div>\r\n              <div class=\"health-count-row health-count-row--warn\" data-dropdown=\"dropdown-excellent\">\r\n                <span>鍋ュ悍濂楅鏁?/span>\r\n                <span><span class=\"value\">3涓?/span><i class=\"trigger\">鈻?/i></span>\r\n                <div id=\"dropdown-excellent\" class=\"health-warn-menu\">\r\n                  <div class=\"item\"><span>5G-A铻嶅悎濂楅199鍏?/span><span class=\"sc sc-green\">91</span></div>\r\n                  <div class=\"item\"><span>5G-A濂楅159鍏?/span><span class=\"sc sc-green\">88</span></div>\r\n                  <div class=\"item\"><span>5G-A铻嶅悎濂楅239鍏?/span><span class=\"sc sc-green\">87</span></div>\r\n                </div>\r\n              </div>\r\n              <div class=\"health-count-row health-count-row--warn\" data-dropdown=\"dropdown-good\">\r\n                <span>浜氬仴搴峰椁愭暟</span>\r\n                <span><span class=\"value\">3涓?/span><i class=\"trigger\">鈻?/i></span>\r\n                <div id=\"dropdown-good\" class=\"health-warn-menu\">\r\n                  <div class=\"item\"><span>5G-A濂楅鍗曞搧199鍏?/span><span class=\"sc sc-yellow\">79</span></div>\r\n                  <div class=\"item\"><span>5G-A铻嶅悎濂楅299鍏?/span><span class=\"sc sc-yellow\">74</span></div>\r\n                  <div class=\"item\"><span>5G-A濂楅鍗曞搧239鍏?/span><span class=\"sc sc-yellow\">65</span></div>\r\n                </div>\r\n              </div>\r\n            </div>\r\n            <div class=\"health-pie-wrap\">\r\n              <div class=\"health-pie-figure\">\r\n                <svg id=\"healthPieSvg\" width=\"220\" height=\"100\" viewBox=\"0 0 220 100\" role=\"img\" aria-label=\"濂楅鍋ュ悍搴﹀垎甯僜">\r\n                  <ellipse cx=\"110\" cy=\"82\" rx=\"78\" ry=\"6\" fill=\"rgba(29, 78, 216, 0.12)\"></ellipse>\r\n                  <ellipse cx=\"110\" cy=\"50\" rx=\"58\" ry=\"16\" stroke=\"#2563eb\" stroke-width=\"3\" fill=\"none\" opacity=\"0.18\"></ellipse>\r\n                  <g id=\"healthPieBottom\" transform=\"translate(0,10) scale(1,0.40) translate(0,64)\"></g>\r\n                  <g id=\"healthPieTop\" transform=\"scale(1,0.40) translate(0,64)\"></g>\r\n                </svg>\r\n              </div>\r\n              <div class=\"health-pie-legend\">\r\n                <div class=\"health-pie-legend-row\">\r\n                  <div class=\"health-pie-legend-left\">\r\n                    <span class=\"health-pie-dot\" style=\"background:#0ea5ff\"></span>\r\n                    <span class=\"health-pie-legend-label\">鍋ュ悍濂楅锛堚墺80鍒嗭級</span>\r\n                  </div>\r\n                  <span class=\"health-pie-legend-value\" style=\"color:#0ea5ff\">30%</span>\r\n                </div>\r\n                <div class=\"health-pie-legend-row\">\r\n                  <div class=\"health-pie-legend-left\">\r\n                    <span class=\"health-pie-dot\" style=\"background:#ff2a7a\"></span>\r\n                    <span class=\"health-pie-legend-label\">浜氬仴搴峰椁愶紙60-80鍒嗭級</span>\r\n                  </div>\r\n                  <span class=\"health-pie-legend-value\" style=\"color:#ff2a7a\">40%</span>\r\n                </div>\r\n                <div class=\"health-pie-legend-row\" style=\"padding-bottom:0;\">\r\n                  <div class=\"health-pie-legend-left\">\r\n                    <span class=\"health-pie-dot\" style=\"background:#ff7a00\"></span>\r\n                    <span class=\"health-pie-legend-label\">棰勮濂楅锛?lt;60鍒嗭級</span>\r\n                  </div>\r\n                  <span class=\"health-pie-legend-value\" style=\"color:#ff7a00\">30%</span>\r\n                </div>\r\n              </div>\r\n            </div>\r\n          </div>\r\n        </div>\r\n      </div>\r\n\r\n      <!-- 绗簩鎺掞細鍗曞椁愭敹鍏ヤ笅閽伙紙tab 鍒囨崲 + 瀵煎嚭 + 鍙屽浘锛?-->\r\n      <div class=\"series-revenue-box\">\r\n        <div class=\"sr-toolbar\">\r\n          <div class=\"sr-tabs\">\r\n            <button type=\"button\" class=\"sr-tab active\" data-tab=\"wga\">5G-A绯诲垪鏀跺叆</button>\r\n            <button type=\"button\" class=\"sr-tab\" data-tab=\"quanyi\">鏉冪泭闅忓績閫夌郴鍒楁敹鍏?/button>\r\n            <button type=\"button\" class=\"sr-tab\" data-tab=\"ronghe\">铻嶅悎濂楅绯诲垪鏀跺叆</button>\r\n          </div>\r\n          <button type=\"button\" class=\"sr-export\" id=\"srExportBtn\">鈬?瀵煎嚭</button>\r\n        </div>\r\n        <div class=\"sr-content\" id=\"srContent\"></div>\r\n      </div>\r\n\r\n      <!-- 绗笁鎺掞細濂楅涓嬮捇鍒楄〃 -->\r\n      <div class=\"card\" id=\"board-list\">\r\n        <div class=\"card-head\">\r\n          <div class=\"card-title\">涓嬮捇鑷冲叏閲忓椁?/div>\r\n          <div class=\"card-sub\">鐐瑰嚮鍗曚釜濂楅杩涘叆鍋ュ悍搴﹁繍钀ヨ鍥?/div>\r\n        </div>\r\n        <div class=\"prod-grid\">\r\n          <div class=\"prod-card\" onclick=\"openHealth('5G-A濂楅159鍏?,'900102308','鍗曞搧')\">\r\n            <div class=\"prod-card-top\">\r\n              <div><div class=\"prod-name\">5G-A濂楅159鍏?/div><div class=\"prod-code\">900102308</div></div>\r\n              <div class=\"health-score hs-green\">88</div>\r\n            </div>\r\n            <span class=\"prod-type pt-single\">鍗曞搧</span>\r\n            <div class=\"prod-metrics\">\r\n              <div>鍒拌揪鐢ㄦ埛<b>12.5 涓?/b></div>\r\n              <div>鏂板<b>1.82 涓?/b></div>\r\n              <div>鐜瘮<b style=\"color:var(--red);\">+16.4%</b></div>\r\n            </div>\r\n          </div>\r\n          <div class=\"prod-card\" onclick=\"openHealth('5G-A濂楅鍗曞搧199鍏?,'900113045','鍗曞搧')\">\r\n            <div class=\"prod-card-top\">\r\n              <div><div class=\"prod-name\">5G-A濂楅鍗曞搧199鍏?/div><div class=\"prod-code\">900113045</div></div>\r\n              <div class=\"health-score hs-green\">85</div>\r\n            </div>\r\n            <span class=\"prod-type pt-single\">鍗曞搧</span>\r\n            <div class=\"prod-metrics\">\r\n              <div>鍒拌揪鐢ㄦ埛<b>8.3 涓?/b></div>\r\n              <div>鏂板<b>1.14 涓?/b></div>\r\n              <div>鐜瘮<b style=\"color:var(--red);\">+12.6%</b></div>\r\n            </div>\r\n          </div>\r\n          <div class=\"prod-card\" onclick=\"openHealth('5G-A濂楅鍗曞搧239鍏?,'900113043','鍗曞搧')\">\r\n            <div class=\"prod-card-top\">\r\n              <div><div class=\"prod-name\">5G-A濂楅鍗曞搧239鍏?/div><div class=\"prod-code\">900113043</div></div>\r\n              <div class=\"health-score hs-green\">82</div>\r\n            </div>\r\n            <span class=\"prod-type pt-single\">鍗曞搧</span>\r\n            <div class=\"prod-metrics\">\r\n              <div>鍒拌揪鐢ㄦ埛<b>6.7 涓?/b></div>\r\n              <div>鏂板<b>0.86 涓?/b></div>\r\n              <div>鐜瘮<b style=\"color:var(--red);\">+8.2%</b></div>\r\n            </div>\r\n          </div>\r\n          <div class=\"prod-card\" onclick=\"openHealth('5G-A濂楅鍗曞搧299鍏?,'900102310','鍗曞搧')\">\r\n            <div class=\"prod-card-top\">\r\n              <div><div class=\"prod-name\">5G-A濂楅鍗曞搧299鍏?/div><div class=\"prod-code\">900102310</div></div>\r\n              <div class=\"health-score hs-yellow\">79</div>\r\n            </div>\r\n            <span class=\"prod-type pt-single\">鍗曞搧</span>\r\n            <div class=\"prod-metrics\">\r\n              <div>鍒拌揪鐢ㄦ埛<b>4.2 涓?/b></div>\r\n              <div>鏂板<b>0.42 涓?/b></div>\r\n              <div>鐜瘮<b style=\"color:var(--green);\">-3.6%</b></div>\r\n            </div>\r\n          </div>\r\n          <div class=\"prod-card\" onclick=\"openHealth('5G-A铻嶅悎濂楅199鍏?,'900113046','铻嶅悎')\">\r\n            <div class=\"prod-card-top\">\r\n              <div><div class=\"prod-name\">5G-A铻嶅悎濂楅199鍏?/div><div class=\"prod-code\">900113046</div></div>\r\n              <div class=\"health-score hs-green\">91</div>\r\n            </div>\r\n            <span class=\"prod-type pt-fusion\">铻嶅悎</span>\r\n            <div class=\"prod-metrics\">\r\n              <div>鍒拌揪鐢ㄦ埛<b>15.8 涓?/b></div>\r\n              <div>鏂板<b>2.14 涓?/b></div>\r\n              <div>鐜瘮<b style=\"color:var(--red);\">+21.8%</b></div>\r\n            </div>\r\n          </div>\r\n          <div class=\"prod-card\" onclick=\"openHealth('5G-A铻嶅悎濂楅199鍏?,'900102306','铻嶅悎')\">\r\n            <div class=\"prod-card-top\">\r\n              <div><div class=\"prod-name\">5G-A铻嶅悎濂楅199鍏?/div><div class=\"prod-code\">900102306</div></div>\r\n              <div class=\"health-score hs-yellow\">74</div>\r\n            </div>\r\n            <span class=\"prod-type pt-fusion\">铻嶅悎</span>\r\n            <div class=\"prod-metrics\">\r\n              <div>鍒拌揪鐢ㄦ埛<b>3.6 涓?/b></div>\r\n              <div>鏂板<b>0.32 涓?/b></div>\r\n              <div>鐜瘮<b style=\"color:var(--green);\">-7.4%</b></div>\r\n            </div>\r\n          </div>\r\n          <div class=\"prod-card\" onclick=\"openHealth('5G-A铻嶅悎濂楅239鍏?,'900113044','铻嶅悎')\">\r\n            <div class=\"prod-card-top\">\r\n              <div><div class=\"prod-name\">5G-A铻嶅悎濂楅239鍏?/div><div class=\"prod-code\">900113044</div></div>\r\n              <div class=\"health-score hs-green\">87</div>\r\n            </div>\r\n            <span class=\"prod-type pt-fusion\">铻嶅悎</span>\r\n            <div class=\"prod-metrics\">\r\n              <div>鍒拌揪鐢ㄦ埛<b>9.4 涓?/b></div>\r\n              <div>鏂板<b>1.26 涓?/b></div>\r\n              <div>鐜瘮<b style=\"color:var(--red);\">+14.2%</b></div>\r\n            </div>\r\n          </div>\r\n          <div class=\"prod-card\" onclick=\"openHealth('5G-A铻嶅悎濂楅299鍏?,'900102307','铻嶅悎')\">\r\n            <div class=\"prod-card-top\">\r\n              <div><div class=\"prod-name\">5G-A铻嶅悎濂楅299鍏?/div><div class=\"prod-code\">900102307</div></div>\r\n              <div class=\"health-score hs-green\">83</div>\r\n            </div>\r\n            <span class=\"prod-type pt-fusion\">铻嶅悎</span>\r\n            <div class=\"prod-metrics\">\r\n              <div>鍒拌揪鐢ㄦ埛<b>5.8 涓?/b></div>\r\n              <div>鏂板<b>0.68 涓?/b></div>\r\n              <div>鐜瘮<b style=\"color:var(--red);\">+9.6%</b></div>\r\n            </div>\r\n          </div>\r\n        </div>\r\n      </div>\r\n\r\n      <!-- ===== 鍗曞搧鍋ュ悍搴﹁鎯咃紙鐐瑰嚮鍚庢樉绀猴級 ===== -->\r\n      <div id=\"health-detail\" style=\"display:none;\">\r\n        <div class=\"back-link\" onclick=\"closeHealth()\">鈫?杩斿洖 5G-A 浜у搧鍒楄〃</div>\r\n        <div class=\"card\" style=\"padding:16px 20px;\">\r\n          <div style=\"display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:12px;\">\r\n            <div>\r\n              <div style=\"font-size:17px;font-weight:700;display:flex;align-items:center;gap:10px;\">\r\n                <span id=\"hd-name\">5G-A濂楅159鍏?/span>\r\n                <span id=\"hd-code\" style=\"font-size:12px;font-weight:400;color:var(--text-lt);\">900102308</span>\r\n                <span id=\"hd-type\" class=\"prod-type pt-single\" style=\"margin-bottom:0;\">鍗曞搧</span>\r\n              </div>\r\n            </div>\r\n            <div style=\"display:flex;gap:8px;\">\r\n              <button class=\"btn btn-sm btn-primary\" onclick=\"opsOpenRules()\">杩愯惀瑙勫垯</button>\n              <button class=\"btn btn-sm\">瀵煎嚭鎶ヨ〃</button>\r\n            </div>\r\n          </div>\r\n        </div>\r\n\r\n        <div class=\"health-hero\">\r\n          <div class=\"hero-score hs-green\"><b>88</b><span>鍒?/span></div>\r\n          <div class=\"hero-body\">\r\n            <div class=\"hero-tagline\" style=\"background:var(--green-bg);color:var(--green);\">鍋ュ悍 路 杈冧笂鏈?+4 鍒?/div>\r\n            <div class=\"hero-desc\">5G-A 缃戠粶椹荤暀姣旀寔缁彁鍗囷紝楂樺€肩敤鎴峰崰姣旂ǔ瀹氾紱褰撴湀鏂板 1.82 涓囥€佹柊澧炲崰姣?10.0% 淇濇寔绋冲仴锛岀画绾︾巼 91.5%銆佹暣浣撹繍钀ュ仴搴枫€?/div>\r\n            <div class=\"hero-metrics\">\r\n              <div>鍒拌揪鐢ㄦ埛<b>12.5 涓?/b></div>\r\n              <div>褰撴湀鏂板<b style=\"color:var(--green);\">1.82 涓?/b></div>\r\n              <div>褰撴湀鏀跺叆<b>3,168 涓?/b></div>\r\n            </div>\r\n          </div>\r\n        </div>\r\n\r\n        <div class=\"dim-grid\">\r\n\r\n          <!-- 鈶?鏁堢泭缁村害锛氳禋浜嗗灏戯紵璧氬緱鎬庝箞鏍凤紵 -->\r\n          <div class=\"ops-mod\">\r\n            <div class=\"ops-mod-head\">\r\n              <div class=\"ops-mod-title\"><span class=\"bar\"></span>鏁堢泭缁村害<span class=\"q\">璧氫簡澶氬皯 路 璧氬緱鎬庝箞鏍?/span></div>\r\n              <div style=\"display:flex;align-items:center;gap:12px;\">\r\n                <div class=\"ops-tabs\" id=\"opsEffectTabs\">\r\n                  <button type=\"button\" class=\"active\" data-effect=\"income\">鏀跺叆瓒嬪娍</button>\r\n                  <button type=\"button\" data-effect=\"profit\">鍒╂鼎鐜?/button>\r\n                  <button type=\"button\" data-effect=\"crm\">钀ラ攢璐圭敤鍗犳瘮</button>\r\n                </div>\r\n                <div class=\"ops-mod-score\">40<small> 鍒?/small></div>\r\n              </div>\r\n            </div>\r\n\r\n            <!-- 鏀跺叆瓒嬪娍锛氬弻鏌?褰撴湀+绱) + 鍚?鐜瘮鎶樼嚎 -->\r\n            <div class=\"ops-tabpane active\" id=\"opsEffectIncomePane\">\r\n              <div class=\"ops-sub-toggle\" id=\"opsEffectRateTabs\">\r\n                <button type=\"button\" class=\"active\" data-rate=\"yoy\">鍚屾瘮</button>\r\n                <button type=\"button\" data-rate=\"mom\">鐜瘮</button>\r\n              </div>\r\n              <div class=\"ops-chartbox\" id=\"opsEffectIncome\" style=\"height:225px;\"></div>\r\n              <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:#1d8bff;\"></i>褰撴湀鏀跺叆(涓囧厓)</span><span><i class=\"ops-dot\" style=\"background:#0a4eb0;\"></i>绱鏀跺叆(涓囧厓)</span><span><i class=\"ops-dot\" style=\"background:#F59E0B;\"></i>褰撴湀鏀跺叆鍚屾瘮/鐜瘮(%)</span></div>\r\n            </div>\r\n\r\n            <!-- 鍒╂鼎鐜囷細鎶樼嚎 -->\r\n            <div class=\"ops-tabpane\" id=\"opsEffectProfitPane\">\r\n              <div class=\"ops-chartbox\" id=\"opsEffectProfit\" style=\"height:225px;\"></div>\r\n              <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:var(--blue);\"></i>鍒╂鼎鐜?%)<b style=\"margin-left:6px;color:var(--text);\">14.2%</b></span></div>\r\n            </div>\r\n\r\n            <!-- 钀ラ攢璐圭敤鍗犳瘮锛氭姌绾?-->\r\n            <div class=\"ops-tabpane\" id=\"opsEffectCrmPane\">\r\n              <div class=\"ops-chartbox\" id=\"opsEffectCrm\" style=\"height:225px;\"></div>\r\n              <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:var(--yellow);\"></i>钀ラ攢璐圭敤鍗犳瘮(%)<b style=\"margin-left:6px;color:var(--text);\">16.5%</b></span></div>\r\n            </div>\r\n          </div>\r\n\r\n          <!-- 鈶?甯傚満缁村害锛氭湁澶氬皯浜?路 澧為暱鎬庝箞鏍?路 浜轰粠鍝潵锛?-->\r\n          <div class=\"ops-mod\">\r\n            <div class=\"ops-mod-head\">\r\n              <div class=\"ops-mod-title\"><span class=\"bar\" style=\"background:var(--green);\"></span>甯傚満缁村害<span class=\"q\">鏈夊灏戜汉 路 澧為暱鎬庝箞鏍?路 浜轰粠鍝噷鏉?/span></div>\r\n              <div class=\"ops-mod-score\">22<small> 鍒?/small></div>\r\n            </div>\r\n\r\n            <!-- 宸︼細鐢ㄦ埛 锛?鍙筹細娓犻亾鍒掑垎 -->\r\n            <div class=\"ops-mkt-2col\">\r\n\r\n              <!-- 鐢ㄦ埛鍖哄潡 -->\r\n              <div class=\"ops-mkt-block ops-mkt-users\">\r\n                <div class=\"ops-mkt-block-head\">\r\n                  <div style=\"display:flex;align-items:center;gap:10px;flex-wrap:wrap;\">\r\n                    <span class=\"ops-mkt-block-title\">鐢ㄦ埛</span>\r\n                    <div class=\"ops-tabs\" id=\"opsUserTabs\">\r\n                      <button type=\"button\" class=\"active\" data-utab=\"scale\">瑙勬ā</button>\r\n                      <button type=\"button\" data-utab=\"active\">娲昏穬</button>\r\n                    </div>\r\n                  </div>\r\n                </div>\r\n\r\n                <!-- 瑙勬ā锛氬弻鏌?鍒拌揪鐢ㄦ埛+鏈堝噣澧? + 鍚?鐜瘮鎶樼嚎 -->\r\n                <div class=\"ops-utabpane active\" id=\"opsUserScalePane\">\r\n                  <div class=\"ops-rate-row\">\r\n                    <span class=\"ops-mkt-sub\">鍒拌揪鐢ㄦ埛 路 鏈堝噣澧?/span>\r\n                    <div class=\"ops-sub-toggle\" id=\"opsGrowthRateTabs\">\r\n                      <button type=\"button\" class=\"active\" data-grate=\"yoy\">鍚屾瘮</button>\r\n                      <button type=\"button\" data-grate=\"mom\">鐜瘮</button>\r\n                    </div>\r\n                  </div>\r\n                  <div class=\"ops-chartbox\" id=\"opsMktScale\" style=\"height:195px;\"></div>\r\n                  <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:#1d8bff;\"></i>鍒拌揪鐢ㄦ埛(涓?</span><span><i class=\"ops-dot\" style=\"background:#0a4eb0;\"></i>鏈堝噣澧?涓?</span><span><i class=\"ops-dot\" style=\"background:#F59E0B;\"></i>鍚?鐜瘮(%)</span></div>\r\n                </div>\r\n\r\n                <!-- 娲昏穬锛氭椿璺冪敤鎴风巼 + 浠樿垂娲昏穬鐜?鍙屾姌绾?-->\r\n                <div class=\"ops-utabpane\" id=\"opsUserActivePane\">\r\n                  <div class=\"ops-chartbox\" id=\"opsActiveTrend\" style=\"height:195px;\"></div>\r\n                  <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:var(--green);\"></i>娲昏穬鐢ㄦ埛鐜?%)</span><span><i class=\"ops-dot\" style=\"background:var(--blue);\"></i>浠樿垂娲昏穬鐜?%)</span></div>\r\n                </div>\r\n              </div>\r\n\r\n              <!-- 娓犻亾鍒掑垎鍖哄潡 -->\r\n              <div class=\"ops-mkt-block ops-mkt-channel\">\r\n                <div class=\"ops-mkt-block-head\">\r\n                  <span class=\"ops-mkt-block-title\">娓犻亾鍒掑垎</span>\r\n                  <select class=\"ops-mkt-month\" id=\"opsChannelMonth\">\r\n                    <option value=\"2025-12\">2025骞?2鏈?/option>\r\n                    <option value=\"2026-01\">2026骞?鏈?/option>\r\n                    <option value=\"2026-02\">2026骞?鏈?/option>\r\n                    <option value=\"2026-03\">2026骞?鏈?/option>\r\n                    <option value=\"2026-04\">2026骞?鏈?/option>\r\n                    <option value=\"2026-05\">2026骞?鏈?/option>\r\n                    <option value=\"2026-06\">2026骞?鏈?/option>\r\n                    <option value=\"2026-07\">2026骞?鏈?/option>\r\n                    <option value=\"2026-08\">2026骞?鏈?/option>\r\n                    <option value=\"2026-09\" selected>2026骞?鏈?/option>\r\n                  </select>\r\n                </div>\r\n                <div class=\"ops-chartbox\" id=\"opsChannelPie\" style=\"height:245px;\"></div>\r\n              </div>\r\n\r\n            </div>\r\n          </div>\r\n\r\n          <!-- 鈶?杩愯惀璐ㄩ噺缁村害锛氳繍钀ユ湁娌℃湁闂锛熷摢閲屾湁闂锛?-->\r\n          <div class=\"ops-mod\">\r\n            <div class=\"ops-mod-head\">\r\n              <div class=\"ops-mod-title\"><span class=\"bar\" style=\"background:var(--yellow);\"></span>杩愯惀璐ㄩ噺缁村害<span class=\"q\">杩愯惀鏈夋病鏈夐棶棰?路 鍝噷鏈夐棶棰?/span></div>\r\n              <div style=\"display:flex;align-items:center;gap:12px;\">\r\n                <div class=\"ops-tabs\" id=\"opsQualityTabs\">\r\n                  <button type=\"button\" class=\"active\" data-qt=\"retain\">鐣欏瓨</button>\r\n                  <button type=\"button\" data-qt=\"service\">鏈嶅姟</button>\r\n                </div>\r\n                <div class=\"ops-mod-score\">18<small> 鍒?/small></div>\r\n              </div>\r\n            </div>\r\n\r\n            <!-- 鐣欏瓨锛歍+1娲昏穬鐜?/ T+3 / T+6 閫€璁㈢巼 -->\r\n            <div class=\"ops-tabpane active\" id=\"opsQRetainPane\">\r\n              <div class=\"ops-q-chips\">\r\n                <div class=\"ops-q-chip c-green\"><span class=\"q-ico\">馃搱</span><span>T+1 娲昏穬鐜?/span><div class=\"q-row\"><b>78.4%</b><span class=\"q-trend\">鈻?.2</span></div><em>鏂板娆℃棩浠嶆椿璺?/em></div>\r\n                <div class=\"ops-q-chip c-yellow\"><span class=\"q-ico\">馃搲</span><span>T+3 閫€璁㈢巼</span><div class=\"q-row\"><b>1.40%</b><span class=\"q-trend\">鈻?.06</span></div><em>鏂板 3 鏃ラ€€璁?/em></div>\r\n                <div class=\"ops-q-chip c-blue\"><span class=\"q-ico\">馃搲</span><span>T+6 閫€璁㈢巼</span><div class=\"q-row\"><b>2.30%</b><span class=\"q-trend\">鈻?.06</span></div><em>鏂板 6 鏃ラ€€璁?/em></div>\r\n              </div>\r\n              <div class=\"ops-chartbox\" id=\"opsQRetain\" style=\"height:225px;\"></div>\r\n              <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:var(--green);\"></i>T+1娲昏穬鐜?%)</span><span><i class=\"ops-dot\" style=\"background:var(--yellow);\"></i>T+3閫€璁㈢巼(%)</span><span><i class=\"ops-dot\" style=\"background:var(--blue);\"></i>T+6閫€璁㈢巼(%)</span></div>\r\n            </div>\r\n\r\n            <!-- 鏈嶅姟锛氳祫璐规姇璇夌巼 / 涓氬姟鍔炵悊鎴愬姛鐜?-->\r\n            <div class=\"ops-tabpane\" id=\"opsQServicePane\">\r\n              <div class=\"ops-q-chips\">\r\n                <div class=\"ops-q-chip c-blue\"><span class=\"q-ico\">鉁?/span><span>涓氬姟鍔炵悊鎴愬姛鐜?/span><div class=\"q-row\"><b>99.2%</b><span class=\"q-trend\">鈻?.1</span></div><em>楂樹簬 98% 鐩爣绾?/em></div>\r\n                <div class=\"ops-q-chip c-red\"><span class=\"q-ico\">鈿狅笍</span><span>璧勮垂鎶曡瘔鐜?/span><div class=\"q-row\"><b>0.02%</b><span class=\"q-trend\">鈻?/span></div><em>杩滀綆浜?0.1% 棰勮绾?/em></div>\r\n              </div>\r\n              <div class=\"ops-chartbox\" id=\"opsQService\" style=\"height:225px;\"></div>\r\n              <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:var(--blue);\"></i>涓氬姟鍔炵悊鎴愬姛鐜?%)</span><span><i class=\"ops-dot\" style=\"background:var(--red);\"></i>璧勮垂鎶曡瘔鐜?%)</span></div>\r\n            </div>\r\n          </div>\r\n\r\n          <!-- 鈶?鐢熷懡鍛ㄦ湡缁村害锛氱敤鎴峰鍦ㄥ摢涓樁娈碉紵鏂板璐ㄩ噺鎬庝箞鏍凤紵 -->\r\n          <div class=\"ops-mod\">\r\n            <div class=\"ops-mod-head\">\r\n              <div class=\"ops-mod-title\"><span class=\"bar\" style=\"background:var(--purple);\"></span>鐢熷懡鍛ㄦ湡缁村害<span class=\"q\">鐢ㄦ埛澶勫湪鍝釜闃舵 路 鏂板璐ㄩ噺鎬庝箞鏍?/span></div>\r\n              <div class=\"ops-mod-score\">8<small> 鍒?/small></div>\r\n            </div>\r\n            <!-- 鐢ㄦ埛鐢熷懡绾匡細鍚勯樁娈佃〃鐜?+ 鍗犳瘮 -->\r\n            <div class=\"ops-life\">\r\n              <div class=\"ops-life-node\">\r\n                <span class=\"ops-tag ln-t-new\">鏂板</span>\r\n                <div class=\"ln-ico c-blue\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M16 11a4 4 0 1 0-8 0 4 4 0 0 0 8 0z\"/><path d=\"M2 19a6 6 0 0 1 10-4.2\"/><path d=\"M19 8v6M16 11h6\"/></svg></div>\r\n                <div class=\"ln-name\">鏂板鐢ㄦ埛</div>\r\n                <div class=\"ln-val\">10.0<small>%</small></div>\r\n                <div class=\"ln-pct\"><i style=\"background:var(--blue);\"></i>鏈湀鏂板鍗犳瘮</div>\r\n              </div>\r\n              <div class=\"ops-life-node\">\r\n                <span class=\"ops-tag ln-t-act\">婵€娲?/span>\r\n                <div class=\"ln-ico c-green\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M13 2 3 14h7l-1 8 10-12h-7l1-8z\"/></svg></div>\r\n                <div class=\"ln-name\">娲昏穬鐢ㄦ埛</div>\r\n                <div class=\"ln-val\">86.4<small>%</small></div>\r\n                <div class=\"ln-pct\"><i style=\"background:var(--green);\"></i>鏈堟椿璺冪巼</div>\r\n              </div>\r\n              <div class=\"ops-life-node\">\r\n                <span class=\"ops-tag ln-t-ret\">鐣欏瓨</span>\r\n                <div class=\"ln-ico c-purple\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M12 3 4 6v5c0 5 3.4 8.5 8 10 4.6-1.5 8-5 8-10V6l-8-3z\"/><path d=\"m9 12 2 2 4-4\"/></svg></div>\r\n                <div class=\"ln-name\">鐣欏瓨鐢ㄦ埛</div>\r\n                <div class=\"ln-val\">97.7<small>%</small></div>\r\n                <div class=\"ln-pct\"><i style=\"background:var(--purple);\"></i>T+6 鐣欏瓨</div>\r\n              </div>\r\n              <div class=\"ops-life-node\">\r\n                <span class=\"ops-tag ln-t-sil\">娌夐粯</span>\r\n                <div class=\"ln-ico c-amber\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M21 13.5A9 9 0 1 1 10.5 3a7 7 0 0 0 10.5 10.5z\"/></svg></div>\r\n                <div class=\"ln-name\">娌夐粯鐢ㄦ埛</div>\r\n                <div class=\"ln-val\">8.5<small>%</small></div>\r\n                <div class=\"ln-pct\"><i style=\"background:#D48806;\"></i>娌夐粯鍗犳瘮</div>\r\n              </div>\r\n              <div class=\"ops-life-node\">\r\n                <span class=\"ops-tag ln-t-churn\">娴佸け 路 缁害</span>\r\n                <div class=\"ln-ico c-red\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M21 12a9 9 0 1 1-2.9-6.6\"/><path d=\"M21 3v5h-5\"/></svg></div>\r\n                <div class=\"ln-name\">缁害鐜?/div>\r\n                <div class=\"ln-val\">91.5<small>%</small></div>\r\n                <div class=\"ln-pct\"><i style=\"background:var(--red);\"></i>鍒版湡缁害鐜?/div>\r\n              </div>\r\n            </div>\r\n\r\n            <!-- 闃舵鍗犳瘮娴侀噺鍥?-->\r\n            <div class=\"ops-flow\">\r\n              <div class=\"ops-flow-head\">\r\n                <div class=\"ops-flow-title\"><i></i>瀛橀噺缁撴瀯 路 闃舵鍗犳瘮</div>\r\n              </div>\r\n              <div class=\"ops-flow-track\">\r\n                <div class=\"ops-flow-seg\" style=\"width:86.4%;background:linear-gradient(90deg,#00A86C,#00B578);\">娲昏穬鐢ㄦ埛<small>86.4%</small></div>\r\n                <div class=\"ops-flow-seg\" style=\"width:8.5%;background:linear-gradient(90deg,#C07A06,#D48806);\">娌夐粯<small>8.5%</small></div>\r\n                <div class=\"ops-flow-seg\" style=\"width:5.1%;background:linear-gradient(90deg,#D91B27,#F5222D);\">娴佸け<small>5.1%</small></div>\r\n              </div>\r\n            </div>\r\n          </div>\r\n\r\n        </div>\r\n\r\n        <div class=\"card\">\r\n          <div class=\"card-head\"><div class=\"card-title\" style=\"font-size:13px;\"><span style=\"width:3px;height:14px;background:var(--red);border-radius:2px;display:inline-block;\"></span>寮傚姩棰勮</div></div>\r\n          <div class=\"alert alert-green\" style=\"margin-bottom:0;\"><div class=\"alert-icon\">鉁?/div><div class=\"alert-text\"><b>瀛橀噺鍩烘湰鐩樼ǔ瀹氾細</b>鎶曡瘔鐜?0.02%銆乀+6 閫€璁㈢巼 2.30%銆佷笟鍔″姙鐞嗘垚鍔熺巼 99.2%銆佺画绾︾巼 91.5% 鍧囧湪姝ｅ父鑼冨洿</div></div>\r\n        </div>\r\n      </div>\r\n        <!-- ===== 5G-A濂楅鍗曞搧299鍏?鍋ュ悍搴﹁鎯咃紙浜氬仴搴风敾鍍忥級 ===== -->\r\n        <div id=\"health-detail-299\" style=\"display:none;\">\r\n          <div class=\"back-link\" onclick=\"closeHealth()\">鈫?杩斿洖 5G-A 浜у搧鍒楄〃</div>\r\n          <div class=\"card\" style=\"padding:16px 20px;\">\r\n            <div style=\"display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:12px;\">\r\n              <div>\r\n                <div style=\"font-size:17px;font-weight:700;display:flex;align-items:center;gap:10px;\">\r\n                  <span id=\"hd-name-299\">5G-A濂楅鍗曞搧299鍏?/span>\r\n                  <span id=\"hd-code-299\" style=\"font-size:12px;font-weight:400;color:var(--text-lt);\">900102310</span>\r\n                  <span id=\"hd-type-299\" class=\"prod-type pt-single\" style=\"margin-bottom:0;\">鍗曞搧</span>\r\n                </div>\r\n              </div>\r\n              <div style=\"display:flex;gap:8px;\">\r\n                <button class=\"btn btn-sm\">瀵煎嚭鎶ヨ〃</button>\r\n              </div>\r\n            </div>\r\n          </div>\r\n\r\n          <div class=\"health-hero\">\r\n            <div class=\"hero-score hs-yellow\"><b>79</b><span>鍒?/span></div>\r\n            <div class=\"hero-body\">\r\n              <div class=\"hero-tagline\" style=\"background:var(--yellow-bg);color:#D48806;\">浜氬仴搴?路 杈冧笂鏈?-3 鍒?/div>\r\n              <div class=\"hero-desc\">楂樼妗ｄ綅鐢ㄦ埛澧為暱鏀剧紦锛屾敹鍏ョ幆姣旇繛缁袱鏈堜笅婊戯紙-2.8%锛夛紝鍒╂鼎鐜囬檷鑷?11.6% 鑰岃惀閿€璐圭敤鍗犳瘮鍗囪嚦 18.9%锛岀画绾︾巼 88.6% 鍋忎綆銆佹矇榛樼敤鎴峰崰姣?14.2% 鍋忛珮锛岄渶閲嶇偣缁寸郴楂樺€煎瓨閲忕敤鎴峰苟鍔犲ぇ鏉冪泭鐒曟椿銆?/div>\r\n              <div class=\"hero-metrics\">\r\n                <div>鍒拌揪鐢ㄦ埛<b>4.2 涓?/b></div>\r\n                <div>褰撴湀鏂板<b style=\"color:var(--green);\">0.42 涓?/b></div>\r\n                <div>褰撴湀鏀跺叆<b>1,246 涓?/b></div>\r\n              </div>\r\n            </div>\r\n          </div>\r\n\r\n          <div class=\"dim-grid\">\r\n\r\n            <!-- 鈶?鏁堢泭缁村害 -->\r\n            <div class=\"ops-mod\">\r\n              <div class=\"ops-mod-head\">\r\n                <div class=\"ops-mod-title\"><span class=\"bar\"></span>鏁堢泭缁村害<span class=\"q\">璧氫簡澶氬皯 路 璧氬緱鎬庝箞鏍?/span></div>\r\n                <div style=\"display:flex;align-items:center;gap:12px;\">\r\n                  <div class=\"ops-tabs\" id=\"opsEffectTabs299\">\r\n                    <button type=\"button\" class=\"active\" data-effect=\"income\">鏀跺叆瓒嬪娍</button>\r\n                    <button type=\"button\" data-effect=\"profit\">鍒╂鼎鐜?/button>\r\n                    <button type=\"button\" data-effect=\"crm\">钀ラ攢璐圭敤鍗犳瘮</button>\r\n                  </div>\r\n                  <div class=\"ops-mod-score\">33<small> 鍒?/small></div>\r\n                </div>\r\n              </div>\r\n\r\n              <div class=\"ops-tabpane active\" id=\"opsEffectIncomePane299\">\r\n                <div class=\"ops-sub-toggle\" id=\"opsEffectRateTabs299\">\r\n                  <button type=\"button\" class=\"active\" data-rate=\"yoy\">鍚屾瘮</button>\r\n                  <button type=\"button\" data-rate=\"mom\">鐜瘮</button>\r\n                </div>\r\n                <div class=\"ops-chartbox\" id=\"opsEffectIncome299\" style=\"height:225px;\"></div>\r\n                <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:#1d8bff;\"></i>褰撴湀鏀跺叆(涓囧厓)</span><span><i class=\"ops-dot\" style=\"background:#0a4eb0;\"></i>绱鏀跺叆(涓囧厓)</span><span><i class=\"ops-dot\" style=\"background:#F59E0B;\"></i>褰撴湀鏀跺叆鍚屾瘮/鐜瘮(%)</span></div>\r\n              </div>\r\n\r\n              <div class=\"ops-tabpane\" id=\"opsEffectProfitPane299\">\r\n                <div class=\"ops-chartbox\" id=\"opsEffectProfit299\" style=\"height:225px;\"></div>\r\n                <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:var(--blue);\"></i>鍒╂鼎鐜?%)<b style=\"margin-left:6px;color:var(--text);\">11.6%</b></span></div>\r\n              </div>\r\n\r\n              <div class=\"ops-tabpane\" id=\"opsEffectCrmPane299\">\r\n                <div class=\"ops-chartbox\" id=\"opsEffectCrm299\" style=\"height:225px;\"></div>\r\n                <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:var(--yellow);\"></i>钀ラ攢璐圭敤鍗犳瘮(%)<b style=\"margin-left:6px;color:var(--text);\">18.9%</b></span></div>\r\n              </div>\r\n            </div>\r\n\r\n            <!-- 鈶?甯傚満缁村害 -->\r\n            <div class=\"ops-mod\">\r\n              <div class=\"ops-mod-head\">\r\n                <div class=\"ops-mod-title\"><span class=\"bar\" style=\"background:var(--green);\"></span>甯傚満缁村害<span class=\"q\">鏈夊灏戜汉 路 澧為暱鎬庝箞鏍?路 浜轰粠鍝噷鏉?/span></div>\r\n                <div class=\"ops-mod-score\">19<small> 鍒?/small></div>\r\n              </div>\r\n\r\n              <div class=\"ops-mkt-2col\">\r\n                <div class=\"ops-mkt-block ops-mkt-users\">\r\n                  <div class=\"ops-mkt-block-head\">\r\n                    <div style=\"display:flex;align-items:center;gap:10px;flex-wrap:wrap;\">\r\n                      <span class=\"ops-mkt-block-title\">鐢ㄦ埛</span>\r\n                      <div class=\"ops-tabs\" id=\"opsUserTabs299\">\r\n                        <button type=\"button\" class=\"active\" data-utab=\"scale\">瑙勬ā</button>\r\n                        <button type=\"button\" data-utab=\"active\">娲昏穬</button>\r\n                      </div>\r\n                    </div>\r\n                  </div>\r\n\r\n                  <div class=\"ops-utabpane active\" id=\"opsUserScalePane299\">\r\n                    <div class=\"ops-rate-row\">\r\n                      <span class=\"ops-mkt-sub\">鍒拌揪鐢ㄦ埛 路 鏈堝噣澧?/span>\r\n                      <div class=\"ops-sub-toggle\" id=\"opsGrowthRateTabs299\">\r\n                        <button type=\"button\" class=\"active\" data-grate=\"yoy\">鍚屾瘮</button>\r\n                        <button type=\"button\" data-grate=\"mom\">鐜瘮</button>\r\n                      </div>\r\n                    </div>\r\n                    <div class=\"ops-chartbox\" id=\"opsMktScale299\" style=\"height:195px;\"></div>\r\n                    <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:#1d8bff;\"></i>鍒拌揪鐢ㄦ埛(涓?</span><span><i class=\"ops-dot\" style=\"background:#0a4eb0;\"></i>鏈堝噣澧?涓?</span><span><i class=\"ops-dot\" style=\"background:#F59E0B;\"></i>鍚?鐜瘮(%)</span></div>\r\n                  </div>\r\n\r\n                  <div class=\"ops-utabpane\" id=\"opsUserActivePane299\">\r\n                    <div class=\"ops-chartbox\" id=\"opsActiveTrend299\" style=\"height:195px;\"></div>\r\n                    <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:var(--green);\"></i>娲昏穬鐢ㄦ埛鐜?%)</span><span><i class=\"ops-dot\" style=\"background:var(--blue);\"></i>浠樿垂娲昏穬鐜?%)</span></div>\r\n                  </div>\r\n                </div>\r\n\r\n                <div class=\"ops-mkt-block ops-mkt-channel\">\r\n                  <div class=\"ops-mkt-block-head\">\r\n                    <span class=\"ops-mkt-block-title\">娓犻亾鍒掑垎</span>\r\n                    <select class=\"ops-mkt-month\" id=\"opsChannelMonth299\">\r\n                      <option value=\"2025-12\">2025骞?2鏈?/option>\r\n                      <option value=\"2026-01\">2026骞?鏈?/option>\r\n                      <option value=\"2026-02\">2026骞?鏈?/option>\r\n                      <option value=\"2026-03\">2026骞?鏈?/option>\r\n                      <option value=\"2026-04\">2026骞?鏈?/option>\r\n                      <option value=\"2026-05\">2026骞?鏈?/option>\r\n                      <option value=\"2026-06\">2026骞?鏈?/option>\r\n                      <option value=\"2026-07\">2026骞?鏈?/option>\r\n                      <option value=\"2026-08\">2026骞?鏈?/option>\r\n                      <option value=\"2026-09\" selected>2026骞?鏈?/option>\r\n                    </select>\r\n                  </div>\r\n                  <div class=\"ops-chartbox\" id=\"opsChannelPie299\" style=\"height:245px;\"></div>\r\n                </div>\r\n              </div>\r\n            </div>\r\n\r\n            <!-- 鈶?杩愯惀璐ㄩ噺缁村害 -->\r\n            <div class=\"ops-mod\">\r\n              <div class=\"ops-mod-head\">\r\n                <div class=\"ops-mod-title\"><span class=\"bar\" style=\"background:var(--yellow);\"></span>杩愯惀璐ㄩ噺缁村害<span class=\"q\">杩愯惀鏈夋病鏈夐棶棰?路 鍝噷鏈夐棶棰?/span></div>\r\n                <div style=\"display:flex;align-items:center;gap:12px;\">\r\n                  <div class=\"ops-tabs\" id=\"opsQualityTabs299\">\r\n                    <button type=\"button\" class=\"active\" data-qt=\"retain\">鐣欏瓨</button>\r\n                    <button type=\"button\" data-qt=\"service\">鏈嶅姟</button>\r\n                  </div>\r\n                  <div class=\"ops-mod-score\">17<small> 鍒?/small></div>\r\n                </div>\r\n              </div>\r\n\r\n              <div class=\"ops-tabpane active\" id=\"opsQRetainPane299\">\r\n                <div class=\"ops-q-chips\">\r\n                  <div class=\"ops-q-chip c-green\"><span class=\"q-ico\">馃搱</span><span>T+1 娲昏穬鐜?/span><div class=\"q-row\"><b>76.1%</b><span class=\"q-trend\">鈻?.3</span></div><em>鏂板娆℃棩浠嶆椿璺?/em></div>\r\n                  <div class=\"ops-q-chip c-red\"><span class=\"q-ico\">鈿狅笍</span><span>T+3 閫€璁㈢巼</span><div class=\"q-row\"><b>2.10%</b><span class=\"q-trend\">鈻?.03</span></div><em>鏂板 3 鏃ラ€€璁?/em></div>\r\n                  <div class=\"ops-q-chip c-yellow\"><span class=\"q-ico\">鈿狅笍</span><span>T+6 閫€璁㈢巼</span><div class=\"q-row\"><b>3.20%</b><span class=\"q-trend\">鈻?.02</span></div><em>鏂板 6 鏃ラ€€璁?/em></div>\r\n                </div>\r\n                <div class=\"ops-chartbox\" id=\"opsQRetain299\" style=\"height:225px;\"></div>\r\n                <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:var(--green);\"></i>T+1娲昏穬鐜?%)</span><span><i class=\"ops-dot\" style=\"background:var(--yellow);\"></i>T+3閫€璁㈢巼(%)</span><span><i class=\"ops-dot\" style=\"background:var(--blue);\"></i>T+6閫€璁㈢巼(%)</span></div>\r\n              </div>\r\n\r\n              <div class=\"ops-tabpane\" id=\"opsQServicePane299\">\r\n                <div class=\"ops-q-chips\">\r\n                  <div class=\"ops-q-chip c-yellow\"><span class=\"q-ico\">鈿狅笍</span><span>涓氬姟鍔炵悊鎴愬姛鐜?/span><div class=\"q-row\"><b>98.4%</b><span class=\"q-trend\">鈻?.1</span></div><em>鐣ヤ綆浜?99% 鐩爣</em></div>\r\n                  <div class=\"ops-q-chip c-red\"><span class=\"q-ico\">鈿狅笍</span><span>璧勮垂鎶曡瘔鐜?/span><div class=\"q-row\"><b>0.04%</b><span class=\"q-trend\">鈻?/span></div><em>鎺ヨ繎 0.05% 棰勮</em></div>\r\n                </div>\r\n                <div class=\"ops-chartbox\" id=\"opsQService299\" style=\"height:225px;\"></div>\r\n                <div class=\"ops-legend\"><span><i class=\"ops-dot\" style=\"background:var(--blue);\"></i>涓氬姟鍔炵悊鎴愬姛鐜?%)</span><span><i class=\"ops-dot\" style=\"background:var(--red);\"></i>璧勮垂鎶曡瘔鐜?%)</span></div>\r\n              </div>\r\n            </div>\r\n\r\n            <!-- 鈶?鐢熷懡鍛ㄦ湡缁村害 -->\r\n            <div class=\"ops-mod\">\r\n              <div class=\"ops-mod-head\">\r\n                <div class=\"ops-mod-title\"><span class=\"bar\" style=\"background:var(--purple);\"></span>鐢熷懡鍛ㄦ湡缁村害<span class=\"q\">鐢ㄦ埛澶勫湪鍝釜闃舵 路 鏂板璐ㄩ噺鎬庝箞鏍?/span></div>\r\n                <div class=\"ops-mod-score\">6<small> 鍒?/small></div>\r\n              </div>\r\n              <div class=\"ops-life\">\r\n                <div class=\"ops-life-node\">\r\n                  <span class=\"ops-tag ln-t-new\">鏂板</span>\r\n                  <div class=\"ln-ico c-blue\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M16 11a4 4 0 1 0-8 0 4 4 0 0 0 8 0z\"/><path d=\"M2 19a6 6 0 0 1 10-4.2\"/><path d=\"M19 8v6M16 11h6\"/></svg></div>\r\n                  <div class=\"ln-name\">鏂板鐢ㄦ埛</div>\r\n                  <div class=\"ln-val\">10.5<small>%</small></div>\r\n                  <div class=\"ln-pct\"><i style=\"background:var(--blue);\"></i>鏈湀鏂板鍗犳瘮</div>\r\n                </div>\r\n                <div class=\"ops-life-node\">\r\n                  <span class=\"ops-tag ln-t-act\">婵€娲?/span>\r\n                  <div class=\"ln-ico c-green\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M13 2 3 14h7l-1 8 10-12h-7l1-8z\"/></svg></div>\r\n                  <div class=\"ln-name\">娲昏穬鐢ㄦ埛</div>\r\n                  <div class=\"ln-val\">80.5<small>%</small></div>\r\n                  <div class=\"ln-pct\"><i style=\"background:var(--green);\"></i>鏈堟椿璺冪巼</div>\r\n                </div>\r\n                <div class=\"ops-life-node\">\r\n                  <span class=\"ops-tag ln-t-ret\">鐣欏瓨</span>\r\n                  <div class=\"ln-ico c-purple\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M12 3 4 6v5c0 5 3.4 8.5 8 10 4.6-1.5 8-5 8-10V6l-8-3z\"/><path d=\"m9 12 2 2 4-4\"/></svg></div>\r\n                  <div class=\"ln-name\">鐣欏瓨鐢ㄦ埛</div>\r\n                  <div class=\"ln-val\">96.8<small>%</small></div>\r\n                  <div class=\"ln-pct\"><i style=\"background:var(--purple);\"></i>T+6 鐣欏瓨</div>\r\n                </div>\r\n                <div class=\"ops-life-node\">\r\n                  <span class=\"ops-tag ln-t-sil\">娌夐粯</span>\r\n                  <div class=\"ln-ico c-amber\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M21 13.5A9 9 0 1 1 10.5 3a7 7 0 0 0 10.5 10.5z\"/></svg></div>\r\n                  <div class=\"ln-name\">娌夐粯鐢ㄦ埛</div>\r\n                  <div class=\"ln-val\">14.2<small>%</small></div>\r\n                  <div class=\"ln-pct\"><i style=\"background:#D48806;\"></i>娌夐粯鍗犳瘮</div>\r\n                </div>\r\n                <div class=\"ops-life-node\">\r\n                  <span class=\"ops-tag ln-t-churn\">娴佸け 路 缁害</span>\r\n                  <div class=\"ln-ico c-red\"><svg viewBox=\"0 0 24 24\" fill=\"none\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"M21 12a9 9 0 1 1-2.9-6.6\"/><path d=\"M21 3v5h-5\"/></svg></div>\r\n                  <div class=\"ln-name\">缁害鐜?/div>\r\n                  <div class=\"ln-val\">88.6<small>%</small></div>\r\n                  <div class=\"ln-pct\"><i style=\"background:var(--red);\"></i>鍒版湡缁害鐜?/div>\r\n                </div>\r\n              </div>\r\n\r\n              <div class=\"ops-flow\">\r\n                <div class=\"ops-flow-head\">\r\n                  <div class=\"ops-flow-title\"><i></i>瀛橀噺缁撴瀯 路 闃舵鍗犳瘮</div>\r\n                </div>\r\n                <div class=\"ops-flow-track\">\r\n                  <div class=\"ops-flow-seg\" style=\"width:80.5%;background:linear-gradient(90deg,#00A86C,#00B578);\">娲昏穬鐢ㄦ埛<small>80.5%</small></div>\r\n                  <div class=\"ops-flow-seg\" style=\"width:14.2%;background:linear-gradient(90deg,#C07A06,#D48806);\">娌夐粯<small>14.2%</small></div>\r\n                  <div class=\"ops-flow-seg\" style=\"width:5.3%;background:linear-gradient(90deg,#D91B27,#F5222D);\">娴佸け<small>5.3%</small></div>\r\n                </div>\r\n              </div>\r\n            </div>\r\n\r\n          </div>\r\n\r\n          <div class=\"card\">\r\n            <div class=\"card-head\"><div class=\"card-title\" style=\"font-size:13px;\"><span style=\"width:3px;height:14px;background:var(--red);border-radius:2px;display:inline-block;\"></span>寮傚姩棰勮</div></div>\r\n            <div class=\"alert alert-yellow\" style=\"margin-bottom:0;\"><div class=\"alert-icon\">!</div><div class=\"alert-text\"><b>鏀跺叆杩炵画涓ゆ湀鐜瘮涓嬫粦锛?2.8%锛?/b>锛岀画绾︾巼 88.6% 涓?T+6 閫€璁㈢巼 3.20% 鍧囧亸绂诲仴搴风嚎銆佹矇榛樼敤鎴峰崰姣?14.2% 鍋忛珮锛屽缓璁拡瀵瑰埌鏈熺敤鎴峰紑灞曟潈鐩婂姞鐮佺淮绯诲苟鍔犲ぇ娌夐粯鐢ㄦ埛鍞ら啋</div></div>\r\n          </div>\r\n        </div>\r\n\r\n\r\n    \n        <!-- 杩愯惀瑙勫垯锛氶€氱敤缁忚惀鎸囨爣路棰勮瑙勫垯路鎺ㄩ€佹柟寮忥紙浠庡崟浜у搧瑙嗗浘绉诲叆锛?-->\n        <div id=\"ops-rules-panel\" style=\"display:none;\">\n          <div class=\"back-link\" onclick=\"opsCloseRules()\">鈫?杩斿洖鍗曚骇鍝佺敾鍍?/div>\n          <div class=\"ops-rules-bar\"><span class=\"ops-rules-title\">杩愯惀瑙勫垯 路 閫氱敤缁忚惀鎸囨爣 路 棰勮瑙勫垯 路 鎺ㄩ€佹柟寮?/span></div>\n<div class=\"card\">\r\n        <div class=\"card-head\">\r\n          <div class=\"card-title\">閫氱敤缁忚惀鎸囨爣 路 棰勮瑙勫垯 路 鎺ㄩ€佹柟寮?/div>\r\n        </div>\r\n\r\n        <div style=\"display:flex;gap:14px;flex-wrap:wrap;align-items:center;font-size:11.5px;color:var(--text-sub);padding:10px 14px;background:#F7F9FC;border-radius:6px;margin-bottom:12px;\">\r\n          <b style=\"color:var(--text);font-size:12px;\">棰勮鏈哄埗</b>\r\n          <span class=\"risk-pill risk-high\"><span class=\"lv\">闃?/span><span class=\"th\">杈惧埌闃堝€煎嵆瑙﹀彂</span></span>\r\n          <span style=\"display:flex;align-items:center;gap:5px;\"><i style=\"width:9px;height:9px;border-radius:2px;background:var(--purple);display:inline-block;\"></i>寮傚父鎺ㄩ€侊紙鏃ヨ皟搴︼級</span>\r\n        </div>\r\n\r\n        <div class=\"table-responsive\">\r\n          <table class=\"metric-table\">\r\n            <thead>\r\n              <tr>\r\n                <th style=\"width:108px;\">鎸囨爣绫诲瀷</th>\r\n                <th style=\"width:116px;\">鎸囨爣鍚嶇О</th>\r\n                <th style=\"width:320px;\">棰勮瑙勫垯</th>\r\n                <th>鎸囨爣鍙ｅ緞</th>\r\n              </tr>\r\n            </thead>\r\n            <tbody>\r\n              <!-- 鏁堢泭缁村害锛氭敹鍏ヤ笌浠峰€肩被 -->\r\n              <tr>\r\n                <td class=\"m-module\" rowspan=\"5\">鏁堢泭缁村害</td>\r\n                <td class=\"m-name\">褰撴湀鏀跺叆</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">闃?/span><span class=\"th\">鐜瘮鈫?gt;10%</span></span></td>\r\n                <td class=\"m-desc\">璇ュ椁愬綋鏈堝嚭璐︽敹鍏ワ紙鎶ヨ〃鍙ｅ緞锛?/td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">绱鏀跺叆</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">闃?/span><span class=\"th\">鐜瘮鈫?gt;10%</span></span></td>\r\n                <td class=\"m-desc\">璇ュ椁愬綋骞?1 鏈堣嚦缁熻鏈堢疮璁″嚭璐︽敹鍏?/td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">鏀跺叆鍚?鐜瘮</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">闃?/span><span class=\"th\">涓嬫粦&gt;5% 鎴栬繛缁袱鏈堜笅婊?/span></span></td>\r\n                <td class=\"m-desc\">锛堟湰鏈堟敹鍏?鈭?涓婃湀鏀跺叆锛壝?涓婃湀鏀跺叆锛堟垨鍚屾瘮鍙ｅ緞锛?/td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">鍒╂鼎鐜?/td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">闃?/span><span class=\"th\">浣庝簬 12%</span></span></td>\r\n                <td class=\"m-desc\">锛堟敹鍏?鈭?钀ラ攢璐圭敤 鈭?鎴愭湰锛壝?鏀跺叆</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">钀ラ攢璐圭敤鍗犳瘮</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">闃?/span><span class=\"th\">楂樹簬 18%</span></span></td>\r\n                <td class=\"m-desc\">璇ュ椁愯惀閿€璐圭敤 梅 璇ュ椁愭敹鍏?/td>\r\n              </tr>\r\n\r\n              <!-- 甯傚満缁村害锛氳妯°€佹椿璺冧笌娓犻亾 -->\r\n              <tr>\r\n                <td class=\"m-module\" rowspan=\"5\">甯傚満缁村害</td>\r\n                <td class=\"m-name\">鍒拌揪鐢ㄦ埛</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">闃?/span><span class=\"th\">鐜瘮鈫?gt;5%</span></span></td>\r\n                <td class=\"m-desc\">褰撴湀鍑鸿处涓旂姸鎬佹甯哥殑璇ュ椁愪富鍗＄敤鎴锋暟锛堝埌杈剧敤鎴凤級锛屽壇鍗′笉閲嶅璁＄畻</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">鏈堝噣澧炵敤鎴?/td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">闃?/span><span class=\"th\">杩炵画 3 鏃ヤ负璐?/span></span></td>\r\n                <td class=\"m-desc\">褰撴湀鏂板 鈭?褰撴湀閫€璁?/td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">娲昏穬鐢ㄦ埛鐜?/td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">闃?/span><span class=\"th\">浣庝簬 60%</span></span></td>\r\n                <td class=\"m-desc\">褰撴湀鏈変娇鐢ㄨ涓虹殑璇ュ椁愮敤鎴?梅 鍒拌揪鐢ㄦ埛</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">浠樿垂娲昏穬鐜?/td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">闃?/span><span class=\"th\">浣庝簬 5%</span></span></td>\r\n                <td class=\"m-desc\">浠樿垂娲昏穬鐢ㄦ埛锛堝綋鏈堜骇鐢熷疄闄呰璐硅处鍗曪級梅 鍒拌揪鐢ㄦ埛</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">娓犻亾缁撴瀯鍗犳瘮</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">闃?/span><span class=\"th\">鍗曚竴娓犻亾鍗犳瘮&gt;70%</span></span></td>\r\n                <td class=\"m-desc\">绾夸笅钀ヤ笟鍘?/ 涓浗鐢典俊绾夸笂娓犻亾 / 10000鐑嚎 / 瀹夊窘鐢典俊绾夸笂娓犻亾 鍒拌揪鐢ㄦ埛鍗犳瘮</td>\r\n              </tr>\r\n\r\n              <!-- 杩愯惀璐ㄩ噺缁村害锛氳川閲忎笌鐣欏瓨 -->\r\n              <tr>\r\n                <td class=\"m-module\" rowspan=\"5\">杩愯惀璐ㄩ噺缁村害</td>\r\n                <td class=\"m-name\">T+1 娲昏穬鐜?/td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">闃?/span><span class=\"th\">浣庝簬 70%</span></span></td>\r\n                <td class=\"m-desc\">璁㈣喘娆℃棩鍐呬粛娲昏穬鐨勬柊澧炵敤鎴?梅 褰撴湡鏂板鐢ㄦ埛</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">T+3 閫€璁㈢巼</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">闃?/span><span class=\"th\">楂樹簬 3%</span></span></td>\r\n                <td class=\"m-desc\">璁㈣喘鍚?3 澶╁唴閫€璁㈢敤鎴?梅 褰撴湡鏂板鐢ㄦ埛</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">T+6 閫€璁㈢巼</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">闃?/span><span class=\"th\">楂樹簬 10%</span></span></td>\r\n                <td class=\"m-desc\">璁㈣喘鍚?6 澶╁唴閫€璁㈢敤鎴?梅 褰撴湡鏂板鐢ㄦ埛</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">涓氬姟鍔炵悊鎴愬姛鐜?/td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">闃?/span><span class=\"th\">浣庝簬 98%</span></span></td>\r\n                <td class=\"m-desc\">璇ュ椁愬彈鐞嗘垚鍔熺瑪鏁?梅 鍙楃悊鎬荤瑪鏁?/td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">璧勮垂鎶曡瘔鐜?/td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">闃?/span><span class=\"th\">楂樹簬 0.1%</span></span></td>\r\n                <td class=\"m-desc\">娑夊強璇ュ椁愯祫璐圭殑鎶曡瘔宸ュ崟鏁?梅 鍒拌揪鐢ㄦ埛</td>\r\n              </tr>\r\n\r\n              <!-- 鐢熷懡鍛ㄦ湡缁村害锛氭柊澧炪€佺暀瀛樹笌鐢ㄦ埛缁撴瀯 -->\r\n              <tr>\r\n                <td class=\"m-module\" rowspan=\"5\">鐢熷懡鍛ㄦ湡缁村害</td>\r\n                <td class=\"m-name\">鏂扮敤鎴峰惛寮曞崰姣?/td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">闃?/span><span class=\"th\">浣庝簬 8%</span></span></td>\r\n                <td class=\"m-desc\">鏂板叆缃戠敤鎴蜂腑璁㈣喘璇ュ椁愮殑浜烘暟鍗犳瘮</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">娲昏穬鐢ㄦ埛鍗犳瘮</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">闃?/span><span class=\"th\">浣庝簬 60%</span></span></td>\r\n                <td class=\"m-desc\">瀛橀噺鐢ㄦ埛涓綋鏈堜负娲昏穬闃舵鐨勫崰姣旓紙鏈堟椿璺冪巼锛?/td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">T+6 鐣欏瓨鐜?/td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">闃?/span><span class=\"th\">浣庝簬 95%</span></span></td>\r\n                <td class=\"m-desc\">璁㈣喘鍚庣 6 澶╀粛淇濇寔濂楅鐘舵€佺殑鏂板鐢ㄦ埛 梅 褰撴湡鏂板鐢ㄦ埛</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">娌夐粯鐢ㄦ埛鍗犳瘮</td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">闃?/span><span class=\"th\">楂樹簬 10%</span></span></td>\r\n                <td class=\"m-desc\">褰撴湀鏃犱换浣曚娇鐢ㄨ涓虹殑鐢ㄦ埛 梅 鍒拌揪鐢ㄦ埛</td>\r\n              </tr>\r\n              <tr>\r\n                <td class=\"m-name\">缁害鐜?/td>\r\n                <td class=\"m-rule\"><span class=\"risk-pill risk-high\"><span class=\"lv\">闃?/span><span class=\"th\">浣庝簬 90%</span></span></td>\r\n                <td class=\"m-desc\">鍒版湡鐢ㄦ埛涓垚鍔熺画绾﹁濂楅鐨勭敤鎴峰崰姣?/td>\r\n              </tr>\r\n            </tbody>\r\n          </table>\r\n        </div>\r\n      </div>\n        </div>\n      </section>\r\n\r\n  </main>\r\n</div>\n          </div>\n        </div>\n      </div>\n      <div v-show=\"activeTab === 'summary'\" class=\"tab-content phone-preview-tab\">\r\n        <div class=\"phone-frame\">\r\n          <!-- 鎵嬫満鐘舵€佹爮 -->\r\n          <div class=\"phone-statusbar\">\r\n            <span class=\"phone-time\">{{ phoneTime }}</span>\r\n            <div class=\"phone-status-icons\">\r\n              <svg width=\"14\" height=\"10\" viewBox=\"0 0 24 18\" fill=\"currentColor\"><path d=\"M1 14h2v6H1zM6 11h2v9H6zM11 8h2v12h-2zM16 5h2v15h-2zM21 2h2v18h-2z\"/></svg>\r\n              <svg width=\"14\" height=\"10\" viewBox=\"0 0 24 18\" fill=\"currentColor\"><path d=\"M12 3C7 3 2.7 5.1 0 8.5l2 2.5C4.2 8.3 7.9 6.5 12 6.5s7.8 1.8 10 4.5l2-2.5C21.3 5.1 17 3 12 3z\"/></svg>\r\n              <div class=\"phone-battery\">\r\n                <div class=\"phone-battery-bar\"></div>\r\n                <div class=\"phone-battery-cap\"></div>\r\n              </div>\r\n            </div>\r\n          </div>\r\n\r\n          <!-- APP鏍囬锟?-->\r\n          <div class=\"phone-navbar\">\r\n            <svg class=\"phone-nav-back\" width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><polyline points=\"15 18 9 12 15 6\"/></svg>\r\n            <span class=\"phone-nav-title\">{{ phoneNavTitle }}</span>\r\n            <svg class=\"phone-nav-more\" width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"currentColor\"><circle cx=\"12\" cy=\"5\" r=\"2\"/><circle cx=\"12\" cy=\"12\" r=\"2\"/><circle cx=\"12\" cy=\"19\" r=\"2\"/></svg>\r\n          </div>\r\n\r\n          <!-- APP鍐呭锟?-->\r\n          <div class=\"phone-screen\">\r\n            <!-- 鏉垮潡1: 鐢ㄦ埛鍙楃悊淇℃伅 -->\r\n            <div v-if=\"phoneTab === 'user'\" class=\"app-page\">\r\n              <!-- 濂楅妯箙鍗＄墖 -->\r\n              <div class=\"app-banner-card\">\r\n                <div class=\"app-banner-bg\"></div>\r\n                <div class=\"app-banner-content\">\r\n                  <div class=\"app-banner-name\">{{ productInfo.prodPrcName }}</div>\r\n                  <div class=\"app-banner-fee\"><span class=\"fee-num\">{{ previewFee }}</span><span class=\"fee-unit\">鍏?鏈?/span></div>\r\n                  <div class=\"app-banner-tags\">\r\n                    <span class=\"app-tag\">{{ previewFlowAmount }}{{ previewFlowUnit }}娴侀噺</span>\r\n                    <span class=\"app-tag\">{{ previewVoiceAmount }}鍒嗛挓</span>\r\n                    <span class=\"app-tag\">{{ dynamicPreview ? (previewRights.length ? previewRights.length + '椤规潈鐩? : '瑙嗛鏉冪泭') : '5G-A缃戠粶鏉冪泭' }}</span>\r\n                  </div>\r\n                </div>\r\n              </div>\r\n              <!-- 璧勬簮璇︽儏鍗＄墖 -->\r\n              <div class=\"app-card\">\r\n                <div class=\"app-card-title\">濂楅璧勬簮</div>\r\n                <div class=\"app-resource-grid\">\r\n                  <div class=\"app-resource-item\">\r\n                    <div class=\"app-resource-icon flow\">\r\n                      <svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M5 12h14M12 5l7 7-7 7\"/></svg>\r\n                    </div>\r\n                    <div class=\"app-resource-num\">{{ previewFlowAmount }}<span class=\"unit\">{{ previewFlowUnit }}</span></div>\r\n                    <div class=\"app-resource-label\">閫氱敤娴侀噺</div>\r\n                  </div>\r\n                  <div class=\"app-resource-item\">\r\n                    <div class=\"app-resource-icon voice\">\r\n                      <svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z\"/></svg>\r\n                    </div>\r\n                    <div class=\"app-resource-num\">{{ previewVoiceAmount }}<span class=\"unit\">鍒嗛挓</span></div>\r\n                    <div class=\"app-resource-label\">鍥藉唴璇煶</div>\r\n                  </div>\r\n                  <div class=\"app-resource-item\">\r\n                    <div class=\"app-resource-icon video\">\r\n                      <svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><rect x=\"2\" y=\"3\" width=\"20\" height=\"14\" rx=\"2\"/><line x1=\"8\" y1=\"21\" x2=\"16\" y2=\"21\"/><line x1=\"12\" y1=\"17\" x2=\"12\" y2=\"21\"/></svg>\r\n                    </div>\r\n                    <div class=\"app-resource-num\">{{ dynamicPreview ? previewRights.length : 2 }}<span class=\"unit\">涓?/span></div>\r\n                    <div class=\"app-resource-label\">{{ dynamicPreview ? '鏉冪泭璧勬簮' : '瑙嗛鏉冪泭' }}</div>\r\n                  </div>\r\n                </div>\r\n              </div>\r\n              <!-- 鏉冪泭璇︽儏 -->\r\n              <div class=\"app-card\">\r\n                <div class=\"app-card-title\">鍖呭惈鏉冪泭</div>\r\n                <div class=\"app-equity-list\">\r\n                  <div class=\"app-equity-item\" v-for=\"eq in equityList\" :key=\"eq.name\">\r\n                    <div class=\"app-equity-icon\" :style=\"{ background: eq.color }\">{{ eq.short }}</div>\r\n                    <div class=\"app-equity-info\">\r\n                      <div class=\"app-equity-name\">{{ eq.name }}</div>\r\n                      <div class=\"app-equity-desc\">{{ eq.desc }}</div>\r\n                    </div>\r\n                  </div>\r\n                </div>\r\n              </div>\r\n              <!-- 璐圭敤璇存槑 -->\r\n              <div class=\"app-card\">\r\n                <div class=\"app-card-title\">璐圭敤璇存槑</div>\r\n                <div class=\"app-fee-list\">\r\n                  <div class=\"app-fee-row\"><span>濂楅鏈堣垂</span><span class=\"app-fee-val\">{{ previewFee }}.00鍏?鏈?/span></div>\r\n                  <div class=\"app-fee-row\"><span>濂楀娴侀噺</span><span class=\"app-fee-val\">{{ previewFlowOvercharge }}</span></div>\r\n                  <div class=\"app-fee-row\"><span>濂楀璇煶</span><span class=\"app-fee-val\">{{ previewVoiceOvercharge }}</span></div>\r\n                  <div v-if=\"previewSmsOvercharge\" class=\"app-fee-row\"><span>濂楀鐭俊</span><span class=\"app-fee-val\">{{ previewSmsOvercharge }}</span></div>\r\n                  <div v-if=\"dynamicPreview\" class=\"app-fee-row\"><span>鏈夋晥鏈?/span><span class=\"app-fee-val\">{{ optionLabel('validity', formData.validity) || '闀挎湡鏈夋晥' }}</span></div>\r\n                  <div v-if=\"!dynamicPreview\" class=\"app-fee-row highlight\"><span>鏀惰垂鏂瑰紡</span><span class=\"app-fee-val\">棣栨湀鎸夊ぉ鎶樼畻锛屾鏈堣捣鎸夋湀鏀跺彇</span></div>\r\n                </div>\r\n              </div>\r\n              <!-- 瑙勫垯璇存槑 -->\r\n              <div class=\"app-card\">\r\n                <div class=\"app-card-title\">瑙勫垯璇存槑</div>\r\n                <div class=\"app-rule-list\">\r\n                  <div class=\"app-rule-item\"><span class=\"app-rule-dot\"></span>鐢熸晥鏂瑰紡锛歿{ optionLabel('effRule', formData.effRule) || '绔嬪嵆鐢熸晥' }}</div>\r\n                  <div class=\"app-rule-item\"><span class=\"app-rule-dot\"></span>閫€璁㈡柟寮忥細{{ optionLabel('cancelRule', formData.cancelRule) || '绔嬪嵆澶辨晥' }}</div>\r\n                  <div v-if=\"!dynamicPreview\" class=\"app-rule-item\"><span class=\"app-rule-dot\"></span>娴侀噺鍙粨杞嚦娆℃湀</div>\r\n                  <div class=\"app-rule-item\"><span class=\"app-rule-dot\"></span>浣跨敤鑼冨洿锛歿{ (Array.isArray(formData.applicableArea) ? formData.applicableArea.map((v) => optionLabel('applicableArea', v)).filter(Boolean).join('銆?) : optionLabel('applicableArea', formData.applicableArea)) || '鍥涘窛鐪佸唴' }}</div>\r\n                  <div v-if=\"dialogueMode\" class=\"app-rule-item\"><span class=\"app-rule-dot\"></span>鍓嶉」闄愬埗锛歿{ (Array.isArray(formData.preLimitTable) ? formData.preLimitTable : []).map((r) => r.tipMsg).filter(Boolean).join('锛?) || '鐩爣瀹㈢兢涓哄湪鏍″ぇ瀛︾敓锛屽厑璁稿姙鐞? }}</div>\r\n                  <div v-else class=\"app-rule-item\"><span class=\"app-rule-dot\"></span>鏂拌€佸晢鍝佷簰鏂ワ紝涓嶅彲鍚屾椂璁㈣喘</div>\r\n                </div>\r\n              </div>\r\n              <!-- 鍔炵悊鎸夐挳 -->\r\n              <div class=\"app-action-bar\">\r\n                <button class=\"app-btn-secondary\">鍔犲叆璐墿杞?/button>\r\n                <button class=\"app-btn-primary\">绔嬪嵆鍔炵悊</button>\r\n              </div>\r\n            </div>\r\n\r\n            <!-- 鏉垮潡2: 鍏嶅～锟?-->\r\n            <div v-if=\"phoneTab === 'freefill'\" class=\"app-page\">\r\n              <div class=\"app-card\">\r\n                <div class=\"app-card-title\">鍏嶅～鍗曚俊鎭?/div>\r\n                <div class=\"app-formfill-list\">\r\n                  <div class=\"app-formfill-row\" v-for=\"item in freeFillItems\" :key=\"item.label\">\r\n                    <span class=\"app-formfill-label\">{{ item.label }}</span>\r\n                    <span class=\"app-formfill-value\" :class=\"{ highlight: item.highlight }\">{{ item.value }}</span>\r\n                  </div>\r\n                </div>\r\n              </div>\r\n              <div v-if=\"!dynamicPreview\" class=\"app-card\">\r\n                <div class=\"app-card-title\">浼樻儬淇℃伅</div>\r\n                <div class=\"app-discount-box\">\r\n                  <div class=\"app-discount-tag\">棣栨湀鍗婁环</div>\r\n                  <div class=\"app-discount-detail\">\r\n                    <div class=\"app-discount-title\">鏂扮敤鎴蜂笓灞?/div>\r\n                    <div class=\"app-discount-desc\">棣栨湀浠呴渶29.50鍏冿紝娆℃湀璧锋仮澶?9鍏?鏈?/div>\r\n                  </div>\r\n                </div>\r\n              </div>\r\n              <div class=\"app-action-bar\">\r\n                <button class=\"app-btn-primary full\">纭鍙楃悊</button>\r\n              </div>\r\n            </div>\r\n\r\n            <!-- 鏉垮潡3: 鐭俊妯℃澘棰勮 -->\r\n            <div v-if=\"phoneTab === 'sms'\" class=\"app-page\">\r\n              <div class=\"app-sms-chat\">\r\n                <div class=\"app-sms-time\">浠婂ぉ {{ phoneTime }}</div>\r\n                <div class=\"app-sms-msg\" v-for=\"(item, idx) in smsItems\" :key=\"idx\">\r\n                  <div class=\"app-sms-avatar\">\r\n                    <svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z\"/></svg>\r\n                  </div>\r\n                  <div class=\"app-sms-bubble-wrap\">\r\n                    <div class=\"app-sms-label\">{{ item.label }}</div>\r\n                    <div class=\"app-sms-bubble\">{{ item.value }}</div>\r\n                  </div>\r\n                </div>\r\n              </div>\r\n            </div>\r\n          </div>\r\n\r\n          <!-- 搴曢儴Tab瀵艰埅 -->\r\n          <div class=\"phone-tabbar\">\r\n            <div class=\"phone-tab-item\" :class=\"{ active: phoneTab === 'user' }\" @click=\"phoneTab = 'user'\">\r\n              <svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2\"/><circle cx=\"12\" cy=\"7\" r=\"4\"/></svg>\r\n              <span>鍙楃悊淇℃伅</span>\r\n            </div>\r\n            <div class=\"phone-tab-item\" :class=\"{ active: phoneTab === 'freefill' }\" @click=\"phoneTab = 'freefill'\">\r\n              <svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z\"/><polyline points=\"14 2 14 8 20 8\"/></svg>\r\n              <span>鍏嶅～鍗?/span>\r\n            </div>\r\n            <div class=\"phone-tab-item\" :class=\"{ active: phoneTab === 'sms' }\" @click=\"phoneTab = 'sms'\">\r\n              <svg width=\"20\" height=\"20\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z\"/></svg>\r\n              <span>鐭俊棰勮</span>\r\n            </div>\r\n          </div>\r\n\r\n          <!-- 鎵嬫満Home锟?-->\r\n          <div class=\"phone-home-indicator\"></div>\r\n        </div>\r\n      </div>\r\n    </div>\r\n\r\n      <div class=\"config-footer\">\r\n      <!-- 鎵归噺閰嶇疆瀵艰埅锛氱x/鍏眣 + 涓婁竴涓?涓嬩竴涓?-->\r\n      <div v-if=\"batchMode\" class=\"batch-nav\">\r\n        <button\r\n          class=\"batch-nav-btn\"\r\n          :disabled=\"!batchContext.hasPrev || submitted\"\r\n          @click=\"$emit('go-prev')\"\r\n          :title=\"batchContext.hasPrev ? '涓婁竴涓崏绋垮晢鍝? : '宸叉槸绗竴涓?\"\r\n        >\r\n          <svg width=\"13\" height=\"13\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\">\r\n            <polyline points=\"15 18 9 12 15 6\"/>\r\n          </svg>\r\n          涓婁竴涓猏r\n        </button>\r\n        <span class=\"batch-nav-count\">绗?{{ batchContext.index }} / {{ batchContext.total }} 涓?/span>\r\n        <button\r\n          class=\"batch-nav-btn\"\r\n          :disabled=\"!batchContext.hasNext || submitted\"\r\n          @click=\"$emit('go-next')\"\r\n          :title=\"batchContext.hasNext ? '涓嬩竴涓崏绋垮晢鍝? : '宸叉槸鏈€鍚庝竴涓?\"\r\n        >\r\n          涓嬩竴涓猏r\n          <svg width=\"13\" height=\"13\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2.5\">\r\n            <polyline points=\"9 18 15 12 9 6\"/>\r\n          </svg>\r\n        </button>\r\n      </div>\r\n      <template v-if=\"!submitted\">\r\n        <div class=\"footer-left\">\r\n          <button class=\"footer-btn btn-draft\" @click=\"saveDraft\">\r\n            <svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n              <path d=\"M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z\"/>\r\n              <polyline points=\"17 21 17 13 7 13 7 21\"/>\r\n              <polyline points=\"7 3 7 8 15 8\"/>\r\n            </svg>\r\n            淇濆瓨鑽夌\r\n          </button>\r\n          <button class=\"footer-btn btn-check\" @click=\"showAudit\">\r\n            <svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n              <path d=\"M9 11l3 3L22 4\"/>\r\n              <path d=\"M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11\"/>\r\n            </svg>\r\n            鏅鸿兘绋芥牳\r\n          </button>\r\n        </div>\r\n        <div class=\"footer-right\">\r\n          <button class=\"btn-submit\" @click=\"onSubmit\">\r\n            <svg width=\"14\" height=\"14\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n              <line x1=\"22\" y1=\"2\" x2=\"11\" y2=\"13\"/>\r\n              <polygon points=\"22 2 15 22 11 13 2 9 22 2\"/>\r\n            </svg>\r\n            鎻愪氦閰嶇疆\r\n          </button>\r\n        </div>\r\n      </template>\r\n      <div v-else class=\"footer-submitted\" :class=\"currentStatus\">\r\n        <svg v-if=\"currentStatus === 'completed' || currentStatus === 'testing'\" width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n          <polyline points=\"20 6 9 17 4 12\"/>\r\n        </svg>\r\n        <svg v-else width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n          <circle cx=\"12\" cy=\"12\" r=\"10\"/>\r\n          <polyline points=\"12 6 12 12 16 14\"/>\r\n        </svg>\r\n        <span class=\"footer-submitted-text\">{{ footerSubmittedText }}</span>\r\n        <span class=\"footer-submitted-id\">宸ュ崟缂栧彿锛歿{ productInfo.workOrderId }}</span>\r\n      </div>\r\n    </div>\r\n\r\n    <!-- 鏅鸿兘绋芥牳寮圭獥 -->\r\n    <transition name=\"audit-fade\">\r\n      <div v-if=\"auditVisible\" class=\"audit-overlay\" @click.self=\"auditVisible = false\">\r\n        <div class=\"audit-modal\">\r\n          <div class=\"audit-modal-header\">\r\n            <span class=\"audit-modal-title\">鏅鸿兘绋芥牳</span>\r\n            <button class=\"audit-close\" @click=\"auditVisible = false\">\r\n              <svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n                <line x1=\"18\" y1=\"6\" x2=\"6\" y2=\"18\"/>\r\n                <line x1=\"6\" y1=\"6\" x2=\"18\" y2=\"18\"/>\r\n              </svg>\r\n            </button>\r\n          </div>\r\n          <div class=\"audit-modal-body\">\r\n            <div v-if=\"auditLoading\" class=\"audit-loading\">\r\n              <div class=\"audit-spinner\"></div>\r\n              <p>{{ auditProgressText }}</p>\r\n            </div>\r\n            <div v-else class=\"audit-results\">\r\n              <div v-for=\"(item, idx) in auditResults\" :key=\"idx\" class=\"audit-item\" :class=\"item.type\">\r\n                <div class=\"audit-icon\">\r\n                  <svg v-if=\"item.type === 'success'\" width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n                    <polyline points=\"20 6 9 17 4 12\"/>\r\n                  </svg>\r\n                  <svg v-else width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\">\r\n                    <circle cx=\"12\" cy=\"12\" r=\"10\"/>\r\n                    <line x1=\"15\" y1=\"9\" x2=\"9\" y2=\"15\"/>\r\n                    <line x1=\"9\" y1=\"9\" x2=\"15\" y2=\"15\"/>\r\n                  </svg>\r\n                </div>\r\n                <div class=\"audit-text\">\r\n                  <div class=\"audit-item-title\">{{ item.title }}</div>\r\n                  <div class=\"audit-item-desc\">{{ item.desc }}</div>\r\n                </div>\r\n              </div>\r\n            </div>\r\n          </div>\r\n        </div>\r\n      </div>\r\n    </transition>\r\n  </div>\r\n";
  ConfigWorkbench.__source = 'ConfigWorkbench';
  global.ConfigWorkbench = ConfigWorkbench;
})(window);
