package com.notetaker.notes.v1.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** An application user, provisioned from the JWT {@code employeeNumber} (EMP_NBR) on first use. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "APP_USER")
public class AppUserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "USER_ID")
  private Long id;

  @Column(name = "EMP_NBR", nullable = false, length = 50)
  private String employeeNumber;

  @Column(name = "EMAIL_TXT", nullable = false, length = 255)
  private String email;

  @Column(name = "DSPL_NM", nullable = false, length = 100)
  private String displayName;

  @Column(name = "REC_CRTN_TMSTP", nullable = false, updatable = false)
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "REC_MODIFY_TMSTP", nullable = false)
  @UpdateTimestamp
  private Instant modifiedAt;
}
