package org.example.store;

import org.example.model.Models;

import java.util.List;
import java.util.Map;

public interface OntologyStore {
    Map<String, Models.FactSet> retrieve(List<Models.EntityRef> refs, String namespace);

    List<String> classes();

    List<String> properties();

    List<Map<String, Object>> sparqlSelect(String query);

    List<Map<String, Object>> samplesFor(String className);

    // === 本体管理方法 ===

    /** 添加一个新类 */
    void addClass(String className);

    /** 添加一个新属性 */
    void addProperty(String propertyName);

    /** 获取所有实例（含属性） */
    List<Map<String, Object>> allInstances();

    /** 添加一个新实例 */
    void addInstance(String uri, String type, Map<String, Object> facts);

    /** 更新实例属性 */
    void updateInstance(String uri, Map<String, Object> facts);

    /** 删除实例 */
    void deleteInstance(String uri);

    /** 获取实例统计信息 */
    Map<String, Object> stats();
}
