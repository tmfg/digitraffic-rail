# Digitraffic-rail database

## ;TL;TR

````bash
docker compose rm && docker compose up
````
OR use script

````bash
./db-rm-build-up.sh
````

For old version of database run SQL command to change derault character set and collation:

````SQL
ALTER DATABASE `{database name}` CHARACTER SET = utf8mb4 COLLATE = utf8mb4_swedish_ci;
````

Check new defaults

````SQL
SELECT SCHEMA_NAME, DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME
FROM information_schema.SCHEMATA;
````
