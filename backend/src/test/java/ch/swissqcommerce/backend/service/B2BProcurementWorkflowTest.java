package ch.swissqcommerce.backend.service;

import ch.swissqcommerce.backend.domain.agent.core.service.*;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
    public void testNegotiateRestockWorkflowFlowApproved() {
        B2BProcurementAgent.NegotiationAnalysis expectedAnalysis = new B2BProcurementAgent.NegotiationAnalysis(
                1.50, 0.95, "Bulk pricing approved", "ACCEPTED", 0.02
        );
        activitiesStub.setAnalysisToReturn(expectedAnalysis);
        activitiesStub.setGuardrailApproved(true);

        B2BProcurementWorkflow workflow = client.newWorkflowStub(
                B2BProcurementWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue("B2B_PROCUREMENT_TASK_QUEUE")
                        .build()
        );

        B2BProcurementAgent.NegotiationAnalysis actualAnalysis = workflow.negotiateRestock(
                "item-1", "Milk", 2.00, "Wholesaler A", 10
        );

        assertEquals(expectedAnalysis.proposedPrice, actualAnalysis.proposedPrice);
        assertEquals(expectedAnalysis.confidence, actualAnalysis.confidence);
        assertEquals(expectedAnalysis.rationale, actualAnalysis.rationale);
        assertEquals(expectedAnalysis.wholesalerResponse, actualAnalysis.wholesalerResponse);
        assertEquals(expectedAnalysis.cost, actualAnalysis.cost);
        assertEquals(1, activitiesStub.callLlmCount);
        assertTrue(activitiesStub.fulfilledOrderCreated);
    }

    @Test
    public void testNegotiateRestockWorkflowFlowBlockedAndApprovedSignal() {
        B2BProcurementAgent.NegotiationAnalysis expectedAnalysis = new B2BProcurementAgent.NegotiationAnalysis(
                1.50, 0.95, "Bulk pricing approved", "ACCEPTED", 0.02
        );
        activitiesStub.setAnalysisToReturn(expectedAnalysis);
        activitiesStub.setGuardrailApproved(false);

        B2BProcurementWorkflow workflow = client.newWorkflowStub(
                B2BProcurementWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("restock-order-123")
                        .setTaskQueue("B2B_PROCUREMENT_TASK_QUEUE")
                        .build()
        );

        // Run workflow asynchronously since it will block waiting for signal
        WorkflowClient.start(workflow::negotiateRestock, "item-1", "Milk", 2.00, "Wholesaler A", 100);

        // Wait a bit to ensure it is blocked
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}

        // Send approve signal
        workflow.approve("supervisor1", "Overriding guardrail");

        // Wait for workflow to finish and get result
        B2BProcurementAgent.NegotiationAnalysis actualAnalysis = workflow.negotiateRestock("item-1", "Milk", 2.00, "Wholesaler A", 100);

        assertTrue(activitiesStub.pendingOrderCreated);
        assertTrue(activitiesStub.auditNegotiationCalled);
        assertEquals("fulfilled", activitiesStub.updatedStatus);
        assertEquals("APPROVED", activitiesStub.approvalStatus);
        assertEquals("supervisor1", activitiesStub.operator);
    }

    @Test
    public void testNegotiateRestockWorkflowFlowBlockedAndAdjustedSignal() {
        B2BProcurementAgent.NegotiationAnalysis expectedAnalysis = new B2BProcurementAgent.NegotiationAnalysis(
                1.50, 0.95, "Bulk pricing approved", "ACCEPTED", 0.02
        );
        activitiesStub.setAnalysisToReturn(expectedAnalysis);
        activitiesStub.setGuardrailApproved(false);

        B2BProcurementWorkflow workflow = client.newWorkflowStub(
                B2BProcurementWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("restock-order-456")
                        .setTaskQueue("B2B_PROCUREMENT_TASK_QUEUE")
                        .build()
        );

        // Run workflow asynchronously
        WorkflowClient.start(workflow::negotiateRestock, "item-1", "Milk", 2.00, "Wholesaler A", 100);

        // Wait a bit
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}

        // Send adjustBid signal
        workflow.adjustBid(1.75, "supervisor1", "Acceptable adjusted bid");

        // Wait for workflow to finish
        B2BProcurementAgent.NegotiationAnalysis actualAnalysis = workflow.negotiateRestock("item-1", "Milk", 2.00, "Wholesaler A", 100);

        assertTrue(activitiesStub.pendingOrderCreated);
        assertTrue(activitiesStub.auditNegotiationCalled);
        assertEquals(1.75, actualAnalysis.proposedPrice);
        assertEquals("fulfilled", activitiesStub.updatedStatus);
        assertEquals("APPROVED", activitiesStub.approvalStatus);
        assertEquals(1.75, activitiesStub.updatedPrice);
    }

    @Test
    public void testNegotiateRestockWorkflowFlowBlockedAndRejectedSignal() {
        B2BProcurementAgent.NegotiationAnalysis expectedAnalysis = new B2BProcurementAgent.NegotiationAnalysis(
                1.50, 0.95, "Bulk pricing approved", "ACCEPTED", 0.02
        );
        activitiesStub.setAnalysisToReturn(expectedAnalysis);
        activitiesStub.setGuardrailApproved(false);

        B2BProcurementWorkflow workflow = client.newWorkflowStub(
                B2BProcurementWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("restock-order-789")
                        .setTaskQueue("B2B_PROCUREMENT_TASK_QUEUE")
                        .build()
        );

        WorkflowClient.start(workflow::negotiateRestock, "item-1", "Milk", 2.00, "Wholesaler A", 100);

        try { Thread.sleep(200); } catch (InterruptedException ignored) {}

        workflow.reject("supervisor1", "Price too high");

        B2BProcurementAgent.NegotiationAnalysis actualAnalysis = workflow.negotiateRestock("item-1", "Milk", 2.00, "Wholesaler A", 100);

        assertTrue(activitiesStub.pendingOrderCreated);
        assertTrue(activitiesStub.auditNegotiationCalled);
        assertEquals("failed", activitiesStub.updatedStatus);
        assertEquals("REJECTED", activitiesStub.approvalStatus);
        assertEquals("supervisor1", activitiesStub.operator);
        assertEquals("REJECTED", actualAnalysis.wholesalerResponse);
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
            workflow.negotiateRestock("item-1", "Milk", 2.00, "Wholesaler A", 10);
        });
    }

    private static class B2BProcurementActivitiesStub implements B2BProcurementActivities {
        private B2BProcurementAgent.NegotiationAnalysis analysisToReturn;
        private RuntimeException exceptionToThrow;
        private int callLlmCount = 0;
        private boolean guardrailApproved = true;
        private boolean fulfilledOrderCreated = false;
        private boolean pendingOrderCreated = false;
        private boolean auditNegotiationCalled = false;
        private String updatedStatus = null;
        private Double updatedPrice = null;
        private String approvalStatus = null;
        private String operator = null;

        public void setAnalysisToReturn(B2BProcurementAgent.NegotiationAnalysis analysisToReturn) {
            this.analysisToReturn = analysisToReturn;
            this.exceptionToThrow = null;
        }

        public void setExceptionToThrow(RuntimeException exceptionToThrow) {
            this.exceptionToThrow = exceptionToThrow;
            this.analysisToReturn = null;
        }

        public void setGuardrailApproved(boolean guardrailApproved) {
            this.guardrailApproved = guardrailApproved;
        }

        @Override
        public B2BProcurementAgent.NegotiationAnalysis callLlmNegotiation(
                String itemId, String itemName, double basePrice, String wholesalerName) {
            callLlmCount++;
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return analysisToReturn;
        }

        @Override
        public boolean checkGuardrail(double proposedPrice, double basePrice, int quantity) {
            return guardrailApproved;
        }

        @Override
        public int createPendingOrder(String itemId, String wholesalerName, double proposedPrice, int quantity) {
            pendingOrderCreated = true;
            return 123;
        }

        @Override
        public int createFulfilledOrder(String itemId, String wholesalerName, double proposedPrice, int quantity) {
            fulfilledOrderCreated = true;
            return 123;
        }

        @Override
        public void auditNegotiation(int restockOrderId, String wholesalerName, double proposedPrice, int quantity) {
            auditNegotiationCalled = true;
        }

        @Override
        public void updateOrderStatus(int restockOrderId, String status) {
            updatedStatus = status;
        }

        @Override
        public void updateOrderPrice(int restockOrderId, double newPrice) {
            updatedPrice = newPrice;
        }

        @Override
        public void updateApprovalStatus(int restockOrderId, String status, String operator, String reason) {
            approvalStatus = status;
            this.operator = operator;
        }
    }
}
