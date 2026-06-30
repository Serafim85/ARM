package com.networkscanner.backend.users.api;

import com.networkscanner.backend.users.dto.DirectorySettingsDto;
import com.networkscanner.backend.users.dto.UpdateDirectorySettingsRequest;

public interface DirectorySettingsService {

  DirectorySettingsDto getSettings();

  DirectorySettingsDto updateSettings(UpdateDirectorySettingsRequest request);
}
