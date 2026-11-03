package com.grocery.platform.logging.sample.controller;

/** Test sample for LoggingAspectTest: in a "controller" package, like a real REST controller. */
public class SampleController {

    public String create(String password) {
        return "created";
    }

    public void fail() {
        throw new IllegalStateException("Out of stock");
    }
}
