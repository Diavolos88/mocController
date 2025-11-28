package com.mockcontroller.repository;

import com.mockcontroller.model.entity.ScheduledConfigUpdateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ScheduledConfigUpdateRepository extends JpaRepository<ScheduledConfigUpdateEntity, String> {
    
    List<ScheduledConfigUpdateEntity> findBySystemNameOrderByScheduledTimeAsc(String systemName);
    
    boolean existsBySystemName(String systemName);
    
    // Находим обновления, которые должны быть применены (время наступило или прошло)
    // Используем <= для того, чтобы захватить обновления, которые уже должны были быть применены
    @Query("SELECT s FROM ScheduledConfigUpdateEntity s WHERE s.scheduledTime <= :now ORDER BY s.scheduledTime ASC")
    List<ScheduledConfigUpdateEntity> findDueUpdates(LocalDateTime now);
    
    @Modifying
    @Query("DELETE FROM ScheduledConfigUpdateEntity s WHERE s.systemName = :systemName")
    void deleteBySystemName(String systemName);
}

