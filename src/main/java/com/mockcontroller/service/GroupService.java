package com.mockcontroller.service;

import com.mockcontroller.model.Group;
import com.mockcontroller.model.entity.GroupEntity;
import com.mockcontroller.model.entity.GroupSystemEntity;
import com.mockcontroller.repository.GroupRepository;
import com.mockcontroller.repository.GroupSystemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private final GroupRepository repository;
    private final GroupSystemRepository systemRepository;
    private final GroupMapper mapper;

    public GroupService(GroupRepository repository, 
                       GroupSystemRepository systemRepository,
                       GroupMapper mapper) {
        this.repository = repository;
        this.systemRepository = systemRepository;
        this.mapper = mapper;
    }

    public Collection<Group> findAll() {
        return repository.findAllByOrderByNameAsc().stream()
                .map(mapper::toModel)
                .collect(Collectors.toList());
    }

    public Optional<Group> findById(String id) {
        return repository.findById(id).map(mapper::toModel);
    }

    public Optional<Group> findByName(String name) {
        return repository.findByNameIgnoreCase(name).map(mapper::toModel);
    }

    @Transactional
    public Group createGroup(String name, String description, List<String> systemNames) {
        GroupEntity entity = new GroupEntity();
        entity.setName(name);
        entity.setDescription(description);
        entity.setCreatedAt(Instant.now());

        // Добавляем системы в группу перед сохранением
        if (systemNames != null && !systemNames.isEmpty()) {
            for (String systemName : systemNames) {
                if (systemName != null && !systemName.trim().isEmpty()) {
                    GroupSystemEntity systemEntity = new GroupSystemEntity(entity, systemName);
                    entity.getSystems().add(systemEntity);
                }
            }
        }
        
        entity = repository.save(entity);
        return mapper.toModel(entity);
    }

    @Transactional
    public Group updateGroup(String id, String name, String description, List<String> systemNames) {
        GroupEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + id));

        entity.setName(name);
        entity.setDescription(description);
        
        // Удаляем старые системы
        systemRepository.deleteByGroupId(id);
        entity.getSystems().clear();
        
        // Добавляем новые системы
        if (systemNames != null && !systemNames.isEmpty()) {
            for (String systemName : systemNames) {
                if (systemName != null && !systemName.trim().isEmpty()) {
                    GroupSystemEntity systemEntity = new GroupSystemEntity(entity, systemName);
                    entity.getSystems().add(systemEntity);
                }
            }
        }
        
        entity = repository.save(entity);
        return mapper.toModel(entity);
    }

    @Transactional
    public void deleteGroup(String id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Group not found: " + id);
        }
        systemRepository.deleteByGroupId(id);
        repository.deleteById(id);
    }

    public List<String> getSystemsByGroupId(String groupId) {
        return systemRepository.findByGroup_Id(groupId).stream()
                .map(GroupSystemEntity::getSystemName)
                .collect(Collectors.toList());
    }

    @Transactional
    public void removeSystemFromGroup(String groupId, String systemName) {
        if (!repository.existsById(groupId)) {
            throw new IllegalArgumentException("Group not found: " + groupId);
        }
        systemRepository.deleteByGroupIdAndSystemName(groupId, systemName);
    }
}

