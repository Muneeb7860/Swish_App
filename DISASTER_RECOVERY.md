# Swish App: Enterprise Disaster Recovery & Operations Playbook 🛡️

This document outlines the standard operational playbooks and disaster recovery (DR) procedures for the **Swiss Quick Commerce (`swiss_App`)** microservices stack, ensuring alignment with **COBIT 2019 DSS04 (Managed Continuity)** and **ITIL v4 Service Design** resilience benchmarks.

---

## 🚨 Incident 1: Kafka Poison Pill & DLQ Recovery

### 🔍 Identification
When an event processing failure occurs:
- The backend logs will contain `DeadLetterPublishingRecoverer - Redirecting record to...`.
- Zipkin distributed traces will display a failed span with the Kafka topic name and target consumer context.

### 🛠️ Mitigation Playbook
1. **Isolate the Poison Pill**: Ensure the record has successfully traveled to the dead-letter topic (e.g. `order.placed.DLQ`) by executing a Kafka CLI dump:
   ```bash
   docker exec -it swiss_kafka rpk topic consume order.placed.DLQ --num 5
   ```
2. **Examine Headers**: Inspect the headers of the failed record to determine the root cause exception and trace ID.
3. **Deploy Hotfix**: If the failure was due to a code bug or schema mismatch, compile and deploy the corrected microservice image.
4. **Replay DLQ Events**: Flush and replay the DLQ records back into the primary queue by triggering the operational admin script:
   ```bash
   docker exec -it swiss_backend java -cp /app/backend.jar \
     ch.swissqcommerce.backend.infrastructure.messaging.DlpReplayUtility \
     --topic order.placed.DLQ --target order.placed
   ```

---

## 💾 Incident 2: Database Replication Lag & Transaction Recovery

### 🔍 Identification
- OLAP synchronization timers in the control panel spike above `10s`.
- Read-after-write operations in the Customer Super App (e.g. immediately querying past statements) display stale data due to replica lag.

### 🛠️ Mitigation Playbook
1. **Check Replication Health**: Query the PostgreSQL master container to check lag status:
   ```bash
   docker exec -it swiss_postgres psql -U swissuser -d swiss_db -c \
     "SELECT client_addr, state, sent_lsn, write_lsn, flush_lsn, replay_lsn FROM pg_stat_replication;"
   ```
2. **Trigger Failover to Secondary Replica**: If the primary master postgres node experiences a disk or hardware failure, elevate the read-replica container to Master:
   ```bash
   docker exec -it swiss_postgres_replica pg_ctl promote -D /var/lib/postgresql/data
   ```
3. **Anonymization Verification (GDPR Purge Audit)**: Under GDPR Article 17 (Right to Erasure), a customer can wipe their statements. In the event of a database restore, ensure past purged logs are NOT restored. Cross-verify by querying double-entry ledgers:
   ```sql
   SELECT actor_id, credit, debit FROM oltp.ledger_lines WHERE actor_id = 'ANONYMIZED-GDPR-CUST';
   ```

---

## ⚡ Incident 3: Cache Crash & Database Throttling Mitigation

### 🔍 Identification
- Redis container experiences a memory leak or crash (`swiss_redis` is Down).
- Spring Actuator Actuator Actuator alert boards report `CacheConnectionException`.
- PostgreSQL write-latency indicators in the cockpit spike above `500ms` due to sudden read amplification (all catalog traffic hits the DB).

### 🛠️ Mitigation Playbook
1. **Spin Up Redis Failover**: Restart the Redis cache container cleanly:
   ```bash
   docker compose -p swish_app restart redis
   ```
2. **Warm the Cache**: Trigger the cache warming pipeline to pre-populate the catalog before directing active traffic:
   ```bash
   curl -X POST https://localhost/api/admin/catalog/cache-warm
   ```
3. **Verify Hit Rate**: Monitor Grafana to ensure the Cache Hit Rate recovers back to `>85%`.
