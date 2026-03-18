# =========================
# Config
# =========================
USER_N ?= 3
ADMIN_N ?= 1

# request topics
USER_TOPIC ?= video.transcode.user.requested
ADMIN_TOPIC ?= video.transcode.admin.requested

# result topics (NEW)
USER_RESULT_TOPIC  ?= video.transcode.user.result
ADMIN_RESULT_TOPIC ?= video.transcode.admin.result

# partitions
USER_PARTITIONS ?= 3
ADMIN_PARTITIONS ?= 1
USER_RESULT_PARTITIONS ?= 1
ADMIN_RESULT_PARTITIONS ?= 1

# consumer groups
USER_GROUP ?= transcoder-worker-user
ADMIN_GROUP ?= transcoder-worker-admin

COMPOSE ?= docker compose
KAFKA_CONTAINER ?= kafka

# =========================
# Help
# =========================
.PHONY: help
help:
	@echo "Targets:"
	@echo "  make up-all                 - 전체 기동(기본 3:1) + 토픽 생성까지"
	@echo "  make up-workers             - 워커만 (3:1) 스케일 포함"
	@echo "  make kafka-setup            - 토픽 생성/파티션 보장(user/admin + result)"
	@echo "  make ps                     - 컨테이너 목록"
	@echo "  make logs SERVICE=...       - 서비스 로그 팔로우"
	@echo "  make kafka-members          - consumer group 할당 확인"
	@echo "  make down                   - 컨테이너 내림(볼륨 유지)"
	@echo "  make reset                  - 컨테이너+볼륨 삭제(DB/MinIO 데이터 초기화)"
	@echo ""
	@echo "Vars:"
	@echo "  USER_N=3 ADMIN_N=1"
	@echo "  USER_TOPIC=$(USER_TOPIC)"
	@echo "  ADMIN_TOPIC=$(ADMIN_TOPIC)"
	@echo "  USER_RESULT_TOPIC=$(USER_RESULT_TOPIC)"
	@echo "  ADMIN_RESULT_TOPIC=$(ADMIN_RESULT_TOPIC)"
	@echo "  USER_PARTITIONS=$(USER_PARTITIONS) ADMIN_PARTITIONS=$(ADMIN_PARTITIONS)"
	@echo "  USER_RESULT_PARTITIONS=$(USER_RESULT_PARTITIONS) ADMIN_RESULT_PARTITIONS=$(ADMIN_RESULT_PARTITIONS)"
	@echo "  USER_GROUP=$(USER_GROUP) ADMIN_GROUP=$(ADMIN_GROUP)"

# =========================
# Up / Down
# =========================
.PHONY: up-all
up-all: up-workers kafka-setup restart-workers kafka-members ps

.PHONY: up-workers
up-workers:
	$(COMPOSE) up -d --build \
		--scale transcoder-worker-user=$(USER_N) \
		--scale transcoder-worker-admin=$(ADMIN_N)

.PHONY: restart-workers
restart-workers:
	$(COMPOSE) restart transcoder-worker-user transcoder-worker-admin || true

.PHONY: down
down:
	$(COMPOSE) down

.PHONY: reset
reset:
	$(COMPOSE) down -v

# =========================
# Observability
# =========================
.PHONY: ps
ps:
	$(COMPOSE) ps

.PHONY: logs
logs:
	$(COMPOSE) logs -f $(SERVICE)

# =========================
# Kafka helpers
# =========================
define KAFKA_CREATE_TOPIC
docker exec -it $(KAFKA_CONTAINER) bash -lc '/opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 \
  --create --if-not-exists --topic $(1) --partitions $(2) --replication-factor 1 || true'
endef

define KAFKA_ALTER_PARTITIONS
docker exec -it $(KAFKA_CONTAINER) bash -lc '/opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 \
  --alter --topic $(1) --partitions $(2) || true'
endef

define KAFKA_DESCRIBE_TOPIC
docker exec -it $(KAFKA_CONTAINER) bash -lc '/opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 \
  --describe --topic $(1) || true'
endef

define KAFKA_GROUP_MEMBERS
docker exec -it $(KAFKA_CONTAINER) bash -lc '/opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 \
  --describe --group $(1) --members --verbose || true'
endef

.PHONY: kafka-setup
kafka-setup:
	@echo "[INFO] Ensure topics/partitions..."

	@echo ""
	@echo "[INFO] USER_TOPIC=$(USER_TOPIC), partitions=$(USER_PARTITIONS)"
	$(call KAFKA_CREATE_TOPIC,$(USER_TOPIC),$(USER_PARTITIONS))
	$(call KAFKA_ALTER_PARTITIONS,$(USER_TOPIC),$(USER_PARTITIONS))
	$(call KAFKA_DESCRIBE_TOPIC,$(USER_TOPIC))

	@echo ""
	@echo "[INFO] ADMIN_TOPIC=$(ADMIN_TOPIC), partitions=$(ADMIN_PARTITIONS)"
	$(call KAFKA_CREATE_TOPIC,$(ADMIN_TOPIC),$(ADMIN_PARTITIONS))
	$(call KAFKA_ALTER_PARTITIONS,$(ADMIN_TOPIC),$(ADMIN_PARTITIONS))
	$(call KAFKA_DESCRIBE_TOPIC,$(ADMIN_TOPIC))

	@echo ""
	@echo "[INFO] USER_RESULT_TOPIC=$(USER_RESULT_TOPIC), partitions=$(USER_RESULT_PARTITIONS)"
	$(call KAFKA_CREATE_TOPIC,$(USER_RESULT_TOPIC),$(USER_RESULT_PARTITIONS))
	$(call KAFKA_ALTER_PARTITIONS,$(USER_RESULT_TOPIC),$(USER_RESULT_PARTITIONS))
	$(call KAFKA_DESCRIBE_TOPIC,$(USER_RESULT_TOPIC))

	@echo ""
	@echo "[INFO] ADMIN_RESULT_TOPIC=$(ADMIN_RESULT_TOPIC), partitions=$(ADMIN_RESULT_PARTITIONS)"
	$(call KAFKA_CREATE_TOPIC,$(ADMIN_RESULT_TOPIC),$(ADMIN_RESULT_PARTITIONS))
	$(call KAFKA_ALTER_PARTITIONS,$(ADMIN_RESULT_TOPIC),$(ADMIN_RESULT_PARTITIONS))
	$(call KAFKA_DESCRIBE_TOPIC,$(ADMIN_RESULT_TOPIC))

.PHONY: kafka-members
kafka-members:
	@echo "[INFO] GROUP=$(USER_GROUP)"
	$(call KAFKA_GROUP_MEMBERS,$(USER_GROUP))
	@echo ""
	@echo "[INFO] GROUP=$(ADMIN_GROUP)"
	$(call KAFKA_GROUP_MEMBERS,$(ADMIN_GROUP))