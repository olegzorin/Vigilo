#!/bin/bash

# Read the name of the builder to use from the first line of the 'lambda_builders' file
BUILDER_NAME=$(sed -n '1p' lambda_builders)

# Run docker build command
#
# --builder     Use a specific builder instance.
# --provenance  Provenance attestation. Must be disabled for AWS Lambda compatibility, see https://github.com/docker/buildx/issues/1533
# --tag         Image URI, ex. 846566076533.dkr.ecr.us-east-1.amazonaws.com/lambda-dev-2:8ead89d4-985c-498f-9621-9616ca88eed1
# --pull        Always attempt to pull the latest versions of all referenced images.
# --push        Automatically push the created image to the remote AWS ECR registry identified by the image URI.
# -             Read dockerfile from stdin.
docker buildx build --builder "$BUILDER_NAME" --provenance=false --pull --push --tag "$1" -