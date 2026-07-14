package net.jrodolfo.llm.service;

import net.jrodolfo.llm.model.PendingToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatToolRouterServiceTest {

    private final ChatToolRouterService router = new ChatToolRouterService();

    @Test
    void routesAwsAuditRequests() {
        var decision = router.route("please run aws audit for us-east-2 sts");

        assertEquals(ChatToolRouterService.DecisionType.AWS_REGION_AUDIT, decision.type());
        assertEquals("us-east-2", decision.region());
        assertTrue(decision.services().contains("sts"));
    }

    @Test
    void routesAwsAccountAnalysisRequestsToAuditTool() {
        var decision = router.route("Analyze my AWS account and summarize the services I am using, highlighting anything unusual or potentially worth reviewing.");

        assertEquals(ChatToolRouterService.DecisionType.AWS_REGION_AUDIT, decision.type());
        assertTrue(decision.shouldUseTool());
        assertEquals("aws audit request", decision.reason());
    }

    @Test
    void routesAwsAccountReviewRequestsToAuditTool() {
        var decision = router.route("Review my AWS account and tell me what services are active.");

        assertEquals(ChatToolRouterService.DecisionType.AWS_REGION_AUDIT, decision.type());
        assertTrue(decision.shouldUseTool());
    }

    @Test
    void routesS3CloudwatchRequests() {
        var decision = router.route("check bucket jrodolfo.net metrics in us-east-2 for 7 days");

        assertEquals(ChatToolRouterService.DecisionType.S3_CLOUDWATCH_REPORT, decision.type());
        assertEquals("jrodolfo.net", decision.bucket());
        assertEquals("us-east-2", decision.region());
        assertEquals(7, decision.days());
    }

    @Test
    void routesS3CloudwatchRequestsWithBucketBeforeKeyword() {
        var decision = router.route("check jrodolfo.net bucket metrics for 30 days");

        assertEquals(ChatToolRouterService.DecisionType.S3_CLOUDWATCH_REPORT, decision.type());
        assertEquals("jrodolfo.net", decision.bucket());
        assertEquals(30, decision.days());
    }

    @Test
    void routesLatestReportReads() {
        var decision = router.route("read the latest audit report");

        assertEquals(ChatToolRouterService.DecisionType.READ_LATEST_REPORT, decision.type());
        assertEquals("audit", decision.reportType());
    }

    @Test
    void asksForClarificationWhenLatestReportTypeIsAmbiguous() {
        var decision = router.route("read the latest report");

        assertEquals(ChatToolRouterService.DecisionType.READ_LATEST_REPORT, decision.type());
        assertTrue(decision.needsClarification());
        assertFalse(decision.shouldUseTool());
    }

    @Test
    void asksForClarificationWhenBucketIsMissing() {
        var decision = router.route("check bucket metrics for the last 7 days");

        assertEquals(ChatToolRouterService.DecisionType.S3_CLOUDWATCH_REPORT, decision.type());
        assertTrue(decision.needsClarification());
        assertEquals(null, decision.bucket());
    }

    @Test
    void routesGenericS3ReportRequestsAndPreservesLastMonthWindow() {
        var decision = router.route("Give me a report from AWS S3 for the last month.");

        assertEquals(ChatToolRouterService.DecisionType.S3_CLOUDWATCH_REPORT, decision.type());
        assertTrue(decision.needsClarification());
        assertEquals(30, decision.days());
        assertTrue(decision.clarification().contains("local AWS CLI credentials"));
        assertTrue(decision.clarification().contains("one bucket at a time"));
        assertTrue(decision.clarification().contains("Please provide the bucket name"));
        assertTrue(decision.clarification().contains("ask me: \"list my S3 buckets\""));
        assertFalse(decision.clarification().contains("run an AWS audit"));
    }

    @Test
    void routesS3BucketListRequestsToS3ScopedAudit() {
        var decision = router.route("list my S3 buckets");

        assertEquals(ChatToolRouterService.DecisionType.AWS_REGION_AUDIT, decision.type());
        assertTrue(decision.shouldUseTool());
        assertEquals(List.of("s3"), decision.services());
    }

    @Test
    void routesNaturalS3BucketDiscoveryQuestionsToS3ScopedAudit() {
        var decision = router.route("what S3 buckets do I have?");

        assertEquals(ChatToolRouterService.DecisionType.AWS_REGION_AUDIT, decision.type());
        assertEquals(List.of("s3"), decision.services());
    }

    @Test
    void allBucketsFollowUpKeepsS3PendingStateButExplainsCurrentBoundary() {
        var clarification = router.route("Give me a report from AWS S3 for the last month.");
        var decision = router.resolvePending(
                new PendingToolCall(
                        clarification.type(),
                        clarification.reportType(),
                        clarification.bucket(),
                        clarification.region(),
                        clarification.days(),
                        clarification.reason(),
                        clarification.services(),
                        List.of("bucket")
                ),
                "all buckets"
        );

        assertEquals(ChatToolRouterService.DecisionType.S3_CLOUDWATCH_REPORT, decision.type());
        assertTrue(decision.needsClarification());
        assertEquals(30, decision.days());
        assertTrue(decision.clarification().contains("not implemented yet"));
    }

    @Test
    void extractsAuditServiceAliases() {
        var decision = router.route("run aws audit for us-east-2 load balancers and secrets");

        assertEquals(ChatToolRouterService.DecisionType.AWS_REGION_AUDIT, decision.type());
        assertTrue(decision.services().contains("elbv2"));
        assertTrue(decision.services().contains("secretsmanager"));
    }

    @Test
    void leavesNormalChatRequestsAlone() {
        var decision = router.route("explain recursion using a simple example");

        assertEquals(ChatToolRouterService.DecisionType.NONE, decision.type());
        assertFalse(decision.shouldUseTool());
    }

    @Test
    void leavesAwsConceptRequestsAlone() {
        var decision = router.route("explain AWS services");

        assertEquals(ChatToolRouterService.DecisionType.NONE, decision.type());
        assertFalse(decision.shouldUseTool());
    }

    @Test
    void leavesAwsAccountConceptRequestsAlone() {
        var decision = router.route("how does an AWS account work?");

        assertEquals(ChatToolRouterService.DecisionType.NONE, decision.type());
        assertFalse(decision.shouldUseTool());
    }

    @Test
    void doesNotTreatGenericReportDiscussionAsToolRequest() {
        var decision = router.route("what makes a good engineering report?");

        assertEquals(ChatToolRouterService.DecisionType.NONE, decision.type());
        assertFalse(decision.shouldUseTool());
    }
}
