package com.sitech.prodai.service;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDF4J;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.eclipse.rdf4j.sail.shacl.ShaclSail;
import org.eclipse.rdf4j.sail.shacl.ShaclSailValidationException;
import org.eclipse.rdf4j.sail.shacl.results.ValidationReport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R7 回归门禁：compliance-shacl.ttl 必须能被 RDF4J ShaclSail 真引擎装载并完成校验，
 * 不允许静默降级 Lite。背景：sh:name 按 shacl.ttl 本体域属 PropertyShape 专属，
 * NodeShape 使用会触发 "Shape with multiple types"（RDF4J discussion #4287），
 * 此前 R-C05 NodeShape 误用 sh:name 导致真引擎从未生效。NodeShape 的 R-C 编号
 * 统一用 rdfs:label 标注（见 compliance-shacl.ttl 头部注解约定）。
 */
class ShaclSailLoadingTest {

    private static final String EX = "http://example.org/";
    private static Model shapes;

    @BeforeAll
    static void loadShapes() throws Exception {
        try (var in = new DefaultResourceLoader()
                .getResource("classpath:ontologies/compliance-shacl.ttl").getInputStream()) {
            shapes = Rio.parse(in, "", RDFFormat.TURTLE);
        }
    }

    @Test
    void shapesShouldLoadAndValidateConformingDataInRealEngine() throws Exception {
        SailRepository repo = newRepository();
        try {
            try (RepositoryConnection conn = repo.getConnection()) {
                conn.begin(ShaclSail.TransactionSettings.ValidationApproach.Bulk);
                conn.add(shapes, RDF4J.SHACL_SHAPE_GRAPH);
                conn.add(conformingModel());
                // commit 触发 shape 解析与校验：任何 multiple types / shape 解析异常在此直接暴露
                conn.commit();
            }
        } finally {
            repo.shutDown();
        }
    }

    @Test
    void realEngineShouldReportRc05ForZeroFeeNoContract() throws Exception {
        SailRepository repo = newRepository();
        try {
            loadShapes(repo);
            try (RepositoryConnection conn = repo.getConnection()) {
                conn.begin(ShaclSail.TransactionSettings.ValidationApproach.Bulk);
                conn.add(zeroFeeNoContractModel());
                try {
                    conn.commit();
                    throw new AssertionError("零固费且无合约草稿应触发 SHACL 违规");
                } catch (RepositoryException e) {
                    ShaclSailValidationException validation = findValidationCause(e);
                    assertTrue(validation != null, "应抛出 SHACL 校验异常而非其他引擎错误: " + e.getMessage());
                    ValidationReport report = validation.getValidationReport();
                    assertFalse(report.conforms(), "报告应为不合规");
                    // 真引擎报告侧：sourceShape=ZeroFeeNoContractShape，message 前缀携带 R-C05
                    boolean rc05 = report.getValidationResult().stream().anyMatch(vr -> {
                        Model m = vr.asModel(new LinkedHashModel());
                        return m.filter(null, Values.iri("http://www.w3.org/ns/shacl#resultMessage"), null).stream()
                                .anyMatch(st -> st.getObject().stringValue().startsWith("R-C05"));
                    });
                    assertTrue(rc05, "真引擎校验报告应含 R-C05 违规（" + report.getValidationResult().size() + " 条结果）");
                }
            }
        } finally {
            repo.shutDown();
        }
    }

    private SailRepository newRepository() {
        ShaclSail sail = new ShaclSail(new MemoryStore());
        sail.setLogValidationPlans(false);
        sail.setLogValidationViolations(false);
        SailRepository repo = new SailRepository(sail);
        repo.init();
        return repo;
    }

    private void loadShapes(SailRepository repo) throws Exception {
        try (RepositoryConnection conn = repo.getConnection()) {
            conn.begin(ShaclSail.TransactionSettings.ValidationApproach.Bulk);
            conn.add(shapes, RDF4J.SHACL_SHAPE_GRAPH);
            conn.commit();
        }
    }

    /** 合规草稿：三条必填齐全、固费 > 0 → 通过。 */
    private Model conformingModel() {
        Model model = new LinkedHashModel();
        ValueFactory vf = Values.getValueFactory();
        org.eclipse.rdf4j.model.IRI subject = Values.iri(EX + "offering/ok");
        model.add(subject, RDF.TYPE, Values.iri(EX + "Offering"));
        model.add(subject, Values.iri(EX + "offeringName"), vf.createLiteral("合规资费"));
        model.add(subject, Values.iri(EX + "fixedFeeAmount"), vf.createLiteral(9.9));
        model.add(subject, Values.iri(EX + "channelScope"), vf.createLiteral("全渠道"));
        return model;
    }

    /** R-C05 场景：固费=0、一次性费=0、无合约。 */
    private Model zeroFeeNoContractModel() {
        Model model = new LinkedHashModel();
        ValueFactory vf = Values.getValueFactory();
        org.eclipse.rdf4j.model.IRI subject = Values.iri(EX + "offering/bad");
        model.add(subject, RDF.TYPE, Values.iri(EX + "Offering"));
        model.add(subject, Values.iri(EX + "offeringName"), vf.createLiteral("零固费资费"));
        model.add(subject, Values.iri(EX + "fixedFeeAmount"), vf.createLiteral(0.0));
        model.add(subject, Values.iri(EX + "oneTimeFee"), vf.createLiteral(0.0));
        model.add(subject, Values.iri(EX + "hasContract"), vf.createLiteral(false));
        model.add(subject, Values.iri(EX + "channelScope"), vf.createLiteral("全渠道"));
        return model;
    }

    private ShaclSailValidationException findValidationCause(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ShaclSailValidationException validation) {
                return validation;
            }
        }
        return null;
    }
}
