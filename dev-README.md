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
