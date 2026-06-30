package com.networkscanner.backend.workstation.api;

import com.networkscanner.backend.workstation.dto.WorkstationDetailDto;
import com.networkscanner.backend.workstation.dto.WorkstationEventEntryDto;
import com.networkscanner.backend.workstation.dto.WorkstationFilter;
import com.networkscanner.backend.workstation.dto.WorkstationLogEntryDto;
import com.networkscanner.backend.workstation.dto.WorkstationMetricsHistoryDto;
import com.networkscanner.backend.workstation.dto.WorkstationPageDto;
import java.time.OffsetDateTime;
import java.util.List;

public interface WorkstationPort {

  WorkstationPageDto list(WorkstationFilter filter, int page, int size, String sortField, String sortOrder);

  WorkstationDetailDto getById(long id);

  WorkstationMetricsHistoryDto getMetricsHistory(
      long id,
      OffsetDateTime from,
      OffsetDateTime to,
      Integer maxPoints
  );

  List<WorkstationLogEntryDto> getLogs(long id, List<String> levels, int limit);

  List<WorkstationEventEntryDto> getEvents(long id, int limit);
}
