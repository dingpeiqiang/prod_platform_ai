# -*- coding: utf-8 -*-
"""为 product.ttl 追加指标异常 + 优化建议(TBox/SWRL, R-O01~R-O05)。"""
import io

P = 'backend-app/src/main/resources/ontology/product.ttl'
s = io.open(P, encoding='utf-8').read()

MARK = '# BEGIN GENERATED: category-individuals (FROM templates/*.json DO NOT EDIT)'
assert MARK in s, 'anchor MARK not found'
assert 'R-O01.revenue' not in s, 'already patched'

TBOX = '''# ---------- 指标异常属性（R-O 优化建议，运行时由 OpsSwrlReasoner 按实际指标断言） ----------
:revenueDropFlag rdf:type owl:DatatypeProperty ;
    rdfs:domain :Offering ; rdfs:range xsd:boolean ;
    rdfs:label "\\u6536\\u5165\\u4e0b\\u964d"@zh ;
    rdfs:comment "\\u9884\\u7b97\\u5e03\\u5c40\\u4e8e revenue_trend=\\u4e0b\\u964d\\u4e14\\u5e45\\u5ea6\\u8d85\\u9608"@zh .

:newUsersDropFlag rdf:type owl:DatatypeProperty ;
    rdfs:domain :Offering ; rdfs:range xsd:boolean ;
    rdfs:label "\\u65b0\\u589e\\u7528\\u6237\\u4e0b\\u964d"@zh .

:churnUsersRiseFlag rdf:type owl:DatatypeProperty ;
    rdfs:domain :Offering ; rdfs:range xsd:boolean ;
    rdfs:label "\\u6d41\\u5931\\u7528\\u6237\\u4e0a\\u5347"@zh .

:activeUsersDropFlag rdf:type owl:DatatypeProperty ;
    rdfs:domain :Offering ; rdfs:range xsd:boolean ;
    rdfs:label "\\u6d3b\\u8dc3\\u7528\\u6237\\u4e0b\\u964d"@zh .

:arpuDropFlag rdf:type owl:DatatypeProperty ;
    rdfs:domain :Offering ; rdfs:range xsd:boolean ;
    rdfs:label "ARPU \\u4e0b\\u964d"@zh .

:attachRateDropFlag rdf:type owl:DatatypeProperty ;
    rdfs:domain :Offering ; rdfs:range xsd:boolean ;
    rdfs:label "\\u7ed1\\u5b9a\\u7387\\u4e0b\\u964d"@zh .

:orderDropFlag rdf:type owl:DatatypeProperty ;
    rdfs:domain :Offering ; rdfs:range xsd:boolean ;
    rdfs:label "\\u8ba2\\u5355\\u91cf\\u4e0b\\u964d"@zh .

# ---------- \\u4f18\\u5316\\u5efa\\u8bae\\u5206\\u7c7b\\uff08SWRL head\\uff0cR-O01~R-O05\\uff09 ----------
:SuggestionOffering rdf:type owl:Class ;
    rdfs:subClassOf :Offering ;
    rdfs:label "\\u53ef\\u4f18\\u5316\\u5546\\u54c1"@zh ;
    rdfs:comment "\\u5b58\\u5728\\u6307\\u6807\\u5f02\\u5e38\\u3001\\u53ef\\u7ed9\\u51fa\\u8fd0\\u8425\\u4f18\\u5316\\u5efa\\u8bae\\u7684\\u5546\\u54c1"@zh .

:ChannelPromotionOffering rdf:type owl:Class ;
    rdfs:subClassOf :SuggestionOffering ;
    rdfs:label "\\u6e20\\u9053\\u4fc3\\u9500\\u5efa\\u8bae"@zh ;
    rdfs:comment "R-O01\\uff1a\\u6536\\u5165/\\u8ba2\\u5355\\u4e0b\\u964d \\u2192 \\u6e20\\u9053\\u4fc3\\u9500"@zh .

:RetentionOffering rdf:type owl:Class ;
    rdfs:subClassOf :SuggestionOffering ;
    rdfs:label "\\u5ba2\\u6237\\u4fdd\\u7559\\u5efa\\u8bae"@zh ;
    rdfs:comment "R-O02\\uff1a\\u6d41\\u5931\\u7528\\u6237\\u4e0a\\u5347 \\u2192 \\u4fdd\\u7559\\u6d3b\\u52a8"@zh .

:BundleUpsellOffering rdf:type owl:Class ;
    rdfs:subClassOf :SuggestionOffering ;
    rdfs:label "\\u7ec4\\u5408\\u4ea4\\u53c9\\u9500\\u552e\\u5efa\\u8bae"@zh ;
    rdfs:comment "R-O03\\uff1a\\u7ed1\\u5b9a\\u7387\\u4e0b\\u964d \\u2192 \\u7ec4\\u5408\\u4ea4\\u53c9\\u9500\\u552e"@zh .

:PriceOptimizeOffering rdf:type owl:Class ;
    rdfs:subClassOf :SuggestionOffering ;
    rdfs:label "\\u5b9a\\u4ef7/\\u5957\\u9910\\u4f18\\u5316\\u5efa\\u8bae"@zh ;
    rdfs:comment "R-O04\\uff1aARPU\\u4e0b\\u964d \\u2192 \\u5b9a\\u4ef7\\u5957\\u9910\\u4f18\\u5316"@zh .

:EngagementOffering rdf:type owl:Class ;
    rdfs:subClassOf :SuggestionOffering ;
    rdfs:label "\\u6d3b\\u8dc3\\u8fd0\\u8425\\u5efa\\u8bae"@zh ;
    rdfs:comment "R-O05\\uff1a\\u6d3b\\u8dc3\\u7528\\u6237\\u4e0b\\u964d \\u2192 \\u6d3b\\u8dc3\\u8fd0\\u8425\\u6fc0\\u52b1"@zh .

'''

s = s.replace(MARK, TBOX + MARK, 1)

# ---- SWRL 规则：插到 R-E04.p1 规则块之后 ----
ANCHOR = ('  swrl:head ( [ rdf:type swrl:ClassAtom ; swrl:classPredicate :NeedWorkOrderOffering '
          '; swrl:argument1 :v_re4bo ] ) ] .\n')
assert ANCHOR in s, 'R-E04.p1 anchor not found'

RULES = '''
# ---------- 阈值个体（R-O 优化建议，趋势劣化布尔，运行时由 reasoner 直接断言） ----------
:th_RO01_revenueDropLte rdf:type owl:NamedIndividual ; :thresholdValue "-0.10"^^xsd:decimal ; rdfs:label "R-O01.revenueDropLte"@en .
:th_RO02_churnRiseGte   rdf:type owl:NamedIndividual ; :thresholdValue "0.05"^^xsd:decimal ; rdfs:label "R-O02.churnRiseGte"@en .
:th_RO03_attachDropLte  rdf:type owl:NamedIndividual ; :thresholdValue "-0.05"^^xsd:decimal ; rdfs:label "R-O03.attachDropLte"@en .
:th_RO04_arpuDropLte    rdf:type owl:NamedIndividual ; :thresholdValue "-0.05"^^xsd:decimal ; rdfs:label "R-O04.arpuDropLte"@en .
:th_RO05_activeDropLte  rdf:type owl:NamedIndividual ; :thresholdValue "-0.05"^^xsd:decimal ; rdfs:label "R-O05.activeDropLte"@en .

# ---------- SWRL 变量声明（R-O） ----------
:v_ro1 rdf:type swrl:Variable . :v_ro2 rdf:type swrl:Variable . :v_ro3 rdf:type swrl:Variable .
:v_ro4 rdf:type swrl:Variable . :v_ro5 rdf:type swrl:Variable . :v_ro6 rdf:type swrl:Variable . :v_ro7 rdf:type swrl:Variable .

# ---------- 优化建议规则（R-O，条件=指标异常布尔） ----------
[ rdf:type swrl:Imp ;
  rdfs:label "R-O01.revenue"@en ;
  swrl:body ( [ rdf:type swrl:ClassAtom ; swrl:classPredicate :Offering ; swrl:argument1 :v_ro1 ]
              [ rdf:type swrl:DatavaluedPropertyAtom ; swrl:propertyPredicate :revenueDropFlag ; swrl:argument1 :v_ro1 ; swrl:argument2 :v_ro2 ]
              [ rdf:type swrl:BuiltinAtom ; swrl:builtin swrlb:equal ; swrl:arguments ( :v_ro2 "true"^^xsd:boolean ) ] ) ;
  swrl:head ( [ rdf:type swrl:ClassAtom ; swrl:classPredicate :ChannelPromotionOffering ; swrl:argument1 :v_ro1 ] ) ] .

[ rdf:type swrl:Imp ;
  rdfs:label "R-O01.order"@en ;
  swrl:body ( [ rdf:type swrl:ClassAtom ; swrl:classPredicate :Offering ; swrl:argument1 :v_ro3 ]
              [ rdf:type swrl:DatavaluedPropertyAtom ; swrl:propertyPredicate :orderDropFlag ; swrl:argument1 :v_ro3 ; swrl:argument2 :v_ro4 ]
              [ rdf:type swrl:BuiltinAtom ; swrl:builtin swrlb:equal ; swrl:arguments ( :v_ro4 "true"^^xsd:boolean ) ] ) ;
  swrl:head ( [ rdf:type swrl:ClassAtom ; swrl:classPredicate :ChannelPromotionOffering ; swrl:argument1 :v_ro3 ] ) ] .

[ rdf:type swrl:Imp ;
  rdfs:label "R-O02.churn"@en ;
  swrl:body ( [ rdf:type swrl:ClassAtom ; swrl:classPredicate :Offering ; swrl:argument1 :v_ro5 ]
              [ rdf:type swrl:DatavaluedPropertyAtom ; swrl:propertyPredicate :churnUsersRiseFlag ; swrl:argument1 :v_ro5 ; swrl:argument2 :v_ro6 ]
              [ rdf:type swrl:BuiltinAtom ; swrl:builtin swrlb:equal ; swrl:arguments ( :v_ro6 "true"^^xsd:boolean ) ] ) ;
  swrl:head ( [ rdf:type swrl:ClassAtom ; swrl:classPredicate :RetentionOffering ; swrl:argument1 :v_ro5 ] ) ] .

[ rdf:type swrl:Imp ;
  rdfs:label "R-O03.attach"@en ;
  swrl:body ( [ rdf:type swrl:ClassAtom ; swrl:classPredicate :Offering ; swrl:argument1 :v_ro7 ]
              [ rdf:type swrl:DatavaluedPropertyAtom ; swrl:propertyPredicate :attachRateDropFlag ; swrl:argument1 :v_ro7 ; swrl:argument2 :v_ro6 ]
              [ rdf:type swrl:BuiltinAtom ; swrl:builtin swrlb:equal ; swrl:arguments ( :v_ro6 "true"^^xsd:boolean ) ] ) ;
  swrl:head ( [ rdf:type swrl:ClassAtom ; swrl:classPredicate :BundleUpsellOffering ; swrl:argument1 :v_ro7 ] ) ] .

[ rdf:type swrl:Imp ;
  rdfs:label "R-O04.arpu"@en ;
  swrl:body ( [ rdf:type swrl:ClassAtom ; swrl:classPredicate :Offering ; swrl:argument1 :v_ro7 ]
              [ rdf:type swrl:DatavaluedPropertyAtom ; swrl:propertyPredicate :arpuDropFlag ; swrl:argument1 :v_ro7 ; swrl:argument2 :v_ro6 ]
              [ rdf:type swrl:BuiltinAtom ; swrl:builtin swrlb:equal ; swrl:arguments ( :v_ro6 "true"^^xsd:boolean ) ] ) ;
  swrl:head ( [ rdf:type swrl:ClassAtom ; swrl:classPredicate :PriceOptimizeOffering ; swrl:argument1 :v_ro7 ] ) ] .

[ rdf:type swrl:Imp ;
  rdfs:label "R-O05.active"@en ;
  swrl:body ( [ rdf:type swrl:ClassAtom ; swrl:classPredicate :Offering ; swrl:argument1 :v_ro7 ]
              [ rdf:type swrl:DatavaluedPropertyAtom ; swrl:propertyPredicate :activeUsersDropFlag ; swrl:argument1 :v_ro7 ; swrl:argument2 :v_ro6 ]
              [ rdf:type swrl:BuiltinAtom ; swrl:builtin swrlb:equal ; swrl:arguments ( :v_ro6 "true"^^xsd:boolean ) ] ) ;
  swrl:head ( [ rdf:type swrl:ClassAtom ; swrl:classPredicate :EngagementOffering ; swrl:argument1 :v_ro7 ] ) ] .
'''

s = s.replace(ANCHOR, ANCHOR + RULES, 1)
io.open(P, 'w', encoding='utf-8').write(s)
print('product.ttl patched, len=', len(s))
