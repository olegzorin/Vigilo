#!/bin/zsh

set -euo pipefail

CONFIG="$VF_HOME/kafka/server.properties"

"$KAFKA_HOME"/bin/kafka-server-start.sh -daemon "$CONFIG"
echo "Kafka start requested with config $CONFIG"
