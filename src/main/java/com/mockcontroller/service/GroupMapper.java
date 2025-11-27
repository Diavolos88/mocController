package com.mockcontroller.service;

import com.mockcontroller.model.Group;
import com.mockcontroller.model.entity.GroupEntity;
import com.mockcontroller.model.entity.GroupSystemEntity;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class GroupMapper {

    public Group toModel(GroupEntity entity) {
        if (entity == null) {
            return null;
        }
        
        return new Group(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getSystems().stream()
                .map(GroupSystemEntity::getSystemName)
                .collect(Collectors.toList()),
            entity.getCreatedAt()
        );
    }

    public GroupEntity toEntity(Group group) {
        if (group == null) {
            return null;
        }
        
        GroupEntity entity = new GroupEntity();
        if (group.getId() != null && !group.getId().isEmpty()) {
            entity.setId(group.getId());
        }
        entity.setName(group.getName());
        entity.setDescription(group.getDescription());
        if (group.getCreatedAt() != null) {
            entity.setCreatedAt(group.getCreatedAt());
        }
        
        // Системы будут добавлены отдельно в сервисе
        return entity;
    }
}

