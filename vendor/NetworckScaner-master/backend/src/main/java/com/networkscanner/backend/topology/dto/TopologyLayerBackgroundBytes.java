package com.networkscanner.backend.topology.dto;

/** Ответ сервиса при чтении фона слоя (GROUP). */
public record TopologyLayerBackgroundBytes(byte[] data, String contentType) {}
