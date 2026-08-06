package com.ddicg.erp.modules.merchandise.dto;

import java.io.Serializable;
import java.util.List;

public class SpecificationGroupDto implements Serializable {
    private String groupName;
    private List<SpecificationDto> specifications;

    public SpecificationGroupDto() {}

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public List<SpecificationDto> getSpecifications() { return specifications; }
    public void setSpecifications(List<SpecificationDto> specifications) { this.specifications = specifications; }
}
