package com.mockcontroller.repository;

import com.mockcontroller.model.entity.GroupSystemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupSystemRepository extends JpaRepository<GroupSystemEntity, String> {
    List<GroupSystemEntity> findByGroup_Id(String groupId);
    void deleteByGroupId(String groupId);
    void deleteByGroupIdAndSystemName(String groupId, String systemName);
}

