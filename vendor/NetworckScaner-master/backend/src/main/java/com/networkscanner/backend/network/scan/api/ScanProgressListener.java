package com.networkscanner.backend.network.scan.api;

@FunctionalInterface
public interface ScanProgressListener {

  void onProgress(int scannedAddresses, int totalAddresses);
}
