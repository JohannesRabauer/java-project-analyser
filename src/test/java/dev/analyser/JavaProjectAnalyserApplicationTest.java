package dev.analyser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JavaProjectAnalyserApplicationTest {

    @Test
    void shouldKeepTheUc003ApplicationStubInPlace() {
        assertEquals("dev.analyser.JavaProjectAnalyserApplication", JavaProjectAnalyserApplication.class.getName());
    }
}
