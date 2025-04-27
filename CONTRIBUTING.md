# Contributing
 
## Creating the Pull Request

To contribute, fork our project on GitHub, then submit a pull request to our `main` branch. Take a look at
[architecture.md](docs/architecture.md) to get a high-level view how the project is structured.

## Static Code Analysis

PR's are checked with [Detekt](https://github.com/detekt/detekt). We recommend you run `./gradlew detekt` locally. You
can often fix formatting errors automatically with `./gradlew detekt --auto-correct`.
 
## Testing

Tests are broken up into two parts. The ones in `integration-test` test the behavior of the generated code is correct
for various features. The ones in `compiler-test` test the output of the compiler.
 
To debug the compiler, you can write the code you want to test and then run
```
./gradlew :integration-tests:ksp:test --no-daemon -Dorg.gradle.debug=true -Dkotlin.compiler.execution.strategy=in-process
```
Then you can create and run a remote run configuration in intellij to attach the debugger.

## Releasing

In order to publish a release, follow the following steps:
1. create a branch named: release/<version>
2. Ensure all changes are listed in CHANGELOG.md
3. Update the kotlin-inject version in gradle/libs.versions.toml to the release version, removing the SNAPSHOT suffix
4. Run ./gradlew patchChangelog
5. Update the version in the README
6. Create a pull request with those changes
7. Merge, CI should create a GitHub release and publish to maven central
8. On the same branch, update the kotlin-inject version again, adding back the SNAPSHOT suffix
9. Create a second pull request & merge
 
 ---

By submitting a pull request, you represent that you have the right to license your contribution to the community, and
agree by submitting the patch that your contributions are licensed under the [Apache License 2.0](LICENSE).