package com.sitech.prodai.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HistoryService {

    public Map<String, Object> listHistory(String userId) {
        return Map.of("success", true, "items", List.of());
    }

    public Map<String, Object> getRecommendValues(String formCode, String fieldCode, String userId, Object context) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("formCode", formCode);
        result.put("fieldCode", fieldCode);
        result.put("userId", userId);
        result.put("recommendations", List.of());
        return result;
    }
}
