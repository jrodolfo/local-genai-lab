package net.jrodolfo.llm.service;

import org.junit.jupiter.api.Test;

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
    void doesNotTreatGenericReportDiscussionAsToolRequest() {
        var decision = router.route("what makes a good engineering report?");

        assertEquals(ChatToolRouterService.DecisionType.NONE, decision.type());
        assertFalse(decision.shouldUseTool());
    }
}
