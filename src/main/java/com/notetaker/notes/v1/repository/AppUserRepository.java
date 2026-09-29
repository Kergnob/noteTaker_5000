package com.notetaker.notes.v1.repository;

import com.notetaker.notes.v1.repository.entity.AppUserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {

  Optional<AppUserEntity> findByEmployeeNumber(String employeeNumber);
}
