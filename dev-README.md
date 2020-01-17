# dev-README

## code coverage report

Run tests:
```shell script
mvn clean test
```

Coverage report: [target/site/jacoco/index.html](target/site/jacoco/index.html)


## GH Actions

To trigger GH Actions:
```shell script
./bin/mirror.sh
```

Check results [here](https://github.com/ArangoDB-Community/mirror-arangodb-java-driver/actions).



## SonarCloud

Set the environment variable `SONAR_LOGIN` and run:

```shell script
mvn verify sonar:sonar -Dgpg.skip
```

Check results [here](https://sonarcloud.io/dashboard?id=rashtao_mirror-arangodb-java-driver).
