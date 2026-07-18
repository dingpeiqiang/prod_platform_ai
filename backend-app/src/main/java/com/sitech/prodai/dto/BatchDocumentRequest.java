package com.sitech.prodai.dto;

import java.util.List;
import java.util.Map;

public class BatchDocumentRequest {

    private String documentText = "";
    private List<Map<String, Object>> packages;

    public String getDocumentText() {
        return documentText;
    }

    public void setDocumentText(String documentText) {
        this.documentText = documentText;
    }

    public List<Map<String, Object>> getPackages() {
        return packages;
    }

    public void setPackages(List<Map<String, Object>> packages) {
        this.packages = packages;
    }
}
