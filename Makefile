# -----------------------------
# Makefile (Back-end root)
# -----------------------------
N ?= 3
TOPIC ?= video.transcode.requested
PARTITIONS ?= 3
GROUP ?= transcoder-worker

KAFKA_CONTAINER ?= kafka
KAFKA_BOOTSTRAP ?= kafka:9092
KAFKA_TOPICS := /opt/kafka/bin/kafka-topics.sh
KAFKA_GROUPS := /opt/kafka/bin/kafka-consumer-groups.sh

.PHONY: up down reset \
        up-workers ps logs \
        kafka-ensure-topic kafka-partitions kafka-describe kafka-members kafka-group

up:
	docker compose up -d --build

down:
	docker compose down

# ⚠️ DB/MinIO 데이터까지 싹 초기화(볼륨 삭제)
reset:
	docker compose down -v

# -----------------------------
# Transcoder workers (scale)
# -----------------------------
up-workers:
	docker compose up -d --build --scale transcoder-worker=$(N)
	$(MAKE) kafka-partitions TOPIC=$(TOPIC) PARTITIONS=$(PARTITIONS)
	$(MAKE) ps SERVICE=transcoder-worker
	$(MAKE) kafka-members GROUP=$(GROUP)

ps:
	docker compose ps $(SERVICE)

logs:
	docker compose logs -f $(SERVICE)

# -----------------------------
# Kafka topic helpers
# -----------------------------
kafka-ensure-topic:
	@echo "[INFO] Ensure topic exists: $(TOPIC) (partitions=$(PARTITIONS))"
	docker exec -it $(KAFKA_CONTAINER) bash -lc '$(KAFKA_TOPICS) --bootstrap-server $(KAFKA_BOOTSTRAP) --create --if-not-exists --topic $(TOPIC) --partitions $(PARTITIONS) --replication-factor 1 || true'

kafka-describe:
	docker exec -it $(KAFKA_CONTAINER) bash -lc '$(KAFKA_TOPICS) --bootstrap-server $(KAFKA_BOOTSTRAP) --describe --topic $(TOPIC)'

kafka-partitions: kafka-ensure-topic
	@echo "[INFO] Topic=$(TOPIC), TargetPartitions=$(PARTITIONS)"
	@echo "[INFO] Current topic 상태:"
	@docker exec -it $(KAFKA_CONTAINER) bash -lc '$(KAFKA_TOPICS) --bootstrap-server $(KAFKA_BOOTSTRAP) --describe --topic $(TOPIC) || true'
	@echo ""
	@echo "[INFO] Alter partitions (이미 같으면 무시)"
	@docker exec -it $(KAFKA_CONTAINER) bash -lc '$(KAFKA_TOPICS) --bootstrap-server $(KAFKA_BOOTSTRAP) --alter --topic $(TOPIC) --partitions $(PARTITIONS) || true'
	@echo ""
	@echo "[INFO] After:"
	@docker exec -it $(KAFKA_CONTAINER) bash -lc '$(KAFKA_TOPICS) --bootstrap-server $(KAFKA_BOOTSTRAP) --describe --topic $(TOPIC)'

kafka-group:
	docker exec -it $(KAFKA_CONTAINER) bash -lc '$(KAFKA_GROUPS) --bootstrap-server $(KAFKA_BOOTSTRAP) --describe --group $(GROUP) || true'

kafka-members:
	docker exec -it $(KAFKA_CONTAINER) bash -lc '$(KAFKA_GROUPS) --bootstrap-server $(KAFKA_BOOTSTRAP) --describe --group $(GROUP) --members --verbose || true'