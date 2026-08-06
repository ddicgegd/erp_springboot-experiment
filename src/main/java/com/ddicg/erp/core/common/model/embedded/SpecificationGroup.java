package com.ddicg.erp.core.common.model.embedded;

import jakarta.persistence.Embeddable;
import java.util.List;

@Embeddable
public class SpecificationGroup {
    private String groupName;
    private List<Object> specifications;

    public SpecificationGroup() {}
    public SpecificationGroup(String groupName, List<Object> specifications) {
        this.groupName = groupName;
        this.specifications = specifications;
    }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public List<Object> getSpecifications() { return specifications; }
    public void setSpecifications(List<Object> specifications) { this.specifications = specifications; }

    public static SpecificationGroupBuilder builder() { return new SpecificationGroupBuilder(); }

    public static class SpecificationGroupBuilder {
        private String groupName;
        private List<Object> specifications;

        SpecificationGroupBuilder() {}

        public SpecificationGroupBuilder groupName(String groupName) { this.groupName = groupName; return this; }
        public SpecificationGroupBuilder specifications(List<Object> specifications) { this.specifications = specifications; return this; }

        public SpecificationGroup build() {
            return new SpecificationGroup(groupName, specifications);
        }
    }
}
