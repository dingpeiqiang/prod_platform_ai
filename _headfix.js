(function(global){
  const { reactive } = global.Vue;
  /* ===== launchOptimizeData ===== */
  /**
 * �?�?- 鍚姩浜у搧浼樺寲
 * AI鍔╂墜纭鏂规A鍚庯紝璋冪敤浜у搧鐮斿彂鍔╂墜鑷姩鎵撳紑閰嶇疆椤甸潰
 * 棰勫～鏁版嵁鍩轰簬鏂规A锛氱晠浜?9鍏冮潚鏄ョ増锛堝師鐣呬韩59鍏冨椁愪紭鍖栧崌绾э�? *
 * 瀛楁鏍囩瑙勫垯锛?
 * - tag: 'ai'     �?AI鎺ㄨ崘锛堟柊澧炵敓鎴愭垨鍩轰簬鏂规淇敼鐨勫€硷紝�?tagReason 璇存槑鎺ㄨ崘鍘熷洜锛?
 * - tag: 'reuse'  �?澶嶇敤锛堜粠鍘熷椁愮户鎵匡紝鏈仛鍙樻洿鐨勫€硷級
 * - tag: null     �?鏃犳爣绛撅紙甯歌��楁锛?
 */

// ============ 鏂规A棰勫～鐨勪骇鍝侀厤缃暟�?============
const launchProductConfig = {
  // 鍟嗗搧鍩烘湰淇℃�?  productInfo: reactive({
    workOrderId: 'WO20260812001',
    prodPrcName: '鐣呬�?9鍏冮潚鏄ョ増',
    template: 'personMainPrc',
    templateLabel: '涓汉涓昏祫�?,
    status: 'draft',
    auditStatus: 'pending',
  }),

  // 鍩虹淇℃伅 Tab 鈥斺�?瀛楁鎽樿嚜鏅哄鍖?鍩烘湰淇℃伅"妯��潡锛屼繚鎸丄I宸ヤ綔鍙版帓鐗堜笌AI鎺ㄨ�?澶嶇敤鏍囩鏈哄�?  baseInfo: {
    prodAttr: {
      value: 'personMainPrc',
      tag: 'ai',
      tagReason: '鏂规A瀹氫綅涓汉涓昏祫璐癸紝娌跨敤鍘熷椁愬晢鍝佸睘鎬у垎�?,
      required: true,
      label: '鍟嗗搧灞炴€?,
      options: [
        { value: 'personMainPrc', label: '涓汉涓昏祫�? },
        { value: 'personOptionalPrc', label: '涓汉鍙€夊寘' },
        { value: 'familyMainPrc', label: '瀹跺涵涓昏祫�? },
        { value: 'enterprisePrc', label: '鏢�夸紒璧勮�? },
      ],
    },
    prodCode: {
      value: 'SP20240590Q',
      tag: 'ai',
      tagReason: '鍩轰簬鍘熷晢鍝丼P-2024-0590鍏宠仈鐢熸垚鏂板晢鍝佺紪�?,
      label: '鍟嗗搧缂栫爜',
    },
    prodName: {
      value: '鐣呬�?9鍏冮潚鏄ョ増',
      tag: 'ai',
      tagReason: '鍩轰簬鏂规A鍛藉悕绛栫暐锛氫繚鐣?鐣呬�?9�?鍝佺墝寤剁画鎬э紝"闈掓槬鐗?瀹氫綅骞磋交瀹㈢兢锛岄厤鍚堝弻瑙嗛鏉冪�?澶ф祦閲忕瓥鐣?,
      required: true,
      label: '鍟嗗搧鍚嶇�?,
    },
    onlineDate: {
      value: '20260901000000',
      tag: 'ai',
      tagReason: '鏂规A璁��垝9�?鏃ヤ笂绾匡紝閬垮紑鏆戞湡淇冮攢楂樺嘲锛岄厤鍚堢瀛ｅ紑��﹀鑾峰',
      required: true,
      label: '涓婄嚎鏃ユ湡',
    },
    offlineDate: {
      value: '20991231235959',
      tag: 'reuse',
      tagReason: '澶嶇敤鍘熷椁愰暱鏈熼攢鍞瓥鐣?,
      required: true,
      label: '涓嬬嚎鏃ユ湡',
    },
    effRule: {
      value: 'immediate',
      tag: 'reuse',
      tagReason: '澶嶇敤鍘熷椁愮敓鏁堣鍒欙細鏂板叆缃戠珛鍗崇敓�?,
      required: true,
      label: '鐢熸晥瑙勫垯',
      options: [
        { value: 'immediate', label: '鏂板叆缃戠珛�?鍙樻洿娆℃湀/缁害鎸囧畾鏃ユ�? },
        { value: 'nextMonth', label: '娆℃湢�鐢熸�? },
        { value: 'appoint', label: '棰勭害鎸囧畾鏃ユ湡鐢熸晥' },
      ],
    },
    validity: {
      value: 'longTerm',
      tag: 'reuse',
      tagReason: '澶嶇敤鍘熷椁愰暱鏈熸湁鏁堢瓥鐣?,
      required: true,
      label: '鍟嗗搧鏈夋晥�?,
      options: [
        { value: 'longTerm', label: '闢�挎湡鏈夋�? },
        { value: '12m', label: '12涓湢�' },
        { value: '24m', label: '24涓湢�' },
        { value: '36m', label: '36涓湢�' },
      ],
    },
    displayCycle: {
      value: '3',
      tag: 'reuse',
      tagReason: '澶嶇敤鍘熷椁愬睍绀哄懆�?�?,
      required: true,
      label: '灞曠ず鍛ㄦ湡',
      unit: '�?,
    },
    cancelRule: {
      value: 'immediate',
      tag: 'reuse',
      tagReason: '澶嶇敤鍘熷椁愰€€璁㈢敓鏁堣�?,
      required: true,
      label: '閫€璁㈢敓鏁堣�?,
      options: [
        { value: 'immediate', label: '绔嬪嵆澶辨晥' },
        { value: 'monthEnd', label: '鏈堢粓澶辨晥' },
        { value: 'nextMonth', label: '娆℃湢�澶辨�? },
      ],
    },
    refProduct: {
      value: 'SP-2024-0590',
      tag: 'reuse',
      tagReason: '鍙傝€冨師鐣呬韩59鍏冨椁愶紝渚夸簬��规瘮涓庣户鎵块厤缃?,
      label: '鍙傝€冨晢鍝?,
      options: [
        { value: '', label: '璇烽€夋�? },
        { value: 'SP-2024-0590', label: '鐣呬�?9鍏冨椁?(SP-2024-0590)' },
      ],
    },
    orderTimes: {
      value: '1',
      tag: 'reuse',
      tagReason: '澶嶇敤鍘熷椁愬悓鏃惰璐鏁伴檺�?,
      required: true,
      label: '鍚屾椂璁㈣喘娆℃�?,
    },
    totalOrderTimes: {
      value: '',
      tag: 'reuse',
      tagReason: '澶嶇敤鍘熷椁愮疮璁¤璐鏁扮瓥鐣?,
      label: '绱璁㈣喘娆℃�?,
      unit: '涓湢�鍐呴檺璁?�?,
    },
    uploadYijing: {
      value: 'no',
      tag: 'reuse',
      tagReason: '澶嶇敤鍘熷椁愭槸鍚︿笂浼犱竴缁忕瓥�?,
      label: '鏄惁涓婁紶涓€�?,
      options: [
        { value: '', label: '璇烽€夋�? },
        { value: 'yes', label: '�? },
        { value: 'no', label: '�? },
      ],
    },
    l1ProdCode: {
      value: 'APAP00590',
      tag: 'ai',
      tagReason: '浠庝骇鍝佸簱鏌ュ埌鐨勫彲鐃6�9��竴绾т骇鍝佺紪鐮侊紝鍩轰簬鍘熷晢鍝佸叧鑱旂敓鎴?,
      label: '涓€绾т骇鍝佺紪鐮?,
    },
    isComm: {
      value: 'yes',
      tag: 'reuse',
      tagReason: '澶嶇敤鍘熷椁愰€氫俊绫诲睘�?,
      required: true,
      label: '鏄惁閫氫俊�?,
      options: [
        { value: '', label: '璇烽€夋�? },
        { value: 'yes', label: '�? },
        { value: 'no', label: '�? },
      ],
    },
    applyBasis: {
      value: '鐪佸叕鍙?026骞寸�?89鍙锋�?,
      tag: 'ai',
      tagReason: '鏂规A鐢虫姤渚濇嵁锛氱渷鍏�徃鍏充簬闈掓槬鐗堝椁愪笂绾跨殑鎵瑰鏂囦欢',
      label: '鐢宠渚濇嵁',
    },
    allowChannel: {
      value: 'yes',
      tag: 'reuse',
      tagReason: '澶嶇敤鍘熷椁愬厑璁告笭閬撳彈鐞嗙瓥�?,
      required: true,
      label: '鏄惁鍏佽鍙楃悊娓犻亾',
      options: [
        { value: '', label: '璇烽€夋�? },
        { value: 'yes', label: '�? },
        { value: 'no', label: '�? },
      ],
    },
  },

  // 璧勬簮閰嶇疆 Tab 鈥斺�?娴侀�?璇�?鐭俊璧勬簮閰嶇疆锛堜粠鍘熷彲閫夐厤缃縼鍏ワ級
  resourceSections: [
    {
      key: 'flow-resource',
      title: '娴侀噺璧勬簮閰嶇�?,
      icon: 'fa-mobile-screen',
      fields: [
        {
          key: 'flowType',
          label: '娴侀噺绫诲��?,
          value: 'general',
          type: 'select',
          tag: 'reuse',
          tagReason: '澶嶇敤鍘熷椁愰€氱敤娴侢�噺绫诲��?,
          options: [
            { value: 'general', label: '閫氱敤娴侢��? },
            { value: '瀹氬�?, label: '瀹氬悜娴侢��? },
            { value: '涓撳�?, label: '涓撳睘娴侢��? },
          ],
        },
        {
          key: 'flowAmount',
          label: '璧勬簮閲?,
          value: '40',
          type: 'number',
          highlight: true,
          tag: 'ai',
          tagReason: '鏂规A鏍�6�0績鍙樻洿�?0GB�?0GB锛岃拷骞崇珵鍝丄锛堝姩鎰熼潚鏄ョ�?0GB锛夛紝娑堥櫎娴侀噺棰濆害鍔ｅ娍锛堟牴鍥犺硢�鐚�?0%�?,
        },
        {
          key: 'flowUnit',
          label: '娴侀噺鍗曚綅',
          value: 'GB',
          type: 'select',
          tag: 'reuse',
          tagReason: '澶嶇敤鍘熷椁愭祦閲忓崟�?,
          options: [
            { value: 'GB', label: 'GB' },
            { value: 'MB', label: 'MB' },
          ],
        },
        {
          key: 'flowOvercharge',
          label: '濂楀璁¤垂鏍囧噯',
          value: '0.29�?MB',
          type: 'text',
          tag: 'reuse',
          tagReason: '澶嶇敤鍘熷椁愬澶栬璐规爣鍑?,
        },
        {
          key: 'flowCarryover',
          label: '鏄惁缁撹浆鍒版鏈?,
          value: 'yes',
          type: 'select',
          tag: 'reuse',
          tagReason: '澶嶇敤鍘熷椁愭祦閲忕粨杞瓥鐣?,
          options: [
            { value: 'yes', label: '�? },
            { value: 'no', label: '�? },
          ],
        },
      ],
    },
    {
      key: 'voice-resource',
      title: '璇煶璧勬簮閰嶇�?,
      icon: 'fa-phone',
      fields: [
        {
          key: 'voiceType',
          label: '璧勬簮绫诲��?,
          value: 'local',
          type: 'select',
          tag: 'reuse',
          tagReason: '澶嶇敤鍘熷椁愭湰鍦拌闊崇被鍨?,
          options: [
            { value: 'local', label: '鏈湴璇煶' },
            { value: 'longdistance', label: '闢�块€旇�? },
          ],
        },
        {
          key: 'voiceAmount',
          label: '璧勬簮閲?,
          value: '100',
          type: 'number',
          unit: '鍒嗛�?,
          tag: 'reuse',
          tagReason: '澶嶇敤鍘熷�?00鍒嗛挓璇煶閰嶇疆锛岄潪鏈浼樺寲閲嶇�?,
        },
        {
          key: 'voiceOvercharge',
          label: '濂楀璁¤垂璐圭巼',
          value: '0.15�?鍒嗛�?,
          type: 'text',
          tag: 'reuse',
          tagReason: '澶嶇敤鍘熷椁愯闊冲澶栬垂鐜?,
        },
      ],
    },
    {
      key: 'sms-resource',
      title: '鐭俊璧勬簮閰嶇�?,
      icon: 'fa-comment-dots',
      fields: [
        {
          key: 'smsAmount',
          label: '璧勬簮閲?,
          value: '100',
          type: 'number',
          unit: '�?,
          tag: 'reuse',
          tagReason: '澶嶇敤鍘熷�?00鏉＄煭淇￠厤�?,
        },
        {
          key: 'smsOvercharge',
          label: '濂楀璁¤垂璐圭巼',
          value: '0.1�?�?,
          type: 'text',
          tag: 'reuse',
          tagReason: '澶嶇敤鍘熷椁愮煭淇″澶栬垂鐜?,
        },
      ],
    },
    {
      key: 'rights-resource',
      title: '鏉冪泭璧勬簮閰嶇�?,
      icon: 'fa-gift',
      fields: [
        {
          key: 'rightsTable',
          label: '鏉冪泭鍒楄��?,
          type: 'table',
          tag: 'reuse',
          tagReason: '鏉冪泭鎸夋ā鏉块粯璁ら厤缃紝棰嗗彇鏂瑰紡鍙墜鍔ㄨ皟�?,
          columns: [
            {
              key: 'rightName',
              label: '鏉冪泭鍚嶇�?,
              type: 'text',
              placeholder: '璇疯緭鍏ユ潈鐩婂悕绉?,
            },
            {
              key: 'obtainType',
              label: '棰嗗彇鏂瑰紡',
              type: 'select',
              options: [
                { value: 'manual', label: '鎵嬪姩棰嗗彇' },
                { value: 'auto', label: '鑷姩鍙戞斁' },
              ],
            },
          ],
          rows: [
            { rightName: '瑙嗛浼氬憳鏈堝�?, obtainType: 'auto' },
            { rightName: '瀹氬悜鍏嶆祦鏉冪泭鍖?, obtainType: 'manual' },
            { rightName: '褰╅搩鍔熻兘', obtainType: 'auto' },
          ],
        },
      ],
    },
  ],

  // 钢�ラ攢璧勬�?Tab 鈥斺�?鍙繚鐣欐湀绉熴€佽处鍔′紭鎯犮€佷繚搴?
  marketingSections: [
    {
      key: 'monthly',
      title: '鏈堢�?,
      icon: 'fa-yen-sign',
      fields: [
        {
          key: 'chargeType',
          label: '鏢�惰垂鏂瑰�?,
          value: 'monthly',
          type: 'select',
          tag: 'reuse',
          tagReason: '澶嶇敤鍘熷椁愭湢�浠樻柟寮?,
          options: [
            { value: 'monthly', label: '鏈堜�? },
            { value: 'quarterly', label: '瀛ｄ�? },
            { value: 'yearly', label: '骞翠�? },
          ],
        },
        { key: 'monthlyFee', label: '濂楅鍥哄畾�?, value: '59.00', type: 'number', unit: '�?, tag: 'reuse', tagReason: '鏂规A淇濇寔鏈堣垂59鍏冧笉鍙? },
        {
          key: 'feeSubject',
          label: '鏢�惰垂绉戠�?,
          value: '001',
          type: 'select',
          tag: 'reuse',
          tagReason: '澶嶇敤鍘熷椁愰€氳璐圭�?,
          options: [
            { value: '001', label: '閫氳璐? },
            { value: '002', label: '澧炲€间笟鍔¤�? },
            { value: '003', label: '浠ｆ敹璐? },
          ],
        },
        { key: 'taxRate', label: '绋庣�?, value: '6', type: 'number', unit: '%', tag: 'reuse', tagReason: '澶嶇敤鍘熷椁愮◣鐜? },
      ],
    },
    {
      key: 'discount',
      title: '璐﹀姟浼樻儬',
      icon: 'fa-tags',
      fields: [
        {
          key: 'discountCondition',
          label: '浼樻儬鏉′欢',
          value: '鏂扮敤鎴烽鏈堝崐浠?,
          type: 'text',
          tag: 'ai',
          tagReason: '鏂板浼樻儬绛栫暐锛氶鏈堝崐浠峰鍐茬珵鍝佷績閿€锛岄拡��瑰勾杞绘柊瀹㈤檷浣庡皾椴滈棬妲?,
        },
        {
          key: 'discountType',
          label: '浼樻儬绫诲��?,
          value: 'cash',
          type: 'select',
          tag: 'ai',
          tagReason: '閫夋嫨鐜伴噾鐩村噺锛岢�敤鎴锋劅鐭ユ渶鐩存帴锛岃浆鍖栨晥鏋滀紭浜庝紭鎯犲�?,
          options: [
            { value: 'cash', label: '鐜伴噾鐩村噺' },
            { value: 'coupon', label: '浼樻儬鍒? },
            { value: 'gift', label: '璧犲�? },
          ],
        },
        { key: 'discountAmount', label: '浼樻儬璐圭敤', value: '29.50', type: 'number', unit: '�?, tag: 'ai', tagReason: '棣栨湢�鍗婁�?59/2=29.50�? },
      ],
    },
    {
      key: 'min-consume',
      title: '淇濆�?,
      icon: 'fa-arrow-down-wide-short',
      fields: [
        {
          key: 'minConsumeType',
          label: '淇濆簳鏂瑰紡',
          value: 'none',
          type: 'select',
          tag: 'reuse',
          tagReason: '澶嶇敤鍘熷椁愭棤淇濆簳绛栫�?,
          options: [
            { value: 'none', label: '鏃犱繚搴? },
            { value: 'monthly', label: '鏈堜繚搴? },
            { value: 'total', label: '鎬婚淇濆簳' },
          ],
        },
      ],
    },
  ],

   // 閿€鍞鍒?Tab 鈥斺�?鎽�6�0嚜鏅哄鍖咃細閿€鍞鍒欍€佸墠椤归檺鍒躲€佸悗椤归檺�?
  salesSections: [
    {
      key: 'sales-rule',
      title: '閿€鍞鍒?,
      icon: 'fa-shield-halved',
      fields: [
        {
          key: 'channelType',
          label: '娓犻亾绫诲��?,
          value: 'offline',
          type: 'checkbox-group',
          tag: 'reuse',
          tagReason: '澶嶇敤鍘熷椁愮嚎涓嬭惀涓氬巺涓绘笭閬撶瓥鐣?,
          options: [
            { value: 'offline', label: '绾夸笅钀ヤ笟�? },
            { value: 'online', label: '涓浗绉诲姩鍦ㄧ嚎娓犻亾' },
            { value: 'local-online', label: '鐪佺Щ鍔ㄧ嚎涓婃笭�? },
            { value: 'cmcc-online', label: '涓浗绉诲姩绾夸笂娓犻亾' },
            { value: 'tv', label: '鐢佃钀ヤ笟�? },
            { value: 'accelerator', label: '鍔犻€熷钩鍙? },
            { value: 'points', label: '绉垎鍟嗗煄' },
            { value: 'meter', label: '涓€绾х數�? },
          ],
        },
        {
          key: 'applicableArea',
          label: '閫傜敤鍦板尯',
          value: 'sc',
          type: 'checkbox-group',
          tag: 'reuse',
          tagReason: '澶嶇敤鍘熷椁愬洓宸濈渷閫傜敤鑼冨洿',
          options: [
            { value: 'sc', label: '鍥涘窛鍏ㄧ渷' },
            { value: 'cd', label: '鎴愰�? },
            { value: 'my', label: '缁甸�? },
            { value: 'dy', label: '寰烽�? },
            { value: 'nc', label: '鍗楀�? },
            { value: 'yb', label: '瀹滃�? },
            { value: 'lz', label: '娉�6�0�? },
            { value: 'ls', label: '涔愬�? },
          ],
        },
        {
          key: 'otherFee',
          label: '鍏朵粬璐圭敤',
          value: '0',
          type: 'text',
          tag: 'reuse',
          tagReason: '澶嶇敤鍘熷椁愭棤鍏朵粬璐圭�?,
        },
        {
          key: 'breachLiability',
          label: '杩濈害璐ｄ换',
          value: 'none',
          type: 'select',
          tag: 'reuse',
          tagReason: '澶嶇敤鍘熷椁愭棤杩濈害璐ｄ换绾︽潫',
          options: [
            { value: 'none', label: '�? },
            { value: 'compensate', label: '鎸夌害��氳ˉ鍋块�? },
          ],
        },
        {
          key: 'renewMethod',
          label: '缁鏂瑰紡',
          value: 'auto',
          type: 'select',
          tag: 'reuse',
          tagReason: '澶嶇敤鍘熷椁愯嚜鍔ㄧ画璁㈢瓥鐣?,
          options: [
            { value: 'auto', label: '鑷姩缁' },
            { value: 'manual', label: '鎵嬪姩缁' },
            { value: 'no', label: '涓嶇画璁? },
          ],
        },
      ],
    },
    {
      key: 'pre-limit',
      title: '鍓嶉」闄愬埗',
      icon: 'fa-circle-arrow-left',
      fields: [
        {
          key: 'preLimitRelation',
          label: '闄愬埗闂村叧�?,
          value: 'and',
          type: 'select',
          tag: 'ai',
          tagReason: '鏂规A锛氫笌鍓嶉��鍟嗗搧"�?鍏崇郴浜掓枼锛岀‘淇濇柊瀹㈡湭璁㈣喘鍏朵粬闈掓槬鐗堝椁?,
          options: [
            { value: 'and', label: '涓旓紙鍏ㄩ儴婊¤冻�? },
            { value: 'or', label: '鎴栵紙浠讳竴婊¤冻�? },
          ],
        },
        {
          key: 'preLimitTable',
          label: '闄愬埗鍒楄��?,
          type: 'table',
          tag: 'ai',
          tagReason: '鏂板鍓嶉��闄愬埗锛氶檺鍒跺勾�?鍏ㄧ悆閫氱瓑�?鍩虹濂楅鏈堣垂鏉′欢锛岀‘淇濈洰鏍囧缇ょ簿�?,
          columns: [
            { key: 'label', label: '鏍囩�?, type: 'select', options: [
              { value: 'age', label: '骞撮�? },
              { value: 'globalLevel', label: '鍏ㄧ悆閫氱瓑�? },
              { value: 'baseMonthlyFee', label: '鍩虹濂楅鏈堣�? },
              { value: 'targetGroup', label: '鐩爣��㈢�? },
              { value: 'studentOnly', label: '闈㈠悜鍦ㄦ牎澶у�? },
              { value: 'studentCert', label: '闇€涓婁紶��︾敓璇佽�? },
              { value: 'realName', label: '浠呴檺鏈汉瀹炲悕鍔炵悊' },
              { value: 'limitToSelf', label: '浠呴檺鏈汉鍔炵�? },
            ] },
            { key: 'ruleType', label: '瑙勫垯绫诲��?, type: 'select', options: [
              { value: 'gt', label: '�? },
              { value: 'lt', label: '�? },
              { value: 'ge', label: '�? },
              { value: 'le', label: '�? },
              { value: 'eq', label: '�? },
            ] },
            { key: 'limitValue', label: '闄愬畾鍊?, type: 'text', placeholder: '璇疯緭鍏ラ檺瀹氬�? },
            { key: 'limitType', label: '闄愬畾绫诲��?, type: 'select', options: [
              { value: 'allow', label: '鍏佽鍔炵悊' },
              { value: 'disallow', label: '涓嶅厑璁稿姙�? },
            ] },
            { key: 'tipMsg', label: '鎻愮ず璇?, type: 'auto', placeholder: '鏍规嵁鏉′欢鑷姩鐢熸垚' },
          ],
          rows: [
            { label: 'age', ruleType: 'lt', limitValue: '16', limitType: 'disallow', tipMsg: '骞撮緞锛?6客�佺殑涓嶅厑璁稿姙鐞嗘湰濂楅' },
            { label: 'baseMonthlyFee', ruleType: 'ge', limitValue: '59', limitType: 'allow', tipMsg: '鍩虹濂楅鏈堣垂鈮?9鍏冪殑鍏佽鍔炵悊鏈�椁? },
          ],
        },
      ],
    },
    {
      key: 'post-limit',
      title: '鍚庨」闄愬埗',
      icon: 'fa-circle-arrow-right',
      fields: [
        {
          key: 'postLimitTable',
          label: '鍚庨」闄愬埗',
          type: 'table',
          tag: 'ai',
          tagReason: '鏂板鍚庨��闄愬埗锛氳璐湰濂楅鍚庨檺鍒堕檷妗?閫€�?鍋滄溢�淇濆�?閿€�?鎼哄彿杞綉/杩囨埛绛夋搷�?,
          columns: [
            { key: 'ruleType', label: '瑙勫垯绫诲��?, type: 'select', options: [
              { value: 'product', label: '鍟嗗搧闄愬埗' },
              { value: 'business', label: '涓氬姟闄愬埗' },
            ] },
            { key: 'bizName', label: '涓氬姟鍚嶇�?, type: 'select', dynamicOptions: true, options: {
              product: [
                { value: 'downgrade', label: '闄嶆�? },
                { value: 'unsubscribe', label: '閫€�? },
              ],
              business: [
                { value: 'suspend', label: '鍋滄溢�淇濆�? },
                { value: 'cancel', label: '閿€�? },
                { value: 'mnp', label: '鎼哄彿杞綉' },
                { value: 'transfer', label: '杩囨�? },
              ],
            } },
            { key: 'limitType', label: '闄愬埗绫诲��?, type: 'select', options: [
              { value: 'disallow', label: '涓嶅厑璁? },
              { value: 'allow', label: '鍏佽�? },
            ] },
            { key: 'limitTime', label: '闄愬埗鏃堕棿', type: 'select', options: [
              { value: '12', label: '12涓湢�' },
              { value: '24', label: '24涓湢�' },
              { value: '36', label: '36涓湢�' },
              { value: '48', label: '48涓湢�' },
            ] },
          ],
          rows: [
            { ruleType: 'product', bizName: 'downgrade', limitType: 'disallow', limitTime: '12' },
            { ruleType: 'business', bizName: 'cancel', limitType: 'disallow', limitTime: '24' },
          ],
        },
      ],
    },
  ],

  // 鍙€夐厤缃?Tab 鈥斺�?鍏煎鏃у紩鐢紝宸叉媶鍒嗗埌resourceSections/marketingSections/salesSections
  optionalSections: [],

  // 淇℃伅棰勮 Tab 鈥斺�?鐃6�9��埛鍙楃悊杩囩▼鐩稿叧淇℃�?+ 鍏嶅～鍗?
  previewSections: [
    {
      key: 'user-facing',
      title: '鐃6�9��埛鍙楃悊淇℃�?,
      icon: 'fa-user',
      description: '鐃6�9��埛鍦ㄥ姙鐞嗚繃绋嬩腑鐪嬪埌鐨勫叧閿俊鎭?,
      items: [
        { label: '鍟嗗搧鍚嶇�?, value: '鐣呬�?9鍏冮潚鏄ョ増', highlight: true },
        { label: '鏈堣�?, value: '59�?�? },
        { label: '鍖呭惈娴侢��?, value: '40GB閫氱敤娴侢�噺锛堝彲缁撹浆娆℃湀�?, highlight: true },
        { label: '鍖呭惈璇煶', value: '100鍒嗛挓鏈�湴璇煶' },
        { label: '瑙嗛鏉冪泭', value: '鑵捐瑙嗛鏈堝�?+ 鐖卞鑹烘湀�?, highlight: true },
        { label: '鍏朵粬鏉冪泭', value: '鐢熸棩鐗规潈銆佹祦閲忕粨�? },
        { label: '濂楀娴侢�噺璐?, value: '0.29�?MB' },
        { label: '濂楀璇煶�?, value: '0.15�?鍒嗛�? },
        { label: '鏂扮敤鎴蜂紭�?, value: '棣栨湢�鍗婁环锛堜粎闇€29.50鍏冿�?, highlight: true },
        { label: '鐢熸晥鏂瑰紡', value: '绔嬪嵆鐢熸晥' },
        { label: '閫€璁㈡柟寮?, value: '绔嬪嵆澶辨晥' },
        { label: '浣跨敤鑼冨洿', value: '鍥涘窛鐪佽寖鍥村�? },
      ],
    },
    {
      key: 'free-fill-preview',
      title: '鍏嶅～鍗?,
      icon: 'fa-file-alt',
      description: '钢�ヤ笟鍛樺彈鐞嗘椂鍏嶅～鐨勮嚜鍔ㄥ甫鍑轰俊�?,
      items: [
        { label: '璧勮垂鍚嶇�?, value: '鐣呬�?9鍏冮潚鏄ョ増' },
        { label: '濂楅鏈堣垂', value: '59�?�? },
        { label: '鍖呭惈璧勬簮', value: '40G娴侀�?100鍒嗛挓璇煶+鑵捐瑙嗛+鐖卞鑹?, highlight: true },
        { label: '瓒呭鏀惰垂鏍囧�?, value: '0.29�?MB' },
        { label: '闄愬畾鏉′欢', value: '鍥涘窛鐪佽寖鍥村唴浣跨敤锛屾柊鑰佸晢鍝佷簰鏂? },
        { label: '鍏朵粬鏉冪泭', value: '鐢熸棩鐗规潈銆佹祦閲忕粨杞€佽吘璁棰戞湢�鍗°€佺埍濂囪壓鏈堝崱', highlight: true },
        { label: '鏢�惰垂鏂瑰�?, value: '鏈堜�? },
        { label: '濂楅鍥哄畾�?, value: '59.00�? },
        { label: '绋庣�?, value: '6%' },
        { label: '浼樻儬鏉′欢', value: '鏂扮敤鎴烽鏈堝崐浠? },
        { label: '浼樻儬绫诲��?, value: '鐜伴噾鐩村噺' },
        { label: '浼樻儬璐圭敤', value: '29.50�? },
      ],
    },
    {
      key: 'sms-preview',
      title: '鐭俊妯℃澘棰勮�?,
      icon: 'fa-comment-sms',
      description: '鍚勮Е鐐��笅鍙戠殑鐭俊鍐呭棰勮',
      items: [
        { label: '鍔炵悊鎴愬姛鐭�?, value: '鎭枩鎮ㄦ垚鍔熷姙鐞嗙晠�?9鍏冮潚鏄ョ増锛屾湢��?9鍏冿紝鍚?0G娴侀噺鍜?00鍒嗛挓璇煶锛屽惈鑵捐瑙嗛�?鐖卞鑹哄弻鏉冪泭锛屾劅璋㈡偍鐨勬敮鎸侊�? },
        { label: '棰勭害鐢熸晥鐭�?, value: '鎮ㄧ殑鐣呬韩59鍏冮潚鏄ョ増宸茬敓鏁堬紝鏈堣�?9鍏冿紝鐢熸晥褰撴湢�璧蜂韩鍙楢�椁愭潈鐩�6�0�? },
        { label: '鍓嶅彴浜岢�‘鐭俊', value: '灏婃暚鐨勫鎴凤紝鎮ㄦ鍦ㄥ姙鐞嗙晠�?9鍏冮潚鏄ョ増锛屾湢��?9鍏冿紝鏄惁纭鍔炵悊�? },
        { label: '閫€璁㈢煭淇?, value: '鎮ㄥソ锛屾偍鐨勭晠浜?9鍏冮潚鏄ョ増宸查€€璁紝濡傛湁鐤戦棶璇疯嚧�?0086�? },
      ],
    },
  ],
}

// ============ 娣卞害鎬濊€冩�?============
const launchThinkingSteps = [
  '瑙ｆ瀽鐢ㄦ埛鎸囦护锛氬惎鍔ㄤ骇鍝佷紭鍖栵紝纭鏂规A',
  '璋冪敤浜у搧鏌ヨ鍔╂墜锛屾绱㈢晠浜?9鍏冨椁愶紙SP-2024-0590锛夊叏閲忕敾鍍忊�?,
  '浜у搧鏌ヨ鍔╂墜杩斿洖锛氬湪�?6涓囩敤鎴枫€佹湀鏢�跺叆5,046涓囥€佸悎绾︾敤�?1.2�?,
  '鏍￠獙浜掓枼瑙勫垯锛氭柊鍟嗗�?鐣呬�?9鍏冮潚鏄ョ増"涓庣幇鏈夊晢鍝丼P-2024-0590浜掓枼锛屽彲骞惰閿€鍞?�?,
  '璋冪敤浜у搧鐮斿彂鍔╂墜锛屾媺鍙栧晢鍝侢�厤缃鍒欎笌璧勮垂瀹℃壒妯℃澘�?,
  '浜у搧鐮斿彂鍔╂墜锛氬尮閰嶄笟鍔℃ā鏉裤€屼釜浜轰富璧勮垂銆嶏紝鍔犺浇閰嶇疆瀛楁锛?涓猅ab�?0涓猻ection�?,
  '鏍规嵁鏂规A棰勫～閰嶇疆锛氭祦閲?0GB�?0GB銆佽棰戞潈�?�?锛堣吘璁?鐖卞鑹猴級锛屾湭鍙樺瓧娈垫爣璁?澶嶇�?',
  '鐢熸垚宸ュ崟缂栧彿锛歐O20260812001',
  '娴嬬畻鏉冪泭鎴愭湰锛?.0鍏冣�?.8�?�?鏈堬紝ROI=5.75锛堣揪鏍囷級',
  '鑷姩鎵撳紑鏂板椁愰厤缃伐浣滃彴',
]

// ============ �?杞姪鎵嬪洖澶嶆枃妗?============
function buildLaunchSummary() {
  return [
    '### 宸插惎鍔ㄤ骇鍝佷紭鍖?�?鏂规A',
    '',
    '> 鏢�跺埌鎸囦护锛屾鍦ㄦ墽琛屾柟妗圓锛堟柊鍟嗗搧绛栧垝锛夈€傚凡璋冪敤浜у搧鏌ヨ鍔╂墜鍜屼骇鍝佺爺鍙戝姪鎵嬪畬鎴愬墠鏈熷噯澶囷紝閰嶇疆宸ヤ綔鍙板凡鑷姩鎵撳紑銆?,
    '',
    '**鎵ц鎽�6�0�?*',
    '',
    '| 姝ラ�?| 鍔ㄤ�?| 缁撴�?|',
    '| ---- | ---- | ---- |',
    '| 1 | 璋冪敤浜у搧鏌ヨ鍔╂�?| 妫€绱㈢晠浜?9鍏冨椁愬叏閲忕敾鍍忥細鍦ㄧ�?6涓囩敤鎴枫€佹湀鏢�跺叆5,046涓囥€佸悎绾︾敤�?1.2�?�?|',
    '| 2 | 鏍￠獙浜掓枼瑙勫�?| 鏂板晢鍝?鐣呬�?9鍏冮潚鏄ョ増"涓嶴P-2024-0590浜掓枼锛屽彲骞惰閿€鍞紝瀛橀噺鐢ㄦ埛涓嶅彈褰卞搷 �?|',
    '| 3 | 璋冪敤浜у搧鐮斿彂鍔╂�?| 鍖归厤涓氬姟妯℃澘銆屼釜浜轰富璧勮垂銆嶏紝鍔犺浇5涓猅ab�?0涓猻ection閰嶇疆��楁�?�?|',
    '| 4 | 棰勫～鏂规A閰嶇�?| 娴侀�?0GB�?*40GB**銆佽棰戞潈�?�?*2**锛堣吘璁?鐖卞鑹猴級銆佹湢��?9鍏冧笉鍙?�?|',
    '| 5 | 鐢熸垚宸ュ崟 | WO20260812001锛屾柊鍟嗗搧鍚嶇О"鐣呬�?9鍏冮潚鏄ョ増" �?|',
    '| 6 | 鏉冪泭鎴愭湰娴嬬�?| 3.0鍏冣�?.8�?�?鏈堬紝ROI=5.75�?3.0闂ㄦ锛岃揪鏍囷級鉁?|',
    '',
    '**宸查濉殑鍏抽敭鍙樻洿瀛楁�?*',
    '- 璧勮垂鍚嶇О锛氱晠�?9鍏冨椁?�?**鐣呬�?9鍏冮潚鏄ョ増**',
    '- 娴侀噺璧勬簮�?0GB �?**40GB**锛堥€氱敤娴侢�噺锛屽彲缁撹浆娆℃湀�?,
    '- 鍖呭惈璧勬簮�?0G娴侀�?100鍒嗛挓璇煶+鑵捐瑙嗛 �?**40G娴侀�?100鍒嗛挓璇煶+鑵捐瑙嗛+鐖卞鑹?*',
    '- 鍏朵粬鏉冪泭锛氱敓鏃ョ壒鏉冦€佹祦閲忕粨杞€佽吘璁棰戞湢��?�?**+鐖卞鑹烘湀�?*',
    '- 鏈堢鍥哄畾璐癸�?9.00鍏冿紙涓嶅彉�?,
    '- 浼樻儬绛栫暐锛氭柊鐢ㄦ埛棣栨湢�鍗婁环锛?9.50鍏冿�?,
    '',
    '> 鍙充晶宸ヤ綔鍙板凡鎵撳紑銆傚瓧娈垫爣绛捐鏄庯細**AI鎺ㄨ�?*=鍩轰簬鏂规鏂板鎴栦慨鏢�圭殑鍊硷紙鎮诞鍙煡鐪嬫帹鑽愬師鍥狅級�?*澶嶇�?*=浠庡師濂楅缁ф壙鏈彉鏇寸殑鍊笺�?,
    '',
    '璇锋鏌ラ濉唴��瑰苟��屾垚閰嶇疆銆傚彲鐐瑰嚮銆屾櫤鑳界ń鏍搞€嶈繘琛屾彁浜ゅ墠鏍￠獙銆?,
  ].join('\n')
}

// ============ 娴嬭瘯鎶ュ憡鏁版嵁锛堟祴璇曚汉鍛樹笂浼犲悗鑷姩瑙ｆ瀽锛?============
// 鍒濆�?uploaded=false锛岃〃绀烘祴璇曚汉鍛樺皻鏈笂浼狅紱涓婁紶鍚庣疆�?true锛岄厤缃汉鍛樺嵆鍙煡�?
const testReport = reactive({
  uploaded: false,
  uploadedAt: null,
  reportId: 'TR20260813001',
  workOrderId: 'WO20260812001',
  productName: '鐣呬�?9鍏冮潚鏄ョ増',
  tester: '寮犳�?,
  testDate: '2026-08-13',
  status: 'passed', // passed | failed
  testMethod: '鏂颁笟鍔℃祴璇曪紙鍚浐璐规牳鏌ャ€佽璐规牳鏌ャ€佹彁閱掓牳鏌ャ€佸伐鍗曟牳鏌ワ級',
  acceptNumber: '13920260813',
  sections: [
    {
      key: 'fixedFee',
      title: '鍥鸿垂鏍告煡',
      status: 'passed',
      items: [
        { label: '鏈堢鍥哄畾�?, expected: '59.00�?, actual: '59.00�?, result: 'pass' },
        { label: '棣栨湢�浼樻儬璐?, expected: '29.50鍏冿紙鏂扮敤鎴烽鏈堝崐浠凤�?, actual: '29.50�?, result: 'pass' },
        { label: '鍥哄畾璐圭敓鏁堟椂闂?, expected: '璁㈣喘娆℃湀1鏃ョ敓鏁?, actual: '娆℃湢�1鏃ョ敓鏁?, result: 'pass' },
      ],
    },
    {
      key: 'billing',
      title: '璁¤垂鏍告�?,
      status: 'passed',
      items: [
        { label: '娴侀噺璁¤�?, expected: '40GB鍐呬笉棰濆璁¤垂锛岃秴鍑哄悗1�?GB鑷姩鍙犲姞', actual: '40GB鍐呮甯革紝瓒呭嚭鎸?�?GB鍙犲�?, result: 'pass' },
        { label: '璇煶璁¤�?, expected: '100鍒嗛挓鍐呭厤璐癸紝瓒呭嚭0.15�?鍒嗛�?, actual: '100鍒嗛挓鍐呭厤璐癸紝瓒呭嚭0.15�?鍒嗛�?, result: 'pass' },
        { label: '鏉冪泭璁¤�?, expected: '鑵捐瑙嗛+鐖卞鑹烘湀鍗★紝涓嶉澶栨墸璐?, actual: '鏉冪泭姝ｅ父涓嬪彂锛屾棤棰濆鎵ｈ垂', result: 'pass' },
        { label: '娴侀噺缁撹浆', expected: '褰撴湢�鍓╀綑娴侢�噺缁撹浆鑷虫鏈堬紝娆℃湢�搴曟竻闆?, actual: '缁撹浆瑙勫垯姝ｇ�?, result: 'pass' },
      ],
    },
    {
      key: 'reminder',
      title: '鎻愰啋鏍告煡',
      status: 'passed',
      items: [
        { label: '娴侀噺浣跨敤鎻愰�?, expected: '浣跨敤杈?0%�?5%鏃朵笅鍙戠煭淇℃彁閱?, actual: '80%�?5%鍧囨甯镐笅�?, result: 'pass' },
        { label: '濂楅璁㈣喘鎻愰�?, expected: '璁㈣喘鎴愬姛鍚庝笅鍙戞杩庣煭淇?, actual: '娆㈣繋鐭俊姝ｅ父涓嬪彂', result: 'pass' },
        { label: '鏉冪泭棰嗗彇鎻愰�?, expected: '鏉冪泭鍒拌处鍚庝笅鍙戦鍙栭€氱�?, actual: '閫氱煡姝ｅ父涓嬪�?, result: 'pass' },
      ],
    },
    {
      key: 'workOrder',
      title: '宸ュ崟鏍告煡',
      status: 'passed',
      items: [
        { label: '璁㈣喘宸ュ崟', expected: '鐢熸垚璁㈣喘宸ュ崟锛岢�姸鎬佹甯告祦杞?, actual: '宸ュ崟鐢熸垚涓旂姸鎬佹�?, result: 'pass' },
        { label: '鍙樻洿宸ュ崟', expected: '鑰佺敤鎴峰彉鏇村悗鐢熸垚鍙樻洿宸ュ崟', actual: '鍙樻洿宸ュ崟姝ｅ�?, result: 'pass' },
        { label: '閫€璁㈠伐鍗?, expected: '閫€璁㈢敓鎴愰€€璁㈠伐鍗曪紝娆℃湀鐢熸�?, actual: '閫€璁㈠伐鍗曟纭紝娆℃湀鐢熸�?, result: 'pass' },
      ],
    },
  ],
  conclusion: '閫氳�?,
  conclusionDesc: '璇ヨ祫璐规柟妗堝悇椤规牳鏌ュ潎绗﹢�悎棰勬湡锛屾祴璇曢€氳繃锛屽彲杩涘叆澶囨鍏ず鐜妭銆?,
})

/**
 * 澶囨鍏ず鏁版�? *
 * started: 鏄惁宸插彂璧峰妗堝叕绢�猴紙鐐瑰�?鍙戣捣澶囨鍏�?鍚庝�?true�?
 * submitted: 鏄惁宸叉彁浜ゅ妗堬紙鐐瑰�?鎻愪氦澶囨"鍚庝�?true锛屾彁浜ゅ悗澶囨閫氳繃 �?鍙笂鏋堕攢鍞�? */
const filing = reactive({
  started: false,
  submitted: false,
  approved: false,
  approvedAt: null,
  filingId: 'FL20260814001',
  workOrderId: 'WO20260812001',
  productName: '鐣呬�?9鍏冮潚鏄ョ増',
  template: '涓汉涓昏祫�?,
  productCode: 'SP20240590Q',
  applicant: '浜у搧閰嶇疆涓撳憳',
  applyDate: '2026-08-14',
  // 澶囨淇℃伅
  info: {
    filingOrg: '浜у搧鐮斿彂涓績',
    filingOfficer: '鏉庤�?,
    filingDate: '2026-08-14',
    effectiveDate: '2026-09-01',
    expireDate: '闢�挎湡鏈夋�?,
  },
  // 澶囨鏍告煡�?
  checks: [
    { label: '璧勮垂鍚堣�?, status: 'passed', desc: '绗﹀悎銆婅祫璐圭鐞嗗姙娉曘€嬬浉鍏宠瀹氾紝鏃犺繚瑙勯�? },
    { label: '鍏ず鍐呭瀹屾暣鎬?, status: 'passed', desc: '璧勮垂鍚嶇О銆佽祫璐规爣鍑嗐€佺敓鏁堟椂闂寸瓑鍏ず淇℃伅瀹屾�? },
    { label: '娴嬭瘯鎶ュ憡鏍搁�?, status: 'passed', desc: '娴嬭瘯鎶ュ憡 TR20260813001 宸查€氳繃锛屽厑璁�6�0妗? },
    { label: '瀹℃壒閾惧畬鏁存�?, status: 'passed', desc: '棰勮��℃壒銆佹祴璇曞鏍稿鎵归摼��屾�? },
  ],
  // 鍏ず棰勮
  notice: {
    title: '鍏充�?鐣呬�?9鍏冮潚鏄ョ増"璧勮垂鏂规澶囨鍏ず',
    target: '鍏ㄤ綋鐢ㄦ埛',
    content: '鐜板�?鐣呬�?9鍏冮潚鏄ョ増"璧勮垂鏂规锛堣祫璐逛唬鐮侊細SP20240590Q锛変簣浠ュ妗堝叕绀恒€傚叕绢�烘湡婊��笖鏃犲紓璁悗锛岃璧勮垂鏂规灏嗘寮忎笂鏋堕攢鍞�?,
    notifyChannels: '钢�ヤ笟鍘呭叕绀烘爮銆佺綉涓婅惀涓氬巺銆佹帉涓婅惢�涓氬�?,
    startDate: '2026-08-15',
    endDate: '2026-08-29',
    duration: '15�?,
  },
  // 鍏ㄩ噺瑙嗗浘瀛楁锛坈ategory: common=鍏辨�?/ filing=澶囨鐗规湁 / public=鍏ず鐗规湁�?
  sections: [
    {
      title: '澶囨鍩烘湰淇℃�?,
      previewOnly: true,
      fields: [
        { label: '澶囨鍏ず娴佺�?, value: '鍏堝妗堝啀鍏�?, category: 'filing' },
        { label: '鍏ず绯诲垪ID', value: '璧勮垂ID', category: 'filing' },
        { label: '鍏ず绯诲垪鍚嶇О', value: launchProductConfig.baseInfo.prodName.value, category: 'filing' },
        { label: '鏂规缂栧彿', value: 'XNBP0001', category: 'filing' },
      ],
    },
    {
      title: '濂楅鍩烘湰淇℃�?,
      previewOnly: false,
      fields: [
        { label: 'BOSSID', value: launchProductConfig.baseInfo.prodCode.value, category: 'filing' },
        { label: '涓€绾у垎�?, value: '鍏�?, category: 'filing' },
        { label: '浜岀骇鍒嗙被', value: '濂楅�?, category: 'filing' },
        { label: '鍚嶇О', value: launchProductConfig.baseInfo.prodName.value, category: 'filing' },
        { label: '璧勮垂灞炴€?, value: '鐪佸�?, category: 'filing' },
        { label: '鏄惁鑷€夊�?, value: '�?, category: 'filing' },
        { label: '妯＄粍鍒嗙被鍚嶇О', value: '�?, category: 'filing' },
        { label: '鏄惁閫氫俊�?, value: launchProductConfig.baseInfo.isComm.value === 'yes' ? '�? : '�?, category: 'filing' },
        { label: '鏄惁涓绘暟�?, value: '�?, category: 'filing' },
        { label: '灞曠ず椤哄簭鏉冮�?, value: '100', category: 'filing' },
        { label: '閫傜敤鑼冨洿', value: '閫氳繃鍓嶉��闄愬埗鐢熸�?, category: 'common' },
        { label: '璐拱閾炬帴', value: 'https://xxx.com/buy', category: 'filing' },
        { label: '璧勮垂鍗曚綅', value: '�?�?, category: 'filing' },
      ],
    },
    {
      title: '鍩虹淇℃伅',
      previewOnly: false,
      fields: [
        { label: '璧勮垂鏍囧噯', value: '59�?�?, category: 'common' },
        { label: '璧勮垂绫诲��?, value: '濂楅�?, category: 'public' },
        { label: '鏂规缂栧彿', value: 'AI203935', category: 'public' },
        { label: '涓婄嚎鏃ユ湡', value: '20260901', category: 'common' },
        { label: '涓嬬嚎鏃ユ湡', value: '20991231', category: 'common' },
        { label: '鏈夋晥鏈熼檺', value: '36涓湢�', category: 'common' },
        { label: '鍚堢害鏈?鍦ㄧ綉瑕佹眰)', value: '12涓湢�', category: 'common' },
        { label: '閫傜敤鍦板尯', value: '鍏ㄧ渷锛堝畨寰斤�?, category: 'common' },
        { label: '閿€鍞笭閬?, value: '鑷湁钀ヤ笟�?缃戜笂钀ヤ笟�?, category: 'common' },
        { label: '閫€璁㈡柟寮?, value: '绾夸笅钀ヤ笟�?, category: 'common' },
        { label: '杩濈害璐ｄ换', value: '鍚堢害鏈熷唴閫€璁㈤渶缂寸撼杩濈害閲?, category: 'common' },
      ],
    },
    {
      title: '璧勬簮淇℃伅',
      previewOnly: false,
      fields: [
        { label: '鍥藉唴閫氳瘽', value: '100鍒嗛�?, category: 'common' },
        { label: '鍥藉唴閫氱敤娴侀�?, value: '40GB', category: 'common' },
        { label: '瀹氬悜娴侢��?, value: '100GB', category: 'common' },
        { label: '鐭�?, value: '100�?, category: 'common' },
        { label: '鍥介檯閫氳瘽(鍥介檯闀块€旈€氳瘽�?', value: '0鍒嗛�?, category: 'common' },
        { label: '娓境鍙版祦�?, value: '0GB', category: 'filing' },
        { label: 'IPTV(绉诲姩楂樻竻)', value: '�?, category: 'common' },
        { label: '瀹藉�?, value: '�?, category: 'common' },
        { label: '鏉冪�?, value: '鑵捐瑙嗛+鐖卞鑹烘湀�?, category: 'common' },
      ],
    },
    {
      title: '璇存槑淇℃伅',
      previewOnly: false,
      fields: [
        { label: '瓒呭嚭璧勮垂璇存�?, value: '鍥藉唴閫氳瘽瓒呭嚭鍚?.15�?鍒嗛挓锛屾祦閲忔�?.29�?MB', category: 'common' },
        { label: '鍏朵粬鏈嶅姟鍐呭�?, value: '鍚潵鐢垫樉绢�恒€佸僵閾冪瓑鍩虹鏈嶅�?, category: 'common' },
        { label: '鍏朵粬璧勮垂璇存�?, value: '�?, category: 'public' },
        { label: '鍏朵粬璇存槑', value: '�?, category: 'common' },
      ],
    },
  ],
})

/**
 * 閿€鍞笭閬?store 鏁版�? */
const salesChannel = reactive({
  // 鏄惁宸茶繘鍏ユ笭閬撻€夋嫨闃舵锛堝妗堥€氳繃鍚庝负 true�?
  available: false,
  // 娓犻亾閫夋嫨鏄惁宸叉彁浜わ紙鎻愪氦鍚庢祦绋嬭繘鍏ヤ笂鏋堕攢鍞�?  submitted: false,
  // 鍙敤娓犻亾鍒楄�?  list: [
    { key: 'own_offline', label: '鑷湁钀ヤ笟�?, desc: '瀹炰綋钀ヤ笟鍘呯嚎涓嬪彈�? },
    { key: 'own_online', label: '缃戜笂钀ヤ笟�?, desc: '瀹樻柟缃戜笂钢�ヤ笟鍘呭湪绾胯�? },
    { key: 'app', label: '鎺屼笂钀ヤ笟鍘匒PP', desc: '鎵嬫溢�钢�ヤ笟鍘呭鎴风' },
    { key: 'customer_service', label: '10086瀹㈡湇鐑嚎', desc: '鐢佃瘽��㈡湇浜哄憳鍙楃�? },
    { key: 'channel_coop', label: '绢�句細鍚堜綔娓犻亾', desc: '绗笁鏂瑰悎浣滀唬鐞嗗晢' },
  ],
  // 宸查€夋笭閬?
  selected: [],
  // 榛�6�0鍕鹃€夛紙鎺ㄨ崘锛?
  defaultSelected: ['own_online', 'app'],
})

  /* ===== campusDraftData ===== */
  /**
 * campusDraftData - 鐣呬韩鏍″洯�?�?瀵硅瘽寮忛厤缃暟鎹?
 *
 * 鍦烘櫙锛氭柊寤轰竴妗ｅ叏鏂板椁愩€岢�晠浜牎鍥�?9鍏冨椁愩€嶏紝**鏃犱换浣曞師濂楅鍙弬�?*�?
 * 鍥犳鏈�溢�鏅墍鏈夊瓧娈电�?AI 鎺ㄨ崘鐞嗙敱鍧囧熢�浜庛€岀敤鎴烽渶�?+ 涓氬姟妯℃澘銆嶇敓鎴愶紝
 * 鍒绘剰瑙勯伩"娌跨敤鍘熷椁愬晢鍝佸睘鎬у垎�?/ 澶嶇敤鍘熷椁��X"绛夎〃杩?鈥斺�?杩欑被琛ㄨ堪鍙湁鍦?
 * 銆屽瓨閲忓晢鍝佸鍒堕厤�?/ 鍚姩浜у搧浼樺寲銆嶈繖绫荤��鏈夊師濂楅鍙傝€冪殑鍦烘櫙鎵嶅悎閫傘�?
 *
 * 瀛楁鏍囩瑙勫垯锛堜笌 launchOptimizeData.js 涓€鑷达級锛?
 * - tag: 'ai'     �?AI鎺ㄨ崘锛堜緷鎹敤鎴烽渶姹傛垨涓氬姟妯℃澘鐢熸垚锛岄�?tagReason 璇存槑鎺ㄨ崘鍘熷洜锛?
 * - tag: 'reuse'  �?寤鸿鐢?AI 鎸変笟鍔℃ā鏉跨洿鎺ヨˉ鍏紙��硅瘽寮?鎵归噺鍦烘櫙涓嬫覆鏌撲负"AI鎺ㄨ�?锛屼笉鏄剧ず"澶嶇�?�?
 * - tag: null     �?鏃犳爣绛撅紙甯歌��楁锛?
 *
 * 缁撴瀯��归�?launchOptimizeData锛歱roductInfo / baseInfo / resourceSections /
 * marketingSections / salesSections / previewSections�?
 *
 * 娉細鎵撳紑鏌愪釜鎵归噺鍟嗗搧宸ヤ綔鍙版椂锛岃��鍗曞€间互璇ュ晢鍝?parsedFields 涓哄噯锛屾湰 schema 鐨勯粯璁ゅ€间粎�?
 * 鍏滃�?AI鎺ㄨ崘寤鸿灞曠ず锛堟澶勪互鏂版柟妗堝叆闂ㄦ��?59 鍏冧负鍑嗭級�?
 */

// ============ 鐣呬韩鏍″洯�?�?瀵硅瘽寮忛厤缃殑浜у搧閰嶇疆鏁版�?============
const campusProductConfig = {
  // 鍟嗗搧鍩烘湰淇℃�?  productInfo: reactive({
    workOrderId: '',
    prodPrcName: '鐣呬韩鏍″洯�?9鍏冨椁?,
    template: 'personMainPrc',
    templateLabel: '涓汉涓昏祫�?,
    status: 'draft',
    auditStatus: 'pending',
  }),

  // 鍩虹淇℃伅 Tab
  baseInfo: {
    prodAttr: {
      value: 'personMainPrc',
      tag: 'ai',
      tagReason: '渚濇嵁浜у搧��氫綅鍒ゅ畾涓洪潰鍚戝鐢熶釜浜虹殑涓昏祫璐瑰晢鍝侊紝閫夋嫨"涓汉涓昏祫�?灞炴�?,
      required: true,
      label: '鍟嗗搧灞炴€?,
      options: [
        { value: 'personMainPrc', label: '涓汉涓昏祫�? },
        { value: 'personOptionalPrc', label: '涓汉鍙€夊寘' },
        { value: 'familyMainPrc', label: '瀹跺涵涓昏祫�? },
        { value: 'enterprisePrc', label: '鏢�夸紒璧勮�? },
      ],
    },
    prodCode: {
      value: 'SP20260828001',
      tag: 'ai',
      tagReason: '鎸夊晢鍝佺紪鐮佽鍒欎负鏂板晢鍝佺敓鎴愬敮涓€缂栫爜',
      label: '鍟嗗搧缂栫爜',
    },
    prodName: {
      value: '鐣呬韩鏍″洯�?9鍏冨椁?,
      tag: 'ai',
      tagReason: '渚濇嵁鐢ㄦ埛鍛藉悕娌跨敤"鐣呬韩鏍″洯�?9鍏冨椁?浣滀负鍟嗗搧鍚嶇О锛岃创鍚堝鐢熷缇ゅ叆闂ㄦ。��氫�?,
      required: true,
      label: '鍟嗗搧鍚嶇�?,
    },
    onlineDate: {
      value: '20260901000000',
      tag: 'ai',
      tagReason: '寤鸿�?�?鏃ュ紑��﹀涓婄嚎锛屽鍚堝湪鏍��ぇ瀛︾敓鏂板鑾峰鑺傚',
      required: true,
      label: '涓婄嚎鏃ユ湡',
    },
    offlineDate: {
      value: '20991231235959',
      tag: 'ai',
      tagReason: '鍟嗗搧闀挎湡鏈夋晥锛屾棤鍥哄畾涓嬬嚎鏃堕�?,
      required: true,
      label: '涓嬬嚎鏃ユ湡',
    },
    effRule: {
      value: 'immediate',
      tag: 'ai',
      tagReason: '鎸変笟鍔℃ā鏉块粯璁わ細鏂板叆缃戠珛鍗崇敓鏁堬紝渚夸簬瀛︾敓寮€鍗″嵆�?,
      required: true,
      label: '鐢熸晥瑙勫垯',
      options: [
        { value: 'immediate', label: '鏂板叆缃戠珛�?鍙樻洿娆℃湀/缁害鎸囧畾鏃ユ�? },
        { value: 'nextMonth', label: '娆℃湢�鐢熸�? },
        { value: 'appoint', label: '棰勭害鎸囧畾鏃ユ湡鐢熸晥' },
      ],
    },
    validity: {
      value: 'longTerm',
      tag: 'ai',
      tagReason: '渚濇嵁鐢ㄦ埛闇€�?鍟嗗搧闀挎湡鏈夋�?锛岃缃负闢�挎湡鏈夋�?,
      required: true,
      label: '鍟嗗搧鏈夋晥�?,
      options: [
        { value: 'longTerm', label: '闢�挎湡鏈夋�? },
        { value: '12m', label: '12涓湢�' },
        { value: '24m', label: '24涓湢�' },
        { value: '36m', label: '36涓湢�' },
      ],
    },
    displayCycle: {
      value: '3',
      tag: 'ai',
      tagReason: '鎸変笟鍔℃ā鏉块粯璁ゅ睍绀哄懆�?�?,
      required: true,
      label: '灞曠ず鍛ㄦ湡',
      unit: '�?,
    },
    cancelRule: {
      value: 'immediate',
      tag: 'ai',
      tagReason: '鎸変笟鍔℃ā鏉块粯璁ら€€璁㈢珛鍗冲け�?,
      required: true,
      label: '閫€璁㈢敓鏁堣�?,
      options: [
        { value: 'immediate', label: '绔嬪嵆澶辨晥' },
        { value: 'monthEnd', label: '鏈堢粓澶辨晥' },
        { value: 'nextMonth', label: '娆℃湢�澶辨�? },
      ],
    },
    refProduct: {
      value: '',
      tag: 'ai',
      tagReason: '鏈。涓烘柊鍟嗗搧锛屾棤鍙傝€冨晢鍝?,
      label: '鍙傝€冨晢鍝?,
      options: [
        { value: '', label: '璇烽€夋�? },
      ],
    },
    orderTimes: {
      value: '1',
      tag: 'ai',
      tagReason: '鎸変笟鍔℃ā鏉块粯璁ゆ瘡浣嶅鐢熼檺鍔?寮狅紝鍚屾椂璁㈣喘娆℃暟�?',
      required: true,
      label: '鍚屾椂璁㈣喘娆℃�?,
    },
    totalOrderTimes: {
      value: '',
      tag: 'ai',
      tagReason: '鎸変笟鍔℃ā鏉块粯璁や笉闄愬埗绱璁㈣喘娆℃�?,
      label: '绱璁㈣喘娆℃�?,
      unit: '涓湢�鍐呴檺璁?�?,
    },
    uploadYijing: {
      value: 'no',
      tag: 'ai',
      tagReason: '鎸変笟鍔℃ā鏉块粯璁や笉涓婁紶涓€�?,
      label: '鏄惁涓婁紶涓€�?,
      options: [
        { value: '', label: '璇烽€夋�? },
        { value: 'yes', label: '�? },
        { value: 'no', label: '�? },
      ],
    },
    l1ProdCode: {
      value: 'APAP202608',
      tag: 'ai',
      tagReason: '浠庝骇鍝佸簱鎸夊晢鍝佸睘鎬у尮閰嶅埌鍙敤涓€绾т骇鍝佺紪鐮?,
      label: '涓€绾т骇鍝佺紪鐮?,
    },
    isComm: {
      value: 'yes',
      tag: 'ai',
      tagReason: '鏈晢鍝佷负閫氫俊绫昏祫璐瑰晢鍝?,
      required: true,
      label: '鏄惁閫氫俊�?,
      options: [
        { value: '', label: '璇烽€夋�? },
        { value: 'yes', label: '�? },
        { value: 'no', label: '�? },
      ],
    },
    applyBasis: {
      value: '闈㈠悜鍦ㄦ牎澶у鐢熺殑鏍″洯涓撲韩璧勮垂鏂规�?,
      tag: 'ai',
      tagReason: '鐢虫姤渚濇嵁锛氭牎鍥缇や笓椤硅祫璐规柟妗?,
      label: '鐢宠渚濇嵁',
    },
    allowChannel: {
      value: 'yes',
      tag: 'ai',
      tagReason: '鎸変笟鍔℃ā鏉垮厑璁�6�0彈鐞嗘笭閬撳姙鐞?,
      required: true,
      label: '鏄惁鍏佽鍙楃悊娓犻亾',
      options: [
        { value: '', label: '璇烽€夋�? },
        { value: 'yes', label: '�? },
        { value: 'no', label: '�? },
      ],
    },
  },

  // 璧勬簮閰嶇疆 Tab
  resourceSections: [
    {
      key: 'flow-resource',
      title: '娴侀噺璧勬簮閰嶇�?,
      icon: 'fa-mobile-screen',
      fields: [
        {
          key: 'flowType',
          label: '娴侀噺绫诲��?,
          value: 'general',
          type: 'select',
          tag: 'ai',
          tagReason: '渚濇嵁鐢ㄦ埛闇€�?閫氱敤娴侢��?0GB"锛屾祦閲忕被鍨嬩负閫氱敤娴侀�?,
          options: [
            { value: 'general', label: '閫氱敤娴侢��? },
            { value: '瀹氬�?, label: '瀹氬悜娴侢��? },
            { value: '涓撳�?, label: '涓撳睘娴侢��? },
          ],
        },
        {
          key: 'flowAmount',
          label: '璧勬簮閲?,
          value: '20',
          type: 'number',
          highlight: true,
          tag: 'ai',
          tagReason: '渚濇嵁鐢ㄦ埛闇€姹傦細閫氱敤娴侀�?0GB',
        },
        {
          key: 'flowUnit',
          label: '娴侀噺鍗曚綅',
          value: 'GB',
          type: 'select',
          tag: 'ai',
          tagReason: '渚濇嵁鐢ㄦ埛闇€姹備互GB涓哄崟浣嶈�?,
          options: [
            { value: 'GB', label: 'GB' },
            { value: 'MB', label: 'MB' },
          ],
        },
        {
          key: 'flowOvercharge',
          label: '濂楀璁¤垂鏍囧噯',
          value: '0.29�?MB',
          type: 'text',
          tag: 'ai',
          tagReason: '渚濇嵁鐢ㄦ埛闇€姹傦細瓒呭�?.29�?MB鎸夐噺璁¤�?,
        },
        {
          key: 'flowCarryover',
          label: '鏄惁缁撹浆鍒版鏈?,
          value: 'no',
          type: 'select',
          tag: 'ai',
          tagReason: '鎸変笟鍔℃ā鏉块粯璁ゅ綋鏈堟祦閲忎笉缁撹浆',
          options: [
            { value: 'yes', label: '�? },
            { value: 'no', label: '�? },
          ],
        },
      ],
    },
    {
      key: 'voice-resource',
      title: '璇煶璧勬簮閰嶇�?,
      icon: 'fa-phone',
      fields: [
        {
          key: 'voiceType',
          label: '璧勬簮绫诲��?,
          value: 'local',
          type: 'select',
          tag: 'ai',
          tagReason: '渚濇嵁鐢ㄦ埛闇€�?鍥藉唴璇煶200鍒嗛�?锛岃祫婧愮被鍨嬩负鏈�湴璇煶',
          options: [
            { value: 'local', label: '鏈湴璇煶' },
            { value: 'longdistance', label: '闢�块€旇�? },
          ],
        },
        {
          key: 'voiceAmount',
          label: '璧勬簮閲?,
          value: '200',
          type: 'number',
          unit: '鍒嗛�?,
          tag: 'ai',
          tagReason: '渚濇嵁鐢ㄦ埛闇€姹傦細鍥藉唴璇�?00鍒嗛�?,
        },
        {
          key: 'voiceOvercharge',
          label: '濂楀璁¤垂璐圭巼',
          value: '0.15�?鍒嗛�?,
          type: 'text',
          tag: 'ai',
          tagReason: '渚濇嵁鐢ㄦ埛闇€姹傦細濂楢�璇煶0.15�?鍒嗛�?,
        },
      ],
    },
    {
      key: 'sms-resource',
      title: '鐭俊璧勬簮閰嶇�?,
      icon: 'fa-comment-dots',
      fields: [
        {
          key: 'smsAmount',
          label: '璧勬簮閲?,
          value: '30',
          type: 'number',
          unit: '�?,
          tag: 'ai',
          tagReason: '渚濇嵁鐢ㄦ埛闇€姹傦細鐭俊30�?,
        },
        {
          key: 'smsOvercharge',
          label: '濂楀璁¤垂璐圭巼',
          value: '0.1�?�?,
          type: 'text',
          tag: 'ai',
          tagReason: '渚濇嵁鐢ㄦ埛闇€姹傦細濂楢�鐭俊0.1�?�?,
        },
      ],
    },
    {
      key: 'rights-resource',
      title: '鏉冪泭璧勬簮閰嶇�?,
      icon: 'fa-gift',
      fields: [
        {
          key: 'rightsTable',
          label: '鏉冪泭鍒楄��?,
          type: 'table',
          tag: 'ai',
          tagReason: '渚濇嵁鐢ㄦ埛闇€姹傚�?鐢熸棩鐗规潈銆佹祦閲忕粨�?锛屽苟鍙寜妗ｄ綅琛ュ厖瑙嗛浼氬憳鏈堝�?瀛ｅ崱绛夋潈�?,
          columns: [
            {
              key: 'rightName',
              label: '鏉冪泭鍚嶇�?,
              type: 'text',
              placeholder: '璇疯緭鍏ユ潈鐩婂悕绉?,
            },
            {
              key: 'obtainType',
              label: '棰嗗彇鏂瑰紡',
              type: 'select',
              options: [
                { value: 'manual', label: '鎵嬪姩棰嗗彇' },
                { value: 'auto', label: '鑷姩鍙戞斁' },
              ],
            },
          ],
          rows: [
            { rightName: '鐢熸棩鐗规潈', obtainType: 'auto' },
            { rightName: '娴侀噺缁撹浆', obtainType: 'auto' },
          ],
        },
      ],
    },
  ],

  // 钢�ラ攢璧勬�?Tab
  marketingSections: [
    {
      key: 'monthly',
      title: '鏈堢�?,
      icon: 'fa-yen-sign',
      fields: [
        {
          key: 'chargeType',
          label: '鏢�惰垂鏂瑰�?,
          value: 'monthly',
          type: 'select',
          tag: 'ai',
          tagReason: '渚濇嵁鐢ㄦ埛闇€�?濂楅鍥哄畾�?9�?�?锛屾敹璐规柟寮忎负鏈堜粯',
          options: [
            { value: 'monthly', label: '鏈堜�? },
            { value: 'quarterly', label: '瀛ｄ�? },
            { value: 'yearly', label: '骞翠�? },
          ],
        },
        { key: 'monthlyFee', label: '濂楅鍥哄畾�?, value: '59', type: 'number', unit: '�?, tag: 'ai', tagReason: '渚濇嵁鐢ㄦ埛闇€姹傦細濂楅鍥哄畾璐?9�?�? },
        {
          key: 'feeSubject',
          label: '鏢�惰垂绉戠�?,
          value: '001',
          type: 'select',
          tag: 'ai',
          tagReason: '鎸変笟鍔℃ā鏉垮綊涓洪€氳璐圭�?,
          options: [
            { value: '001', label: '閫氳璐? },
            { value: '002', label: '澧炲€间笟鍔¤�? },
            { value: '003', label: '浠ｆ敹璐? },
          ],
        },
        { key: 'taxRate', label: '绋庣�?, value: '6', type: 'number', unit: '%', tag: 'ai', tagReason: '鎸変笟鍔℃ā鏉块噰�?%绋庣�? },
      ],
    },
    {
      key: 'discount',
      title: '璐﹀姟浼樻儬',
      icon: 'fa-tags',
      fields: [
        {
          key: 'discountCondition',
          label: '浼樻儬鏉′欢',
          value: '',
          type: 'text',
          tag: 'ai',
          tagReason: '鎸変笟鍔℃ā鏉胯鍟嗗搧鏆傛棤璐﹀姟浼樻儬',
        },
        {
          key: 'discountType',
          label: '浼樻儬绫诲��?,
          value: 'cash',
          type: 'select',
          tag: 'ai',
          tagReason: '鎸変笟鍔℃ā鏉块粯璁ゆ棤浼樻儬绫诲�?,
          options: [
            { value: 'cash', label: '鐜伴噾鐩村噺' },
            { value: 'coupon', label: '浼樻儬鍒? },
            { value: 'gift', label: '璧犲�? },
          ],
        },
        { key: 'discountAmount', label: '浼樻儬璐圭敤', value: '', type: 'number', unit: '�?, tag: 'ai', tagReason: '鎸変笟鍔℃ā鏉挎殏鏃犱紭鎯犺垂�? },
      ],
    },
    {
      key: 'min-consume',
      title: '淇濆�?,
      icon: 'fa-arrow-down-wide-short',
      fields: [
        {
          key: 'minConsumeType',
          label: '淇濆簳鏂瑰紡',
          value: 'none',
          type: 'select',
          tag: 'ai',
          tagReason: '鎸変笟鍔℃ā鏉胯鍟嗗搧鏃犱繚�?,
          options: [
            { value: 'none', label: '鏃犱繚搴? },
            { value: 'monthly', label: '鏈堜繚搴? },
            { value: 'total', label: '鎬婚淇濆簳' },
          ],
        },
      ],
    },
  ],

  // 閿€鍞鍒?Tab
  salesSections: [
    {
      key: 'sales-rule',
      title: '閿€鍞鍒?,
      icon: 'fa-shield-halved',
      fields: [
        {
          key: 'channelType',
          label: '娓犻亾绫诲��?,
          value: 'offline',
          type: 'checkbox-group',
          tag: 'ai',
          tagReason: '渚濇嵁涓氬姟妯℃澘榛樿鍚嚎涓嬩笌绾夸笂娓犻亾',
          options: [
            { value: 'offline', label: '绾夸笅钀ヤ笟�? },
            { value: 'online', label: '涓浗绉诲姩鍦ㄧ嚎娓犻亾' },
            { value: 'local-online', label: '鐪佺Щ鍔ㄧ嚎涓婃笭�? },
            { value: 'cmcc-online', label: '涓浗绉诲姩绾夸笂娓犻亾' },
            { value: 'tv', label: '鐢佃钀ヤ笟�? },
            { value: 'accelerator', label: '鍔犻€熷钩鍙? },
            { value: 'points', label: '绉垎鍟嗗煄' },
            { value: 'meter', label: '涓€绾х數�? },
          ],
        },
        {
          key: 'applicableArea',
          label: '閫傜敤鍦板尯',
          value: 'sc',
          type: 'checkbox-group',
          tag: 'ai',
          tagReason: '渚濇嵁涓氬姟妯℃澘榛樿闈㈠悜鍏ㄧ渷鑼冨�?,
          options: [
            { value: 'sc', label: '鍥涘窛鍏ㄧ渷' },
            { value: 'cd', label: '鎴愰�? },
            { value: 'my', label: '缁甸�? },
            { value: 'dy', label: '寰烽�? },
            { value: 'nc', label: '鍗楀�? },
            { value: 'yb', label: '瀹滃�? },
            { value: 'lz', label: '娉�6�0�? },
            { value: 'ls', label: '涔愬�? },
          ],
        },
        {
          key: 'otherFee',
          label: '鍏朵粬璐圭敤',
          value: '0',
          type: 'text',
          tag: 'ai',
          tagReason: '鎸変笟鍔℃ā鏉挎湰鍟嗗搧鏃犲叾浠栬垂鐢?,
        },
        {
          key: 'breachLiability',
          label: '杩濈害璐ｄ换',
          value: 'none',
          type: 'select',
          tag: 'ai',
          tagReason: '鎸変笟鍔℃ā鏉挎湰鍟嗗搧鏃犺繚绾﹁矗浠荤害�?,
          options: [
            { value: 'none', label: '�? },
            { value: 'compensate', label: '鎸夌害��氳ˉ鍋块�? },
          ],
        },
        {
          key: 'renewMethod',
          label: '缁鏂瑰紡',
          value: 'auto',
          type: 'select',
          tag: 'ai',
          tagReason: '渚濇嵁鐢ㄦ埛闇€�?鏢�寔鏈堟湯鑷姩缁�?锛岀画璁㈡柟寮忎负鑷姩缁�?,
          options: [
            { value: 'auto', label: '鑷姩缁' },
            { value: 'manual', label: '鎵嬪姩缁' },
            { value: 'no', label: '涓嶇画璁? },
          ],
        },
      ],
    },
    {
      key: 'pre-limit',
      title: '鍓嶉」闄愬埗',
      icon: 'fa-circle-arrow-left',
      fields: [
        {
          key: 'preLimitRelation',
          label: '闄愬埗闂村叧�?,
          value: 'and',
          type: 'select',
          tag: 'ai',
          tagReason: '渚濇嵁鐢ㄦ埛闇€姹傚椤硅祫鏍兼潯浠堕渶鍚屾椂�6�2¤冻锛岄噰�?�?鍏崇�?,
          options: [
            { value: 'and', label: '涓旓紙鍏ㄩ儴婊¤冻�? },
            { value: 'or', label: '鎴栵紙浠讳竴婊¤冻�? },
          ],
        },
        {
          key: 'preLimitTable',
          label: '闄愬埗鍒楄��?,
          type: 'table',
          tag: 'ai',
          tagReason: '渚濇嵁鐢ㄦ埛闇€姹傦細浠呴檺鍥涘窛鍦ㄦ牎澶у鐢熷姙鐞嗐€侢�渶涓婁紶瀛︾敓璇佽�?,
          columns: [
            { key: 'label', label: '鏍囩�?, type: 'select', options: [
              { value: 'age', label: '骞撮�? },
              { value: 'globalLevel', label: '鍏ㄧ悆閫氱瓑�? },
              { value: 'baseMonthlyFee', label: '鍩虹濂楅鏈堣�? },
              { value: 'targetGroup', label: '鐩爣��㈢�? },
              { value: 'studentOnly', label: '闈㈠悜鍦ㄦ牎澶у�? },
              { value: 'studentCert', label: '闇€涓婁紶��︾敓璇佽�? },
              { value: 'realName', label: '浠呴檺鏈汉瀹炲悕鍔炵悊' },
              { value: 'limitToSelf', label: '浠呴檺鏈汉鍔炵�? },
            ] },
            { key: 'ruleType', label: '瑙勫垯绫诲��?, type: 'select', options: [
              { value: 'gt', label: '�? },
              { value: 'lt', label: '�? },
              { value: 'ge', label: '�? },
              { value: 'le', label: '�? },
              { value: 'eq', label: '�? },
            ] },
            { key: 'limitValue', label: '闄愬畾鍊?, type: 'text', placeholder: '璇疯緭鍏ラ檺瀹氬�? },
            { key: 'limitType', label: '闄愬畾绫诲��?, type: 'select', options: [
              { value: 'allow', label: '鍏佽鍔炵悊' },
              { value: 'disallow', label: '涓嶅厑璁稿姙�? },
            ] },
            { key: 'tipMsg', label: '鎻愮ず璇?, type: 'auto', placeholder: '鏍规嵁鏉′欢鑷姩鐢熸垚' },
          ],
          rows: [
            { label: 'targetGroup', ruleType: 'eq', limitValue: '鍥涘窛鍦ㄦ牎澶у�?, limitType: 'allow', tipMsg: '鐩爣��㈢兢涓哄洓宸濆湪鏍″ぇ瀛︾敓锛屽厑璁�6�0姙鐞? },
            { label: 'studentCert', ruleType: 'eq', limitValue: '闇€涓婁紶��︾敓璇佽�?, limitType: 'allow', tipMsg: '闈㈠悜鍥涘窛鍦ㄦ牎澶у鐢燂紝闇€瀛︾敓璇佽璇佸悗鍔炵悊' },
          ],
        },
      ],
    },
    {
      key: 'post-limit',
      title: '鍚庨」闄愬埗',
      icon: 'fa-circle-arrow-right',
      fields: [
        {
          key: 'postLimitTable',
          label: '鍚庨」闄愬埗',
          type: 'table',
          tag: 'ai',
          tagReason: '鎸変笟鍔℃ā鏉夸负瀛︾敓涓撲韩鍗¤缃€€璁笌闄嶆��闄愬埗',
          columns: [
            { key: 'ruleType', label: '瑙勫垯绫诲��?, type: 'select', options: [
              { value: 'product', label: '鍟嗗搧闄愬埗' },
              { value: 'business', label: '涓氬姟闄愬埗' },
            ] },
            { key: 'bizName', label: '涓氬姟鍚嶇�?, type: 'select', dynamicOptions: true, options: {
              product: [
                { value: 'downgrade', label: '闄嶆�? },
                { value: 'unsubscribe', label: '閫€�? },
              ],
              business: [
                { value: 'suspend', label: '鍋滄溢�淇濆�? },
                { value: 'cancel', label: '閿€�? },
                { value: 'mnp', label: '鎼哄彿杞綉' },
                { value: 'transfer', label: '杩囨�? },
              ],
            } },
            { key: 'limitType', label: '闄愬埗绫诲��?, type: 'select', options: [
              { value: 'disallow', label: '涓嶅厑璁? },
              { value: 'allow', label: '鍏佽�? },
            ] },
            { key: 'limitTime', label: '闄愬埗鏃堕棿', type: 'select', options: [
              { value: '12', label: '12涓湢�' },
              { value: '24', label: '24涓湢�' },
              { value: '36', label: '36涓湢�' },
              { value: '48', label: '48涓湢�' },
            ] },
          ],
          rows: [
            { ruleType: 'product', bizName: 'downgrade', limitType: 'disallow', limitTime: '12' },
            { ruleType: 'product', bizName: 'unsubscribe', limitType: 'disallow', limitTime: '12' },
          ],
        },
      ],
    },
  ],

  // 淇℃伅棰勮 Tab
  previewSections: [
    {
      key: 'user-facing',
      title: '鐃6�9��埛鍙楃悊淇℃�?,
      icon: 'fa-user',
      description: '鐃6�9��埛鍦ㄥ姙鐞嗚繃绋嬩腑鐪嬪埌鐨勫叧閿俊鎭?,
      items: [
        { label: '鍟嗗搧鍚嶇�?, value: '鐣呬韩鏍″洯�?9鍏冨椁?, highlight: true },
        { label: '鏈堣�?, value: '59�?�? },
        { label: '鍖呭惈娴侢��?, value: '20GB閫氱敤娴侢��?, highlight: true },
        { label: '鍖呭惈璇煶', value: '200鍒嗛挓鍥藉唴璇�? },
        { label: '鍖呭惈鐭俊', value: '30�? },
        { label: '闄愬畾鏉′欢', value: '浠呴檺鍥涘窛鍦ㄦ牎澶у鐢燂紝闇€瀛︾敓璇佽�?, highlight: true },
        { label: '濂楀娴侢�噺璐?, value: '0.29�?MB' },
        { label: '濂楀璇煶�?, value: '0.15�?鍒嗛�? },
        { label: '鐢熸晥鏂瑰紡', value: '绔嬪嵆鐢熸晥' },
        { label: '閫€璁㈡柟寮?, value: '绔嬪嵆澶辨晥' },
        { label: '浣跨敤鑼冨洿', value: '鍥涘窛鐪佽寖鍥村�? },
      ],
    },
    {
      key: 'free-fill-preview',
      title: '鍏嶅～鍗?,
      icon: 'fa-file-alt',
      description: '钢�ヤ笟鍛樺彈鐞嗘椂鍏嶅～鐨勮嚜鍔ㄥ甫鍑轰俊�?,
      items: [
        { label: '璧勮垂鍚嶇�?, value: '鐣呬韩鏍″洯�?9鍏冨椁? },
        { label: '濂楅鏈堣垂', value: '59�?�? },
        { label: '鍖呭惈璧勬簮', value: '20G娴侀�?200鍒嗛挓璇煶+30鏉＄煭淇?, highlight: true },
        { label: '瓒呭鏀惰垂鏍囧�?, value: '娴侀�?.29�?MB銆佽闊?.15�?鍒嗛�? },
        { label: '闄愬畾鏉′欢', value: '浠呴檺鍥涘窛鍦ㄦ牎澶у鐢熴€侢�渶��︾敓璇佽�? },
        { label: '鍏朵粬鏉冪泭', value: '鐢熸棩鐗规潈銆佹祦閲忕粨�?, highlight: true },
        { label: '鏢�惰垂鏂瑰�?, value: '鏈堜�? },
        { label: '濂楅鍥哄畾�?, value: '59.00�? },
        { label: '绋庣�?, value: '6%' },
        { label: '缁鏂瑰紡', value: '鑷姩缁' },
      ],
    },
    {
      key: 'sms-preview',
      title: '鐭俊妯℃澘棰勮�?,
      icon: 'fa-comment-sms',
      description: '鍚勮Е鐐��笅鍙戠殑鐭俊鍐呭棰勮',
      items: [
        { label: '鍔炵悊鎴愬姛鐭�?, value: '鎭枩鎮ㄦ垚鍔熷姙鐞嗙晠浜牎鍥崱59鍏冨椁愶紝鏈堣�?9鍏冿紝鍚?0G娴侀噺銆?00鍒嗛挓璇煶�?0鏉＄煭淇★紝鍚鐢熶笓浜潈鐩婏紝鎰熻阿鎮ㄧ殑鏢�寔�? },
        { label: '棰勭害鐢熸晥鐭�?, value: '鎮ㄧ殑鐣呬韩鏍��洯�?9鍏冨椁愬凡鐢熸晥锛屾湀�?9鍏冿紝鐢熸晥褰撴湢�璧蜂韩鍙楢�椁愭潈鐩�6�0�? },
        { label: '鍓嶅彴浜岢�‘鐭俊', value: '灏婃暚鐨勫鎴凤紝鎮ㄦ鍦ㄥ姙鐞嗙晠浜牎鍥崱59鍏冨椁愶紝鏈堣�?9鍏冿紝鏄惁纭鍔炵悊�? },
        { label: '閫€璁㈢煭淇?, value: '鎮ㄥソ锛屾偍鐨勭晠浜牎鍥�?9鍏冨椁愬凡閫€璁紝濡傛湁鐤戦棶璇疯嚧�?0086�? },
      ],
    },
  ],
}

  // ===== 闇€姹傝鏍间功鍐呭鏁寸悊锛氬悎骞朵笖�?5G-A濂楅�?99�?濉�?=====
  ;(function(restructure){
    var cfg = restructure.launchProductConfig;
    if (!cfg) return;
    var R = function(){ return { tag: 'ai', tagReason: '鍙傝€冪浉浼间骇�? 900102308 5G-A濂楅�?99�? }; };
    var P = function(){ return { tag: 'parsed', tagReason: '鍘熷闇€姹傛彁�? }; };
    var A = function(){ return { tag: 'ai', tagReason: '榛�6�0鍊? }; };
    var selOpts = function(){ return function(vals){ return vals.map(function(v){ return { value: v, label: v }; }); }; }();

    // ---- 浜у搧澶撮儴锛堥厤缃伐浣滃彴椤堕儴鏂规�?/ 淇℃伅棰勮 APP 鍚嶇О�?---
    cfg.productInfo.prodPrcName = '5G-A濂楅�?99�?;
    cfg.productInfo.templateLabel = '5G-A涓昏祫璐?;

    // ---- 鍩虹淇℃伅妯��潡锛氫粎淇濈暀 8 涓瓧娈碉紙璧勮垂鍚嶇�?璁㈣喘鐢熸晥鏂瑰�?閫€璁㈢敓鏁堟柟�?閿€鍞紑濮嬫棩�?閿€鍞粓姝㈡棩�?鍙戝竷娓犻亾/鍙戝竷鍦板競/鍙戝竷鍦板競鏄庣粏锛?---
    cfg.baseInfo = {
      prodPrcName: Object.assign({ value: '5G-A濂楅�?99�?, required: true, label: '璧勮垂鍚嶇�? }, P()),
      orderEffRule: Object.assign({ value: '绔嬪嵆鐢熸晥', required: true, label: '璁㈣喘鐢熸晥鏂瑰�?, type: 'select', options: selOpts(['绔嬪嵆鐢熸晥', '棰勭害鐢熸晥']) }, R()),
      cancelEffRule: Object.assign({ value: '棰勭害鐢熸晥', required: true, label: '閫€璁㈢敓鏁堟柟�?, type: 'select', options: selOpts(['棰勭害鐢熸晥', '绔嬪嵆鐢熸晥']) }, R()),
      saleStartDate: Object.assign({ value: '2026-09-18', required: true, label: '閿€鍞紑濮嬫棩�?, type: 'text' }, A()),
      saleEndDate: Object.assign({ value: '2099-12-31', required: true, label: '閿€鍞粓姝㈡棩�?, type: 'text' }, A()),
      pubChannel: Object.assign({ value: '钢�ヤ笟鍘呫€丄PP', required: true, label: '鍙戝竷娓犻亾' }, P()),
      pubCity: Object.assign({ value: '鍏ㄧ�?, required: true, label: '鍙戝竷鍦板競', type: 'select', options: selOpts(['鍏ㄧ�?, '闈炲叏鐪?]) }, R()),
      pubCityDetail: Object.assign({ value: '22涓湴甯?, required: true, label: '鍙戝竷鍦板競鏄庣�? }, R()),
    };

    // ---- 璧勬簮閰嶇疆妯��潡锛氫粎淇濈暀 璇�?娴侀�?鐭俊锛屾寜 璇煶鈫掓祦閲忊啋鐭俊 鎺掑�?----
    var resByKey = {};
    cfg.resourceSections.forEach(function(s){ resByKey[s.key] = s; });
    cfg.resourceSections = [];
    function addRes(key, title, fields){ var s = resByKey[key] || { key: key, icon: '' }; s.title = title; s.fields = fields; cfg.resourceSections.push(s); }
    // 璇煶璧勬簮
    addRes('voice-resource', '璇煶璧勬簮', [
      Object.assign({ key: 'voiceType', value: '鍥藉唴涓诲彨', label: '璧勬簮绫诲��?, type: 'select', options: selOpts(['鍥藉唴涓诲彨', '鏈湴涓诲彨', '闢�块€斾富�?]) }, R()),
      Object.assign({ key: 'voiceAmount', value: '1000', label: '璧勬簮閲?, type: 'number', unit: '鍒嗛�? }, R()),
      Object.assign({ key: 'voiceFirstMonth', value: '鍏ㄩ璧犻€佹祦�?, label: '棣栨湢�璧勬簮璧犻€佹柟�?, type: 'select', options: selOpts(['鍏ㄩ璧犻€佹祦�?, '鎸夊ぉ鎶樼畻']) }, R()),
      Object.assign({ key: 'voiceOvercharge', value: '0.15', label: '濂楀璁¤垂璐圭巼', type: 'text' }, R()),
      Object.assign({ key: 'voiceRemind', value: '�?, label: '鏄惁鎻愰啋', type: 'select', options: selOpts(['�?, '�?]) }, R()),
    ]);
    // 娴侀噺璧勬簮
    addRes('flow-resource', '娴侀噺璧勬簮', [
      Object.assign({ key: 'flowType', value: '鍥藉�?閫氱�?鍗曟湢�', label: '娴侀噺绫诲��?, type: 'select', options: selOpts(['鍥藉�?閫氱�?鍗曟湢�', '鍥藉�?閫氱�?鍗婂勾鍖?, '瀹氬悜娴侢��?]) }, R()),
      Object.assign({ key: 'flowAmount', value: '120', label: '璧勬簮閲?, type: 'number', unit: 'GB', highlight: true }, R()),
      Object.assign({ key: 'flowUnit', value: 'GB', label: '娴侀噺鍗曚綅', type: 'select', options: selOpts(['GB', 'MB']) }, R()),
      Object.assign({ key: 'flowFirstMonth', value: '鍏ㄩ璧犻€佹祦�?, label: '棣栨湢�璧勬簮璧犻€佹柟�?, type: 'select', options: selOpts(['鍏ㄩ璧犻€佹祦�?, '鎸夊ぉ鎶樼畻']) }, R()),
      Object.assign({ key: 'flowCarryover', value: '�?, label: '鏄惁缁撹浆鍒版鏈?, type: 'select', options: selOpts(['�?, '�?]) }, R()),
      Object.assign({ key: 'flowOvercharge', value: '3�?GB', label: '濂楀璁¤垂鏍囧噯', type: 'text' }, R()),
      Object.assign({ key: 'flowRemind', value: '鎻愰�?, label: '娴侀噺鎻愰啋', type: 'select', options: selOpts(['鎻愰�?, '涓嶆彁閱?]) }, R()),
    ]);
    // 鐭俊璧勬簮
    addRes('sms-resource', '鐭俊璧勬簮', [
      Object.assign({ key: 'smsType', value: '鐭�?, label: '璧勬簮绫诲��?, type: 'text' }, R()),
      Object.assign({ key: 'smsOvercharge', value: '0.1', label: '濂楀璁¤垂璐圭巼', type: 'text' }, R()),
      Object.assign({ key: 'smsRemind', value: '�?, label: '鏄惁鎻愰啋', type: 'select', options: selOpts(['�?, '�?]) }, R()),
    ]);

    // ---- 钢�ラ攢璧勬簮妯″潡锛氫粎淇濈暀 鏈堢锛堝幓鎺夎处鍔′紭�?淇濆簳锛?---
    cfg.marketingSections = [];
    cfg.marketingSections.push({
      key: 'monthly',
      title: '鏈堢�?,
      icon: 'fa-yen-sign',
      fields: [
        Object.assign({ key: 'chargeType', value: '棣栨湢�鎸夊ぉ鎶樼畻涓€娆℃€ф敹鍙栨鏈堟寜鏈堟敹鍙?, label: '鏢�惰垂鏂瑰�?, type: 'select', options: selOpts(['棣栨湢�鎸夊ぉ鎶樼畻涓€娆℃€ф敹鍙栨鏈堟寜鏈堟敹鍙?, '鎸夋湢�鏢�跺彇', '鎸夊鏀跺彇']) }, R()),
        Object.assign({ key: 'monthlyFee', value: '199', label: '濂楅鍥哄畾�?, type: 'number', unit: '�? }, R()),
        Object.assign({ key: 'feeSubject', value: '鏈堢璐?, label: '鏢�惰垂绉戠�?, type: 'select', options: selOpts(['鏈堢璐?, '閫氳璐?, '澧炲€间笟鍔¤�?]) }, R()),
        Object.assign({ key: 'taxRate', value: '浣跨敤榛樿绋庣�?, label: '绋庣�?, type: 'select', options: selOpts(['浣跨敤榛樿绋庣�?, '6%', '9%']) }, R()),
        Object.assign({ key: 'monthValidity', value: '闢�挎湡鏈夋�?, label: '鏈堢鏈夋晥�?, type: 'select', options: selOpts(['闢�挎湡鏈夋�?, '24涓湢�']) }, R()),
      ]
    });

    // ---- 閿€鍞鍒欐ā鍧楋細浠呬繚鐣?鍓嶉」闄愬埗 + 鍚庨」闄愬埗锛堝幓鎺?sales-rule 妯��潡�?---
    cfg.salesSections = cfg.salesSections.filter(function(s){ return s.key === 'pre-limit' || s.key === 'post-limit'; });
    cfg.salesSections.forEach(function(s){ if (s.title) s.title = s.title; });
    // 鍓嶉」闄愬埗锛氬熢�纭€濂楅鏈堣垂闂ㄦ�?59�?�?199鍏冿紙涓?5G-A濂楅�?99�?淇濇寔涓€鑷达級
    cfg.salesSections.forEach(function(s){
      if (s.fields) s.fields.forEach(function(f){
        if (f.rows) f.rows.forEach(function(r){
          if (r.label === 'baseMonthlyFee') {
            r.limitValue = '199';
            r.tipMsg = String(r.tipMsg || '').replace('59', '199');
          }
        });
      });
    });

    // ---- 淇℃伅棰勮 Tab锛氬厤濉崟鍐呭锛堜笌閰嶇疆淇℃伅鍚屾锛?---
    cfg.previewSections = (cfg.previewSections || []).map(function(s){
      if (s.key === 'free-fill-preview') {
        s.items = [
          { label: '濂楅鏈堣垂', value: '199', highlight: true },
          { label: '鍖呭惈璧勬簮', value: '鍥藉唴閫氱敤娴侀�?20GB銆佸浗鍐呴€氳瘽1000鍒嗛�?, highlight: true },
          { label: '瓒呭鏀惰垂鏍囧�?, value: '濂楀娴侢�噺鍓?00MB�?.03�?MB鏢�惰垂锛岃揪鍒?00MB�?鍏冿級鏃堕澶栬禒閫?24MB锛堝�?�?1GB锛夛紝娴侢�噺瓒呰繃1GB鎸夋瘡瓒呭嚭1GB 3鍏冩敹璐癸紱濂楀璇煶鍥藉�?.15�?鍒嗛挓锛涘澶栫煭褰╢��?.1�?鏉★紱濂楢�鍗槦鎷ㄦ墦鍥藉唴鐢佃�?�?鍒嗛挓锛屾嫧鎵撳浗闄呴暱�?0�?鍒嗛挓锛屾嫧鎵撳浗闄呭崼鏄熺數璇?0�?鍒嗛挓锛屽浗鍐呮帴鍚?�?鍒嗛挓锛屽彂閫佺煭淇?�?�? },
          { label: '闄愬畾鏉′欢', value: '鍥藉唴閫氱敤娴侀噺浠呴檺涓浗鍐呭湴锛堜笉鍚腐婢冲彴鍜屽浗闄咃級浣跨敤锛屼笉鍚玏LAN锛涘椁愬唴璇煶浠呴檺涓浗鍐呭湴浣跨敤锛?G-A缃戠粶鏉冪泭闇€缁堢鏀寔涓斿�?G-A缃戠粶瑕嗙洊鑹ソ鐜涓嬩韩鍙? },
        ];
      } else if (s.key === 'sms-preview') {
        s.items = [
          { label: '鍔炵悊鎴愬姛鐭�?, value: '鎭枩鎮ㄦ垚鍔熷姙鐞?G-A濂楅�?99鍏冿紝濂楅鏈堣�?99鍏冿紝鍚?20GB鍥藉唴閫氱敤娴侀噺鍜?000鍒嗛挓鍥藉唴閫氳瘽锛屾劅璋㈡偍鐨勬敮鎸侊�? },
          { label: '棰勭害鐢熸晥鐭�?, value: '鎮ㄧ�?G-A濂楅�?99鍏冨凡棰勭害鐢熸晥锛屾湀�?99鍏冿紝鐢熸晥褰撴湢�璧蜂韩鍙楢�椁愭潈鐩�6�0�? },
          { label: '鍓嶅彴浜岢�‘鐭俊', value: '灏婃暚鐨勫鎴凤紝鎮ㄦ鍦ㄥ姙鐞?G-A濂楅�?99鍏冿紝鏈堣垂199鍏冿紝鏄惁纭鍔炵悊�? },
          { label: '閫€璁㈢煭淇?, value: '鎮ㄥソ锛屾偍�?G-A濂楅�?99鍏冨凡閫€璁紝濡傛湁鐤戦棶璇疯嚧鐢?0086�? },
        ];
      }
      return s;
    });
  })({ launchProductConfig: launchProductConfig });
  global.__CFG = {
    launchProductConfig: typeof launchProductConfig !== 'undefined' ? launchProductConfig : null,
    testReport: typeof testReport !== 'undefined' ? testReport : null,
    filing: typeof filing !== 'undefined' ? filing : null,
    salesChannel: typeof salesChannel !== 'undefined' ? salesChannel : null,
    campusProductConfig: typeof campusProductConfig !== 'undefined' ? campusProductConfig : null,
  };
})(window);
(function(global){
  /**
   * @deprecated 鍚勬姤鍛婇��绛惧凡鏢�逛负�?ConfigWorkbench 鍐呯�?productInstance/demandView/schemeView
   * 绛夊搷搴斿紡鏁版嵁婧愬姩鎬佹淳鐢燂紙�?setup 灏鹃儴锛夛紝闅忓晢鍝佸疄渚嬫暟鎹仈鍔ㄣ€傛湰闈欐€佹紨绢�哄璞��粎浣滃巻鍙叉暟鎹繚鐣欙紝
   * 涓嶅啢�琚伐浣滃彴椤电娑堣垂锛屽悗缁凯浠ｅ彲鏁翠綋鍒犻櫎銆?
   */
  global.__VIEWS = {
    demand: {
      no: 'XQ-20260918-001',
      name: '5G-A濂楅�?99�?,
      applicant: '鐜嬫�?,
      department: '浜ч攢鍝佸�?/ 浜у晢鍝佷腑蹇?,
      date: '2026-09-18',
      target: '闈㈠悜楂樻祦閲忎釜浜哄鎴凤紝涓绘墦5G-A缃戠粶鏋佽嚧閫熺巼涓庤秴澶ф祦閲忎綋楠?,
      require: '鎺ㄥ嚭鏈堣垂199�?G-A涓诲椁愶紝鍚浗鍐呴€氱敤娴侀�?20GB锛堝彲缁撹浆锛夈€佸浗鍐呴€氳瘽1000鍒嗛挓锛涢鏈堟寜澶╂姌绠楋紝娆℃湀璧锋寜鏈堟敹鍙栵紱鏀拺钢�ヤ笟�?APP娓犻亾閿€鍞紝瑕嗙洊鍏ㄧ渷22涓湴甯傘€?,
      effective: '2026-10-01',
      channels: ['钢�ヤ笟�?, 'APP']
    },
    scheme: {
      no: 'SCH-20260918',
      requireNo: 'XQ-20260918-001',
      version: 'V1.0',
      name: '5G-A濂楅�?99鍏冨姞杞芥柟�?,
      author: '浜у晢鍝佷腑蹇冿紙CRM_POS�?,
      status: '宸茬‘璁?,
      summary: [
        ['璧勮垂鍚嶇�?, '5G-A濂楅�?99�?],
        ['鏈堢�?, '199�?鏈堬紙棣栨湀鎸夊ぉ鎶樼畻锛屾鏈堣捣鎸夋湢�鏢�跺彇�?],
        ['鍖呭惈璧勬簮', '鍥藉唴閫氱敤娴侀�?20GB锛堝綋鏈堟湭鐃6�9��粨杞級銆佸浗鍐呴€氳瘽1000鍒嗛�?],
        ['濂楀鏍囧噯', '娴侀�?�?GB銆佽闊?.15�?鍒嗛挓銆佺煭�?.1�?�?],
        ['鐢熸晥鏂瑰紡', '璁㈣喘绔嬪嵆鐢熸�?/ 閫€璁㈤绾︾敓�?],
        ['鍙戝竷鑼冨洿', '鍏ㄧ�?2涓湴甯傦紝钢�ヤ笟鍘呫€丄PP娓犻�?]
      ],
      items: ['鍩虹淇℃伅', '鏈堢�?, '璇煶璧勬簮', '娴侀噺璧勬簮', '鐭俊璧勬簮', '鍓嶉」闄愬埗', '鍚庨」闄愬埗']
    },
    audit: {
      object: 'offer_id = 970946234',
      status: '閫氳�?,
      time: '2026-09-18',
      conclusion: '绋芥牳閫氳繃锛氶厤缃鍚堟湰娆¤惤鍦伴攢鍞�?鏂板閿€鍞搧970946234 瑙勫垯銆?,
      checks: [
        ['鍩虹淇℃伅瀹屾暣鎬?, '閫氳�?],
        ['璧勬簮閰嶇疆瀹屾暣鎬?, '閫氳�?],
        ['濂楀璧勮垂瀹屾暣鎬?, '閫氳�?],
        ['瀛楁鏍煎紡瑙勮寖鎬?, '閫氳�?],
        ['瀛楁鏋氫妇鍚堟硶鎬?, '閫氳�?],
        ['閰嶇疆椤瑰叧鑱斾竴鑷存€?, '閫氳�?],
        ['涓氬姟瑙勫垯瀹屾暣鎬?, '閫氳�?]
      ]
    },
    calibrate: {
      object: '970946234',
      status: '閫氳�?,
      time: '2026-09-18',
      conclusion: '璧勮垂鏍″噯閫氳繃锛氳璐归厤缃笌鏂规闇€姹備繚鎸佷竴鑷达紝鏈彂鐜板彔�?浜掓枼鍐茬獊�?,
      rows: [
        ['濂楅鍥哄畾�?, '199�?, '199�?�?, '涓€�?],
        ['鍥藉唴閫氱敤娴侀�?, '120GB', '120GB', '涓€�?],
        ['鍥藉唴閫氳瘽', '1000鍒嗛�?, '1000鍒嗛�?, '涓€�?],
        ['濂楀娴侢�噺鏍囧噯', '3�?GB', '3�?GB', '涓€�?],
        ['濂楀璇煶璐圭�?, '0.15�?鍒嗛�?, '0.15�?鍒嗛�?, '涓€�?],
        ['濂楀鐭俊璐圭�?, '0.1�?�?, '0.1�?�?, '涓€�?]
      ]
    },
    testRep: {
      no: 'TEST-REP-20260918-199',
      name: '5G-A濂楅�?99�?,
      total: 30,
      pass: 30,
      rate: '100%',
      conclusion: '寤鸿涓婄嚎',
      dims: [
        ['鍙楃悊楠岃瘉 (ACC)', '10', '10', '閫氳�?],
        ['璁¤垂楠岃�?(BILL)', '10', '10', '閫氳�?],
        ['瀹㈡湇楠岃瘉 (CUST)', '10', '10', '閫氳�?]
      ],
      cases: [
        ['ACC-001', '閿€鍞搧鍩虹鍑嗗叆瑙勫垯鏍￠�?, 'P0', '閫氳�?],
        ['ACC-004', '璁㈣喘鎿嶄綔鑳藉姏鏍￠獙', 'P0', '閫氳�?],
        ['ACC-011', '妯℃嫙璁㈣喘鎺ュ彛棰勬祴�?, 'P0', '閫氳�?],
        ['BILL-001', '鍩虹璧勮垂閲戦鍚堟硶鎬ф牎�?, 'P0', '閫氳�?],
        ['BILL-004', '璧勬簮鎵ｅ噺瑙勫垯鏍￠獙', 'P0', '閫氳�?],
        ['BILL-010', '璧勮垂鐢熸晥澶辨晥鑱斿姩鏍￠�?, 'P0', '閫氳�?],
        ['CUST-001', '瀹㈡湇浜у搧鍩虹瑙嗗浘��屾暣鎬?, 'P0', '閫氳�?],
        ['CUST-004', '浜у搧璧勮垂��瑰璇存槑璇濇湳鏍￠獙', 'P0', '閫氳�?],
        ['CUST-008', '瀵瑰灞曠ず淇℃伅鍚堣鏍￠�?, 'P0', '閫氳�?]
      ]
    },
    verify: {
      orderId: 'ORD20260918151923',
      offerInstId: 'OI20260918151923',
      status: '閫氳�?,
      time: '2026-09-18',
      scenes: [
        ['濂楅鏂拌 (S_O_TC)', '閫氳�?],
        ['鍓崱鍔犺 (S_ADD_CARD)', '閫氳�?],
        ['濂楅閫€璁?(S_U_TC)', '閫氳�?]
      ],
      points: [
        ['P_EFF_DATE 鐢熸晥鏃堕棿', '閫氳�?],
        ['P_EXP_DATE 澶辨晥鏃堕棿', '閫氳�?],
        ['P_STATUS 瀹炰緥鐘舵€?, '閫氳�?],
        ['P_ORD_CNT 璁㈣喘鏁伴噺', '閫氳�?]
      ]
    },
    approval: {
      no: 'SP-20260918-005',
      name: '5G-A濂楅�?99�?,
      status: '宸查€氳�?,
      nodes: [
        ['浜у搧缁忕悊��℃�?, '浜у搧缁忕�?, '宸查€氳�?, '瀹℃牳閫氳繃锛屽悓鎰忎笂�?, '2026-09-18 15:32:30'],
        ['璧勮垂涓荤瀹℃�?, '璧勮垂涓荤', '宸查€氳�?, '瀹℃牳閫氳繃锛屽悓鎰忎笂�?, '2026-09-18 15:32:30'],
        ['杩愯惢�瀹℃�?, '杩愯惢�涓撳�?, '宸查€氳�?, '瀹℃牳閫氳繃锛屽悓鎰忎笂�?, '2026-09-18 15:32:30'],
        ['涓婄嚎��℃�?, 'IT鏢�拺', '宸查€氳�?, '瀹℃牳閫氳繃锛屽悓鎰忎笂�?, '2026-09-18 15:32:30']
      ],
      note: '瀹℃壒宸查€氳繃锛岄攢鍞搧涓婃灦��屾�?鉁咃紙��℃壒閫氳繃鍗宠涓洪攢鍞搧宸茶嚜鍔ㄤ笂绾匡紝鏃犻渶浜屾纭锛夈€?
    },
    monitor: {
      single: {
        name: '5G-A濂楅�?99�?,
        status: '鍦ㄥ敭涓?,
        cards: [
          ['绱璁㈣喘�?, '3,842', '�?],
          ['鏈湢�鏂板�?, '1,206', '�?],
          ['鏈湢�閫€�?, '86', '�?],
          ['鍑€�?, '1,120', '�?],
          ['鍦ㄥ敭鏀跺叆', '76.4', '涓囧�?],
          ['璧勬簮鍒╃敤�?, '68%', '�?]
        ],
        warn: '褰撳墠鏃犲紓甯�6�0憡璀︼紱閫氱敤娴侢�噺璧勬簮鍒╃敤鐜囨帴杩戦槇鍊硷紝寤鸿鍏虫敞鍚庣画瓒嬪娍�?
      },
      total: {
        cards: [
          ['鍦ㄥ敭閿€鍞搧', '48', '�?],
          ['鏈湢�鏂板璁㈣喘', '2.4�?, '�?],
          ['鎬诲湪鍞敹�?, '1,820', '涓囧�?],
          ['鏁翠綋鍋ュ悍�?, '鑹�?, '�?]
        ],
        risks: [
          ['娴侀噺璧勬簮鍒╃敤鐜囧亸�?, '�?, '澶氭閿€鍞搧鍐呴€氱敤娴侢�噺鍖呴鍙戞斁杈冮泦�?],
          ['閮ㄥ垎鑰佸椁愰€€璁笂鍗?, '�?, '闇€鍏虫敞鍚?G-A绛夋柊濂楅鐨勮縼绉诲紩�?]
        ]
      }
    }
  };
})(window)

