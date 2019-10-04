package com.sap.cloud.lm.sl.cf.core.model;

public enum ResourceTypeEnum {
    HDI_CONTAINER("com.sap.xs.hdi-container");

    private final String resourceType;

    ResourceTypeEnum(String resourceType) {
        this.resourceType = resourceType;
    }

    @Override
    public String toString() {
        return this.resourceType;
    }
}
