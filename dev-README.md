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
