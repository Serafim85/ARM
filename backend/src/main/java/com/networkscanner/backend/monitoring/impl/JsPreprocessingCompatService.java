package com.networkscanner.backend.monitoring.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * JAVASCRIPT preprocessing (Zabbix-like) on Graal Polyglot API: one {@link Engine}, one {@link Context}
 * on a dedicated worker thread, cached {@link Source} per stable compile key.
 *
 * <p>Compilation is keyed by raw {@code scriptBody} plus sorted macro <em>names</em> (not values) to
 * avoid compile churn when only macro values change.
 */
@Component
public class JsPreprocessingCompatService {

  private static final Logger log = LoggerFactory.getLogger(JsPreprocessingCompatService.class);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final long EXECUTION_TIMEOUT_MS = 10_000L;
  private static final long SOFT_HEAP_GUARD_BYTES = 512L * 1024L * 1024L;
  private static final int MAX_SCRIPT_CHARS = 1_000_000;
  private static final int MAX_VALUE_CHARS = 10_000_000;
  private static final String DISCARD_SENTINEL = "__NETSCAN_JS_DISCARD_SENTINEL__";
  private static final String PAYLOAD_WITH_MACROS = "1\n";
  private static final String PAYLOAD_NO_MACROS = "0\n";


  private final Map<String, String> scriptBodiesByHash = new ConcurrentHashMap<>();
  private final Cache<String, Source> sourceCache = Caffeine.newBuilder()
      .maximumSize(1_024)
      .expireAfterAccess(Duration.ofHours(4))
      .recordStats()
      .removalListener((String key, Source value, RemovalCause cause) -> {
        if (key != null) {
          scriptBodiesByHash.remove(key);
        }
      })
      .build();
  private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
  private final Object runtimeLock = new Object();
  private final AtomicLong evaluationsSinceEngineReset = new AtomicLong(0);
  private final AtomicLong lastEngineSoftResetMillis = new AtomicLong(System.currentTimeMillis());
  private final AtomicLong compileDebugSampleCounter = new AtomicLong(0);

  @Nullable
  private final MeterRegistry meterRegistry;

  @Value("${monitoring.js.engine-soft-reset-interval-ms:0}")
  private long engineSoftResetIntervalMs;

  @Value("${monitoring.js.engine-soft-reset-min-evaluations:50000}")
  private long engineSoftResetMinEvaluations;

  private volatile Engine graalEngine;
  private volatile Context graalContext;
  private volatile ExecutorService executorService;

  @Nullable
  private Timer executeTimer;
  @Nullable
  private Timer queueWaitTimer;
  @Nullable
  private Timer evalTimer;
  @Nullable
  private Timer compileTimer;
  @Nullable
  private io.micrometer.core.instrument.Counter runtimeReinitCounter;
  @Nullable
  private io.micrometer.core.instrument.Counter compileTotalCounter;

  @Autowired
  public JsPreprocessingCompatService(Optional<MeterRegistry> meterRegistry) {
    this.meterRegistry = meterRegistry.orElse(null);
    System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
  }

  @PostConstruct
  void registerMicrometerBinders() {
    initializeRuntime();
    if (meterRegistry == null) {
      return;
    }
    executeTimer = Timer.builder("netscan.js.execute")
        .description("Wall time for execute() including queue wait and eval")
        .register(meterRegistry);
    queueWaitTimer = Timer.builder("netscan.js.queue_wait")
        .description("Time spent waiting in the single-thread JS executor queue")
        .register(meterRegistry);
    evalTimer = Timer.builder("netscan.js.eval")
        .description("Time spent inside runScript on the JS worker thread")
        .register(meterRegistry);
    compileTimer = Timer.builder("netscan.js.compile")
        .description("Time spent building cached Graal Source")
        .register(meterRegistry);
    runtimeReinitCounter = io.micrometer.core.instrument.Counter.builder("netscan.js.runtime_reinit_total")
        .description("Number of Graal JS runtime reinitializations (Engine+Context)")
        .register(meterRegistry);
    compileTotalCounter = io.micrometer.core.instrument.Counter.builder("netscan.js.compile_total")
        .description("Number of cache-miss Source builds / compilations")
        .register(meterRegistry);
    Gauge.builder("netscan.js.script_bodies_map_size", scriptBodiesByHash, Map::size)
        .description("Entries in scriptBodiesByHash (tracks source cache keys)")
        .register(meterRegistry);
    CaffeineCacheMetrics.monitor(meterRegistry, sourceCache, "netscan.js.script_cache");
  }

  public JsResult execute(String value, String scriptBody, Map<String, String> macros) {
    String inputValue = value == null ? "" : value;
    if (scriptBody == null || scriptBody.isBlank()) {
      return error("JAVASCRIPT body is empty");
    }
    if (scriptBody.length() > MAX_SCRIPT_CHARS) {
      return error("JAVASCRIPT body is too large");
    }
    if (inputValue.length() > MAX_VALUE_CHARS) {
      return error("JAVASCRIPT value is too large");
    }
    long softBytes = (long) scriptBody.length() + (long) inputValue.length() + macroPayloadBytes(macros);
    if (softBytes > SOFT_HEAP_GUARD_BYTES) {
      return error("JAVASCRIPT soft heap guard exceeded");
    }

    ensureRuntimeReady();

    boolean hasMacros = macros != null && !macros.isEmpty();
    String innerForCompile = hasMacros ? rewriteMacroLiteralsToBridgeCalls(scriptBody, macros) : scriptBody;
    String compileKey = compileCacheKey(scriptBody, macros);
    String payload = (hasMacros ? PAYLOAD_WITH_MACROS : PAYLOAD_NO_MACROS) + innerForCompile;
    scriptBodiesByHash.put(compileKey, payload);

    Map<String, String> macroSnapshot = hasMacros ? Map.copyOf(macros) : Map.of();

    Timer.Sample totalSample = executeTimer == null ? null : Timer.start(meterRegistry);
    long submittedAtNanos = System.nanoTime();
    Future<JsResult> task;
    try {
      task = executorService.submit(
          new JsExecutionTask(submittedAtNanos, compileKey, inputValue, macroSnapshot, hasMacros)
      );
    } catch (RejectedExecutionException rejectedExecutionException) {
      initializeRuntime();
      task = executorService.submit(
          new JsExecutionTask(submittedAtNanos, compileKey, inputValue, macroSnapshot, hasMacros)
      );
    }
    try {
      JsResult result = task.get(EXECUTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      if ("error".equals(result.status())) {
        registerFailure("JAVASCRIPT runtime error: " + result.note());
      } else {
        resetFailures();
      }
      return result;
    } catch (TimeoutException timeoutException) {
      task.cancel(true);
      registerFailure("JAVASCRIPT execution timeout");
      return error("JAVASCRIPT timeout after 10 seconds");
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      registerFailure("JAVASCRIPT execution interrupted");
      return error("JAVASCRIPT execution interrupted");
    } catch (ExecutionException executionException) {
      String message = rootMessage(executionException.getCause());
      registerFailure("JAVASCRIPT execution failed: " + message);
      return error(message);
    } finally {
      if (totalSample != null && executeTimer != null) {
        totalSample.stop(executeTimer);
      }
    }
  }

  @PreDestroy
  public void shutdown() {
    synchronized (runtimeLock) {
      sourceCache.invalidateAll();
      scriptBodiesByHash.clear();
      shutdownExecutorQuietly(executorService);
      executorService = null;
      closeContextQuietly(graalContext);
      graalContext = null;
      closeEngineQuietly(graalEngine);
      graalEngine = null;
    }
  }

  private JsResult runScript(String compileKey, String value, Map<String, String> macros, boolean hasMacros) {
    Context ctx = graalContext;
    if (ctx == null) {
      return error("JAVASCRIPT engine is not initialized");
    }
    try {
      Source source = sourceCache.get(compileKey, this::buildCachedSource);
      var bindings = ctx.getBindings("js");
      bindings.putMember("__ns_value", value);
      if (hasMacros) {
        try {
          bindings.putMember("__ns_macros_json", JSON.writeValueAsString(macros));
        } catch (JsonProcessingException e) {
          return error("JAVASCRIPT macros serialization failed: " + e.getMessage());
        }
      } else {
        bindings.putMember("__ns_macros_json", "");
      }
      var executionResult = ctx.eval(source);
      if (executionResult.isNull()) {
        return error("JAVASCRIPT did not return value");
      }
      String result = executionResult.asString();
      if (DISCARD_SENTINEL.equals(result)) {
        return new JsResult("discarded", value, "JAVASCRIPT returned null");
      }
      JsResult ok = new JsResult("ok", result, null);
      maybeSoftResetRuntime();
      return ok;
    } catch (PolyglotException polyglotException) {
      return error(rootMessage(polyglotException));
    } catch (RuntimeException runtimeException) {
      return error(rootMessage(runtimeException));
    }
  }

  private Source buildCachedSource(String compileKey) {
    if (compileTotalCounter != null) {
      compileTotalCounter.increment();
    }
    maybeLogCompileDebugSample(compileKey);
    String payload = scriptBodiesByHash.get(compileKey);
    if (payload == null) {
      throw new IllegalArgumentException("JAVASCRIPT source not found in cache");
    }
    boolean hasMacros = payload.startsWith(PAYLOAD_WITH_MACROS);
    if (!hasMacros && !payload.startsWith(PAYLOAD_NO_MACROS)) {
      throw new IllegalStateException("Invalid JAVASCRIPT cache payload");
    }
    String inner = payload.substring(2);
    String fullJs = wrapAsExpression(inner, hasMacros);
    Callable<Source> build = () -> {
      try {
        return Source.newBuilder("js", fullJs, compileKey).internal(true).cached(true).build();
      } catch (Exception e) {
        throw new IllegalArgumentException("Invalid JAVASCRIPT body: " + rootMessage(e), e);
      }
    };
    try {
      if (compileTimer == null) {
        return build.call();
      }
      return compileTimer.recordCallable(build);
    } catch (Exception e) {
      if (e instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException(e);
    }
  }

  private void maybeLogCompileDebugSample(String compileKey) {
    if (!log.isDebugEnabled()) {
      return;
    }
    long n = compileDebugSampleCounter.incrementAndGet();
    if ((n & 0xffL) != 0L) {
      return;
    }
    log.debug(
        "JAVASCRIPT compile sample keyPrefix={} keyLen={}",
        compileKey.substring(0, Math.min(12, compileKey.length())),
        compileKey.length()
    );
  }

  static String wrapAsExpression(String innerUserSource, boolean hasMacros) {
    String discardBlock = "  if (__ns_result === null) {\n"
        + "    return '" + DISCARD_SENTINEL + "';\n"
        + "  }\n"
        + "  if (typeof __ns_result === 'undefined') {\n"
        + "    throw new Error('JAVASCRIPT returned undefined');\n"
        + "  }\n"
        + "  return String(__ns_result);\n";
    if (!hasMacros) {
      return "(function(value){\n"
          + "  var __ns_result = (function(value){\n"
          + innerUserSource + "\n"
          + "  })(value);\n"
          + discardBlock
          + "})(String(__ns_value));";
    }
    return "(function(value, mJson){\n"
        + "  var __ns_macros = (typeof mJson === 'string' && mJson.length > 0) ? JSON.parse(mJson) : {};\n"
        + "  function __ns_macro_bridge(k){ return __ns_macros[k]; }\n"
        + "  var __ns_result = (function(value){\n"
        + innerUserSource + "\n"
        + "  })(value);\n"
        + discardBlock
        + "})(String(__ns_value), String(__ns_macros_json));";
  }

  static String rewriteMacroLiteralsToBridgeCalls(String scriptBody, Map<String, String> macros) {
    if (macros == null || macros.isEmpty()) {
      return scriptBody;
    }
    List<String> keys = new ArrayList<>(macros.keySet());
    keys.removeIf(k -> k == null || k.isEmpty());
    keys.sort(Comparator.comparingInt(String::length).reversed());
    StringBuilder out = new StringBuilder(scriptBody.length() + 64);
    int i = 0;
    final int n = scriptBody.length();
    while (i < n) {
      char c = scriptBody.charAt(i);
      if (c == '\'' || c == '"') {
        int end = skipStringLiteral(scriptBody, i);
        String literalSegment = scriptBody.substring(i, end);
        String inner = unquoteJsStringLiteral(literalSegment);
        if (inner != null && keys.contains(inner)) {
          out.append(macroAsQuotedStringExpr(inner));
        } else {
          out.append(literalSegment);
        }
        i = end;
        continue;
      }
      boolean replaced = false;
      for (String key : keys) {
        if (key.isEmpty() || i + key.length() > n) {
          continue;
        }
        if (scriptBody.regionMatches(i, key, 0, key.length())) {
          out.append(macroAsBareBridgeCall(key));
          i += key.length();
          replaced = true;
          break;
        }
      }
      if (!replaced) {
        out.append(c);
        i++;
      }
    }
    return out.toString();
  }

  private static int skipStringLiteral(String s, int start) {
    char q = s.charAt(start);
    int idx = start + 1;
    while (idx < s.length()) {
      char ch = s.charAt(idx);
      if (ch == '\\' && idx + 1 < s.length()) {
        idx += 2;
        continue;
      }
      if (ch == q) {
        return idx + 1;
      }
      idx++;
    }
    return s.length();
  }

  private static String unquoteJsStringLiteral(String literal) {
    if (literal.length() < 2) {
      return null;
    }
    char q = literal.charAt(0);
    if (q != '\'' && q != '"') {
      return null;
    }
    if (literal.charAt(literal.length() - 1) != q) {
      return null;
    }
    StringBuilder inner = new StringBuilder(literal.length() - 2);
    for (int p = 1; p < literal.length() - 1; p++) {
      char c = literal.charAt(p);
      if (c == '\\' && p + 1 < literal.length() - 1) {
        inner.append(literal.charAt(p + 1));
        p++;
        continue;
      }
      inner.append(c);
    }
    return inner.toString();
  }

  private static String macroAsBareBridgeCall(String key) {
    try {
      return "__ns_macro_bridge(" + JSON.writeValueAsString(key) + ")";
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot encode macro key for JS", e);
    }
  }

  private static String macroAsQuotedStringExpr(String key) {
    try {
      return "String(__ns_macro_bridge(" + JSON.writeValueAsString(key) + "))";
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot encode macro key for JS", e);
    }
  }

  static String compileCacheKey(String scriptBody, Map<String, String> macros) {
    if (macros == null || macros.isEmpty()) {
      return scriptCacheKey(scriptBody);
    }
    String keys = new TreeSet<>(macros.keySet()).stream()
        .filter(k -> k != null && !k.isEmpty())
        .collect(Collectors.joining("\0"));
    return scriptCacheKey(scriptBody + "\0__KEYS__\0" + keys);
  }

  private static long macroPayloadBytes(Map<String, String> macros) {
    if (macros == null || macros.isEmpty()) {
      return 0L;
    }
    long sum = 0L;
    for (Map.Entry<String, String> e : macros.entrySet()) {
      if (e.getKey() != null) {
        sum += (long) e.getKey().length();
      }
      if (e.getValue() != null) {
        sum += (long) e.getValue().length();
      }
    }
    return sum;
  }

  private void maybeSoftResetRuntime() {
    if (engineSoftResetIntervalMs <= 0L) {
      return;
    }
    long count = evaluationsSinceEngineReset.incrementAndGet();
    if (count < engineSoftResetMinEvaluations) {
      return;
    }
    long now = System.currentTimeMillis();
    if (now - lastEngineSoftResetMillis.get() < engineSoftResetIntervalMs) {
      return;
    }
    synchronized (runtimeLock) {
      if (System.currentTimeMillis() - lastEngineSoftResetMillis.get() < engineSoftResetIntervalMs) {
        return;
      }
      log.warn(
          "JS engine soft reset (evaluations={}, monitoring.js.engine-soft-reset-interval-ms={})",
          count,
          engineSoftResetIntervalMs
      );
      lastEngineSoftResetMillis.set(System.currentTimeMillis());
      initializeRuntime();
    }
  }

  static String scriptCacheKey(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private void registerFailure(String reason) {
    int failures = consecutiveFailures.incrementAndGet();
    if (failures >= 3) {
      synchronized (runtimeLock) {
        if (consecutiveFailures.get() >= 3) {
          log.debug("Reinitializing JS runtime after {} consecutive failures", failures);
          initializeRuntime();
        }
      }
    }
    log.debug(reason);
  }

  private void resetFailures() {
    consecutiveFailures.set(0);
  }

  /**
   * Spring calls {@link #initializeRuntime()} from {@link #registerMicrometerBinders()}; unit tests
   * construct this bean without {@code @PostConstruct}, so the first {@link #execute} must bootstrap.
   */
  private void ensureRuntimeReady() {
    if (graalEngine != null && graalContext != null && executorService != null) {
      return;
    }
    synchronized (runtimeLock) {
      if (graalEngine != null && graalContext != null && executorService != null) {
        return;
      }
      initializeRuntime();
    }
  }

  private void initializeRuntime() {
    synchronized (runtimeLock) {
      boolean hadPrevious = graalEngine != null;
      shutdownExecutorQuietly(executorService);
      executorService = null;
      closeContextQuietly(graalContext);
      graalContext = null;
      closeEngineQuietly(graalEngine);
      graalEngine = null;
      sourceCache.invalidateAll();
      scriptBodiesByHash.clear();
      evaluationsSinceEngineReset.set(0L);

      Engine engine = Engine.newBuilder()
          .option("engine.WarnInterpreterOnly", "false")
          .build();
      Context context = Context.newBuilder("js")
          .engine(engine)
          .allowAllAccess(false)
          .allowHostAccess(HostAccess.NONE)
          .allowCreateThread(false)
          .allowCreateProcess(false)
          .allowEnvironmentAccess(org.graalvm.polyglot.EnvironmentAccess.NONE)
          .allowExperimentalOptions(true)
          .option("js.ecmascript-version", "2022")
          .build();

      this.graalEngine = engine;
      this.graalContext = context;
      this.executorService = Executors.newSingleThreadExecutor(new JsThreadFactory());
      consecutiveFailures.set(0);
      if (hadPrevious && runtimeReinitCounter != null) {
        runtimeReinitCounter.increment();
      }
    }
  }

  private static void shutdownExecutorQuietly(ExecutorService service) {
    if (service == null) {
      return;
    }
    service.shutdownNow();
    try {
      service.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static void closeContextQuietly(Context context) {
    if (context == null) {
      return;
    }
    try {
      context.close();
    } catch (Exception e) {
      log.warn("Failed to close Graal Context", e);
    }
  }

  private static void closeEngineQuietly(Engine engine) {
    if (engine == null) {
      return;
    }
    try {
      engine.close();
    } catch (Exception e) {
      log.warn("Failed to close Graal Engine", e);
    }
  }

  private String rootMessage(Throwable throwable) {
    if (throwable == null) {
      return "unknown JAVASCRIPT error";
    }
    Throwable cursor = throwable;
    while (cursor.getCause() != null) {
      cursor = cursor.getCause();
    }
    String message = cursor.getMessage();
    return (message == null || message.isBlank()) ? cursor.getClass().getSimpleName() : message;
  }

  private JsResult error(String note) {
    return new JsResult("error", null, note);
  }

  public record JsResult(String status, String value, String note) {
  }

  /** For tests: Caffeine load count (cache misses → Source builds). */
  long scriptCacheLoadCountForTests() {
    return sourceCache.stats().loadCount();
  }

  private final class JsExecutionTask implements Callable<JsResult> {
    private final long submittedAtNanos;
    private final String compileKey;
    private final String value;
    private final Map<String, String> macros;
    private final boolean hasMacros;

    private JsExecutionTask(
        long submittedAtNanos,
        String compileKey,
        String value,
        Map<String, String> macros,
        boolean hasMacros
    ) {
      this.submittedAtNanos = submittedAtNanos;
      this.compileKey = compileKey;
      this.value = value;
      this.macros = macros;
      this.hasMacros = hasMacros;
    }

    @Override
    public JsResult call() throws Exception {
      if (queueWaitTimer != null) {
        queueWaitTimer.record(System.nanoTime() - submittedAtNanos, TimeUnit.NANOSECONDS);
      }
      if (evalTimer == null) {
        return runScript(compileKey, value, macros, hasMacros);
      }
      return evalTimer.recordCallable(() -> runScript(compileKey, value, macros, hasMacros));
    }
  }

  private static final class JsThreadFactory implements ThreadFactory {
    private final AtomicInteger index = new AtomicInteger(1);

    @Override
    public Thread newThread(Runnable runnable) {
      Thread thread = new Thread(runnable, "netscan-js-preprocess-" + index.getAndIncrement());
      thread.setDaemon(true);
      return thread;
    }
  }
}
