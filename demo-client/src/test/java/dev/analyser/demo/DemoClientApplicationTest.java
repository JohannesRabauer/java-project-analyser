package dev.analyser.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DemoClientApplicationTest {

    @Test
    void uc003_shouldKeepTheStandaloneDemoClientStubInPlace() {
        assertEquals("dev.analyser.demo.DemoClientApplication", DemoClientApplication.class.getName());
    }
}
