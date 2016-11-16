package com.sap.cloud.lm.sl.cf.core.dto.serialization.v2;

import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.google.gson.annotations.JsonAdapter;
import com.sap.cloud.lm.sl.common.model.json.PropertiesAdapterFactory;
import com.sap.cloud.lm.sl.common.model.xml.PropertiesAdapter;
import com.sap.cloud.lm.sl.mta.model.v2_0.PlatformResourceType;
import com.sap.cloud.lm.sl.mta.model.v2_0.PlatformResourceType.PlatformResourceTypeBuilder;

public class PlatformResourceTypeDto {

    @XmlElement
    protected String name;

    @JsonAdapter(PropertiesAdapterFactory.class)
    @XmlJavaTypeAdapter(PropertiesAdapter.class)
    protected Map<String, Object> parameters;

    protected PlatformResourceTypeDto() {
        // Required by JAXB
    }

    public PlatformResourceTypeDto(PlatformResourceType resourceType) {
        name = resourceType.getName();
        parameters = resourceType.getParameters();
    }

    public PlatformResourceType toPlatformResourceType() {
        PlatformResourceTypeBuilder result = new PlatformResourceTypeBuilder();
        result.setName(name);
        result.setParameters(parameters);
        return result.build();
    }

}
