package com.sitech.prodai.dto;

import java.util.Map;

public class ComplianceRequest {

    private Map<String, Object> draft;

    public Map<String, Object> getDraft() {
        return draft;
    }

    public void setDraft(Map<String, Object> draft) {
        this.draft = draft;
    }
}
