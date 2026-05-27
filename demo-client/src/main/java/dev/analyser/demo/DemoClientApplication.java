package dev.analyser.demo;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public final class DemoClientApplication {

    public static void main(String... args) {
        Quarkus.run(args);
    }
}
