#!/bin/bash
if [ -x "$(command -v docker-compose)" ]; then
  docker-compose down && docker-compose rm db && docker-compose build && docker-compose up
else
  docker compose down && docker compose rm db && docker compose build && docker compose up
fi
