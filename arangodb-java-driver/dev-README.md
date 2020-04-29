# dev-README


## compile

```shell script
mvn clean compile
```

It triggers:
- pmd checks
- spotbugs checks
- checkstyle validations


## test

Run tests:
```shell script
mvn clean test
```

To skip resiliency tests (which are slower): `-DexcludedGroups="resiliency"`

To run Api tests only: `-Dgroups="api"`

Code coverage report is generated here: [target/site/jacoco/index.html](target/site/jacoco/index.html)

### docker image

To specify the docker image to use in tests:
```shell script
mvn test -Dtest.docker.image="docker.io/arangodb/arangodb:3.6.2"
```

### enterprise license

When testing against an enterprise docker image, a license key must be specified (also an evaluation one is fine):

```shell script
mvn test -Dtest.docker.image="docker.io/arangodb/enterprise:3.6.2" -Darango.license.key="<ARANGO_LICENSE_KEY>"
```

### reuse test containers

Test containers used in API tests can be reused. To enable it:
- append `testcontainers.reuse.enable=true` to `~/.testcontainers.properties`
- add the option `-Dtestcontainers.reuse.enable=true` when running tests


### test provided deployment

API tests can be executed also against a provided deployment, setting the following properties:
- `test.useProvidedDeployment`: avoids starting containers and runs tests against the provided deployment
- `test.arangodb.version`: version of the target deployment, eg. `x.y.z` 
- `test.arangodb.isEnterprise`: whether the target deployment is enterprise 
- `test.arangodb.hosts`: comma separated host list, eg. `1.2.3.4:8529,4.5.6.7:8529` 
- `test.arangodb.authentication`: username and password separated by colon, eg. `username:passwd`  
- `test.arangodb.topology`: topology of the target deployment, can be: `SINGLE_SERVER`, `ACTIVE_FAILOVER` or `CLUSTER`

Eg. for single server:
```shell script
mvn test -Dgroups="api" \
  -Dtest.arangodb.requestTimeout="5000" \
  -Dtest.useProvidedDeployment="true" \
  -Dtest.arangodb.version="3.6.3" \
  -Dtest.arangodb.isEnterprise="false" \
  -Dtest.arangodb.hosts="localhost:8529" \
  -Dtest.arangodb.authentication="root:test" \
  -Dtest.arangodb.topology="SINGLE_SERVER"
```

Eg. for active failover:
```shell script
mvn test -Dgroups="api" \
  -Dtest.arangodb.requestTimeout="5000" \
  -Dtest.useProvidedDeployment="true" \
  -Dtest.arangodb.version="3.6.3" \
  -Dtest.arangodb.isEnterprise="false" \
  -Dtest.arangodb.hosts="server1:8529,server2:8529,server3:8529" \
  -Dtest.arangodb.authentication="root:test" \
  -Dtest.arangodb.topology="ACTIVE_FAILOVER"
```

Eg. for cluster:
```shell script
mvn test -Dgroups="api" \
  -Dtest.arangodb.requestTimeout="5000" \
  -Dtest.useProvidedDeployment="true" \
  -Dtest.arangodb.version="3.6.3" \
  -Dtest.arangodb.isEnterprise="false" \
  -Dtest.arangodb.hosts="coordinator1:8529,coordinator2:8529" \
  -Dtest.arangodb.authentication="root:test" \
  -Dtest.arangodb.topology="CLUSTER"
```


## GH Actions

To trigger GH Actions:
```shell script
./bin/mirror.sh
```

Check results [here](https://github.com/ArangoDB-Community/mirror-arangodb-java-driver/actions).


## SonarCloud

Check results [here](https://sonarcloud.io/dashboard?id=ArangoDB-Community_mirror-arangodb-java-driver).


## check dependecies updates

```shell script
mvn versions:display-dependency-updates
```


## snapshot release

Tagging a git revision with `v**-SNAPSHOT` will automatically trigger a snapshot release in [Github Packages](https://github.com/ArangoDB-Community/mirror-arangodb-java-driver/packages).
To import such package in another project:

```xml
    <dependencies>
        <!-- ... -->
        <dependency>
            <groupId>com.arangodb</groupId>
            <artifactId>arangodb-java-driver</artifactId>
            <version>7.0.0-20200203.102705-1</version>
        </dependency>    
    </dependencies>
    <repositories>
        <repository>
            <id>mirror-arangodb-java-driver</id>
            <name>mirror-arangodb-java-driver</name>
            <url>https://maven.pkg.github.com/ArangoDB-Community/mirror-arangodb-java-driver</url>
            <snapshots>
                <enabled>true</enabled>
            </snapshots>
        </repository>
    </repositories>
```
