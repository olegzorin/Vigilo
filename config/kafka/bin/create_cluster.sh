#!/bin/zsh

STORAGE="$KAFKA_HOME/bin/kafka-storage.sh"
CONFIG="$VF_HOME/kafka/server.properties"

KAFKA_CLUSTER_ID="$("$STORAGE" random-uuid)"

"$STORAGE" format -t "$KAFKA_CLUSTER_ID" -c "$CONFIG"
echo "Kafka cluster storage formatted with cluster id $KAFKA_CLUSTER_ID"