# Digitraffic-rail

This repository contains source code for Java applications that serve some of the APIs in [rata.digitraffic.fi](https://rata.digitraffic.fi/)

Digitraffic is operated by [Fintraffic](https://www.fintraffic.fi)

# Build 

## Init git submodules

```bash
git submodule update --init --recursive
```

## Building and testing

Init and start database
```bash
cd dbrail
./db-rm-build-up.sh
```

Run tests
```bash
mvn clean test
```

# Architecture

This repository has three main components:

* AvoinDataServer
    * Spring Boot application that serves APIs
* AvoindataUpdater
    * Fetches data from integration sources and updates the database
    * Also has HTTP-server for health check and for manually triggering jobs
    * Some examples
    ```
    curl "http://localhost:18081/reinitialize?date=2024-11-25"  
    curl "http://localhost:18081/reinitialize-compositions?date=2024-11-25"
    curl "http://localhost:18081/extract"
    curl "http://localhost:18081/gtfs"
    curl "http://localhost:18081/gtfs-dev"
    ```
* AvoinDataCommon
    * Place for shared code, db related things etc

# Run dependency check

    mvn -Pdepcheck

Report can be found at  [target/dependency-check-report.html](target/dependency-check-report.html)

Oneliner to run dependency check and open the report in default browser (MacOS):

    mvn -Pdepcheck; open target/dependency-check-report.html