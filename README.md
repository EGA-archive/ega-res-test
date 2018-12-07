# EGA-RES-TEST

This application tests the res and other dependent services.

## Table of Contents

- [Prerequisites](#Prerequisites)
- [How to run](#How-to-run)

## Prerequisites

Before running this application you should run the ega-data-api.

## How to run

java -jar ega-rest-test-0.0.1-SNAPSHOT-jar-with-dependencies.jar [<eurekaip>/eureka/apps] [File size] [Thread size] [chunk size] [fileid] [file size] [filechecksum]

eg:
java -jar ega-rest-test-0.0.1-SNAPSHOT-jar-with-dependencies.jar "http://localhost:8761/eureka/apps" 200 2 1 EGAF00000238742 13124284 3099618c907220b8a6916f97528198d8

