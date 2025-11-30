# spring_batch_cheat_app
Single file Spring Batch demo application “cheat sheet” that you can copy-paste and study. It shows:

@SpringBootApplication + @EnableBatchProcessing

A tasklet step and a chunk step

@Service, @Component, @Configuration/@Bean

@Profile-driven beans (dev vs prod)

@ConfigurationProperties for typed config

@JobScope/@StepScope with SpEL access to JobParameters

@ConditionalOnProperty to toggle an optional step

In-memory JobRepository (no DB) so it stays self-contained

Usage hints via a CommandLineRunner

I put everything (with rich inline comments) into a single Java file on the canvas so it’s easy to read and reuse.

Run examples (once you package it as a jar):

Dev (in-memory reader):

java -jar app.jar --spring.profiles.active=dev --job.name=demoJob name=Aleks app.skip-uppercase=true


Prod (reads lines from a file):

java -jar app.jar --spring.profiles.active=prod --job.name=demoJob name=World path=/tmp/lines.txt app.enable-second-step=true

