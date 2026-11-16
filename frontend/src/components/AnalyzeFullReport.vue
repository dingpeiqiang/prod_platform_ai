<template>
  <div class="analyze-report">
    <!-- 引言 -->
    <p class="report-intro"><strong>【助手·产品运营助手】</strong></p>
    <blockquote>好的，已对「WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)」近30天运营数据完成多维分析，结论分四层讲，最后给根因定位。</blockquote>
    <p><strong>总判断</strong>：这是一起典型的 <strong>"竞争性新增萎缩"</strong> ——即我们的产品在高速配额上被竞品反超，导致年轻新客的转化效率显著下降。已有漫游用户基本稳定，说明存量基本盘没有垮，问题出在获客端。</p>

    <hr />

    <!-- 一、产品画像 -->
    <h2>一、产品画像：当前在售产品的完整快照</h2>
    <p>先确认当前分析对象的基本面——这是「WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)」作为在售产品的完整配置和运营状态：</p>

    <table>
      <tbody>
        <tr><td><strong>产品名称</strong></td><td>WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)</td></tr>
        <tr><td><strong>产品编码</strong></td><td>SP-HK-TW-30D</td></tr>
        <tr><td><strong>在售状态</strong></td><td>✅ 在售中（2026-05-01起售）</td></tr>
        <tr><td><strong>资费类型</strong></td><td>一次性（Data Only）</td></tr>
        <tr><td><strong>高速配额</strong></td><td>30GB（总量，香港.台湾共用）</td></tr>
        <tr><td><strong>有效期</strong></td><td>30天（自然天）</td></tr>
        <tr><td><strong>漫游地</strong></td><td>香港.台湾</td></tr>
        <tr><td><strong>卡类型/网络</strong></td><td>5G 数据卡</td></tr>
        <tr><td><strong>高速用尽后</strong></td><td>不限量限速（PCRF）</td></tr>
      </tbody>
    </table>

    <p><strong>累计销售</strong>：<strong>86,420户</strong>（其中30日套餐主力档）</p>
    <p><strong>销售渠道</strong>：线上线下全渠道（APP/网厅/10086/线下营业厅）</p>
    <p><strong>互斥产品</strong>：WeWalk 15日不限量、WeWalk 30日3GB/日高配</p>
    <p><strong>关联资源</strong>：香港.台湾漫游覆盖（CMCC-ROAM-HK-TW）</p>

    <p><strong>当前健康评分：<strong>72分</strong></strong>（较上周↓9分，跌破80分警戒线）</p>

    <table>
      <thead><tr><th>维度</th><th>得分</th><th>变化</th><th>权重</th></tr></thead>
      <tbody>
        <tr><td>新增转化</td><td>62</td><td>↓18</td><td>30%</td></tr>
        <tr><td>续约率</td><td>78</td><td>↓5</td><td>25%</td></tr>
        <tr><td>投诉率</td><td>85</td><td>↓2</td><td>20%</td></tr>
        <tr><td>竞品对标</td><td>68</td><td>↓12</td><td>25%</td></tr>
      </tbody>
    </table>

    <blockquote><strong>产品画像小结</strong>：这是一款30GB高速配额、30天有效期、香港.台湾双地漫游的一次性5G数据卡，累计销售86,420户。核心健康短板在 <strong>"新增转化"（62分）</strong> 和 <strong>"竞品对标"（68分）</strong> 两个维度，综合评分72分，已跌破80分警戒线。接下来我具体拆解这两个维度到底出了什么问题。</blockquote>

    <hr />

    <!-- 二、趋势层 -->
    <h2>二、趋势层：新增萎缩、存量稳定、退订未恶化</h2>
    <p>先看核心指标走势。这是近14天的数据：</p>

    <AnalyzeCharts type="trend" />

    <table>
      <thead><tr><th>日期</th><th>新增订购</th><th>环比昨日</th><th>退订</th><th>净增</th><th>在网用户数</th></tr></thead>
      <tbody>
        <tr><td>7/15</td><td>265</td><td>—</td><td>78</td><td>+187</td><td>857,842</td></tr>
        <tr><td>7/16</td><td>258</td><td>-2.6%</td><td>81</td><td>+177</td><td>858,019</td></tr>
        <tr><td>7/17</td><td>249</td><td>-3.5%</td><td>76</td><td>+173</td><td>858,192</td></tr>
        <tr><td>7/18</td><td>237</td><td>-4.8%</td><td>79</td><td>+158</td><td>858,350</td></tr>
        <tr><td>7/19</td><td>228</td><td>-3.8%</td><td>74</td><td>+154</td><td>858,504</td></tr>
        <tr><td>7/20</td><td>215</td><td>-5.7%</td><td>82</td><td>+133</td><td>858,637</td></tr>
        <tr><td>7/21</td><td>208</td><td>-3.3%</td><td>77</td><td>+131</td><td>858,768</td></tr>
        <tr><td><strong>前7日（7/15-7/21）均值</strong></td><td><strong>237</strong></td><td>—</td><td><strong>78</strong></td><td><strong>+159</strong></td><td>—</td></tr>
        <tr><td>7/22</td><td>195</td><td>-6.3%</td><td>82</td><td>+113</td><td>859,210</td></tr>
        <tr><td>7/23</td><td>182</td><td>-6.7%</td><td>79</td><td>+103</td><td>859,313</td></tr>
        <tr><td>7/24</td><td>170</td><td>-6.6%</td><td>85</td><td>+85</td><td>859,398</td></tr>
        <tr><td>7/25</td><td>162</td><td>-4.7%</td><td>80</td><td>+82</td><td>859,480</td></tr>
        <tr><td>7/26</td><td>175</td><td>+8.0%</td><td>76</td><td>+99</td><td>859,579</td></tr>
        <tr><td>7/27</td><td>168</td><td>-4.0%</td><td>88</td><td>+80</td><td>859,659</td></tr>
        <tr><td>7/28</td><td>177</td><td>+5.4%</td><td>84</td><td>+93</td><td>859,752</td></tr>
        <tr><td><strong>后7日（7/22-7/28）均值</strong></td><td><strong>177</strong></td><td>—</td><td><strong>82</strong></td><td><strong>+94</strong></td><td>—</td></tr>
        <tr><td><strong>变化</strong></td><td><strong>-25.3%</strong></td><td>—</td><td><strong>+5.1%</strong></td><td><strong>-40.9%</strong></td><td><strong>+0.4%</strong></td></tr>
      </tbody>
    </table>

    <blockquote>
      <p>几个关键点：</p>
      <ul>
        <li>新增日均 <strong>177单</strong>，对比前7日 <strong>237单</strong>，降幅 <strong>25.3%</strong>。同比5月该产品刚上架时（日均约320单），降幅已接近 <strong>45%</strong>；</li>
        <li>退订日均 <strong>82单</strong>，环比 +5.1%，波动在正常范围内，<strong>在网用户的体验没有明显恶化</strong>；</li>
        <li>在网用户规模 <strong>86万</strong>，净增逐日递减但仍为正数，大盘没有失血；</li>
        <li><strong>转折点出现在7月20日之后</strong>，降幅从日均3%左右突然放大到6%以上。</li>
      </ul>
    </blockquote>
    <blockquote><strong>趋势层结论</strong>：这是一次<strong>新增获客侧</strong>的异动，而非存量流失型衰退。问题在"进水管"，不在"出水管"。</blockquote>

    <hr />

    <!-- 三、结构层 -->
    <h2>三、结构层：年轻客群在流失，信息流渠道效率下降</h2>
    <p>再拆新增用户的画像，我把<strong>客群结构</strong>和<strong>渠道来源</strong>两个维度结合起来看——谁在流失、从哪里流失。</p>

    <AnalyzeCharts type="structure" />

    <p><strong>客群结构变化：</strong></p>
    <table>
      <thead><tr><th>客群标签</th><th>近7日新增占比</th><th>前30日占比</th><th>变化</th><th>近7日日均新增数</th></tr></thead>
      <tbody>
        <tr><td>18-25岁学生/年轻职场</td><td><strong>18%</strong></td><td>31%</td><td><strong>-13pp</strong></td><td>≈32单</td></tr>
        <tr><td>26-35岁白领</td><td><strong>43%</strong></td><td>41%</td><td>+2pp</td><td>≈76单</td></tr>
        <tr><td>36-45岁中年</td><td><strong>27%</strong></td><td>22%</td><td>+5pp</td><td>≈48单</td></tr>
        <tr><td>46岁以上</td><td><strong>12%</strong></td><td>6%</td><td>+6pp</td><td>≈21单</td></tr>
      </tbody>
    </table>
    <blockquote>年轻客群（18-25岁）从第一客群（31%）跌至第三（18%），<strong>绝对数量从日均约73单降至32单，日流失约41单</strong>。这个数字解释了总降幅60%以上。</blockquote>

    <p><strong>渠道贡献变化：</strong></p>
    <table>
      <thead><tr><th>渠道</th><th>近7日新增占比</th><th>前30日占比</th><th>变化</th><th>备注</th></tr></thead>
      <tbody>
        <tr><td>线上营业厅APP</td><td>44%</td><td>48%</td><td>-4pp</td><td>自然流量，下滑相对温和</td></tr>
        <tr><td><strong>抖音/快手信息流</strong></td><td><strong>18%</strong></td><td>27%</td><td><strong>-9pp</strong></td><td>⚠️ 降幅最大的渠道</td></tr>
        <tr><td>线下营业厅</td><td>22%</td><td>18%</td><td>+4pp</td><td>线下未受明显影响</td></tr>
        <tr><td>第三方合作（支付宝/京东）</td><td>10%</td><td>5%</td><td>+5pp</td><td>新合作渠道，基数小</td></tr>
        <tr><td>10086外呼</td><td>6%</td><td>2%</td><td>+4pp</td><td>主动营销，基数小</td></tr>
      </tbody>
    </table>
    <blockquote><strong>交叉分析关键发现</strong>：抖音/快手信息流渠道的年轻客群占比<strong>从52%骤降至31%</strong>。后台竞价数据显示，<strong>竞品近两周在该人群的投放出价提高了约22%</strong>，直接挤压了我们的曝光量。这属于渠道侧的被动态势，叠加产品本身对年轻客群吸引力下降，形成了双重挤压。</blockquote>
    <blockquote><strong>结构层结论</strong>：新增下滑主要集中在 <strong>"18-25岁 × 抖音/快手信息流"</strong> 这个交叉象限。产品和渠道需要协同修复。</blockquote>

    <hr />

    <!-- 四、竞品层 -->
    <h2>四、竞品层：高速配额落后29%，且竞品在7月集体升级</h2>
    <p>再看竞品。系统抓取了三大友商同档位产品的最新数据，加了一列'近1个月变化'，帮你看清动态：</p>

    <AnalyzeCharts type="competitor" />

    <table>
      <thead><tr><th>维度</th><th>本产品</th><th>友商A（游讯1日卡）</th><th>友商B（NiceTrip 15日）</th><th>友商C（大湾区伴游30日）</th></tr></thead>
      <tbody>
        <tr><td>资费</td><td>一次性</td><td>一次性</td><td>一次性（更便宜）</td><td>一次性</td></tr>
        <tr><td>高速配额</td><td><strong>30GB（总量）</strong></td><td><strong>20GB/日</strong></td><td><strong>15GB（总量）</strong></td><td><strong>50GB（总量）</strong></td></tr>
        <tr><td>有效期</td><td>30天</td><td>1天</td><td><strong>15天</strong></td><td>30天</td></tr>
        <tr><td>漫游地</td><td>香港.台湾</td><td><strong>香港.台湾+澳门</strong></td><td>香港.台湾</td><td><strong>亚太多国</strong></td></tr>
        <tr><td>网络速率</td><td>5G</td><td>4G</td><td><strong>5G</strong></td><td>5G</td></tr>
        <tr><td><strong>近1个月变化</strong></td><td>➡️ 无变化</td><td>⬆️ 新增澳门漫游（7/1）</td><td>⬇️ 降价（7/15）</td><td>⬆️ 高速50GB（7/20）</td></tr>
      </tbody>
    </table>
    <blockquote>
      <p>核心差距：</p>
      <ol>
        <li><strong>高速配额落后</strong>：30GB vs 竞品均值42.5GB，落后 <strong>29%</strong>。三家竞品中我们高速额度偏低；</li>
        <li><strong>友商A和C在7月集中升级</strong>（澳门漫游、50GB高速），友商B降价，<strong>我们没有任何配置调整响应</strong>；</li>
        <li>友商A的游讯1日卡目前日均新增约 <strong>620单</strong>，是本品的 <strong>3.5倍</strong>，其中约30%的新增用户是从本品分流过去的。</li>
      </ol>
    </blockquote>
    <blockquote><strong>竞品层结论</strong>：竞品在7月完成了对本品 <strong>"高速配额+漫游覆盖"的双重包围</strong>。我们的相对竞争力在过去30天内被动大幅下滑。</blockquote>

    <hr />

    <!-- 五、根因定位 -->
    <h2>五、根因定位（综合判断）</h2>
    <p>把四层串起来，我给出根因定位和贡献度拆解：</p>

    <AnalyzeCharts type="rootcause" />

    <table>
      <thead><tr><th>根因</th><th>贡献度</th><th>说明</th></tr></thead>
      <tbody>
        <tr><td><strong>主因：高速配额落后竞品均值29%</strong></td><td><strong>50%</strong></td><td>本品30GB vs 竞品均值42.5GB，且竞品7月集中升级，本品无跟进</td></tr>
        <tr><td><strong>辅因1：年轻客群在信息流渠道被截流</strong></td><td><strong>20%</strong></td><td>竞品在抖音/快手渠道提价抢量，叠加产品吸引力不足</td></tr>
        <tr><td><strong>辅因2：漫游覆盖受限</strong></td><td><strong>15%</strong></td><td>友商A新增澳门漫游，本品的香港.台湾双地覆盖吸引力下降</td></tr>
        <tr><td><strong>辅因3：友商B降价竞争</strong></td><td><strong>10%</strong></td><td>价格敏感型用户被分流</td></tr>
        <tr><td><strong>其他（季节性/大促空窗等）</strong></td><td><strong>5%</strong></td><td>7月为传统淡季，叠加无大型营销活动</td></tr>
      </tbody>
    </table>
    <blockquote><strong>最终结论</strong>：本次异动为 <strong>"竞争性新增萎缩"</strong> ，核心矛盾是<strong>高速配额配置落后于竞品</strong>，次要矛盾是<strong>信息流渠道获客效率下降</strong>和<strong>漫游覆盖不足</strong>。属于<strong>可优化型异动</strong>——通过新产品策划和渠道投放策略调整，可快速修复。</blockquote>
  </div>
</template>

<script setup>
import AnalyzeCharts from './AnalyzeCharts.vue'
</script>

<style scoped>
.analyze-report {
  font-size: 13px;
  line-height: 1.7;
  color: #334155;
}

.analyze-report :deep(h2) {
  font-size: 17px;
  font-weight: 600;
  color: #1e293b;
  margin: 20px 0 10px;
  padding-bottom: 6px;
  border-bottom: 1px solid #e5e7eb;
}

.analyze-report :deep(p) {
  margin: 8px 0;
}

.analyze-report :deep(strong) {
  color: #1e293b;
}

.analyze-report :deep(blockquote) {
  border-left: 3px solid #3b82f6;
  margin: 10px 0;
  padding: 8px 14px;
  color: #475569;
  background: rgba(59, 130, 246, 0.04);
  border-radius: 0 6px 6px 0;
}

.analyze-report :deep(blockquote p) {
  margin: 4px 0;
}

.analyze-report :deep(blockquote ul),
.analyze-report :deep(blockquote ol) {
  margin: 4px 0;
  padding-left: 20px;
}

.analyze-report :deep(blockquote li) {
  margin: 3px 0;
}

.analyze-report :deep(table) {
  border-collapse: collapse;
  margin: 10px 0;
  width: 100%;
  font-size: 12px;
}

.analyze-report :deep(th),
.analyze-report :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 6px 10px;
  text-align: left;
}

.analyze-report :deep(th) {
  background: #f8fafc;
  font-weight: 600;
  color: #1e293b;
}

.analyze-report :deep(td) {
  color: #475569;
}

.analyze-report :deep(hr) {
  border: none;
  border-top: 1px solid #e5e7eb;
  margin: 20px 0;
}

.analyze-report :deep(code) {
  background: rgba(0, 0, 0, 0.06);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, monospace;
}
</style>
