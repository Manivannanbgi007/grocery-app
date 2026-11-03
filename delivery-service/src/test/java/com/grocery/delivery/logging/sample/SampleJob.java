package com.grocery.delivery.logging.sample;

import org.springframework.scheduling.annotation.Scheduled;

/** Test sample for LoggingAspectTest: a scheduled job (the annotation is enough; nothing is scheduled in the test). */
public class SampleJob {

    @Scheduled(fixedDelay = 60_000)
    public void run() {
    }
}
