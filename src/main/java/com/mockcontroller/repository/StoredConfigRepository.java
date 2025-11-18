package com.mockcontroller.repository;

import com.mockcontroller.model.entity.StoredConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoredConfigRepository extends JpaRepository<StoredConfigEntity, String> {
    
    Optional<StoredConfigEntity> findBySystemName(String systemName);
    
    boolean existsBySystemName(String systemName);
}

