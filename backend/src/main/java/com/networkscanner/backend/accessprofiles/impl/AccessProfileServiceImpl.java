package com.networkscanner.backend.accessprofiles.impl;

import com.networkscanner.backend.accessprofiles.api.AccessProfileService;
import com.networkscanner.backend.accessprofiles.dto.AccessProfileDetailDto;
import com.networkscanner.backend.accessprofiles.dto.AccessProfileSummaryDto;
import com.networkscanner.backend.accessprofiles.dto.UpsertAccessProfileRequest;
import com.networkscanner.backend.accessprofiles.model.AccessProfileEntity;
import com.networkscanner.backend.accessprofiles.repository.AccessProfileRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccessProfileServiceImpl implements AccessProfileService {

  private final AccessProfileRepository repository;

  public AccessProfileServiceImpl(AccessProfileRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<AccessProfileSummaryDto> listSummaries() {
    return repository.findAllByOrderByNameAsc().stream().map(this::toSummary).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<AccessProfileDetailDto> listDetails() {
    return repository.findAllByOrderByNameAsc().stream().map(this::toDetail).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public AccessProfileDetailDto getById(Long id) {
    return toDetail(requireEntity(id));
  }

  @Override
  @Transactional(readOnly = true)
  public AccessProfileEntity requireEntity(Long id) {
    if (id == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не указан идентификатор профиля доступа.");
    }
    return repository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Профиль доступа не найден."));
  }

  @Override
  @Transactional
  public AccessProfileDetailDto create(UpsertAccessProfileRequest request) {
    String name = normalizeName(request.name());
    if (repository.findByNameIgnoreCase(name).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Профиль с таким именем уже существует.");
    }
    validateProtocols(request, null);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    AccessProfileEntity entity = new AccessProfileEntity();
    entity.setName(name);
    applyRequest(entity, request, true);
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    return toDetail(repository.save(entity));
  }

  @Override
  @Transactional
  public AccessProfileDetailDto update(Long id, UpsertAccessProfileRequest request) {
    AccessProfileEntity entity = requireEntity(id);
    String name = normalizeName(request.name());
    if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Профиль с таким именем уже существует.");
    }
    validateProtocols(request, entity);
    entity.setName(name);
    applyRequest(entity, request, false);
    entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    return toDetail(repository.save(entity));
  }

  @Override
  @Transactional
  public void delete(Long id) {
    AccessProfileEntity entity = requireEntity(id);
    long usage = repository.countScanJobsUsingProfile(id);
    if (usage > 0) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Профиль используется в " + usage + " задачах сканирования и не может быть удалён."
      );
    }
    repository.delete(entity);
  }

  private void validateProtocols(UpsertAccessProfileRequest request, AccessProfileEntity existing) {
    if (!Boolean.TRUE.equals(request.snmpV1Enabled())
        && !Boolean.TRUE.equals(request.snmpV2Enabled())
        && !Boolean.TRUE.equals(request.snmpV3Enabled())
        && !Boolean.TRUE.equals(request.sshEnabled())
        && !Boolean.TRUE.equals(request.httpsEnabled())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Включите хотя бы один протокол (SNMP, SSH или HTTPS)."
      );
    }
    if (Boolean.TRUE.equals(request.snmpV1Enabled()) && requiresCommunity(
        request.snmpV1Community(),
        request.clearSnmpV1Community(),
        existing == null ? null : existing.getSnmpV1Community()
    )) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для SNMP v1 укажите community string.");
    }
    if (Boolean.TRUE.equals(request.snmpV2Enabled()) && requiresCommunity(
        request.snmpV2Community(),
        request.clearSnmpV2Community(),
        existing == null ? null : existing.getSnmpV2Community()
    )) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для SNMP v2c укажите community string.");
    }
    if (Boolean.TRUE.equals(request.snmpV3Enabled()) && isBlank(request.snmpV3SecurityUsername())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для SNMP v3 укажите имя пользователя.");
    }
  }

  private boolean requiresCommunity(String incoming, Boolean clear, String existing) {
    if (!isBlank(incoming)) {
      return false;
    }
    if (Boolean.TRUE.equals(clear)) {
      return true;
    }
    return !hasText(existing);
  }

  private void applyRequest(AccessProfileEntity entity, UpsertAccessProfileRequest request, boolean creating) {
    entity.setDescription(trimToNull(request.description()));

    entity.setSnmpV1Enabled(Boolean.TRUE.equals(request.snmpV1Enabled()));
    entity.setSnmpV1Port(request.snmpV1Port() == null ? 161 : request.snmpV1Port());
    applySecret(
        creating,
        Boolean.TRUE.equals(request.clearSnmpV1Community()),
        request.snmpV1Community(),
        entity::setSnmpV1Community,
        entity::getSnmpV1Community
    );

    entity.setSnmpV2Enabled(Boolean.TRUE.equals(request.snmpV2Enabled()));
    entity.setSnmpV2Port(request.snmpV2Port() == null ? 161 : request.snmpV2Port());
    applySecret(
        creating,
        Boolean.TRUE.equals(request.clearSnmpV2Community()),
        request.snmpV2Community(),
        entity::setSnmpV2Community,
        entity::getSnmpV2Community
    );

    entity.setSnmpV3Enabled(Boolean.TRUE.equals(request.snmpV3Enabled()));
    entity.setSnmpV3Port(request.snmpV3Port() == null ? 161 : request.snmpV3Port());
    entity.setSnmpV3SecurityUsername(trimToNull(request.snmpV3SecurityUsername()));
    entity.setSnmpV3AuthProtocol(trimToNull(request.snmpV3AuthProtocol()));
    applySecret(
        creating,
        Boolean.TRUE.equals(request.clearSnmpV3AuthPassword()),
        request.snmpV3AuthPassword(),
        entity::setSnmpV3AuthPassword,
        entity::getSnmpV3AuthPassword
    );
    entity.setSnmpV3PrivacyProtocol(trimToNull(request.snmpV3PrivacyProtocol()));
    applySecret(
        creating,
        Boolean.TRUE.equals(request.clearSnmpV3PrivacyPassword()),
        request.snmpV3PrivacyPassword(),
        entity::setSnmpV3PrivacyPassword,
        entity::getSnmpV3PrivacyPassword
    );

    entity.setSshEnabled(Boolean.TRUE.equals(request.sshEnabled()));
    entity.setSshPort(request.sshPort() == null ? 22 : request.sshPort());
    entity.setSshUsername(trimToNull(request.sshUsername()));
    applySecret(
        creating,
        Boolean.TRUE.equals(request.clearSshPassword()),
        request.sshPassword(),
        entity::setSshPassword,
        entity::getSshPassword
    );
    applySecret(
        creating,
        Boolean.TRUE.equals(request.clearSshPrivateKey()),
        request.sshPrivateKeyPem(),
        entity::setSshPrivateKeyPem,
        entity::getSshPrivateKeyPem
    );
    applySecret(
        creating,
        Boolean.TRUE.equals(request.clearSshPassphrase()),
        request.sshPassphrase(),
        entity::setSshPassphrase,
        entity::getSshPassphrase
    );

    entity.setHttpsEnabled(Boolean.TRUE.equals(request.httpsEnabled()));
    entity.setHttpsPort(request.httpsPort() == null ? 443 : request.httpsPort());
    entity.setHttpsUsername(trimToNull(request.httpsUsername()));
    applySecret(
        creating,
        Boolean.TRUE.equals(request.clearHttpsPassword()),
        request.httpsPassword(),
        entity::setHttpsPassword,
        entity::getHttpsPassword
    );
    applySecret(
        creating,
        Boolean.TRUE.equals(request.clearHttpsClientCert()),
        request.httpsClientCertPem(),
        entity::setHttpsClientCertPem,
        entity::getHttpsClientCertPem
    );
    applySecret(
        creating,
        Boolean.TRUE.equals(request.clearHttpsClientKey()),
        request.httpsClientKeyPem(),
        entity::setHttpsClientKeyPem,
        entity::getHttpsClientKeyPem
    );
    entity.setHttpsInsecureSkipVerify(Boolean.TRUE.equals(request.httpsInsecureSkipVerify()));
  }

  private void applySecret(
      boolean creating,
      boolean clear,
      String incoming,
      java.util.function.Consumer<String> setter,
      java.util.function.Supplier<String> current
  ) {
    if (clear) {
      setter.accept(null);
      return;
    }
    if (incoming != null && !incoming.isBlank()) {
      setter.accept(incoming);
      return;
    }
    if (creating) {
      setter.accept(null);
    } else {
      setter.accept(current.get());
    }
  }

  private AccessProfileSummaryDto toSummary(AccessProfileEntity entity) {
    return new AccessProfileSummaryDto(
        entity.getId(),
        entity.getName(),
        entity.getDescription(),
        entity.isSnmpV1Enabled(),
        entity.isSnmpV2Enabled(),
        entity.isSnmpV3Enabled(),
        entity.isSshEnabled(),
        entity.isHttpsEnabled()
    );
  }

  private AccessProfileDetailDto toDetail(AccessProfileEntity entity) {
    return new AccessProfileDetailDto(
        entity.getId(),
        entity.getName(),
        entity.getDescription(),
        entity.isSnmpV1Enabled(),
        entity.getSnmpV1Port(),
        maskSecret(entity.getSnmpV1Community()),
        hasText(entity.getSnmpV1Community()),
        entity.isSnmpV2Enabled(),
        entity.getSnmpV2Port(),
        maskSecret(entity.getSnmpV2Community()),
        hasText(entity.getSnmpV2Community()),
        entity.isSnmpV3Enabled(),
        entity.getSnmpV3Port(),
        entity.getSnmpV3SecurityUsername(),
        entity.getSnmpV3AuthProtocol(),
        hasText(entity.getSnmpV3AuthPassword()),
        entity.getSnmpV3PrivacyProtocol(),
        hasText(entity.getSnmpV3PrivacyPassword()),
        entity.isSshEnabled(),
        entity.getSshPort(),
        entity.getSshUsername(),
        hasText(entity.getSshPassword()),
        hasText(entity.getSshPrivateKeyPem()),
        hasText(entity.getSshPassphrase()),
        entity.isHttpsEnabled(),
        entity.getHttpsPort(),
        entity.getHttpsUsername(),
        hasText(entity.getHttpsPassword()),
        hasText(entity.getHttpsClientCertPem()),
        hasText(entity.getHttpsClientKeyPem()),
        entity.isHttpsInsecureSkipVerify(),
        entity.getCreatedAt(),
        entity.getUpdatedAt()
    );
  }

  private static String maskSecret(String value) {
    return hasText(value) ? "********" : null;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static String normalizeName(String name) {
    if (name == null || name.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите имя профиля.");
    }
    return name.trim();
  }

  private static String trimToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
