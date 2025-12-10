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
    
    // Находим обновления, которые должны быть применены (время наступило или прошло) и еще не применены
    @Query("SELECT s FROM ScheduledConfigUpdateEntity s WHERE s.scheduledTime <= :now AND (s.applied = false OR s.applied IS NULL) ORDER BY s.scheduledTime ASC")
    List<ScheduledConfigUpdateEntity> findDueUpdates(LocalDateTime now);
    
    @Modifying
    @Query("DELETE FROM ScheduledConfigUpdateEntity s WHERE s.systemName = :systemName")
    void deleteBySystemName(String systemName);
    
    /**
     * Находит запланированные обновления для указанной системы и времени (только не примененные)
     */
    @Query("SELECT s FROM ScheduledConfigUpdateEntity s WHERE s.systemName = :systemName AND s.scheduledTime = :scheduledTime AND (s.applied = false OR s.applied IS NULL) ORDER BY s.createdAt ASC")
    List<ScheduledConfigUpdateEntity> findBySystemNameAndScheduledTime(String systemName, LocalDateTime scheduledTime);
    
    /**
     * Получает все обновления, отсортированные по времени (для истории)
     */
    @Query("SELECT s FROM ScheduledConfigUpdateEntity s WHERE s.comment LIKE 'Сценарий:%' ORDER BY s.scheduledTime ASC")
    List<ScheduledConfigUpdateEntity> findAllScenarioUpdates();
}

