package ch.swissqcommerce.backend.domain.agent.config;

import ch.swissqcommerce.backend.domain.agent.core.service.B2BProcurementActivities;
import ch.swissqcommerce.backend.domain.agent.core.service.B2BProcurementWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Configuration
@Slf4j
public class TemporalConfig {

    @Value("${temporal.service-address:localhost:7233}")
    private String serviceAddress;

    private WorkerFactory workerFactory;

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        log.info("Initializing Temporal WorkflowServiceStubs connecting to {}", serviceAddress);
        return WorkflowServiceStubs.newInstance(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(serviceAddress)
                        .build());
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs serviceStubs) {
        log.info("Initializing Temporal WorkflowClient");
        return WorkflowClient.newInstance(serviceStubs,
                WorkflowClientOptions.newBuilder().build());
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient workflowClient) {
        this.workerFactory = WorkerFactory.newInstance(workflowClient);
        return this.workerFactory;
    }

    @Bean
    public Worker b2bWorker(WorkerFactory factory, B2BProcurementActivities activities) {
        log.info("Registering Temporal worker on task queue B2B_PROCUREMENT_TASK_QUEUE");
        Worker worker = factory.newWorker("B2B_PROCUREMENT_TASK_QUEUE");
        worker.registerWorkflowImplementationTypes(B2BProcurementWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        return worker;
    }

    @PostConstruct
    public void startWorkerFactory() {
        try {
            if (workerFactory != null) {
                log.info("Starting Temporal Worker Factory...");
                workerFactory.start();
                log.info("Temporal Worker Factory started successfully.");
            }
        } catch (Exception e) {
            log.error("Failed to start Temporal Worker Factory (Temporal server offline?): {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stopWorkerFactory() {
        try {
            if (workerFactory != null) {
                log.info("Stopping Temporal Worker Factory...");
                workerFactory.shutdown();
                log.info("Temporal Worker Factory stopped successfully.");
            }
        } catch (Exception e) {
            log.error("Error shutting down Temporal Worker Factory: {}", e.getMessage());
        }
    }
}
