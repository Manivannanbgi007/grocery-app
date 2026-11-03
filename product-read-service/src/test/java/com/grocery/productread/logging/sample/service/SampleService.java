package com.grocery.productread.logging.sample.service;

/** Test sample for LoggingAspectTest: in a "service" package, like a real service. */
public class SampleService {

    public void quickWork() {
    }

    public void slowWork() {
        try {
            Thread.sleep(150);   // longer than the 100 ms limit of the test
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public void fail() {
        throw new IllegalArgumentException("Bad input");
    }
}
