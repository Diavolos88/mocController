package com.mockcontroller.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "group_systems")
public class GroupSystemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private GroupEntity group;

    @Column(name = "system_name", nullable = false, length = 255)
    private String systemName;

    public GroupSystemEntity() {
    }

    public GroupSystemEntity(GroupEntity group, String systemName) {
        this.group = group;
        this.systemName = systemName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public GroupEntity getGroup() {
        return group;
    }

    public void setGroup(GroupEntity group) {
        this.group = group;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }
}

