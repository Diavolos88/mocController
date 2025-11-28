package com.mockcontroller.repository;

import com.mockcontroller.model.entity.GroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<GroupEntity, String> {
    List<GroupEntity> findAllByOrderByNameAsc();
    Optional<GroupEntity> findByNameIgnoreCase(String name);
}

