#!/bin/zsh

create_topic() {
    local topic_name="$1"
    local partitions="$2"
    local retention_minutes="$3"
    local retention_ms=$((retention_minutes * 60 * 1000))

    "$KAFKA_HOME/bin/kafka-topics.sh" \
        --bootstrap-server localhost:9092 \
        --create \
        --topic "$topic_name" \
        --partitions "$partitions" \
        --replication-factor 1 \
        --config "retention.ms=$retention_ms"
}

create_topic lambda-run-completion 5 10
create_topic lambda-input 5 10
create_topic lambda-invoke-request 4 10
create_topic lambda-reset 5 10
create_topic lambda-schedule 5 10080
create_topic lambda-code 1 60
create_topic lambda-error 1 600
create_topic lambda-assignment-log 1 60
create_topic cache-invalidation 1 10
create_topic lasting-lambda 2 60
create_topic operations 2 5
