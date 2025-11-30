# Spring Boot + Spring Batch — Single-File Cheat Sheet

This project is a **self-contained demonstration** of core concepts in **Spring Boot 4** and **Spring Batch 6**, all implemented in **one Java file**.  
It is designed as a **cheat sheet** / **playground** for quickly recalling how to wire up jobs, steps, profiles, properties, and scoped beans.

The main class (e.g. `BatchCheatSheetApplication`) shows:

- `@SpringBootApplication` and `@EnableBatchProcessing`
- Tasklet-style step and chunk-style step
- `@Service`, `@Component`, `@Configuration` and `@Bean`
- `@Profile`-specific beans (`dev` vs `prod`) for different `ItemReader` implementations
- `@ConfigurationProperties` for strongly-typed config (`app.*`)
- `@JobScope` and `@StepScope` with SpEL access to `jobParameters`
- `@ConditionalOnProperty` to optionally add a second step
- In-memory `JobRepository` and `PlatformTransactionManager` (no DB!)
- A `CommandLineRunner` that prints usage hints when `--job.name` is missing

It is intentionally minimal and console-only, so you can focus on **Spring Batch structure** rather than infrastructure.

---

## ✅ Requirements

- **Java 21** (Java 17+ works, Java 21 recommended)
- **Maven 3.6.3+**
- Internet access to download dependencies from Maven Central

Spring Boot used: **4.0.0** (GA)  
Spring Batch used transitively via `spring-boot-starter-batch`.

---

## 🛠 Build

Clone / copy the project so that you have:

- `pom.xml`
- `src/main/java/.../BatchCheatSheetApplication.java`

Then run:

```bash
mvn clean package
```

This produces a fat jar under target/, e.g.:

target/batch-cheatsheet-0.0.1-SNAPSHOT.jar

