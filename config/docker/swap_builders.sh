#!/bin/bash

# Docker does not directly support limiting the builder cache by time
# or size, so this can only be done with scheduled cleanups. To avoid
# conflicts between cleanup and the ongoing compilation in a builder,
# we use a configuration that includes two different builder instances,
# one in use and one reserved, switching between them periodically.
# The cache of reserved instance can be safely cleared while the active
# instance is used to compile lambdas. The names of the builder instances
# are stored in a file called 'lambda_builders', where the first line
# contains the name of the builder instance that is currently in use.

# This script clears the cache of the builder that is not currently in
# use, and then places its name in the first line of 'lambda_builders'.

FILE=/home/ec2-user/lambda_builders

# Read the builder instance names from the file
BUILDER_1=$(sed -n '1p' $FILE)  # active
BUILDER_2=$(sed -n '2p' $FILE)  # reserved

# Clear the cache of the reserved builder instance
docker buildx prune --builder "${BUILDER_2}" --force

# Swap the lines with the names of the builder instances in the file
echo "${BUILDER_2}" > ${FILE}.tmp
echo "${BUILDER_1}" >> ${FILE}.tmp
mv ${FILE}.tmp $FILE
