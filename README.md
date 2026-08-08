# Jetpack Compose Bytecode Instrumentation

Having fun! Exploring how to inject UI into a Jetpack Compose function using ASM.

## Project structure

- `app` contains the sample application and the bytecode instrumentation logic.
- `instrumentation-runtime` contains the `@InjectComposable` annotation and the UI that is injected.

The visitor is declared directly in `app/build.gradle.kts`, but in a real-world scenario, it could be placed in a reusable binary plugin.
