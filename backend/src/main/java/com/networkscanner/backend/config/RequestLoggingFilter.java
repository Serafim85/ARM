package com.networkscanner.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

  private static final String REQUEST_ID_MDC_KEY = "requestId";
  private static final String REQUEST_ID_HEADER = "X-Request-Id";

  private static final Set<String> EXCLUDED_PREFIXES = Set.of(
      "/swagger-ui",
      "/v3/api-docs"
  );

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path == null) {
      return true;
    }
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    for (String prefix : EXCLUDED_PREFIXES) {
      if (path.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    long startedAtNanos = System.nanoTime();

    String requestId = resolveRequestId(request);
    MDC.put(REQUEST_ID_MDC_KEY, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
      int status = response.getStatus();

      String path = request.getRequestURI();
      String method = request.getMethod();
      String remoteAddr = request.getRemoteAddr();

      if (status >= 500) {
        log.warn("HTTP {} {} -> {} ({} ms, ip={})", method, path, status, durationMs, remoteAddr);
      } else {
        log.debug("HTTP {} {} -> {} ({} ms, ip={})", method, path, status, durationMs, remoteAddr);
      }

      MDC.remove(REQUEST_ID_MDC_KEY);
    }
  }

  private static String resolveRequestId(HttpServletRequest request) {
    String headerValue = request.getHeader(REQUEST_ID_HEADER);
    if (headerValue != null && !headerValue.isBlank()) {
      return headerValue.trim();
    }
    return UUID.randomUUID().toString();
  }
}

