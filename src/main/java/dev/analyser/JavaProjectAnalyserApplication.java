package dev.analyser;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public final class JavaProjectAnalyserApplication {

    public static void main(String... args) {
        Quarkus.run(args);
    }
}
