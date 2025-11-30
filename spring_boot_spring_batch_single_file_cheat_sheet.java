package demo.batchcheatsheet;

import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.MapJobRepositoryFactoryBean;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.*;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.ResourcelessTransactionManager;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Spring Boot + Spring Batch single-file cheat sheet.
 * 
 * This file demonstrates, in one place:
 *  - @SpringBootApplication bootstrap + @EnableBatchProcessing
 *  - Job/Step creation with the modern (Batch 5) builders
 *  - Tasklet step and chunk-oriented step
 *  - @Service vs @Component; @Configuration + @Bean
 *  - @Profile to swap beans per env (dev vs prod)
 *  - @ConfigurationProperties for typed config
 *  - @JobScope/@StepScope + SpEL access to JobParameters
 *  - @ConditionalOnProperty to include optional steps
 *  - In-memory MapJobRepository + ResourcelessTransactionManager to avoid DB setup
 *  - CommandLineRunner to print usage and demonstrate programmatic Job launch
 *
 * How to run (typical examples):
 *   # DEV profile (uses in-memory list reader)
 *   java -jar app.jar --spring.profiles.active=dev --job.name=demoJob name=Aleks skipUppercase=true app.sleep-millis=0
 *
 *   # PROD profile (reads from file via job param 'path')
 *   java -jar app.jar --spring.profiles.active=prod --job.name=demoJob name=World path=/tmp/lines.txt app.enable-second-step=true
 *
 * Notes:
 *  - In Spring Batch, the special --job.name=<jobId> activates JobLauncherApplicationRunner.
 *  - Additional job parameters come after the boot args as key=value (no leading dashes).
 */
@SpringBootApplication
@EnableBatchProcessing
@EnableConfigurationProperties(BatchCheatSheetApplication.AppProps.class)
public class BatchCheatSheetApplication {
  private static final Logger log = LoggerFactory.getLogger(BatchCheatSheetApplication.class);

  public static void main(String[] args) {
    SpringApplication.run(BatchCheatSheetApplication.class, args);
  }

  /* ========================= Core Infrastructure ========================= */
  @Bean
  public PlatformTransactionManager transactionManager() {
    // For cheat-sheet simplicity (no DB). In real apps, prefer DataSourceTransactionManager.
    return new ResourcelessTransactionManager();
  }

  @Bean
  public JobRepository jobRepository(PlatformTransactionManager tm) throws Exception {
    MapJobRepositoryFactoryBean f = new MapJobRepositoryFactoryBean(tm);
    f.afterPropertiesSet();
    return f.getObject();
  }

  /* ========================= Properties / Config ========================= */
  /**
   * Typesafe config mapped from prefix "app" (e.g., app.sleep-millis=100, app.enable-second-step=true).
   */
  @ConfigurationProperties(prefix = "app")
  public static class AppProps {
    /** if true, skip uppercasing in chunk step */
    private boolean skipUppercase = false;
    /** optional artificial delay per item (ms) */
    private long sleepMillis = 0;
    /** toggles second step via @ConditionalOnProperty alternative */
    private boolean enableSecondStep = false; // also controllable via property
    public boolean isSkipUppercase() { return skipUppercase; }
    public void setSkipUppercase(boolean skipUppercase) { this.skipUppercase = skipUppercase; }
    public long getSleepMillis() { return sleepMillis; }
    public void setSleepMillis(long sleepMillis) { this.sleepMillis = sleepMillis; }
    public boolean isEnableSecondStep() { return enableSecondStep; }
    public void setEnableSecondStep(boolean enableSecondStep) { this.enableSecondStep = enableSecondStep; }
  }

  /* ========================= Services & Components ========================= */
  /** A simple business service (stateless). */
  @Service
  public static class GreetingService {
    public String greet(String name) { return "Hello, " + name + "!"; }
  }

  /** A reusable processor bean. Demonstrates @Component. */
  @Component
  public static class UppercaseProcessor implements ItemProcessor<String, String> {
    @Autowired AppProps props;
    @Override public String process(String item) throws Exception {
      if (props.isSkipUppercase()) return item;
      return item.toUpperCase(Locale.ROOT);
    }
  }

  /* ========================= Readers (with Profiles) ========================= */
  /** SPI to provide an ItemReader depending on environment. */
  public interface SourceProvider { ItemReader<String> reader(); }

  /** dev: small in-memory list */
  @Profile("dev")
  @Configuration
  public static class DevSourceConfig {
    @Bean
    public SourceProvider devSourceProvider() {
      return () -> new ListItemReader<>(List.of("alpha", "bravo", "charlie"));
    }
  }

  /** prod: load from a text file, path passed via JobParameter 'path'. */
  @Profile("prod")
  @Configuration
  public static class ProdSourceConfig {
    /**
     * @StepScope lets us access JobParameters with SpEL.
     * We return a ListItemReader over the file's lines for simplicity (no FlatFileItemReader setup).
     */
    @Bean
    @StepScope
    public ItemReader<String> fileReader(@Value("#{jobParameters['path']}") String path) {
      return new ItemReader<>() {
        BufferedReader br;
        String next;
        private void init() throws IOException {
          if (br == null) br = Files.newBufferedReader(Path.of(path));
        }
        @Override public String read() throws Exception {
          init();
          next = br.readLine();
          if (next == null) { br.close(); return null; }
          return next;
        }
      };
    }
    @Bean
    public SourceProvider prodSourceProvider(@Qualifier("fileReader") ItemReader<String> r) {
      return () -> r;
    }
  }

  /* ========================= Tasklets ========================= */
  /** Example tasklet that validates required params and logs startup info. */
  @Bean
  @JobScope
  public Tasklet validateParamsTasklet(@Value("#{jobParameters['name']}") String nameParam,
                                       GreetingService greeter) {
    return (contribution, chunkContext) -> {
      if (nameParam == null || nameParam.isBlank())
        throw new JobParametersInvalidException("Missing required job parameter 'name'");
      String greet = greeter.greet(nameParam);
      LoggerFactory.getLogger("validate").info("{} (at {})", greet, LocalDateTime.now());
      return RepeatStatus.FINISHED;
    };
  }

  /** Optional post-processing tasklet, can be toggled via property */
  @Bean
  @ConditionalOnProperty(value = "app.enable-second-step", havingValue = "true", matchIfMissing = false)
  public Tasklet secondTasklet() {
    return (contribution, chunkContext) -> {
      LoggerFactory.getLogger("second").info("Second tasklet executed.");
      return RepeatStatus.FINISHED;
    };
  }

  /* ========================= Writers ========================= */
  @Bean
  public ItemWriter<String> loggingWriter(AppProps props) {
    Logger wlog = LoggerFactory.getLogger("writer");
    return items -> {
      for (String s : items) {
        if (props.getSleepMillis() > 0) try { Thread.sleep(props.getSleepMillis()); } catch (InterruptedException ignored) {}
        wlog.info("wrote: {}", s);
      }
    };
  }

  /* ========================= Job & Steps ========================= */
  @Bean
  public Job demoJob(JobRepository repo,
                     PlatformTransactionManager tm,
                     Tasklet validateParamsTasklet,
                     UppercaseProcessor processor,
                     ItemWriter<String> writer,
                     ApplicationContext ctx,
                     AppProps props) {

    // Step 1: tasklet-style validation
    StepBuilder step1b = new StepBuilder("validateParams", repo);
    Step step1 = step1b.tasklet(validateParamsTasklet, tm).build();

    // Step 2: chunk-style processing using SourceProvider
    SourceProvider sourceProvider = ctx.getBean(SourceProvider.class); // resolved per @Profile
    ItemReader<String> reader = sourceProvider.reader();
    StepBuilder step2b = new StepBuilder("processData", repo);
    Step step2 = step2b.<String, String>chunk(3, tm)
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .faultTolerant()                     // example: fault-tolerance toggles
        .skip(IllegalStateException.class)
        .skipLimit(3)
        .taskExecutor(new SimpleAsyncTaskExecutor("chunk-")) // illustrate async chunks
        .throttleLimit(2)
        .build();

    JobBuilder jb = new JobBuilder("demoJob", repo);
    JobFlowBuilder flow = jb.start(step1).on("COMPLETED").to(step2).from(step1).on("FAILED").fail();

    // Optionally add a second tasklet step if bean exists and property enabled
    if (props.isEnableSecondStep() && ctx.containsBean("secondTasklet")) {
      Tasklet st = ctx.getBean("secondTasklet", Tasklet.class);
      Step s3 = new StepBuilder("secondStep", repo).tasklet(st, tm).build();
      flow = flow.next(s3);
    }

    return flow.end().build();
  }

  /* ========================= CLI / Usage Helper ========================= */
  @Bean
  public CommandLineRunner usagePrinter(ApplicationArguments args) {
    return ignored -> {
      if (!args.containsOption("job.name")) {
        log.info("\nUSAGE (examples):\n" +
            "  --spring.profiles.active=dev --job.name=demoJob name=Aleks app.skip-uppercase=true\n" +
            "  --spring.profiles.active=prod --job.name=demoJob name=World path=/tmp/lines.txt app.enable-second-step=true\n" +
            "Notes: job parameters follow as key=value (no leading dashes).\n");
      }
    };
  }
}
