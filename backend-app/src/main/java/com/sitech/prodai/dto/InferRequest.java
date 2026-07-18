package com.sitech.prodai.dto;

import java.util.Map;

public class InferRequest {

    private Map<String, Object> slots;
    private Map<String, Object> draft;

    public Map<String, Object> getSlots() {
        return slots;
    }

    public void setSlots(Map<String, Object> slots) {
        this.slots = slots;
    }

    public Map<String, Object> getDraft() {
        return draft;
    }

    public void setDraft(Map<String, Object> draft) {
        this.draft = draft;
    }
}
