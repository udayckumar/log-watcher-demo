package com.example.logwatcher;

import com.example.logwatcher.core.BackwardSeekStrategy;
import com.example.logwatcher.core.FileTailService;
import com.example.logwatcher.core.LineReadingStrategy;
import com.example.logwatcher.core.LogNotifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot entry point. Wires core components into Spring context.
 */
@SpringBootApplication(scanBasePackages = "com.example.logwatcher")
public class LogwatcherApplication {

//    @Value("${app.log-file:./logwatcher.log}")
    @Value("${app.log-file}")
    private String logFilePath;

    @Value("${app.poll-interval-ms:200}")
    private int pollIntervalMs;

    public static void main(String[] args) {
        SpringApplication.run(LogwatcherApplication.class, args);
    }

    @Bean
    public LogNotifier logNotifier() {
        return new LogNotifier();
    }

    @Bean
    public LineReadingStrategy lineReadingStrategy() {
        return new BackwardSeekStrategy();
    }

    @Bean
    public FileTailService fileTailService(LogNotifier notifier, LineReadingStrategy strategy) {
        // instantiate with configured file path and poll interval
        return new FileTailService(logFilePath, notifier, strategy, pollIntervalMs);
    }
}
