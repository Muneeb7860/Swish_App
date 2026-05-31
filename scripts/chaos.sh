#!/bin/bash

# Get list of running container IDs
CONTAINERS=($(docker ps -q))

if [ ${#CONTAINERS[@]} -eq 0 ]; then
  echo "No running containers found."
  exit 1
fi

# Select a random container
RANDOM_INDEX=$((RANDOM % ${#CONTAINERS[@]}))
CONTAINER_ID=${CONTAINERS[$RANDOM_INDEX]}

# Get container name
CONTAINER_NAME=$(docker inspect --format="{{.Name}}" "$CONTAINER_ID" | sed 's/^\///')

echo "Killing container: $CONTAINER_NAME ($CONTAINER_ID)"
docker stop "$CONTAINER_ID"

echo "Waiting 15 seconds..."
sleep 15

echo "Reviving container: $CONTAINER_NAME ($CONTAINER_ID)"
docker start "$CONTAINER_ID"
echo "Done."
