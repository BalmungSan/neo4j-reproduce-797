# neo4j-reproduce-797

This repository contains the necessary files to reproduce the [#797](https://github.com/neo4j/neo4j-java-driver/issues/797) issue of the **neo4j** **Java** driver.

## Steps to reproduce

1. Run a **neo4j** server in background. For example, using **Docker**:

> docker run -d -p7687:7687 --env=NEO4J_AUTH=none neo4j:4.2.1

2. Run the following command:

> ./sbt run
