package com.networkscanner.backend.network.scan.api;

public interface ReverseDnsLookupService {

  /**
   * Resolves PTR record for the given IP. Returns "-" when lookup fails or no PTR is configured.
   */
  String lookup(String ip);
}
