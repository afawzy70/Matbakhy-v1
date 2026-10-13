# Local observability rehearsal (MB-039: OPS-01/OPS-02 local part), 2026-10-13

Self-review by the implementation agent, not independent review. Local Docker on the agent's container, **not** the
preview host: every deployed check (OPS-01, OPS-02, LOG-01, AUD-01 on the preview) stays NOT_RUN.

- Stack: `deploy/web-preview/compose.yaml` (project `mb-obs`) with postgres, identity, storage, api and the new
  loki, alloy, prometheus, alertmanager, alert-sink, node-exporter and grafana services. The proxy was not started.
- API image: built locally from the working tree (`backend/Dockerfile`). First pass at f439b87 plus the uncommitted
  observability changes; DB-outage pass after the gauge fix, at e5cfb41 (RELEASE_COMMIT in the logs).
- Synthetic data only; secrets generated for the run, kept outside the repository.

## Results

| Check | Result | Evidence |
|---|---|---|
| Scrape targets | PASS: api (management port 8081), node, loki, alloy, alertmanager, prometheus all up | Prometheus targets API |
| Metrics not on the API port | PASS: `/actuator/prometheus` on 8080 answers 401; only 8081 serves it; nothing but Grafana is published, on 127.0.0.1 | ManagementPortIntegrationTest; `docker port` below |
| Searchable logs by request ID | PASS: `{container="mb-obs-api-1"} \|= "rehearsal-search-0002"` returns the API's ECS line (service.version = release commit, environment PREVIEW); also through Grafana's Loki data source with the admin login | Loki and Grafana queries |
| No duplicates after collector restart | PASS: the request line is stored once | Loki count = 1 |
| Collector outage | PASS: Alloy stopped; 3 requests logged; 0 lines in Loki during the outage; Alloy started; all 3 lines shipped (read position kept in its volume) | Loki counts 0 then 3 |
| Retention across API redeploy | PASS: `up -d --force-recreate api` deleted the old container and its json log file; the 6 earlier request lines stay searchable | Loki count 6 |
| Retention across log-store restart | PASS: Loki restarted; still 6 | Loki count 6 |
| Alert: failing outbox | PASS: a FAILED outbox event fired **OutboxFailed** at the sink (07:34:49) | sink log |
| Alert: stale backup | PASS: no backup metric → **BackupStale** firing (07:37:34); `deploy.sh` `backup_db` (real function) wrote the dump and the metric → resolved (07:38:34) | sink log |
| Alert: API down | PASS: API stopped 08:01:15 → **ApiDown** 08:02:52; started → resolved 08:03:54 | sink log |
| Alert: database down | **Defect found, fixed, PASS:** first pass, a stopped database made each gauge wait for a connection, the scrape timed out (10 s) and only ApiDown fired. After the fix (red first: 57 s scrape → < 4 s), DB stopped 07:58:25 → **DatabaseUnavailable** 08:00:06, scrape 1.5 s; DB started → resolved 08:01:07 | RED/GREEN-OperationalMetricsOutage*, sink log |
| Alert: disk pressure | Fired for real: this container's disk had about 8% free (**DiskPressure** at 07:37:34). Behaviour of Loki/Prometheus on a full disk: NOT_RUN | sink log |
| Alert rules unit tests | PASS: `promtool test rules observability/alerts.test.yml` (ApiDown, OutboxFailed, LedgerUnbalanced quiet, DatabaseUnavailable, AuditWriteFailed, OutboxStale, BackupStale stale and absent, DiskPressure) | below |
| Grafana access | PASS: health 200 on 127.0.0.1:3000; anonymous API 401; not reachable on the host's other address | curl |
| Real alert routing | NOT_RUN by design: no approved recipient (O07); every alert goes to the local sink | alertmanager.yml |

## Alert sink (received notifications)

```
07:33:49 firing   ApiDown (critical)
07:34:49 firing   OutboxFailed (ops)
07:34:49 resolved ApiDown (critical)
07:37:34 firing   DiskPressure (warning)
07:37:34 firing   BackupStale (incident)
07:38:34 resolved BackupStale (incident)
07:39:49 resolved OutboxFailed (ops)
07:40:04 firing   ApiDown (critical)
07:58:49 firing   OutboxFailed (ops)
07:59:04 resolved ApiDown (critical)
07:59:49 resolved OutboxFailed (ops)
08:00:04 firing   DatabaseUnavailable (critical)
08:00:49 firing   OutboxFailed (ops)
08:01:04 resolved DatabaseUnavailable (critical)
08:01:49 resolved OutboxFailed (ops)
08:02:49 firing   ApiDown (critical)
08:03:49 firing   OutboxFailed (ops)
08:03:49 resolved ApiDown (critical)
```

## Outage timings

```
DB stop at 07:58:25
DatabaseUnavailable received 08:00:06
api target up 1.514839636
DatabaseUnavailable resolved 08:01:07
API stop at 08:01:15
ApiDown received 08:02:52
ApiDown resolved 08:03:54
```

## Published ports and memory after the run

```
loki: 
alloy: 
prometheus: 
alertmanager: 
alert-sink: 
node-exporter: 
grafana: 3000/tcp -> 127.0.0.1:3000 
api: 
postgres: 
mb-obs-alert-sink-1 11.29MiB / 64MiB
mb-obs-alertmanager-1 19.44MiB / 128MiB
mb-obs-alloy-1 48.4MiB / 256MiB
mb-obs-api-1 271.8MiB / 2GiB
mb-obs-grafana-1 76.3MiB / 256MiB
mb-obs-identity-1 579.3MiB / 1GiB
mb-obs-loki-1 83.71MiB / 384MiB
mb-obs-node-exporter-1 7.43MiB / 64MiB
mb-obs-postgres-1 44.6MiB / 1.5GiB
mb-obs-prometheus-1 38.1MiB / 512MiB
mb-obs-storage-1 60.69MiB / 512MiB
```

## promtool

```
  SUCCESS

```

## Observations to tune (provisional rules)

- **OutboxFailed "resolves" when its data disappears.** While the database or the API is down the gauge is NaN or
  missing, so OutboxFailed shows "resolved" (07:39:49, 07:59:49, 08:01:49) and fires again on recovery. The outage
  itself is covered by DatabaseUnavailable/ApiDown, but a "resolved" message is misleading. Proposal (not applied):
  evaluate data-gauge alerts over `max_over_time(...[15m])` or suppress them with an inhibition rule while
  DatabaseUnavailable or ApiDown fires.
- ApiDown fired once at stack start because Prometheus was up a minute before the API; expected on a cold start.
- Loki retention is by time only (rehearsal value 168 h, O06 open); it has no size cap. Its disk use is bounded only
  by the volume of container logs, which the json-file cap does not limit over time. A full-disk drill is NOT_RUN.
