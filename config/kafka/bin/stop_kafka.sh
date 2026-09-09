#!/bin/zsh

set -euo pipefail

"$KAFKA_HOME"/bin/kafka-server-stop.sh
echo "Kafka stop requested."
