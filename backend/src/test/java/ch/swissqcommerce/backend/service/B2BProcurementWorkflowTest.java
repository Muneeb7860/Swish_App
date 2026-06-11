package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.agent.core.service.*;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class B2BProcurementWorkflowTest {

    private TestWorkflowEnvironment testEnv;
    private Worker worker;
    private WorkflowClient client;
    private B2BProcurementActivitiesStub activitiesStub;

    @BeforeEach
    public void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        worker = testEnv.newWorker("B2B_PROCUREMENT_TASK_QUEUE");
        worker.registerWorkflowImplementationTypes(B2BProcurementWorkflowImpl.class);

        activitiesStub = new B2BProcurementActivitiesStub();
        worker.registerActivitiesImplementations(activitiesStub);

        client = testEnv.getWorkflowClient();
        testEnv.start();
    }

    @AfterEach
    public void tearDown() {
        testEnv.close();
    }

    @Test
    public void testNegotiateRestockWorkflowFlow() {
        B2BProcurementAgent.NegotiationAnalysis expectedAnalysis = new B2BProcurementAgent.NegotiationAnalysis(
                1.50, 0.95, "Bulk pricing approved", "ACCEPTED", 0.02
        );
        activitiesStub.setAnalysisToReturn(expectedAnalysis);

        B2BProcurementWorkflow workflow = client.newWorkflowStub(
                B2BProcurementWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue("B2B_PROCUREMENT_TASK_QUEUE")
                        .build()
        );

        B2BProcurementAgent.NegotiationAnalysis actualAnalysis = workflow.negotiateRestock(
                "item-1", "Milk", 2.00, "Wholesaler A"
        );

        assertEquals(expectedAnalysis.proposedPrice, actualAnalysis.proposedPrice);
        assertEquals(expectedAnalysis.confidence, actualAnalysis.confidence);
        assertEquals(expectedAnalysis.rationale, actualAnalysis.rationale);
        assertEquals(expectedAnalysis.wholesalerResponse, actualAnalysis.wholesalerResponse);
        assertEquals(expectedAnalysis.cost, actualAnalysis.cost);
        assertEquals(1, activitiesStub.callCount);
    }

    @Test
    public void testNegotiateRestockWorkflowFailureFlow() {
        activitiesStub.setExceptionToThrow(new RuntimeException("Simulated activity failure"));

        B2BProcurementWorkflow workflow = client.newWorkflowStub(
                B2BProcurementWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue("B2B_PROCUREMENT_TASK_QUEUE")
                        .build()
        );

        assertThrows(Exception.class, () -> {
            workflow.negotiateRestock("item-1", "Milk", 2.00, "Wholesaler A");
        });
    }

    private static class B2BProcurementActivitiesStub implements B2BProcurementActivities {
        private B2BProcurementAgent.NegotiationAnalysis analysisToReturn;
        private RuntimeException exceptionToThrow;
        private int callCount = 0;

        public void setAnalysisToReturn(B2BProcurementAgent.NegotiationAnalysis analysisToReturn) {
            this.analysisToReturn = analysisToReturn;
            this.exceptionToThrow = null;
        }

        public void setExceptionToThrow(RuntimeException exceptionToThrow) {
            this.exceptionToThrow = exceptionToThrow;
            this.analysisToReturn = null;
        }

        @Override
        public B2BProcurementAgent.NegotiationAnalysis callLlmNegotiation(
                String itemId, String itemName, double basePrice, String wholesalerName) {
            callCount++;
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return analysisToReturn;
        }
    }
}
