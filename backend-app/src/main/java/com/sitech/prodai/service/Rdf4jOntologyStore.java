package com.sitech.prodai.service;

import jakarta.annotation.PostConstruct;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RDF4J 本体存储服务，提供基于真实 RDF 三元组存储的本体操作。
 * 使用内存存储，启动时自动加载 TTL 本体文件。
 */
@Service
public class Rdf4jOntologyStore {

    private static final Logger log = LoggerFactory.getLogger(Rdf4jOntologyStore.class);

    private static final SimpleValueFactory VF = SimpleValueFactory.getInstance();
    private static final String NS = "http://example.org/";

    private final Repository repository;
    private final Map<String, List<Map<String, Object>>> sparqlCache = new ConcurrentHashMap<>();

    @Value("${prodai.ontology.rdf4j.ttl-path:classpath:ontology/sample-ontology.ttl}")
    private Resource ontologyResource;

    public Rdf4jOntologyStore() {
        this.repository = new SailRepository(new MemoryStore());
    }

    @PostConstruct
    public void init() {
        try {
            repository.init();
            if (ontologyResource != null && ontologyResource.exists()) {
                try (InputStream in = ontologyResource.getInputStream();
                     RepositoryConnection conn = repository.getConnection()) {
                    conn.add(in, "", RDFFormat.TURTLE);
                    long count = conn.size();
                    log.info("RDF4J 本体存储初始化完成，已加载 {} 条三元组，来源: {}", count, ontologyResource.getFilename());
                }
            } else {
                log.warn("未找到 TTL 本体文件，将使用空存储");
            }
        } catch (IOException e) {
            log.error("加载本体文件失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 执行 SPARQL 查询并返回结果列表
     */
    public List<Map<String, Object>> sparqlQuery(String sparql) {
        List<Map<String, Object>> results = new ArrayList<>();
        try (RepositoryConnection conn = repository.getConnection()) {
            TupleQuery query = conn.prepareTupleQuery(QueryLanguage.SPARQL, sparql);
            try (TupleQueryResult result = query.evaluate()) {
                List<String> bindingNames = result.getBindingNames();
                while (result.hasNext()) {
                    BindingSet bs = result.next();
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (String name : bindingNames) {
                        org.eclipse.rdf4j.model.Value value = bs.getValue(name);
                        if (value instanceof IRI) {
                            row.put(name, value.stringValue());
                        } else {
                            row.put(name, valueToString(value));
                        }
                    }
                    results.add(row);
                }
            }
        } catch (Exception e) {
            log.error("SPARQL 查询失败: {} - {}", sparql, e.getMessage());
        }
        return results;
    }

    /**
     * 获取实体的所有属性
     */
    public Map<String, Object> getEntity(String uri) {
        Map<String, Object> props = new LinkedHashMap<>();
        IRI subject = VF.createIRI(uri);
        try (RepositoryConnection conn = repository.getConnection();
             RepositoryResult<Statement> stmts = conn.getStatements(subject, null, null, false)) {
            props.put("entityId", uri);
            while (stmts.hasNext()) {
                Statement stmt = stmts.next();
                String pred = stmt.getPredicate().getLocalName();
                if (pred.isEmpty()) pred = stmt.getPredicate().getLocalName();
                String val = stmt.getObject().isIRI()
                        ? stmt.getObject().stringValue()
                        : valueToString(stmt.getObject());

                // 如果属性名是 "type" 则视为实体类型
                String predName = stmt.getPredicate().getLocalName();
                if ("type".equals(predName) || stmt.getPredicate().getNamespace().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#")) {
                    String localName = stmt.getObject().isIRI() ? ((IRI) stmt.getObject()).getLocalName() : val;
                    if (localName != null && !localName.isEmpty()) {
                        props.put("entityType", localName);
                    }
                    props.put("type", localName);
                    continue;
                }
                props.put(stmt.getPredicate().getLocalName(), val);
            }
        }
        return props;
    }

    /**
     * 获取所有 RDF 类（owl:Class 或 rdfs:Class 的实例）
     */
    public List<String> getClasses() {
        Set<String> classes = new TreeSet<>();
        String sparql = """
                SELECT DISTINCT ?class WHERE {
                  { ?class rdf:type owl:Class } UNION { ?class rdf:type rdfs:Class }
                }
                """;
        List<Map<String, Object>> results = sparqlQuery(sparql);
        for (Map<String, Object> row : results) {
            String val = String.valueOf(row.getOrDefault("class", ""));
            // 提取 localName
            int hashIdx = val.indexOf('#');
            int slashIdx = val.lastIndexOf('/');
            int idx = Math.max(hashIdx, slashIdx);
            if (idx >= 0 && idx < val.length() - 1) {
                classes.add(val.substring(idx + 1));
            } else {
                classes.add(val);
            }
        }
        return new ArrayList<>(classes);
    }

    /**
     * 获取所有属性（DatatypeProperty 和 ObjectProperty）
     */
    public List<String> getProperties() {
        Set<String> props = new TreeSet<>();
        String sparql = """
                SELECT DISTINCT ?prop WHERE {
                  { ?prop rdf:type owl:DatatypeProperty } UNION
                  { ?prop rdf:type owl:ObjectProperty }
                }
                """;
        List<Map<String, Object>> results = sparqlQuery(sparql);
        for (Map<String, Object> row : results) {
            String val = String.valueOf(row.getOrDefault("prop", ""));
            int hashIdx = val.indexOf('#');
            int slashIdx = val.lastIndexOf('/');
            int idx = Math.max(hashIdx, slashIdx);
            if (idx >= 0 && idx < val.length() - 1) {
                props.add(val.substring(idx + 1));
            } else {
                props.add(val);
            }
        }
        return new ArrayList<>(props);
    }

    /**
     * 根据类名获取该类实例
     */
    public List<Map<String, Object>> getInstances(String className) {
        String classUri = NS + className;
        String sparql = "SELECT ?entity WHERE { ?entity rdf:type <" + classUri + "> }";
        List<Map<String, Object>> results = sparqlQuery(sparql);
        List<Map<String, Object>> instances = new ArrayList<>();
        for (Map<String, Object> row : results) {
            String entityUri = String.valueOf(row.get("entity"));
            instances.add(getEntity(entityUri));
        }
        return instances;
    }

    /**
     * 判断实体是否存在
     */
    public boolean hasEntity(String uri) {
        try (RepositoryConnection conn = repository.getConnection()) {
            return conn.hasStatement(VF.createIRI(uri), null, null, false);
        }
    }

    /**
     * 获取所有实体 URI（按类筛选）
     */
    public List<String> getAllEntityUris() {
        List<String> uris = new ArrayList<>();
        String sparql = "SELECT DISTINCT ?entity WHERE { ?entity rdf:type ?type . ?type rdf:type owl:Class }";
        List<Map<String, Object>> results = sparqlQuery(sparql);
        for (Map<String, Object> row : results) {
            uris.add(String.valueOf(row.get("entity")));
        }
        return uris;
    }

    public long getTripleCount() {
        try (RepositoryConnection conn = repository.getConnection()) {
            return conn.size();
        } catch (Exception e) {
            return 0;
        }
    }

    private String valueToString(org.eclipse.rdf4j.model.Value value) {
        if (value == null) return "";
        if (value.isIRI()) {
            IRI iri = (IRI) value;
            String localName = iri.getLocalName();
            if (localName != null && !localName.isEmpty()) return localName;
            return iri.stringValue();
        }
        if (value.isLiteral()) {
            Literal lit = (Literal) value;
            return lit.getLabel();
        }
        return value.stringValue();
    }

    public Map<String, Object> importTtl(String ttlContent, boolean replace) {
        Map<String, Object> result = new LinkedHashMap<>();
        try (RepositoryConnection conn = repository.getConnection()) {
            if (replace) {
                conn.clear();
                log.info("已清空现有本体数据");
            }
            
            long beforeCount = conn.size();
            conn.add(new StringReader(ttlContent), "", RDFFormat.TURTLE);
            long afterCount = conn.size();
            long addedCount = afterCount - beforeCount;
            
            log.info("TTL 导入完成，新增 {} 条三元组，当前总数: {}", addedCount, afterCount);
            
            result.put("success", true);
            result.put("message", "TTL 导入成功");
            result.put("addedTriples", addedCount);
            result.put("totalTriples", afterCount);
            result.put("mode", replace ? "replace" : "merge");
            
        } catch (Exception e) {
            log.error("TTL 导入失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "TTL 导入失败: " + e.getMessage());
        }
        return result;
    }

    public Map<String, Object> getGraphData() {
        Map<String, Object> graph = new LinkedHashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        
        Set<String> classUris = new TreeSet<>();
        String classQuery = """
                SELECT DISTINCT ?class ?label WHERE {
                  { ?class rdf:type owl:Class } UNION { ?class rdf:type rdfs:Class }
                  OPTIONAL { ?class rdfs:label ?label }
                }
                """;
        List<Map<String, Object>> classResults = sparqlQuery(classQuery);
        for (Map<String, Object> row : classResults) {
            String classUri = String.valueOf(row.get("class"));
            String label = String.valueOf(row.getOrDefault("label", ""));
            String localName = extractLocalName(classUri);
            classUris.add(classUri);
            
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", classUri);
            node.put("name", localName);
            node.put("label", label.isEmpty() ? localName : label);
            node.put("type", "class");
            node.put("color", "#6366f1");
            nodes.add(node);
        }
        
        Set<String> propertyUris = new TreeSet<>();
        String propertyQuery = """
                SELECT DISTINCT ?prop ?label ?domain ?range ?propType WHERE {
                  { ?prop rdf:type owl:ObjectProperty . BIND("object" AS ?propType) } UNION
                  { ?prop rdf:type owl:DatatypeProperty . BIND("datatype" AS ?propType) }
                  OPTIONAL { ?prop rdfs:label ?label }
                  OPTIONAL { ?prop rdfs:domain ?domain }
                  OPTIONAL { ?prop rdfs:range ?range }
                }
                """;
        List<Map<String, Object>> propertyResults = sparqlQuery(propertyQuery);
        for (Map<String, Object> row : propertyResults) {
            String propUri = String.valueOf(row.get("prop"));
            String label = String.valueOf(row.getOrDefault("label", ""));
            String domain = String.valueOf(row.getOrDefault("domain", ""));
            String range = String.valueOf(row.getOrDefault("range", ""));
            String propType = String.valueOf(row.getOrDefault("propType", "datatype"));
            String localName = extractLocalName(propUri);
            
            propertyUris.add(propUri);
            
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", propUri);
            node.put("name", localName);
            node.put("label", label.isEmpty() ? localName : label);
            node.put("type", propType.equals("object") ? "object_property" : "datatype_property");
            node.put("color", propType.equals("object") ? "#22c55e" : "#f59e0b");
            nodes.add(node);
            
            if (!domain.isEmpty() && classUris.contains(domain)) {
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("id", "edge_" + domain + "_" + propUri);
                edge.put("source", domain);
                edge.put("target", propUri);
                edge.put("label", localName);
                edge.put("type", "domain");
                edges.add(edge);
            }
            
            if (!range.isEmpty()) {
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("id", "edge_" + propUri + "_" + range);
                edge.put("source", propUri);
                edge.put("target", range);
                edge.put("label", "range");
                edge.put("type", propType.equals("object") ? "object_range" : "datatype_range");
                edges.add(edge);
            }
        }
        
        String instanceQuery = """
                SELECT DISTINCT ?instance ?class ?label WHERE {
                  ?instance rdf:type ?class .
                  ?class rdf:type owl:Class .
                  OPTIONAL { ?instance rdfs:label ?label }
                }
                """;
        List<Map<String, Object>> instanceResults = sparqlQuery(instanceQuery);
        for (Map<String, Object> row : instanceResults) {
            String instanceUri = String.valueOf(row.get("instance"));
            String classUri = String.valueOf(row.get("class"));
            String label = String.valueOf(row.getOrDefault("label", ""));
            String localName = extractLocalName(instanceUri);
            
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", instanceUri);
            node.put("name", localName);
            node.put("label", label.isEmpty() ? localName : label);
            node.put("type", "instance");
            node.put("color", "#8b5cf6");
            nodes.add(node);
            
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("id", "edge_instance_" + instanceUri);
            edge.put("source", classUri);
            edge.put("target", instanceUri);
            edge.put("label", "instanceOf");
            edge.put("type", "instance_of");
            edges.add(edge);
        }
        
        String objectRelationQuery = """
                SELECT DISTINCT ?subject ?predicate ?object WHERE {
                  ?subject ?predicate ?object .
                  ?predicate rdf:type owl:ObjectProperty .
                  FILTER(isIRI(?object))
                }
                """;
        List<Map<String, Object>> relationResults = sparqlQuery(objectRelationQuery);
        for (Map<String, Object> row : relationResults) {
            String subject = String.valueOf(row.get("subject"));
            String predicate = String.valueOf(row.get("predicate"));
            String object = String.valueOf(row.get("object"));
            String predLocalName = extractLocalName(predicate);
            
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("id", "edge_relation_" + subject + "_" + predicate);
            edge.put("source", subject);
            edge.put("target", object);
            edge.put("label", predLocalName);
            edge.put("type", "relation");
            edges.add(edge);
        }
        
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        graph.put("classCount", classUris.size());
        graph.put("propertyCount", propertyUris.size());
        graph.put("instanceCount", instanceResults.size());
        graph.put("edgeCount", edges.size());
        
        return graph;
    }

    private String extractLocalName(String uri) {
        if (uri == null || uri.isEmpty()) return "";
        int hashIdx = uri.indexOf('#');
        int slashIdx = uri.lastIndexOf('/');
        int idx = Math.max(hashIdx, slashIdx);
        if (idx >= 0 && idx < uri.length() - 1) {
            return uri.substring(idx + 1);
        }
        return uri;
    }
}