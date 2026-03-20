package com.logilink.emailanalyzer.domain;

import java.io.Serializable;

public class Breakdown implements Serializable {
    private Integer businessPriority;
    private Integer urgency;
    private Integer actionRequired;
    private Integer financialImpact;

    public Breakdown() {
    }

    public Breakdown(Integer businessPriority, Integer urgency, Integer actionRequired, Integer financialImpact) {
        this.businessPriority = businessPriority;
        this.urgency = urgency;
        this.actionRequired = actionRequired;
        this.financialImpact = financialImpact;
    }

    public static BreakdownBuilder builder() {
        return new BreakdownBuilder();
    }

    public static class BreakdownBuilder {
        private Breakdown b = new Breakdown();

        public BreakdownBuilder businessPriority(Integer p) {
            b.setBusinessPriority(p);
            return this;
        }

        public BreakdownBuilder urgency(Integer u) {
            b.setUrgency(u);
            return this;
        }

        public BreakdownBuilder actionRequired(Integer a) {
            b.setActionRequired(a);
            return this;
        }

        public BreakdownBuilder financialImpact(Integer f) {
            b.setFinancialImpact(f);
            return this;
        }

        public Breakdown build() {
            return b;
        }
    }

    public Integer getBusinessPriority() {
        return businessPriority;
    }

    public void setBusinessPriority(Integer businessPriority) {
        this.businessPriority = businessPriority;
    }

    public Integer getUrgency() {
        return urgency;
    }

    public void setUrgency(Integer urgency) {
        this.urgency = urgency;
    }

    public Integer getActionRequired() {
        return actionRequired;
    }

    public void setActionRequired(Integer actionRequired) {
        this.actionRequired = actionRequired;
    }

    public Integer getFinancialImpact() {
        return financialImpact;
    }

    public void setFinancialImpact(Integer financialImpact) {
        this.financialImpact = financialImpact;
    }
}
