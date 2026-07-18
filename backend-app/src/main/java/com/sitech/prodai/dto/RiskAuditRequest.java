package com.sitech.prodai.dto;

import java.util.List;

public class RiskAuditRequest {

    private List<String> offeringIds;

    public List<String> getOfferingIds() {
        return offeringIds;
    }

    public void setOfferingIds(List<String> offeringIds) {
        this.offeringIds = offeringIds;
    }
}
