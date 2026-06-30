package com.networkscanner.backend.accessprofiles.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.networkscanner.backend.accessprofiles.dto.AccessProfileDetailDto;
import com.networkscanner.backend.accessprofiles.dto.UpsertAccessProfileRequest;
import com.networkscanner.backend.accessprofiles.model.AccessProfileEntity;
import com.networkscanner.backend.accessprofiles.repository.AccessProfileRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AccessProfileServiceImplTest {

  @Mock
  private AccessProfileRepository repository;

  @InjectMocks
  private AccessProfileServiceImpl service;

  @Test
  void create_masksSecretsInResponse() {
    when(repository.findByNameIgnoreCase("lab")).thenReturn(Optional.empty());
    when(repository.save(any(AccessProfileEntity.class))).thenAnswer(invocation -> {
      AccessProfileEntity entity = invocation.getArgument(0);
      entity.setId(1L);
      entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
      entity.setUpdatedAt(entity.getCreatedAt());
      return entity;
    });

    AccessProfileDetailDto created = service.create(requestV2("lab", "secret-community"));

    assertEquals("********", created.snmpV2Community());
    assertEquals(true, created.hasSnmpV2Community());
    ArgumentCaptor<AccessProfileEntity> captor = ArgumentCaptor.forClass(AccessProfileEntity.class);
    verify(repository).save(captor.capture());
    assertEquals("secret-community", captor.getValue().getSnmpV2Community());
  }

  @Test
  void delete_rejectsWhenUsedByScanJobs() {
    AccessProfileEntity entity = new AccessProfileEntity();
    entity.setId(5L);
    entity.setName("in-use");
    when(repository.findById(5L)).thenReturn(Optional.of(entity));
    when(repository.countScanJobsUsingProfile(5L)).thenReturn(2L);

    ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.delete(5L));
    assertEquals(409, ex.getStatusCode().value());
  }

  @Test
  void update_keepsSnmpV2CommunityWhenFieldLeftEmpty() {
    AccessProfileEntity entity = new AccessProfileEntity();
    entity.setId(3L);
    entity.setName("lab");
    entity.setSnmpV2Enabled(true);
    entity.setSnmpV2Community("secret-community");
    entity.setSshEnabled(false);
    entity.setHttpsEnabled(false);
    entity.setHttpsInsecureSkipVerify(false);
    entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    entity.setUpdatedAt(entity.getCreatedAt());
    when(repository.findById(3L)).thenReturn(Optional.of(entity));
    when(repository.existsByNameIgnoreCaseAndIdNot("lab", 3L)).thenReturn(false);
    when(repository.save(any(AccessProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    AccessProfileDetailDto updated = service.update(3L, requestV2("lab", ""));

    assertEquals("********", updated.snmpV2Community());
    assertEquals(true, updated.hasSnmpV2Community());
    ArgumentCaptor<AccessProfileEntity> captor = ArgumentCaptor.forClass(AccessProfileEntity.class);
    verify(repository).save(captor.capture());
    assertEquals("secret-community", captor.getValue().getSnmpV2Community());
  }

  @Test
  void getById_masksPasswordFlags() {
    AccessProfileEntity entity = new AccessProfileEntity();
    entity.setId(2L);
    entity.setName("p");
    entity.setSnmpV3Enabled(true);
    entity.setSnmpV3SecurityUsername("user");
    entity.setSnmpV3AuthPassword("auth");
    entity.setSnmpV3PrivacyPassword("priv");
    entity.setSshEnabled(false);
    entity.setHttpsEnabled(false);
    entity.setHttpsInsecureSkipVerify(false);
    entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    entity.setUpdatedAt(entity.getCreatedAt());
    when(repository.findById(2L)).thenReturn(Optional.of(entity));

    AccessProfileDetailDto dto = service.getById(2L);

    assertFalse(dto.hasSnmpV2Community());
    assertEquals(true, dto.hasSnmpV3AuthPassword());
    assertEquals(true, dto.hasSnmpV3PrivacyPassword());
  }

  private static UpsertAccessProfileRequest requestV2(String name, String community) {
    return new UpsertAccessProfileRequest(
        name,
        null,
        false,
        161,
        null,
        false,
        true,
        161,
        community,
        false,
        false,
        161,
        null,
        null,
        null,
        false,
        null,
        null,
        false,
        false,
        22,
        null,
        null,
        false,
        null,
        false,
        null,
        false,
        false,
        443,
        null,
        null,
        false,
        null,
        false,
        null,
        false,
        false
    );
  }
}
