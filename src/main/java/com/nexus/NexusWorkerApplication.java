package com.nexus;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Worker entry point. No HTTP listener — it only reacts to SQS messages
 * (wired up in Phase 3). Runs from the same artifact and image as
 * {@link NexusApiApplication}; which one starts is a deployment-time choice
 * (ECS task command), not a build-time one.
 */
@SpringBootApplication
public class NexusWorkerApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(NexusWorkerApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }
}
