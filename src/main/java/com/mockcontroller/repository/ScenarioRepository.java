package com.mockcontroller.repository;

import com.mockcontroller.model.entity.ScenarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScenarioRepository extends JpaRepository<ScenarioEntity, String> {
}

