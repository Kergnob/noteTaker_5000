package com.notetaker.notes.v1.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.notetaker.notes.v1.repository.AppUserRepository;
import com.notetaker.notes.v1.repository.entity.AppUserEntity;
import com.notetaker.security.CurrentUserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the internal {@link AppUserEntity} from an external identity (JWT employeeNumber),
 * provisioning a row on first sight so callers never have to be pre-registered.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

  private final AppUserRepository appUserRepository;
  private final CurrentUserService currentUserService;

  public AppUserEntity currentUser() {
    return getOrCreate(currentUserService.getCurrentUserId());
  }

  public AppUserEntity getOrCreate(String employeeNumber) {
    return appUserRepository.findByEmployeeNumber(employeeNumber)
        .orElseGet(() -> provision(employeeNumber));
  }

  public java.util.List<AppUserEntity> findAll(java.util.Collection<Long> ids) {
    return appUserRepository.findAllById(ids);
  }

  private AppUserEntity provision(String employeeNumber) {
    AppUserEntity user = new AppUserEntity();
    user.setEmployeeNumber(employeeNumber);
    // Synthesize an email/display name from the employee number.
    user.setEmail(employeeNumber + "@notetaker.local");
    user.setDisplayName(employeeNumber);
    AppUserEntity saved = appUserRepository.save(user);
    log.info("Provisioned user {}", saved.getId());
    return saved;
  }
}
