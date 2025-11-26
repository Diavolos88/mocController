package com.mockcontroller.repository;

import com.mockcontroller.model.entity.TemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemplateRepository extends JpaRepository<TemplateEntity, String> {
    
    List<TemplateEntity> findBySystemName(String systemName);
    
    List<TemplateEntity> findAllByOrderByNameAsc();
}

