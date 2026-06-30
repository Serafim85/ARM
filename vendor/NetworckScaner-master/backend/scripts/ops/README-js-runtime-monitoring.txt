JS preprocessing runtime (Graal) — operations notes
===================================================

Scope
-----
Single-threaded Graal **Polyglot** runtime (`org.graalvm.polyglot.Engine` + one `Context` on
`netscan-js-preprocess-*`) for Zabbix-style JAVASCRIPT preprocessing. Cached `Source` per stable
compile key (Caffeine). Metrics prefix: netscan.js.*

Key metrics
-----------
- netscan.js.compile_total — cache-miss builds of cached `Source` (Truffle work per unique key).
- netscan.js.compile — timer for building cached `Source`.
- netscan.js.script_cache — Caffeine stats (hit rate, evictions, load count).
- netscan.js.script_bodies_map_size — size of the side map kept in sync with cache keys; should
  stay bounded (~ script cache size). A large gap vs maximum cache size can indicate churn.
- netscan.js.queue_wait — time waiting on the single JS worker queue (scale-out if this grows
  with device count, e.g. toward ~3000 hosts).
- netscan.js.eval — worker thread eval time.
- netscan.js.execute — end-to-end wall time including queue.
- netscan.js.runtime_reinit_total — runtime rebuilds (failures or configured soft reset).

Compile churn (~3000 devices)
-----------------------------
Compilation is keyed by the raw script body plus sorted macro *names*, not values. If
compile_total or script_cache loadCount grows roughly with poll ticks, check templates for
unique script text per device or per tick.

Soft reset (last resort)
------------------------
Properties (see application.properties):

  monitoring.js.engine-soft-reset-interval-ms (default 0 = off)
  monitoring.js.engine-soft-reset-min-evaluations (default 50000)

When both interval and min-evaluations are satisfied after successful evals, the Engine+Context
are recreated and caches cleared. Use only with evidence (e.g. heap growth in PolyglotContextImpl).

Heap dump (PolyglotContextImpl growth)
---------------------------------------
1. jcmd <pid> GC.heap_dump /path/to/heap.hprof
2. Open in Eclipse MAT or VisualVM; dominator tree on org.graalvm.polyglot.impl.PolyglotContextImpl
3. Correlate time window with netscan.js.compile_total and script_cache eviction spikes.

Hikari vs collector threads
----------------------------
If `monitoring.collector.enabled=true` in the same JVM, set
`spring.datasource.hikari.maximum-pool-size` (e.g. env `DB_HIKARI_MAX_POOL_SIZE`) so the pool is
not orders of magnitude below `monitoring.collector-threads`; otherwise expect long
"Connection is not available" waits under GC or slow queries.

Kafka / collector lag
-----------------------
If the evaluator falls behind, JS queue_wait rises. Mitigations: more backend instances with
partitioned devices, tuning collector/evaluator throughput, or reducing JS-heavy templates.
