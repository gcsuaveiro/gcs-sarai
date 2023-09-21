# ksql-anonymization-udfs

This repository contains the dev environment for developing and testing UDFs that will be imported to KSQL for data anonymization, as well as the UDFs themselves.

## How-To's
### How to build the UDF Jar?
```
gradle shadowJar
```
### How to deploy a created UDF?

- Copy the Jar created above to helk's /docker/helk-ksql/udfs folder. 
- build the docker image and push it to registry
- edit new image on k8s-helk-ksql.yaml image and apply configuration

It's needed to restart container to import created function to Ksql (Hot Reloading is NOT supported.)

Local Testing: docker-compose up

### How to deploy to production?
- place .jar in correct folder
- build a docker image and push it to registry
- edit .yaml image and apply configuration


### How to check if UDF was correctly imported?

Analyze container logs after restart and search for 'udaf' -- If present, there will be a line with the name of the function created.

## More Reading
[How to Create/Deploy an UDF](https://docs.ksqldb.io/en/latest/how-to-guides/create-a-user-defined-function/#add-the-uberjar-to-ksqldb-server)

[UDF reference guide](https://docs.ksqldb.io/en/latest/reference/user-defined-functions/)
