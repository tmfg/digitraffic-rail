# Purpose
Constains the source code for [rata.digitraffic.fi](https://rata.digitraffic.fi/)

# Architechture

rata.digitraffic.fi source code consists of four components:

## AvoinDataCommon

- JPA repositories for interacting with DB
- Entity classes which model database rows
- Common util functions

## AvoinDataServer

- Actual HTTP endpoints (marked with @Controller) which serve json 

## AvoinDataUpdater

- Fetches new data from LIIKE (LiikeInterface) and other systems and pushes it to DB and MQTT
- Scheduled background processes that keep things tidy

## LiikeInterface

- A component that runs in LIIKE-environment, has access to LIIKE DB and serves LIIKE-data in JSON format 