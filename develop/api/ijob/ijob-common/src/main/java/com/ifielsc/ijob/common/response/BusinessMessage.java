// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.common.response;

import java.util.HashMap;
import java.util.Map;

/** Explicit catalog for audited business call sites. No arbitrary exception text is exposed. */
public enum BusinessMessage implements MessageDefinition {
    PLUGIN_1("IJOB-DETAIL-0300", "error.detail.plugin-1", "data check resourceId must be positive"),
    PLUGIN_2("IJOB-DETAIL-0301", "error.detail.plugin-2", "data check SQL must be a read query"),
    PLUGIN_3("IJOB-DETAIL-0302", "error.detail.plugin-3", "push SQL must be a read query"),
    PLUGIN_4("IJOB-DETAIL-0303", "error.detail.plugin-4", "push Kafka topic is invalid"),
    PLUGIN_5("IJOB-DETAIL-0304", "error.detail.plugin-5", "Local JAR SHA-256 does not match"),
    PLUGIN_6("IJOB-DETAIL-0305", "error.detail.plugin-6", "Script content is required"),
    PLUGIN_7(
            "IJOB-DETAIL-0306",
            "error.detail.plugin-7",
            "resolved monitor disk paths must be unique"),
    PLUGIN_8("IJOB-DETAIL-0307", "error.detail.plugin-8", "application PID is invalid"),
    PLUGIN_9("IJOB-DETAIL-0308", "error.detail.plugin-9", "affected rows < minRows"),
    PLUGIN_10("IJOB-DETAIL-0309", "error.detail.plugin-10", "affected rows > maxRows"),
    DETAIL_207(
            "IJOB-DETAIL-207",
            "error.detail.condition-complexity-limit-exceeded",
            "Condition complexity limit exceeded"),
    DETAIL_208(
            "IJOB-DETAIL-208",
            "error.detail.condition-literal-type-is-not-supported",
            "Condition literal type is not supported"),
    DETAIL_209(
            "IJOB-DETAIL-209",
            "error.detail.condition-operand-must-reference-a-variable",
            "Condition operand must reference a variable"),
    DETAIL_210(
            "IJOB-DETAIL-210",
            "error.detail.condition-operator-is-required",
            "Condition operator is required"),
    DETAIL_211(
            "IJOB-DETAIL-211",
            "error.detail.default-gateway-route-contains-a-condition",
            "Default gateway route contains a condition"),
    DETAIL_212(
            "IJOB-DETAIL-212",
            "error.detail.etl-checkpoint-could-not-be-initialized",
            "ETL checkpoint could not be initialized"),
    DETAIL_213(
            "IJOB-DETAIL-213",
            "error.detail.etl-checkpoint-tie-breakers-are-invalid",
            "ETL checkpoint tie breakers are invalid"),
    DETAIL_214(
            "IJOB-DETAIL-214",
            "error.detail.etl-checkpoint-value-is-invalid",
            "ETL checkpoint value is invalid"),
    DETAIL_215(
            "IJOB-DETAIL-215",
            "error.detail.etl-task-identity-is-unavailable",
            "ETL task identity is unavailable"),
    DETAIL_216("IJOB-DETAIL-216", "error.detail.event-creation-failed", "Event creation failed"),
    DETAIL_217(
            "IJOB-DETAIL-217",
            "error.detail.gateway-condition-context-cannot-be-digested",
            "Gateway condition context cannot be digested"),
    DETAIL_218(
            "IJOB-DETAIL-218",
            "error.detail.gateway-condition-context-is-missing",
            "Gateway condition context is missing"),
    DETAIL_219(
            "IJOB-DETAIL-219",
            "error.detail.gateway-condition-membership-list-is-invalid",
            "Gateway condition membership list is invalid"),
    DETAIL_220(
            "IJOB-DETAIL-220",
            "error.detail.gateway-condition-node-reference-is-ambiguous",
            "Gateway condition node reference is ambiguous"),
    DETAIL_221(
            "IJOB-DETAIL-221",
            "error.detail.gateway-condition-numeric-value-is-invalid",
            "Gateway condition numeric value is invalid"),
    DETAIL_222(
            "IJOB-DETAIL-222",
            "error.detail.gateway-condition-value-types-do-not-match",
            "Gateway condition value types do not match"),
    DETAIL_223(
            "IJOB-DETAIL-223",
            "error.detail.gateway-condition-values-are-not-comparable",
            "Gateway condition values are not comparable"),
    DETAIL_224(
            "IJOB-DETAIL-224",
            "error.detail.gateway-decision-query-returned-null",
            "Gateway decision query returned null"),
    DETAIL_225(
            "IJOB-DETAIL-225",
            "error.detail.gateway-decision-service-is-unavailable",
            "Gateway decision service is unavailable"),
    DETAIL_226(
            "IJOB-DETAIL-226",
            "error.detail.gateway-has-no-matching-or-default-route",
            "Gateway has no matching or default route"),
    DETAIL_227(
            "IJOB-DETAIL-227",
            "error.detail.gateway-job-instance-snapshot-is-invalid",
            "Gateway job instance snapshot is invalid"),
    DETAIL_228(
            "IJOB-DETAIL-228",
            "error.detail.gateway-route-metadata-is-incomplete",
            "Gateway route metadata is incomplete"),
    DETAIL_229(
            "IJOB-DETAIL-229",
            "error.detail.gateway-trigger-snapshot-is-invalid",
            "Gateway trigger snapshot is invalid"),
    DETAIL_230(
            "IJOB-DETAIL-230",
            "error.detail.in-literal-array-must-contain-1-to-100-values",
            "IN literal array must contain 1 to 100 values"),
    DETAIL_231(
            "IJOB-DETAIL-231",
            "error.detail.job-dag-coordinator-is-stalled",
            "Job DAG coordinator is stalled"),
    DETAIL_232(
            "IJOB-DETAIL-232",
            "error.detail.job-dag-history-result-is-invalid",
            "Job DAG history result is invalid"),
    DETAIL_233(
            "IJOB-DETAIL-233",
            "error.detail.job-dag-inactive-history-is-invalid",
            "Job DAG inactive history is invalid"),
    DETAIL_234(
            "IJOB-DETAIL-234",
            "error.detail.job-definition-edge-is-invalid",
            "Job definition edge is invalid"),
    DETAIL_235(
            "IJOB-DETAIL-235",
            "error.detail.job-definition-graph-is-incomplete",
            "Job definition graph is incomplete"),
    DETAIL_236(
            "IJOB-DETAIL-236",
            "error.detail.job-definition-node-is-invalid",
            "Job definition node is invalid"),
    DETAIL_237(
            "IJOB-DETAIL-237",
            "error.detail.job-definition-policy-is-invalid",
            "Job definition policy is invalid"),
    DETAIL_238(
            "IJOB-DETAIL-238",
            "error.detail.job-definition-snapshot-is-missing",
            "Job definition snapshot is missing"),
    DETAIL_239(
            "IJOB-DETAIL-239",
            "error.detail.job-runtime-definition-is-invalid",
            "Job runtime definition is invalid"),
    DETAIL_240(
            "IJOB-DETAIL-240",
            "error.detail.not-requires-exactly-one-condition-operand",
            "NOT requires exactly one condition operand"),
    DETAIL_241(
            "IJOB-DETAIL-241",
            "error.detail.ordinary-job-definition-edge-contains-route-metadata",
            "Ordinary job definition edge contains route metadata"),
    DETAIL_242(
            "IJOB-DETAIL-242",
            "error.detail.persisted-gateway-decision-context-does-not-match",
            "Persisted gateway decision context does not match"),
    DETAIL_243(
            "IJOB-DETAIL-243",
            "error.detail.persisted-gateway-decision-failed",
            "Persisted gateway decision failed"),
    DETAIL_244(
            "IJOB-DETAIL-244",
            "error.detail.persisted-gateway-decision-is-invalid",
            "Persisted gateway decision is invalid"),
    DETAIL_245(
            "IJOB-DETAIL-245",
            "error.detail.persisted-gateway-route-is-not-part-of-the-execution-plan",
            "Persisted gateway route is not part of the execution plan"),
    DETAIL_246(
            "IJOB-DETAIL-246",
            "error.detail.persisted-gateway-route-selection-is-invalid",
            "Persisted gateway route selection is invalid"),
    DETAIL_247("IJOB-DETAIL-247", "error.detail.sha-256-is-unavailable", "SHA-256 is unavailable"),
    DETAIL_248(
            "IJOB-DETAIL-248",
            "error.detail.suspended-node-cannot-be-converted-to-terminal-status",
            "Suspended node cannot be converted to terminal status"),
    DETAIL_249(
            "IJOB-DETAIL-249",
            "error.detail.task-runtime-definition-is-invalid",
            "Task runtime definition is invalid"),
    DETAIL_0001(
            "IJOB-DETAIL-0001",
            "error.detail.api-role-is-disabled-on-this-runtime-node",
            "API role is disabled on this runtime node"),
    DETAIL_0002(
            "IJOB-DETAIL-0002",
            "error.detail.allowed-operators-are-invalid",
            "Allowed operators are invalid"),
    DETAIL_0003(
            "IJOB-DETAIL-0003",
            "error.detail.approval-decision-is-invalid",
            "Approval decision is invalid"),
    DETAIL_0004(
            "IJOB-DETAIL-0004",
            "error.detail.approval-operator-snapshot-creation-failed",
            "Approval operator snapshot creation failed"),
    DETAIL_0005(
            "IJOB-DETAIL-0005",
            "error.detail.approval-operator-snapshot-is-invalid",
            "Approval operator snapshot is invalid"),
    DETAIL_0006(
            "IJOB-DETAIL-0006",
            "error.detail.approval-operator-snapshot-is-missing",
            "Approval operator snapshot is missing"),
    DETAIL_0007(
            "IJOB-DETAIL-0007",
            "error.detail.approval-operator-snapshots-are-inconsistent",
            "Approval operator snapshots are inconsistent"),
    DETAIL_0008("IJOB-DETAIL-0008", "error.detail.calendar-not-found", "Calendar not found"),
    DETAIL_0009(
            "IJOB-DETAIL-0009",
            "error.detail.concurrency-limit-cannot-decrease-while-permits-are-active",
            "Concurrency limit cannot decrease while permits are active"),
    DETAIL_0010(
            "IJOB-DETAIL-0010",
            "error.detail.concurrency-policy-cannot-change-while-permits-are-active",
            "Concurrency policy cannot change while permits are active"),
    DETAIL_0011(
            "IJOB-DETAIL-0011",
            "error.detail.concurrency-policy-data-is-invalid",
            "Concurrency policy data is invalid"),
    DETAIL_0012(
            "IJOB-DETAIL-0012",
            "error.detail.concurrency-resource-cannot-change-while-matching-task-instances-are-active",
            "Concurrency resource cannot change while matching task instances are active"),
    DETAIL_0013(
            "IJOB-DETAIL-0013",
            "error.detail.concurrency-resource-code-already-exists",
            "Concurrency resource code already exists"),
    DETAIL_0014(
            "IJOB-DETAIL-0014",
            "error.detail.concurrency-resource-code-is-invalid",
            "Concurrency resource code is invalid"),
    DETAIL_0015(
            "IJOB-DETAIL-0015",
            "error.detail.concurrency-resource-creation-failed",
            "Concurrency resource creation failed"),
    DETAIL_0016(
            "IJOB-DETAIL-0016",
            "error.detail.concurrency-resource-deletion-conflicted",
            "Concurrency resource deletion conflicted"),
    DETAIL_0017(
            "IJOB-DETAIL-0017",
            "error.detail.concurrency-resource-does-not-exist",
            "Concurrency resource does not exist"),
    DETAIL_0018(
            "IJOB-DETAIL-0018",
            "error.detail.concurrency-resource-is-still-referenced-by-tasks",
            "Concurrency resource is still referenced by tasks"),
    DETAIL_0019(
            "IJOB-DETAIL-0019",
            "error.detail.concurrency-resource-name-is-invalid",
            "Concurrency resource name is invalid"),
    DETAIL_0020(
            "IJOB-DETAIL-0020",
            "error.detail.concurrency-resource-update-conflicted",
            "Concurrency resource update conflicted"),
    DETAIL_0021(
            "IJOB-DETAIL-0021",
            "error.detail.conditional-route-fields-are-only-allowed-on-gateway-outgoing-edges",
            "Conditional route fields are only allowed on gateway outgoing edges"),
    DETAIL_0022(
            "IJOB-DETAIL-0022",
            "error.detail.decision-comment-cannot-exceed-500-characters",
            "Decision comment cannot exceed 500 characters"),
    DETAIL_0023(
            "IJOB-DETAIL-0023",
            "error.detail.default-gateway-route-cannot-define-a-condition",
            "Default gateway route cannot define a condition"),
    DETAIL_0024(
            "IJOB-DETAIL-0024",
            "error.detail.description-must-not-exceed-500-characters",
            "Description must not exceed 500 characters"),
    DETAIL_0025(
            "IJOB-DETAIL-0025",
            "error.detail.dingtalk-secret-cannot-be-blank",
            "DingTalk secret cannot be blank"),
    DETAIL_0026(
            "IJOB-DETAIL-0026",
            "error.detail.dingtalk-webhook-is-required",
            "DingTalk webhook is required"),
    DETAIL_0027(
            "IJOB-DETAIL-0027",
            "error.detail.dingtalk-webhook-must-be-a-safe-https-url",
            "DingTalk webhook must be a safe HTTPS URL"),
    DETAIL_0028(
            "IJOB-DETAIL-0028",
            "error.detail.dingtalk-webhook-must-be-a-valid-https-url",
            "DingTalk webhook must be a valid HTTPS URL"),
    DETAIL_0029(
            "IJOB-DETAIL-0029",
            "error.detail.etl-checkpoint-source-has-changed",
            "ETL checkpoint source has changed"),
    DETAIL_0030(
            "IJOB-DETAIL-0030",
            "error.detail.etl-checkpoint-was-advanced-concurrently",
            "ETL checkpoint was advanced concurrently"),
    DETAIL_0031("IJOB-DETAIL-0031", "error.detail.enabled-is-required", "Enabled is required"),
    DETAIL_0032(
            "IJOB-DETAIL-0032",
            "error.detail.event-binding-id-is-invalid",
            "Event binding ID is invalid"),
    DETAIL_0033(
            "IJOB-DETAIL-0033",
            "error.detail.event-binding-already-exists",
            "Event binding already exists"),
    DETAIL_0034(
            "IJOB-DETAIL-0034",
            "error.detail.event-binding-batch-lock-failed",
            "Event binding batch lock failed"),
    DETAIL_0035(
            "IJOB-DETAIL-0035",
            "error.detail.event-binding-creation-failed",
            "Event binding creation failed"),
    DETAIL_0036(
            "IJOB-DETAIL-0036",
            "error.detail.event-binding-deletion-conflict",
            "Event binding deletion conflict"),
    DETAIL_0037(
            "IJOB-DETAIL-0037",
            "error.detail.event-binding-is-invalid",
            "Event binding is invalid"),
    DETAIL_0038(
            "IJOB-DETAIL-0038", "error.detail.event-binding-not-found", "Event binding not found"),
    DETAIL_0039(
            "IJOB-DETAIL-0039",
            "error.detail.event-binding-target-is-invalid",
            "Event binding target is invalid"),
    DETAIL_0040(
            "IJOB-DETAIL-0040",
            "error.detail.event-binding-target-not-found",
            "Event binding target not found"),
    DETAIL_0041(
            "IJOB-DETAIL-0041",
            "error.detail.event-binding-update-failed",
            "Event binding update failed"),
    DETAIL_0042("IJOB-DETAIL-0042", "error.detail.event-code-is-invalid", "Event code is invalid"),
    DETAIL_0043(
            "IJOB-DETAIL-0043",
            "error.detail.event-idempotency-key-is-required",
            "Event idempotency key is required"),
    DETAIL_0044(
            "IJOB-DETAIL-0044",
            "error.detail.event-idempotency-key-was-reused-with-different-content",
            "Event idempotency key was reused with different content"),
    DETAIL_0045(
            "IJOB-DETAIL-0045",
            "error.detail.event-payload-exceeds-16384-bytes",
            "Event payload exceeds 16384 bytes"),
    DETAIL_0046(
            "IJOB-DETAIL-0046",
            "error.detail.event-payload-must-be-json-serializable",
            "Event payload must be JSON serializable"),
    DETAIL_0047(
            "IJOB-DETAIL-0047",
            "error.detail.event-source-is-required",
            "Event source is required"),
    DETAIL_0048(
            "IJOB-DETAIL-0048",
            "error.detail.event-source-key-is-required",
            "Event source key is required"),
    DETAIL_0049(
            "IJOB-DETAIL-0049",
            "error.detail.event-wait-creation-failed",
            "Event wait creation failed"),
    DETAIL_0050(
            "IJOB-DETAIL-0050",
            "error.detail.event-wait-has-already-been-resolved",
            "Event wait has already been resolved"),
    DETAIL_0051(
            "IJOB-DETAIL-0051",
            "error.detail.event-wait-is-not-an-approval",
            "Event wait is not an approval"),
    DETAIL_0052("IJOB-DETAIL-0052", "error.detail.event-wait-not-found", "Event wait not found"),
    DETAIL_0053(
            "IJOB-DETAIL-0053",
            "error.detail.execution-node-cannot-define-a-gateway",
            "Execution node cannot define a gateway"),
    DETAIL_0054(
            "IJOB-DETAIL-0054",
            "error.detail.gateway-condition-cannot-be-serialized",
            "Gateway condition cannot be serialized"),
    DETAIL_0055(
            "IJOB-DETAIL-0055",
            "error.detail.gateway-condition-may-only-reference-a-direct-upstream-node",
            "Gateway condition may only reference a direct upstream node"),
    DETAIL_0056(
            "IJOB-DETAIL-0056",
            "error.detail.gateway-condition-payload-exceeds-16384-bytes",
            "Gateway condition payload exceeds 16384 bytes"),
    DETAIL_0057(
            "IJOB-DETAIL-0057",
            "error.detail.gateway-condition-payload-is-invalid",
            "Gateway condition payload is invalid"),
    DETAIL_0058(
            "IJOB-DETAIL-0058",
            "error.detail.gateway-condition-version-is-not-supported",
            "Gateway condition version is not supported"),
    DETAIL_0059(
            "IJOB-DETAIL-0059",
            "error.detail.gateway-mode-is-required",
            "Gateway mode is required"),
    DETAIL_0060(
            "IJOB-DETAIL-0060",
            "error.detail.gateway-node-cannot-define-execution-target-or-node-policy",
            "Gateway node cannot define execution target or node policy"),
    DETAIL_0061(
            "IJOB-DETAIL-0061",
            "error.detail.gateway-requires-exactly-one-default-route",
            "Gateway requires exactly one default route"),
    DETAIL_0062(
            "IJOB-DETAIL-0062",
            "error.detail.gateway-retry-snapshot-is-invalid",
            "Gateway retry snapshot is invalid"),
    DETAIL_0063(
            "IJOB-DETAIL-0063",
            "error.detail.gateway-route-key-is-invalid-or-duplicated",
            "Gateway route key is invalid or duplicated"),
    DETAIL_0064(
            "IJOB-DETAIL-0064",
            "error.detail.gateway-route-priority-is-invalid-or-duplicated",
            "Gateway route priority is invalid or duplicated"),
    DETAIL_0065(
            "IJOB-DETAIL-0065",
            "error.detail.idempotency-key-belongs-to-another-runtime-command",
            "Idempotency key belongs to another runtime command"),
    DETAIL_0066(
            "IJOB-DETAIL-0066",
            "error.detail.idempotency-key-is-already-used-by-another-trigger",
            "Idempotency key is already used by another trigger"),
    DETAIL_0067(
            "IJOB-DETAIL-0067",
            "error.detail.idempotency-key-header-does-not-match-request-idempotencykey",
            "Idempotency-Key header does not match request idempotencyKey"),
    DETAIL_0068(
            "IJOB-DETAIL-0068", "error.detail.instance-id-is-invalid", "Instance ID is invalid"),
    DETAIL_0069(
            "IJOB-DETAIL-0069",
            "error.detail.instance-cannot-be-reset-for-retry",
            "Instance cannot be reset for retry"),
    DETAIL_0070(
            "IJOB-DETAIL-0070",
            "error.detail.instance-cannot-be-retried",
            "Instance cannot be retried"),
    DETAIL_0071(
            "IJOB-DETAIL-0071",
            "error.detail.instance-cannot-enter-event-wait",
            "Instance cannot enter event wait"),
    DETAIL_0072(
            "IJOB-DETAIL-0072",
            "error.detail.instance-creation-failed",
            "Instance creation failed"),
    DETAIL_0073(
            "IJOB-DETAIL-0073",
            "error.detail.instance-input-snapshot-cannot-be-updated",
            "Instance input snapshot cannot be updated"),
    DETAIL_0074(
            "IJOB-DETAIL-0074", "error.detail.instance-is-not-a-task", "Instance is not a task"),
    DETAIL_0075(
            "IJOB-DETAIL-0075",
            "error.detail.instance-is-not-active-in-this-process",
            "Instance is not active in this process"),
    DETAIL_0076(
            "IJOB-DETAIL-0076", "error.detail.instance-is-not-paused", "Instance is not paused"),
    DETAIL_0077(
            "IJOB-DETAIL-0077", "error.detail.instance-is-not-running", "Instance is not running"),
    DETAIL_0078(
            "IJOB-DETAIL-0078", "error.detail.instance-is-not-waiting", "Instance is not waiting"),
    DETAIL_0079(
            "IJOB-DETAIL-0079",
            "error.detail.instance-is-not-waiting-for-a-concurrency-permit",
            "Instance is not waiting for a concurrency permit"),
    DETAIL_0080("IJOB-DETAIL-0080", "error.detail.instance-not-found", "Instance not found"),
    DETAIL_0081("IJOB-DETAIL-0081", "error.detail.instance-not-paused", "Instance not paused"),
    DETAIL_0082("IJOB-DETAIL-0082", "error.detail.instance-not-running", "Instance not running"),
    DETAIL_0083(
            "IJOB-DETAIL-0083",
            "error.detail.instance-output-snapshot-cannot-be-updated",
            "Instance output snapshot cannot be updated"),
    DETAIL_0084(
            "IJOB-DETAIL-0084",
            "error.detail.instance-state-changed-concurrently",
            "Instance state changed concurrently"),
    DETAIL_0085(
            "IJOB-DETAIL-0085",
            "error.detail.instance-tree-is-still-active-in-this-process",
            "Instance tree is still active in this process"),
    DETAIL_0086(
            "IJOB-DETAIL-0086",
            "error.detail.job-dag-result-is-incomplete",
            "Job DAG result is incomplete"),
    DETAIL_0087(
            "IJOB-DETAIL-0087",
            "error.detail.job-dag-result-is-invalid",
            "Job DAG result is invalid"),
    DETAIL_0088(
            "IJOB-DETAIL-0088",
            "error.detail.job-branch-instance-cannot-be-reset-for-retry",
            "Job branch instance cannot be reset for retry"),
    DETAIL_0089(
            "IJOB-DETAIL-0089",
            "error.detail.job-child-instance-snapshot-is-invalid",
            "Job child instance snapshot is invalid"),
    DETAIL_0090(
            "IJOB-DETAIL-0090",
            "error.detail.job-child-instance-snapshot-is-missing",
            "Job child instance snapshot is missing"),
    DETAIL_0091(
            "IJOB-DETAIL-0091",
            "error.detail.job-child-snapshot-source-is-invalid",
            "Job child snapshot source is invalid"),
    DETAIL_0092(
            "IJOB-DETAIL-0092",
            "error.detail.job-graph-contains-cycle",
            "Job graph contains cycle"),
    DETAIL_0093(
            "IJOB-DETAIL-0093", "error.detail.job-instance-not-found", "Job instance not found"),
    DETAIL_0094(
            "IJOB-DETAIL-0094",
            "error.detail.job-instance-retry-is-already-claimed",
            "Job instance retry is already claimed"),
    DETAIL_0095(
            "IJOB-DETAIL-0095",
            "error.detail.job-instance-tree-contains-a-cycle",
            "Job instance tree contains a cycle"),
    DETAIL_0096(
            "IJOB-DETAIL-0096",
            "error.detail.job-instance-tree-is-still-active-in-this-process",
            "Job instance tree is still active in this process"),
    DETAIL_0097(
            "IJOB-DETAIL-0097",
            "error.detail.job-instance-tree-still-contains-active-nodes",
            "Job instance tree still contains active nodes"),
    DETAIL_0098(
            "IJOB-DETAIL-0098",
            "error.detail.job-node-retry-snapshot-is-invalid",
            "Job node retry snapshot is invalid"),
    DETAIL_0099(
            "IJOB-DETAIL-0099",
            "error.detail.job-retry-instance-snapshot-is-missing",
            "Job retry instance snapshot is missing"),
    DETAIL_0100(
            "IJOB-DETAIL-0100",
            "error.detail.job-retry-instance-tree-contains-a-cycle",
            "Job retry instance tree contains a cycle"),
    DETAIL_0101(
            "IJOB-DETAIL-0101",
            "error.detail.job-runtime-instance-ancestry-is-invalid",
            "Job runtime instance ancestry is invalid"),
    DETAIL_0102(
            "IJOB-DETAIL-0102",
            "error.detail.job-trigger-snapshot-does-not-match-target",
            "Job trigger snapshot does not match target"),
    DETAIL_0103(
            "IJOB-DETAIL-0103",
            "error.detail.manual-trigger-requestid-must-contain-1-to-96-safe-characters",
            "Manual trigger requestId must contain 1 to 96 safe characters"),
    DETAIL_0104(
            "IJOB-DETAIL-0104",
            "error.detail.nested-job-runtime-cycle-detected",
            "Nested job runtime cycle detected"),
    DETAIL_0105("IJOB-DETAIL-0105", "error.detail.no-failed-node", "No failed node"),
    DETAIL_0106(
            "IJOB-DETAIL-0106",
            "error.detail.non-default-gateway-route-requires-a-condition",
            "Non-default gateway route requires a condition"),
    DETAIL_0107("IJOB-DETAIL-0107", "error.detail.not-job-instance", "Not job instance"),
    DETAIL_0108(
            "IJOB-DETAIL-0108",
            "error.detail.notification-deduplication-key-was-reused-with-different-content",
            "Notification deduplication key was reused with different content"),
    DETAIL_0109(
            "IJOB-DETAIL-0109",
            "error.detail.notification-message-is-invalid",
            "Notification message is invalid"),
    DETAIL_0110(
            "IJOB-DETAIL-0110",
            "error.detail.notification-outbox-enqueue-failed",
            "Notification outbox enqueue failed"),
    DETAIL_0111(
            "IJOB-DETAIL-0111",
            "error.detail.notify-config-id-is-invalid",
            "Notify config ID is invalid"),
    DETAIL_0112(
            "IJOB-DETAIL-0112",
            "error.detail.only-exclusive-gateway-is-supported-in-version-1",
            "Only EXCLUSIVE gateway is supported in version 1"),
    DETAIL_0113(
            "IJOB-DETAIL-0113",
            "error.detail.only-failed-or-timed-out-instances-can-be-retried",
            "Only failed or timed out instances can be retried"),
    DETAIL_0114(
            "IJOB-DETAIL-0114",
            "error.detail.only-failed-or-timed-out-job-instances-can-be-retried",
            "Only failed or timed out job instances can be retried"),
    DETAIL_0115(
            "IJOB-DETAIL-0115",
            "error.detail.only-failed-or-timed-out-task-instances-can-be-retried",
            "Only failed or timed out task instances can be retried"),
    DETAIL_0116(
            "IJOB-DETAIL-0116",
            "error.detail.only-root-instances-can-be-retried",
            "Only root instances can be retried"),
    DETAIL_0117(
            "IJOB-DETAIL-0117",
            "error.detail.only-root-job-instances-can-be-retried",
            "Only root job instances can be retried"),
    DETAIL_0118(
            "IJOB-DETAIL-0118",
            "error.detail.operator-is-not-allowed-to-access-this-approval",
            "Operator is not allowed to access this approval"),
    DETAIL_0119("IJOB-DETAIL-0119", "error.detail.operator-is-required", "Operator is required"),
    DETAIL_0120(
            "IJOB-DETAIL-0120",
            "error.detail.parent-instance-not-found",
            "Parent instance not found"),
    DETAIL_0121(
            "IJOB-DETAIL-0121",
            "error.detail.plugin-concurrency-policy-cannot-change-while-matching-task-instances-are-active",
            "Plugin concurrency policy cannot change while matching task instances are active"),
    DETAIL_0122(
            "IJOB-DETAIL-0122",
            "error.detail.plugin-concurrency-policy-creation-conflicted",
            "Plugin concurrency policy creation conflicted"),
    DETAIL_0123(
            "IJOB-DETAIL-0123",
            "error.detail.plugin-concurrency-policy-deletion-conflicted",
            "Plugin concurrency policy deletion conflicted"),
    DETAIL_0124(
            "IJOB-DETAIL-0124",
            "error.detail.plugin-concurrency-policy-version-changed",
            "Plugin concurrency policy version changed"),
    DETAIL_0125(
            "IJOB-DETAIL-0125", "error.detail.plugin-type-is-required", "Plugin type is required"),
    DETAIL_0126(
            "IJOB-DETAIL-0126",
            "error.detail.python-interpreter-is-required",
            "Python interpreter is required"),
    DETAIL_0127(
            "IJOB-DETAIL-0127",
            "error.detail.request-body-is-required",
            "Request body is required"),
    DETAIL_0128(
            "IJOB-DETAIL-0128",
            "error.detail.retry-work-cannot-be-reactivated",
            "Retry work cannot be reactivated"),
    DETAIL_0129(
            "IJOB-DETAIL-0129",
            "error.detail.root-instance-initialization-failed",
            "Root instance initialization failed"),
    DETAIL_0130(
            "IJOB-DETAIL-0130",
            "error.detail.runtime-command-not-found",
            "Runtime command not found"),
    DETAIL_0131(
            "IJOB-DETAIL-0131",
            "error.detail.runtime-command-request-id-is-invalid",
            "Runtime command request id is invalid"),
    DETAIL_0132(
            "IJOB-DETAIL-0132",
            "error.detail.runtime-variable-snapshot-is-invalid",
            "Runtime variable snapshot is invalid"),
    DETAIL_0133(
            "IJOB-DETAIL-0133",
            "error.detail.schedule-batch-lock-failed",
            "Schedule batch lock failed"),
    DETAIL_0134(
            "IJOB-DETAIL-0134",
            "error.detail.schedule-creation-failed",
            "Schedule creation failed"),
    DETAIL_0135(
            "IJOB-DETAIL-0135",
            "error.detail.schedule-fire-claim-token-is-required",
            "Schedule fire claim token is required"),
    DETAIL_0136(
            "IJOB-DETAIL-0136",
            "error.detail.schedule-fire-claim-was-lost",
            "Schedule fire claim was lost"),
    DETAIL_0137(
            "IJOB-DETAIL-0137",
            "error.detail.schedule-has-no-future-fire-time",
            "Schedule has no future fire time"),
    DETAIL_0138(
            "IJOB-DETAIL-0138",
            "error.detail.schedule-misfire-or-queue-delay-policy-is-invalid",
            "Schedule misfire or queue delay policy is invalid"),
    DETAIL_0139("IJOB-DETAIL-0139", "error.detail.schedule-not-found", "Schedule not found"),
    DETAIL_0140(
            "IJOB-DETAIL-0140",
            "error.detail.some-concurrency-resources-do-not-exist-or-are-disabled",
            "Some concurrency resources do not exist or are disabled"),
    DETAIL_0141(
            "IJOB-DETAIL-0141",
            "error.detail.some-event-bindings-do-not-exist",
            "Some event bindings do not exist"),
    DETAIL_0142(
            "IJOB-DETAIL-0142",
            "error.detail.some-schedules-do-not-exist",
            "Some schedules do not exist"),
    DETAIL_0143(
            "IJOB-DETAIL-0143",
            "error.detail.task-already-owns-concurrency-permits",
            "Task already owns concurrency permits"),
    DETAIL_0144(
            "IJOB-DETAIL-0144",
            "error.detail.task-concurrency-identity-is-invalid",
            "Task concurrency identity is invalid"),
    DETAIL_0145(
            "IJOB-DETAIL-0145",
            "error.detail.task-concurrency-policy-cannot-change-while-task-instances-are-active",
            "Task concurrency policy cannot change while task instances are active"),
    DETAIL_0146(
            "IJOB-DETAIL-0146",
            "error.detail.task-concurrency-policy-creation-conflicted",
            "Task concurrency policy creation conflicted"),
    DETAIL_0147(
            "IJOB-DETAIL-0147",
            "error.detail.task-concurrency-policy-version-changed",
            "Task concurrency policy version changed"),
    DETAIL_0148(
            "IJOB-DETAIL-0148",
            "error.detail.task-concurrency-resource-binding-failed",
            "Task concurrency resource binding failed"),
    DETAIL_0149(
            "IJOB-DETAIL-0149",
            "error.detail.task-concurrency-resources-cannot-be-read",
            "Task concurrency resources cannot be read"),
    DETAIL_0150("IJOB-DETAIL-0150", "error.detail.task-does-not-exist", "Task does not exist"),
    DETAIL_0151(
            "IJOB-DETAIL-0151",
            "error.detail.task-instance-identity-is-invalid",
            "Task instance identity is invalid"),
    DETAIL_0152(
            "IJOB-DETAIL-0152", "error.detail.task-instance-not-found", "Task instance not found"),
    DETAIL_0153(
            "IJOB-DETAIL-0153",
            "error.detail.task-instance-retry-is-already-claimed",
            "Task instance retry is already claimed"),
    DETAIL_0154("IJOB-DETAIL-0154", "error.detail.task-is-retired", "Task is retired"),
    DETAIL_0155(
            "IJOB-DETAIL-0155",
            "error.detail.task-references-a-disabled-or-invalid-concurrency-resource",
            "Task references a disabled or invalid concurrency resource"),
    DETAIL_0156(
            "IJOB-DETAIL-0156",
            "error.detail.task-trigger-snapshot-does-not-match-target",
            "Task trigger snapshot does not match target"),
    DETAIL_0157(
            "IJOB-DETAIL-0157", "error.detail.trigger-creation-failed", "Trigger creation failed"),
    DETAIL_0158("IJOB-DETAIL-0158", "error.detail.trigger-not-found", "Trigger not found"),
    DETAIL_0159(
            "IJOB-DETAIL-0159",
            "error.detail.trigger-state-changed-concurrently",
            "Trigger state changed concurrently"),
    DETAIL_0160(
            "IJOB-DETAIL-0160",
            "error.detail.variable-snapshot-exceeds-65536-bytes",
            "Variable snapshot exceeds 65536 bytes"),
    DETAIL_0161(
            "IJOB-DETAIL-0161",
            "error.detail.variables-must-be-json-serializable",
            "Variables must be JSON serializable"),
    DETAIL_0162(
            "IJOB-DETAIL-0162",
            "error.detail.afterid-cannot-be-negative",
            "afterId cannot be negative"),
    DETAIL_0163(
            "IJOB-DETAIL-0163",
            "error.detail.atmobiles-cannot-contain-more-than-100-entries",
            "atMobiles cannot contain more than 100 entries"),
    DETAIL_0164(
            "IJOB-DETAIL-0164",
            "error.detail.atmobiles-contains-a-duplicate-number",
            "atMobiles contains a duplicate number"),
    DETAIL_0165(
            "IJOB-DETAIL-0165",
            "error.detail.atmobiles-contains-an-invalid-mobile-number",
            "atMobiles contains an invalid mobile number"),
    DETAIL_0166(
            "IJOB-DETAIL-0166",
            "error.detail.exactly-one-matching-notify-config-is-required",
            "exactly one matching notify config is required"),
    DETAIL_0167(
            "IJOB-DETAIL-0167",
            "error.detail.expectedversion-must-be-greater-than-zero",
            "expectedVersion must be greater than zero"),
    DETAIL_0168(
            "IJOB-DETAIL-0168",
            "error.detail.expectedversion-must-be-zero-or-greater",
            "expectedVersion must be zero or greater"),
    DETAIL_0169(
            "IJOB-DETAIL-0169", "error.detail.from-cannot-be-after-to", "from cannot be after to"),
    DETAIL_0170(
            "IJOB-DETAIL-0170",
            "error.detail.from-time-and-to-time-must-be-provided-together",
            "from time and to time must be provided together"),
    DETAIL_0171(
            "IJOB-DETAIL-0171",
            "error.detail.limit-must-be-greater-than-zero",
            "limit must be greater than zero"),
    DETAIL_0172(
            "IJOB-DETAIL-0172",
            "error.detail.name-cannot-exceed-128-characters",
            "name cannot exceed 128 characters"),
    DETAIL_0173(
            "IJOB-DETAIL-0173",
            "error.detail.notify-binding-already-exists",
            "notify binding already exists"),
    DETAIL_0174(
            "IJOB-DETAIL-0174",
            "error.detail.notify-binding-batch-lock-failed",
            "notify binding batch lock failed"),
    DETAIL_0175(
            "IJOB-DETAIL-0175",
            "error.detail.notify-binding-creation-failed",
            "notify binding creation failed"),
    DETAIL_0176(
            "IJOB-DETAIL-0176",
            "error.detail.notify-binding-deletion-conflict",
            "notify binding deletion conflict"),
    DETAIL_0177(
            "IJOB-DETAIL-0177",
            "error.detail.notify-binding-does-not-exist",
            "notify binding does not exist"),
    DETAIL_0178(
            "IJOB-DETAIL-0178",
            "error.detail.notify-binding-references-changed-during-creation",
            "notify binding references changed during creation"),
    DETAIL_0179(
            "IJOB-DETAIL-0179",
            "error.detail.notify-binding-target-does-not-exist",
            "notify binding target does not exist"),
    DETAIL_0180(
            "IJOB-DETAIL-0180",
            "error.detail.notify-binding-update-failed",
            "notify binding update failed"),
    DETAIL_0181(
            "IJOB-DETAIL-0181",
            "error.detail.notify-config-id-must-be-greater-than-0",
            "notify config ID must be greater than 0"),
    DETAIL_0182(
            "IJOB-DETAIL-0182",
            "error.detail.notify-config-batch-lock-failed",
            "notify config batch lock failed"),
    DETAIL_0183(
            "IJOB-DETAIL-0183",
            "error.detail.notify-config-creation-failed",
            "notify config creation failed"),
    DETAIL_0184(
            "IJOB-DETAIL-0184",
            "error.detail.notify-config-deletion-conflict",
            "notify config deletion conflict"),
    DETAIL_0185(
            "IJOB-DETAIL-0185",
            "error.detail.notify-config-does-not-exist",
            "notify config does not exist"),
    DETAIL_0186(
            "IJOB-DETAIL-0186",
            "error.detail.notify-config-is-not-usable",
            "notify config is not usable"),
    DETAIL_0187(
            "IJOB-DETAIL-0187",
            "error.detail.notify-config-is-still-referenced-by-bindings",
            "notify config is still referenced by bindings"),
    DETAIL_0188(
            "IJOB-DETAIL-0188",
            "error.detail.notify-config-name-cannot-exceed-128-characters",
            "notify config name cannot exceed 128 characters"),
    DETAIL_0189(
            "IJOB-DETAIL-0189",
            "error.detail.notify-config-name-is-required",
            "notify config name is required"),
    DETAIL_0190(
            "IJOB-DETAIL-0190",
            "error.detail.notify-config-payload-is-invalid",
            "notify config payload is invalid"),
    DETAIL_0191(
            "IJOB-DETAIL-0191",
            "error.detail.notify-config-serialization-failed",
            "notify config serialization failed"),
    DETAIL_0192(
            "IJOB-DETAIL-0192",
            "error.detail.notify-config-update-failed",
            "notify config update failed"),
    DETAIL_0193(
            "IJOB-DETAIL-0193",
            "error.detail.notify-policy-is-required",
            "notify policy is required"),
    DETAIL_0194(
            "IJOB-DETAIL-0194", "error.detail.notify-type-is-required", "notify type is required"),
    DETAIL_0195(
            "IJOB-DETAIL-0195",
            "error.detail.query-time-range-cannot-exceed-31-days",
            "query time range cannot exceed 31 days"),
    DETAIL_0196(
            "IJOB-DETAIL-0196",
            "error.detail.request-body-is-required",
            "request body is required"),
    DETAIL_0197("IJOB-DETAIL-0197", "error.detail.resource-not-found", "resource not found"),
    DETAIL_0198(
            "IJOB-DETAIL-0198", "error.detail.resource-type-mismatch", "resource type mismatch"),
    DETAIL_0199(
            "IJOB-DETAIL-0199",
            "error.detail.response-extract-failed-empty-body",
            "response extract failed: empty body"),
    DETAIL_0200(
            "IJOB-DETAIL-0200",
            "error.detail.secret-and-clearsecret-cannot-be-set-together",
            "secret and clearSecret cannot be set together"),
    DETAIL_0201(
            "IJOB-DETAIL-0201",
            "error.detail.some-notify-bindings-do-not-exist",
            "some notify bindings do not exist"),
    DETAIL_0202(
            "IJOB-DETAIL-0202",
            "error.detail.some-notify-configs-do-not-exist",
            "some notify configs do not exist"),
    DETAIL_0203(
            "IJOB-DETAIL-0203",
            "error.detail.target-type-and-target-id-must-be-provided-together",
            "target type and target ID must be provided together"),
    DETAIL_0204(
            "IJOB-DETAIL-0204", "error.detail.target-type-is-required", "target type is required"),
    DETAIL_0205(
            "IJOB-DETAIL-0205",
            "error.detail.to-and-cc-cannot-contain-the-same-recipient",
            "to and cc cannot contain the same recipient"),
    DETAIL_0206(
            "IJOB-DETAIL-0206",
            "error.detail.to-time-must-be-after-from-time",
            "to time must be after from time"),
    DETAIL_0207(
            "IJOB-DETAIL-0207",
            "error.detail.etl-file-checkpoint-owned",
            "ETL file checkpoint is owned by another execution"),
    DETAIL_0208(
            "IJOB-DETAIL-0208",
            "error.detail.mail-channel-referenced",
            "Mail channel is still referenced by notifications or pending deliveries"),
    DETAIL_0209(
            "IJOB-DETAIL-0209",
            "error.detail.mail-channel-references-unverified",
            "Mail channel references cannot be verified"),
    DETAIL_0210(
            "IJOB-DETAIL-0210",
            "error.detail.mail-channel-deletion-conflict",
            "Mail channel deletion conflict"),
    NOTIFY_WEBHOOK_INVALID(
            "IJOB-NOTIFY-WEBHOOK-INVALID",
            "error.notify.webhook-invalid",
            "Notification webhook configuration is invalid"),
    NOTIFY_COOLDOWN(
            "IJOB-NOTIFY-COOLDOWN",
            "error.notify.cooldown",
            "Notification target is cooling down; test messages are not queued or retried"),
    NOTIFY_DELIVERY_MISSING(
            "IJOB-NOTIFY-DELIVERY-MISSING",
            "error.notify.delivery-missing",
            "Notification delivery was not found");
    private final String code;
    private final String key;
    private final String text;
    private static final Map<String, BusinessMessage> BY_TEXT = new HashMap<>();

    static {
        for (BusinessMessage value : values()) BY_TEXT.put(value.text, value);
    }

    BusinessMessage(String code, String key, String text) {
        this.code = code;
        this.key = key;
        this.text = text;
    }

    public static BusinessMessage find(String text) {
        return BY_TEXT.get(text);
    }

    @Override
    public String errorCode() {
        return code;
    }

    @Override
    public String messageKey() {
        return key;
    }

    @Override
    public String defaultMessage() {
        return text;
    }
}
