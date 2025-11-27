package com.mockcontroller.repository;

import com.mockcontroller.model.entity.ScenarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ScenarioRepository extends JpaRepository<ScenarioEntity, String> {
    boolean existsByGroupIdAndName(String groupId, String name);
    boolean existsByGroupIdAndNameAndIdNot(String groupId, String name, String id);
    Optional<ScenarioEntity> findByGroupIdAndName(String groupId, String name);
}
