# Spring Boot + Spring Batch — Single-File Cheat Sheet

This project is a **self-contained demonstration** of nearly all core concepts in **Spring Boot** and **Spring Batch**, written in a **single Java file** for easy study and experimentation.  
It uses **no database**, relying on in-memory job storage, and works directly from the command line.

---

## 📘 What It Shows

The example covers:

- ✅ `@SpringBootApplication` and `@EnableBatchProcessing`
- ✅ `@Service`, `@Component`, and `@Configuration`
- ✅ `@Profile`-specific beans (`dev` vs `prod`)
- ✅ `@ConfigurationProperties` for typed configuration
- ✅ `@JobScope` and `@StepScope` for scoped beans
- ✅ Tasklet-style and Chunk-style steps
- ✅ `@ConditionalOnProperty` to toggle optional steps
- ✅ In-memory `JobRepository` + `ResourcelessTransactionManager`
- ✅ Command-line job parameters and dynamic Job launch
- ✅ Logging, SpEL expressi

