const fs = require('fs');

const gbkDecoder = new TextDecoder('gbk');
let reverse = null;
function buildTable() {
  reverse = new Map();
  for (let lead = 0x81; lead <= 0xfe; lead++) {
    for (let trail = 0x40; trail <= 0xfe; trail++) {
      if (trail === 0x7f) continue;
      const ch = gbkDecoder.decode(Buffer.from([lead, trail]));
      if (ch === '\uFFFD' || ch.length !== 1) continue;
      if (!reverse.has(ch)) reverse.set(ch, Buffer.from([lead, trail]));
    }
  }
}
function encodeGBK(str) {
  if (!reverse) buildTable();
  const out = [];
  for (const ch of str) {
    const c = ch.codePointAt(0);
    if (c < 0x80) out.push(c);
    else {
      const b = reverse.get(ch);
      if (!b) throw new Error('no gbk map U+' + c.toString(16) + ' ' + ch);
      out.push(b[0], b[1]);
    }
  }
  return Buffer.from(out);
}

// rough commonness: more common chars first
const FREQ = '的一是不了在人有我他这个们中来上大为和国地到以说时要就出会可也你对生能而子那得于着下自之年过发后作里用道行所然家种事成方多经么去法学如都同现当没动面起看定天分还进好小部其些主样理心她本前开但因只从想实日军者意无力它与长把机十民第公此已工使情明性知全三又关点正业外将两高间由问很最重并物手应战向头文体政美相见被利什二等产或新己制身果加西斯月话合回特代内信表化老给世位次度门任常先海通教儿原东声提立及比员解水名真论处走义各入几口认条平系气题活尔更别打女变四神总何电数安少报才结反受目太量再感建务做接必场件计管期市直德资命山金指克许统区保至队形社便空决治展马科司五基眼书非则听白却界达光放强即像难且权思王象完设式色路记南品住告类求据程北边死张该交规万取拉格望觉术领共确传师观清今切院让识候带导争运笑飞风步改收根干造言联持组每济车亲极林服快办议往元英士证近失转夫令准布始怎呢存未远叫台单影具罗字爱击流备兵连调深商算质团集百需价花党华城石级整府离况亚请技际约示复病息究线似官火断精满支视消越器容照须九增研写称企八功吗包片史委乎查轻易早曾除农找装广显吧阿李标谈吃图念六引历首医局突专费号尽另周较注语仅考落青随选列武红响虽推势参希古众构房半节土投某案黑维革划敌致陈律足态护七兴派孩验责营星够章音跟志底站严巴例防族供效续施留讲型料终答紧黄绝奇察母京段依批群项故按河米围江织害斗双境客纪采举杀攻父苏密低朝友诉止细愿千值仍男钱破网热助倒育属坐帝限船脸职速刻乐否刚威毛状率甚独球般普怕弹校苦创假久错承印晚兰试股拿脑预谁益阳若哪微尼继送急血惊伤素药适波夜省初喜卫源食险待述陆习置居劳财环排福纳欢雷警获模充负云停木游龙树疑层冷洲冲射略范竟句室异激汉村哈策演简卡罪判担州静退既衣您宗积余痛检差富灵协角占配征修皮挥胜降阶审沉坚善妈刘读啊超免压银买皇养伊怀执副乱抗犯追帮宣佛岁航优怪香著田铁控税左右份穿艺背阵草脚概恶块顿敢守酒岛托央户烈洋哥索胡款靠评版宝座释景顾弟登货互付伯慢欧换闻危忙核暗姐介坏讨丽良序升监临亮露永呼味野架域沙掉括舰鱼杂误湾吉减编楚肯测败屋跑梦散温困剑渐封救贵枪缺楼县尚毫移娘朋画班智亦耳恩短掌恐遗固席松秘谢鲁遇康虑幸均销钟诗藏赶剧票损忽巨炮旧端探湖录叶春乡附吸予礼港雨呀板庭妇归睛饭额含顺输摇招婚脱补谓督途夹检粮漫烈惊'
  + '曾骑绍纷诉祖叹拼状缓男友';
const charRank = new Map();
for (let i = 0; i < FREQ.length; i++) charRank.set(FREQ[i], i);

function guessByte(b1, b2) {
  let best = null, bestRank = Infinity;
  for (let b3 = 0x80; b3 <= 0xbf; b3++) {
    const bytes = b1 === null ? Buffer.from([b3]) : b2 === null ? Buffer.from([b1, b3]) : Buffer.from([b1, b2, b3]);
    const ch = bytes.toString('utf8');
    if (ch === '\uFFFD' || ch.length !== 1) continue;
    const rank = charRank.has(ch) ? charRank.get(ch) : 10000;
    if (rank < bestRank) { bestRank = rank; best = b3; }
  }
  return best === null ? 0x80 : best;
}

function reconstructLine(line) {
  // quick test: does it contain mojibake-ish bytes?
  const bytes = encodeGBK(line);
  // Now decode bytes as utf8, repairing 0x3f placeholders.
  const out = [];
  let i = 0;
  let repaired = 0, unrepairable = 0;
  while (i < bytes.length) {
    const b = bytes[i];
    if (b < 0x80) {
      if (b === 0x3f) {
        // placeholder? look ahead: if next byte is a lead or another 3f -> might be lost byte
        // Heuristic: only treat 3f as placeholder when preceded by incomplete seq (handled below)
      }
      out.push(Buffer.from([b]));
      i++;
      continue;
    }
    // determine sequence length
    let need = 0;
    if ((b & 0xe0) === 0xc0) need = 1;
    else if ((b & 0xf0) === 0xe0) need = 2;
    else if ((b & 0xf8) === 0xf0) need = 3;
    else { out.push(Buffer.from([b])); i++; continue; }
    const seq = [b];
    let j = i + 1;
    while (seq.length <= need && j < bytes.length) {
      const nb = bytes[j];
      if (nb === 0x3f) {
        // lost byte
        if (seq.length === need) { break; }
        const b3 = guessByte(seq.length === 1 ? seq[0] : null, seq.length === 2 ? seq[1] : null);
        seq.push(b3); repaired++; j++;
        continue;
      }
      if ((nb & 0xc0) === 0x80) { seq.push(nb); j++; continue; }
      break;
    }
    if (seq.length === need + 1) {
      out.push(Buffer.from(seq));
      i = j;
    } else {
      // can't complete; emit partial and advance past consumed bytes
      out.push(Buffer.from(seq));
      i = j > i ? j : i + 1;
      unrepairable++;
    }
  }
  return { text: Buffer.concat(out).toString('utf8'), repaired, unrepairable };
}

if (require.main === module) {
  buildTable();
  const file = process.argv[2];
  const t = fs.readFileSync(file, 'utf8');
  const lines = t.split('\n');
  const idxs = (process.argv[3] || '').split(',').filter(Boolean).map(Number);
  for (const n of idxs) {
    const r = reconstructLine(lines[n - 1]);
    console.log(`L${n}: [rep=${r.repaired} unrep=${r.unrepairable}] ${JSON.stringify(r.text)}`);
  }
}
module.exports = { encodeGBK, buildTable, reconstructLine };
