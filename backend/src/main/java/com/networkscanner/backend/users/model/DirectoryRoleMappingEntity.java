package com.networkscanner.backend.users.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "directory_role_mappings")
public class DirectoryRoleMappingEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "group_dn", nullable = false, unique = true, length = 1024)
  private String groupDn;

  @Column(name = "group_name", nullable = false, length = 255)
  private String groupName;

  @Column(name = "role_name", nullable = false, length = 32)
  private String roleName;

  public Long getId() {
    return id;
  }

  public String getGroupDn() {
    return groupDn;
  }

  public void setGroupDn(String groupDn) {
    this.groupDn = groupDn;
  }

  public String getGroupName() {
    return groupName;
  }

  public void setGroupName(String groupName) {
    this.groupName = groupName;
  }

  public String getRoleName() {
    return roleName;
  }

  public void setRoleName(String roleName) {
    this.roleName = roleName;
  }
}
