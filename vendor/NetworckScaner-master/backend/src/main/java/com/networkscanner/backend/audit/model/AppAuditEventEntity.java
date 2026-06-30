package com.networkscanner.backend.audit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "app_audit_event")
public class AppAuditEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "occurred_at", nullable = false)
  private OffsetDateTime occurredAt;

  @Column(name = "actor_login", nullable = false, length = 320)
  private String actorLogin;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 64)
  private AuditCategory category;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private AuditAction action;

  @Column(nullable = false, length = 512)
  private String target;

  @Column(columnDefinition = "text")
  private String details;

  public Long getId() {
    return id;
  }

  public OffsetDateTime getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(OffsetDateTime occurredAt) {
    this.occurredAt = occurredAt;
  }

  public String getActorLogin() {
    return actorLogin;
  }

  public void setActorLogin(String actorLogin) {
    this.actorLogin = actorLogin;
  }

  public AuditCategory getCategory() {
    return category;
  }

  public void setCategory(AuditCategory category) {
    this.category = category;
  }

  public AuditAction getAction() {
    return action;
  }

  public void setAction(AuditAction action) {
    this.action = action;
  }

  public String getTarget() {
    return target;
  }

  public void setTarget(String target) {
    this.target = target;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(String details) {
    this.details = details;
  }
}
