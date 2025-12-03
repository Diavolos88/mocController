package com.mockcontroller.repository;

import com.mockcontroller.model.entity.MockInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MockInstanceRepository extends JpaRepository<MockInstanceEntity, Long> {

    Optional<MockInstanceEntity> findBySystemNameAndInstanceId(String systemName, String instanceId);

    List<MockInstanceEntity> findBySystemName(String systemName);
    
    @Modifying
    @Query("DELETE FROM MockInstanceEntity m WHERE m.systemName = :systemName")
    void deleteBySystemName(@Param("systemName") String systemName);

    List<MockInstanceEntity> findAllByOrderBySystemNameAsc();

    @Modifying
    @Query("DELETE FROM MockInstanceEntity m WHERE m.lastHealthcheckTime < :threshold")
    void deleteOldInstances(@Param("threshold") Instant threshold);

    @Query("SELECT m FROM MockInstanceEntity m WHERE m.lastHealthcheckTime >= :threshold")
    List<MockInstanceEntity> findActiveInstances(@Param("threshold") Instant threshold);
}

