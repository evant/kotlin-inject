# Contributing
 
## Creating the Pull Request

To contribute, fork our project on GitHub, then submit a pull request to our `main` branch. Take a look at
[architecture.md](docs/architecture.md) to get a high-level view how the project is structured.

## Static Code Analysis

PR's are checked [Detekt](https://github.com/detekt/detekt). We recommend you run `./gradlew detekt` locally. You can
often fix formatting errors automatically with `./gradlew detekt --auto-correct`.
 
## Testing

Tests are broken up into two parts. The ones in `integration-test` test the behavior of the generated code is correct
for various features. The ones in `compiler-test` test the output of the compiler.
 
To debug the compiler, you can write the code you want to test and then run
 ```
./gradlew :integration-tests:kapt:test --no-daemon -Dorg.gradle.debug=true -Dkotlin.compiler.execution.strategy=in-process
```
or
```
./gradlew :integration-tests:ksp:test --no-daemon -Dorg.gradle.debug=true -Dkotlin.compiler.execution.strategy=in-process
```
for kapt or ksp respectively. Then you can create and run a remote run configuration in intellij to attach the debugger.
 
 ---

By submitting a pull request, you represent that you have the right to license your contribution to the community, and
agree by submitting the patch that your contributions are licensed under the [Apache License 2.0](LICENSE).