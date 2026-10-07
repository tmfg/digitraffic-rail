# Digitraffic-rail database

## ;TL;TR

````bash
docker compose rm && docker compose up
````
OR use script

````bash
./db-rm-build-up.sh
````

For old version of database run SQL command to change default character set and collation:

````SQL
ALTER DATABASE `your_database_name` CHARACTER SET = utf8mb4 COLLATE = utf8mb4_swedish_ci;
````

Check new defaults

````SQL
SELECT SCHEMA_NAME, DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME
FROM information_schema.SCHEMATA;
````

### Linux ARM64 Users

The default config uses `platform: linux/amd64`. If you're on Linux ARM64, create a local `docker-compose.override.yml` file to use your native architecture:

```yaml
# docker-compose.override.yml (git-ignored)
services:
  db:
    platform: linux/arm64/v8
```

This file is git-ignored and won't affect other team members.
