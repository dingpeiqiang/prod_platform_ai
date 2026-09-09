package com.sitech.prodai.service.kb;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内嵌向量检索（方案 §6-B6，知识库检索供给 v1）：字符 bigram TF-IDF + 余弦相似度。
 * <p>
 * 设计约束：
 * <ul>
 *   <li>零外部依赖：中文按字符 bigram 切分（无分词依赖），英文/数字按词段小写，
 *       TF-IDF 权重 + 余弦相似度，同输入同输出确定性可复现；</li>
 *   <li>配置驱动扩展位（§4.6 数据源工厂模式）：v1 内嵌内存实现；生产文档规模化
 *       （上万条 / 多实例共享 / ANN 索引诉求）时以同 API 实现替换为外部向量库
 *       （pgvector/Milvus/Qdrant）Bean，调用方零改动；</li>
 *   <li>embedding 来源预留：v1 词面向量（TF-IDF）；接 embedding 模型
 *       （本地 bge-small-zh / LLM 服务）时仅替换向量构建，topK 排序契约不变。</li>
 * </ul>
 */
@Component
public class InMemoryVectorSearch {

    /** 检索接口：文档注册 + 相似度 Top-K（外部向量库实现替换点）。 */
    public interface VectorSearch {
        /** 注册/更新一篇文档的向量（幂等，同 id 覆盖）。 */
        void upsert(String docId, String title, String content);

        /** 注销文档向量。 */
        void remove(String docId);

        /** 查询 Top-K（k 上限保护；结果按相似度降序，平分按 docId 稳定排序）。 */
        List<Map<String, Object>> topK(String query, int k);
    }

    /** 文档向量：词 → TF-IDF 权重（注册时建索引，查询时计算）。 */
    private static final class DocVector {
        final String title;
        final Map<String, Double> weights;
        final double norm;

        DocVector(String title, Map<String, Double> weights, double norm) {
            this.title = title;
            this.weights = weights;
            this.norm = norm;
        }
    }

    /** docId → 文档向量（重建索引时全量重建，保证 IDF 一致）。 */
    private final Map<String, DocVector> index = new HashMap<>();
    /** 全局文档频率（IDF 计算，upsert/remove 时维护）。 */
    private final Map<String, Integer> docFrequency = new HashMap<>();

    /** 注册/更新文档向量（幂等，同 id 覆盖；IDF 全量重算，规模内成本可接受）。 */
    public synchronized void upsert(String docId, String title, String content) {
        remove(docId);
        Map<String, Integer> tf = termFrequency(title, content);
        Map<String, Double> weights = new HashMap<>();
        double sq = 0;
        for (Map.Entry<String, Integer> e : tf.entrySet()) {
            double w = tfIdf(e.getKey(), e.getValue());
            weights.put(e.getKey(), w);
            sq += w * w;
        }
        for (String term : tf.keySet()) {
            docFrequency.merge(term, 1, Integer::sum);
        }
        index.put(docId, new DocVector(title == null ? "" : title, weights, Math.sqrt(sq)));
    }

    /** 注销文档向量（维护文档频率）。 */
    public synchronized void remove(String docId) {
        DocVector old = index.remove(docId);
        if (old == null) {
            return;
        }
        for (String term : old.weights.keySet()) {
            docFrequency.computeIfPresent(term, (k, v) -> v <= 1 ? null : v - 1);
        }
    }

    /** 查询 Top-K（相似度降序；平分按 docId 字典序稳定排序，确定性）。 */
    public synchronized List<Map<String, Object>> topK(String query, int k) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (query == null || query.isBlank() || index.isEmpty() || k <= 0) {
            return out;
        }
        Map<String, Integer> qtf = termFrequency(query, "");
        Map<String, Double> qWeights = new HashMap<>();
        double qSq = 0;
        for (Map.Entry<String, Integer> e : qtf.entrySet()) {
            double w = tfIdf(e.getKey(), e.getValue());
            qWeights.put(e.getKey(), w);
            qSq += w * w;
        }
        double qNorm = Math.sqrt(qSq);
        if (qNorm == 0) {
            return out;
        }
        List<String> ranked = new ArrayList<>();
        Map<String, Double> sims = new HashMap<>();
        for (Map.Entry<String, DocVector> e : index.entrySet()) {
            double dot = 0;
            for (Map.Entry<String, Double> t : qWeights.entrySet()) {
                Double dw = e.getValue().weights.get(t.getKey());
                if (dw != null) {
                    dot += t.getValue() * dw;
                }
            }
            double sim = dot / (qNorm * e.getValue().norm);
            if (sim > 0) {
                sims.put(e.getKey(), sim);
                ranked.add(e.getKey());
            }
        }
        ranked.sort((a, b) -> {
            int bySim = Double.compare(sims.get(b), sims.get(a));
            return bySim != 0 ? bySim : a.compareTo(b);
        });
        for (String id : ranked.subList(0, Math.min(k, ranked.size()))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("similarity", Math.round(sims.get(id) * 10000) / 10000.0);
            out.add(row);
        }
        return out;
    }

    // ===== 切分与权重 =====

    /**
     * 词频统计：中文按字符 bigram（无分词依赖，单字退化为 unigram），
     * 英文/数字按连续段小写。确定性：同文本同词频。
     */
    private Map<String, Integer> termFrequency(String title, String content) {
        String text = (title == null ? "" : title) + " " + (content == null ? "" : content);
        Map<String, Integer> tf = new HashMap<>();
        StringBuilder latin = new StringBuilder();
        List<String> cjk = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c) && c < 128) {
                latin.append(Character.toLowerCase(c));
                continue;
            }
            flushLatin(tf, latin);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                cjk.add(String.valueOf(c));
            } else {
                flushCjk(tf, cjk);
            }
        }
        flushLatin(tf, latin);
        flushCjk(tf, cjk);
        return tf;
    }

    private void flushLatin(Map<String, Integer> tf, StringBuilder latin) {
        if (latin.length() > 0) {
            tf.merge(latin.toString(), 1, Integer::sum);
            latin.setLength(0);
        }
    }

    private void flushCjk(Map<String, Integer> tf, List<String> cjk) {
        for (int i = 0; i < cjk.size(); i++) {
            if (i + 1 < cjk.size()) {
                tf.merge(cjk.get(i) + cjk.get(i + 1), 1, Integer::sum);
            } else if (cjk.size() == 1) {
                tf.merge(cjk.get(i), 1, Integer::sum);
            }
        }
        cjk.clear();
    }

    /** TF-IDF：tf × ln(1 + N/df)；df=0（查询新词）时取 ln(1 + N)。 */
    private double tfIdf(String term, int tf) {
        int n = Math.max(1, index.size());
        int df = docFrequency.getOrDefault(term, 0);
        double idf = df == 0 ? Math.log(1 + n) : Math.log(1 + (double) n / df);
        return tf * idf;
    }
}
