package com.ddicg.erp.core.event.domainevent;

public class ActiveLogDto {
    private String entityId;
    private String action;
    private String actor;

    public ActiveLogDto() {}
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
}
