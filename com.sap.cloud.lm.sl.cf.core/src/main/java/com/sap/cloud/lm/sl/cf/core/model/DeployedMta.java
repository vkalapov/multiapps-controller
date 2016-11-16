package com.sap.cloud.lm.sl.cf.core.model;

import java.util.List;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(value = javax.xml.bind.annotation.XmlAccessType.FIELD)
@XmlRootElement(name = "mta")
public class DeployedMta {

    @XmlElement(name = "metadata")
    private DeployedMtaMetadata metadata;

    @XmlElementWrapper(name = "modules")
    @XmlElement(name = "module")
    private List<DeployedMtaModule> modules;

    @XmlElementWrapper(name = "services")
    @XmlElement(name = "service")
    private Set<String> services;

    public DeployedMta() {
        // Required by JAXB
    }

    public DeployedMta(DeployedMtaMetadata metadata, List<DeployedMtaModule> modules, Set<String> services) {
        this.metadata = metadata;
        this.modules = modules;
        this.services = services;
    }

    public DeployedMtaMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(DeployedMtaMetadata metadata) {
        this.metadata = metadata;
    }

    public List<DeployedMtaModule> getModules() {
        return modules;
    }

    public void setModules(List<DeployedMtaModule> modules) {
        this.modules = modules;
    }

    public Set<String> getServices() {
        return services;
    }

    public void setServices(Set<String> services) {
        this.services = services;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((metadata == null) ? 0 : metadata.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DeployedMta other = (DeployedMta) obj;
        if (metadata == null) {
            if (other.metadata != null)
                return false;
        } else if (!metadata.equals(other.metadata))
            return false;
        return true;
    }

    public DeployedMtaModule findDeployedModule(String moduleName) {
        return getModules().stream().filter(module -> module.getModuleName().equalsIgnoreCase(moduleName)).findFirst().orElse(null);
    }

}
