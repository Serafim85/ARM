package com.networkscanner.backend.network.scan.api;

public interface IcmpProbeService {

  IcmpProbeResult probe(String ip);
}
