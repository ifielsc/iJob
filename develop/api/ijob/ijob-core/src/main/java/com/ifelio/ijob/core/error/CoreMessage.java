package com.ifelio.ijob.core.error;

import com.ifelio.ijob.common.response.MessageDefinition;

/** Stable built-in business messages. Do not rename or recycle published codes/keys. */
public enum CoreMessage implements MessageDefinition {
    NOTIFICATION_REFERENCED_BY_MONITOR(
            "CORE_MONITOR_NOTIFY_REFERENCED",
            "core.monitor.notifyReferenced",
            "Notification is still referenced by published SERVER_MONITOR definitions"),
    NOTIFICATION_REFERENCED_BY_DATA_CHECK(
            "CORE_DATA_CHECK_NOTIFY_REFERENCED",
            "core.dataCheck.notifyReferenced",
            "Notification is still referenced by published DATA_CHECK definitions"),
    NOTIFICATION_REFERENCE_CHECK_FAILED(
            "CORE_DATA_CHECK_NOTIFY_SCAN_FAILED",
            "core.dataCheck.notifyScanFailed",
            "Notification reference verification failed"),
    CALENDAR_CREATION_FAILED(
            "IJOB-BIZ-0014", "error.business.calendar-creation-failed", "Calendar creation failed"),
    CALENDAR_UPDATE_FAILED(
            "IJOB-BIZ-0025", "error.business.calendar-update-failed", "Calendar update failed"),
    EXECUTION_TIME_IS_REQUIRED(
            "IJOB-BIZ-0053",
            "error.business.execution-time-is-required",
            "Execution time is required"),
    EXECUTION_DATE_IS_REQUIRED(
            "IJOB-BIZ-0052",
            "error.business.execution-date-is-required",
            "Execution date is required"),
    PREVIEW_DAYS_MUST_BE_BETWEEN_1_AND_366(
            "IJOB-BIZ-0170",
            "error.business.preview-days-must-be-between-1-and-366",
            "Preview days must be between 1 and 366"),
    CALENDAR_DOES_NOT_EXIST(
            "IJOB-BIZ-0017", "error.business.calendar-does-not-exist", "Calendar does not exist"),
    CALENDAR_WORKDAY_CONFIGURATION_IS_INVALID(
            "IJOB-BIZ-0026",
            "error.business.calendar-workday-configuration-is-invalid",
            "Calendar workday configuration is invalid"),
    CALENDAR_BATCH_LOCKING_FAILED(
            "IJOB-BIZ-0013",
            "error.business.calendar-batch-locking-failed",
            "Calendar batch locking failed"),
    SOME_CALENDARS_DO_NOT_EXIST(
            "IJOB-BIZ-0255",
            "error.business.some-calendars-do-not-exist",
            "Some calendars do not exist"),
    CALENDAR_IS_STILL_REFERENCED_BY_SCHEDULES(
            "IJOB-BIZ-0021",
            "error.business.calendar-is-still-referenced-by-schedules",
            "Calendar is still referenced by schedules"),
    CALENDAR_DELETION_CONFLICTS_WITH_A_CONCURRENT_UPDATE(
            "IJOB-BIZ-0015",
            "error.business.calendar-deletion-conflicts-with-a-concurrent-update",
            "Calendar deletion conflicts with a concurrent update"),
    REQUEST_BODY_IS_REQUIRED(
            "IJOB-BIZ-0192", "error.business.request-body-is-required", "Request body is required"),
    CALENDAR_NAME_IS_REQUIRED(
            "IJOB-BIZ-0023",
            "error.business.calendar-name-is-required",
            "Calendar name is required"),
    CALENDAR_NAME_MUST_NOT_EXCEED_128_CHARACTERS(
            "IJOB-BIZ-0024",
            "error.business.calendar-name-must-not-exceed-128-characters",
            "Calendar name must not exceed 128 characters"),
    CALENDAR_DESCRIPTION_MUST_NOT_EXCEED_500_CHARACTERS(
            "IJOB-BIZ-0016",
            "error.business.calendar-description-must-not-exceed-500-characters",
            "Calendar description must not exceed 500 characters"),
    AT_LEAST_ONE_VALID_WORKDAY_IS_REQUIRED(
            "IJOB-BIZ-0009",
            "error.business.at-least-one-valid-workday-is-required",
            "At least one valid workday is required"),
    EXCEPTION_DATES_MUST_NOT_EXCEED_2000_ITEMS(
            "IJOB-BIZ-0051",
            "error.business.exception-dates-must-not-exceed-2000-items",
            "Exception dates must not exceed 2000 items"),
    EXCEPTION_DATE_AND_TYPE_ARE_REQUIRED(
            "IJOB-BIZ-0049",
            "error.business.exception-date-and-type-are-required",
            "Exception date and type are required"),
    EXCEPTION_DATES_MUST_BE_UNIQUE(
            "IJOB-BIZ-0050",
            "error.business.exception-dates-must-be-unique",
            "Exception dates must be unique"),
    CALENDAR_EXCEPTION_PERSISTENCE_FAILED(
            "IJOB-BIZ-0018",
            "error.business.calendar-exception-persistence-failed",
            "Calendar exception persistence failed"),
    CALENDAR_NAME_ALREADY_EXISTS(
            "IJOB-BIZ-0022",
            "error.business.calendar-name-already-exists",
            "Calendar name already exists"),
    CALENDAR_ID_MUST_BE_GREATER_THAN_ZERO(
            "IJOB-BIZ-0019",
            "error.business.calendar-id-must-be-greater-than-zero",
            "Calendar ID must be greater than zero"),
    JOB_EDGE_COUNT_MUST_NOT_EXCEED_VALUE(
            "IJOB-BIZ-0084",
            "error.business.job-edge-count-must-not-exceed-value",
            "Job edge count must not exceed {0}"),
    A_JOB_REQUIRES_AT_LEAST_ONE_NODE(
            "IJOB-BIZ-0007",
            "error.business.a-job-requires-at-least-one-node",
            "A job requires at least one node"),
    JOB_NODE_COUNT_MUST_NOT_EXCEED_VALUE(
            "IJOB-BIZ-0099",
            "error.business.job-node-count-must-not-exceed-value",
            "Job node count must not exceed {0}"),
    JOB_NODE_IS_REQUIRED(
            "IJOB-BIZ-0101", "error.business.job-node-is-required", "Job node is required"),
    DUPLICATE_NODE_CODE_VALUE(
            "IJOB-BIZ-0042",
            "error.business.duplicate-node-code-value",
            "Duplicate node code: {0}"),
    NODE_CODE_IS_REQUIRED(
            "IJOB-BIZ-0132", "error.business.node-code-is-required", "Node code is required"),
    NODE_CODE_MUST_NOT_HAVE_LEADING_OR_TRAILING_WHITESPACE(
            "IJOB-BIZ-0134",
            "error.business.node-code-must-not-have-leading-or-trailing-whitespace",
            "Node code must not have leading or trailing whitespace"),
    NODE_CODE_LENGTH_MUST_NOT_EXCEED_VALUE(
            "IJOB-BIZ-0133",
            "error.business.node-code-length-must-not-exceed-value",
            "Node code length must not exceed {0}"),
    JOB_EDGE_IS_REQUIRED(
            "IJOB-BIZ-0085", "error.business.job-edge-is-required", "Job edge is required"),
    EDGE_SOURCE_IS_REQUIRED(
            "IJOB-BIZ-0044", "error.business.edge-source-is-required", "Edge source is required"),
    EDGE_TARGET_IS_REQUIRED(
            "IJOB-BIZ-0046", "error.business.edge-target-is-required", "Edge target is required"),
    EDGE_SOURCE_DOES_NOT_EXIST_VALUE(
            "IJOB-BIZ-0043",
            "error.business.edge-source-does-not-exist-value",
            "Edge source does not exist: {0}"),
    EDGE_TARGET_DOES_NOT_EXIST_VALUE(
            "IJOB-BIZ-0045",
            "error.business.edge-target-does-not-exist-value",
            "Edge target does not exist: {0}"),
    JOB_EDGE_MUST_NOT_REFERENCE_ITSELF_VALUE(
            "IJOB-BIZ-0086",
            "error.business.job-edge-must-not-reference-itself-value",
            "Job edge must not reference itself: {0}"),
    DUPLICATE_JOB_EDGE_VALUE_VALUE(
            "IJOB-BIZ-0041",
            "error.business.duplicate-job-edge-value-value",
            "Duplicate job edge: {0} -> {1}"),
    MULTI_NODE_JOB_MUST_NOT_CONTAIN_AN_ISOLATED_NODE_VALUE(
            "IJOB-BIZ-0124",
            "error.business.multi-node-job-must-not-contain-an-isolated-node-value",
            "Multi-node job must not contain an isolated node: {0}"),
    JOB_GRAPH_MUST_NOT_CONTAIN_CYCLES(
            "IJOB-BIZ-0087",
            "error.business.job-graph-must-not-contain-cycles",
            "Job graph must not contain cycles"),
    JOB_IS_DISABLED("IJOB-BIZ-0095", "error.business.job-is-disabled", "Job is disabled"),
    JOB_DOES_NOT_EXIST("IJOB-BIZ-0083", "error.business.job-does-not-exist", "Job does not exist"),
    JOB_IDENTITY_LOCK_RESULT_IS_INCONSISTENT(
            "IJOB-BIZ-0094",
            "error.business.job-identity-lock-result-is-inconsistent",
            "Job identity lock result is inconsistent"),
    JOB_RETIRED_STATE_DATA_IS_INCOMPLETE(
            "IJOB-BIZ-0106",
            "error.business.job-retired-state-data-is-incomplete",
            "Job retired state data is incomplete"),
    JOB_IS_RETIRED("IJOB-BIZ-0096", "error.business.job-is-retired", "Job is retired"),
    JOB_HAS_NO_PUBLISHED_DEFINITION(
            "IJOB-BIZ-0089",
            "error.business.job-has-no-published-definition",
            "Job has no published definition"),
    JOB_CURRENT_POINTER_DATA_IS_INCOMPLETE(
            "IJOB-BIZ-0076",
            "error.business.job-current-pointer-data-is-incomplete",
            "Job current pointer data is incomplete"),
    JOB_CURRENT_DEFINITION_OWNERSHIP_IS_INCONSISTENT(
            "IJOB-BIZ-0075",
            "error.business.job-current-definition-ownership-is-inconsistent",
            "Job current definition ownership is inconsistent"),
    JOB_DEFINITION_EXECUTION_FIELDS_IS_INCOMPLETE(
            "IJOB-BIZ-0079",
            "error.business.job-definition-execution-fields-is-incomplete",
            "Job definition execution fields is incomplete"),
    JOB_ID_MUST_BE_GREATER_THAN_ZERO(
            "IJOB-BIZ-0092",
            "error.business.job-id-must-be-greater-than-zero",
            "Job ID must be greater than zero"),
    JOB_IDENTITY_CREATION_FAILED(
            "IJOB-BIZ-0093",
            "error.business.job-identity-creation-failed",
            "Job identity creation failed"),
    INITIAL_JOB_DEFINITION_ACTIVATION_FAILED(
            "IJOB-BIZ-0067",
            "error.business.initial-job-definition-activation-failed",
            "Initial job definition activation failed"),
    JOB_CURRENT_DEFINITION_ACTIVATION_FAILED(
            "IJOB-BIZ-0073",
            "error.business.job-current-definition-activation-failed",
            "Job current definition activation failed"),
    JOB_PUBLICATION_BARRIER_RETURNED_NULL(
            "IJOB-BIZ-0103",
            "error.business.job-publication-barrier-returned-null",
            "Job publication barrier returned null"),
    JOB_PUBLICATION_BARRIER_DATA_IS_INCOMPLETE(
            "IJOB-BIZ-0102",
            "error.business.job-publication-barrier-data-is-incomplete",
            "Job publication barrier data is incomplete"),
    TARGET_JOB_IS_MISSING_ITS_CURRENT_DEFINITION_SNAPSHOT(
            "IJOB-BIZ-0274",
            "error.business.target-job-is-missing-its-current-definition-snapshot",
            "Target job is missing its current definition snapshot"),
    REFERENCED_TASK_DOES_NOT_EXIST_OR_HAS_CHANGED(
            "IJOB-BIZ-0185",
            "error.business.referenced-task-does-not-exist-or-has-changed",
            "Referenced task does not exist or has changed"),
    CURRENT_DEFINITION_REFERENCE_SNAPSHOT_RETURNED_NULL(
            "IJOB-BIZ-0032",
            "error.business.current-definition-reference-snapshot-returned-null",
            "Current definition reference snapshot returned null"),
    CURRENT_JOB_DEFINITION_SNAPSHOT_IS_INCOMPLETE(
            "IJOB-BIZ-0034",
            "error.business.current-job-definition-snapshot-is-incomplete",
            "Current job definition snapshot is incomplete"),
    CURRENT_DEFINITION_REFERENCE_SNAPSHOT_IS_INCOMPLETE(
            "IJOB-BIZ-0031",
            "error.business.current-definition-reference-snapshot-is-incomplete",
            "Current definition reference snapshot is incomplete"),
    CURRENT_DEFINITION_REFERENCE_TARGET_IS_INCOMPLETE(
            "IJOB-BIZ-0033",
            "error.business.current-definition-reference-target-is-incomplete",
            "Current definition reference target is incomplete"),
    REFERENCED_JOB_DOES_NOT_EXIST_VALUE(
            "IJOB-BIZ-0182",
            "error.business.referenced-job-does-not-exist-value",
            "Referenced job does not exist: {0}"),
    REFERENCED_JOB_HAS_NO_CURRENT_DEFINITION_VALUE(
            "IJOB-BIZ-0183",
            "error.business.referenced-job-has-no-current-definition-value",
            "Referenced job has no current definition: {0}"),
    JOB_HAS_NO_BACKFILLED_CURRENT_DEFINITION(
            "IJOB-BIZ-0088",
            "error.business.job-has-no-backfilled-current-definition",
            "Job has no backfilled current definition"),
    JOB_REVISION_HAS_CHANGED(
            "IJOB-BIZ-0108", "error.business.job-revision-has-changed", "Job revision has changed"),
    JOB_REVISION_HAS_REACHED_ITS_LIMIT(
            "IJOB-BIZ-0109",
            "error.business.job-revision-has-reached-its-limit",
            "Job revision has reached its limit"),
    JOB_DEFINITION_CREATION_FAILED(
            "IJOB-BIZ-0077",
            "error.business.job-definition-creation-failed",
            "Job definition creation failed"),
    JOB_DEFINITION_NODE_CREATION_FAILED(
            "IJOB-BIZ-0080",
            "error.business.job-definition-node-creation-failed",
            "Job definition node creation failed"),
    JOB_DEFINITION_NODE_POLICY_CREATION_FAILED(
            "IJOB-BIZ-0081",
            "error.business.job-definition-node-policy-creation-failed",
            "Job definition node policy creation failed"),
    JOB_DEFINITION_EDGE_CREATION_FAILED(
            "IJOB-BIZ-0078",
            "error.business.job-definition-edge-creation-failed",
            "Job definition edge creation failed"),
    REFERENCED_TARGET_DEFINITION_SNAPSHOT_IS_MISSING(
            "IJOB-BIZ-0184",
            "error.business.referenced-target-definition-snapshot-is-missing",
            "Referenced target definition snapshot is missing"),
    EXPECTED_REVISION_MUST_BE_GREATER_THAN_ZERO(
            "IJOB-BIZ-0054",
            "error.business.expected-revision-must-be-greater-than-zero",
            "Expected revision must be greater than zero"),
    JOB_REQUEST_BODY_IS_REQUIRED(
            "IJOB-BIZ-0105",
            "error.business.job-request-body-is-required",
            "Job request body is required"),
    JOB_TIMEOUT_MUST_BE_GREATER_THAN_ZERO(
            "IJOB-BIZ-0110",
            "error.business.job-timeout-must-be-greater-than-zero",
            "Job timeout must be greater than zero"),
    JOB_DESCRIPTION_MUST_NOT_EXCEED_VALUE_CHARACTERS(
            "IJOB-BIZ-0082",
            "error.business.job-description-must-not-exceed-value-characters",
            "Job description must not exceed {0} characters"),
    NODE_TYPE_IS_REQUIRED(
            "IJOB-BIZ-0139", "error.business.node-type-is-required", "Node type is required"),
    NODE_REFERENCE_ID_MUST_BE_GREATER_THAN_ZERO(
            "IJOB-BIZ-0138",
            "error.business.node-reference-id-must-be-greater-than-zero",
            "Node reference ID must be greater than zero"),
    NODE_POLICY_IS_REQUIRED(
            "IJOB-BIZ-0137", "error.business.node-policy-is-required", "Node policy is required"),
    NODE_FAILURE_POLICY_IS_REQUIRED(
            "IJOB-BIZ-0136",
            "error.business.node-failure-policy-is-required",
            "Node failure policy is required"),
    NODE_EXECUTION_CONDITION_IS_REQUIRED(
            "IJOB-BIZ-0135",
            "error.business.node-execution-condition-is-required",
            "Node execution condition is required"),
    VALUE_IS_REQUIRED("IJOB-BIZ-0001", "error.business.value-is-required", "{0} is required"),
    VALUE_MUST_NOT_HAVE_LEADING_OR_TRAILING_WHITESPACE(
            "IJOB-BIZ-0004",
            "error.business.value-must-not-have-leading-or-trailing-whitespace",
            "{0} must not have leading or trailing whitespace"),
    VALUE_MUST_NOT_EXCEED_VALUE_CHARACTERS(
            "IJOB-BIZ-0003",
            "error.business.value-must-not-exceed-value-characters",
            "{0} must not exceed {1} characters"),
    CURRENT_JOB_ID_MUST_BE_GREATER_THAN_ZERO(
            "IJOB-BIZ-0035",
            "error.business.current-job-id-must-be-greater-than-zero",
            "Current job ID must be greater than zero"),
    JOB_NODE_DEFINITIONS_IS_REQUIRED(
            "IJOB-BIZ-0100",
            "error.business.job-node-definitions-is-required",
            "Job node definitions is required"),
    NESTED_JOB_REFERENCE_SNAPSHOT_IS_REQUIRED(
            "IJOB-BIZ-0129",
            "error.business.nested-job-reference-snapshot-is-required",
            "Nested job reference snapshot is required"),
    NESTED_JOB_REFERENCE_SNAPSHOTS_MUST_NOT_CONTAIN_NULL_REFERENCE_SETS(
            "IJOB-BIZ-0130",
            "error.business.nested-job-reference-snapshots-must-not-contain-null-reference-sets",
            "Nested job reference snapshots must not contain null reference sets"),
    A_JOB_MUST_NOT_REFERENCE_ITSELF(
            "IJOB-BIZ-0006",
            "error.business.a-job-must-not-reference-itself",
            "A job must not reference itself"),
    NESTED_JOB_DEPTH_MUST_NOT_EXCEED_VALUE(
            "IJOB-BIZ-0127",
            "error.business.nested-job-depth-must-not-exceed-value",
            "Nested job depth must not exceed {0}"),
    NESTED_JOB_REFERENCES_MUST_NOT_CONTAIN_CYCLES(
            "IJOB-BIZ-0131",
            "error.business.nested-job-references-must-not-contain-cycles",
            "Nested job references must not contain cycles"),
    NESTED_JOB_REFERENCE_SNAPSHOT_IS_INCOMPLETE(
            "IJOB-BIZ-0128",
            "error.business.nested-job-reference-snapshot-is-incomplete",
            "Nested job reference snapshot is incomplete"),
    JOB_REFERENCE_ID_MUST_BE_GREATER_THAN_ZERO(
            "IJOB-BIZ-0104",
            "error.business.job-reference-id-must-be-greater-than-zero",
            "Job reference ID must be greater than zero"),
    JOB_CURRENT_DEFINITION_DATA_IS_INCOMPLETE(
            "IJOB-BIZ-0074",
            "error.business.job-current-definition-data-is-incomplete",
            "Job current definition data is incomplete"),
    JOB_LIST_QUERY_FAILED(
            "IJOB-BIZ-0097", "error.business.job-list-query-failed", "Job list query failed"),
    JOB_NAME_MUST_NOT_EXCEED_128_CHARACTERS(
            "IJOB-BIZ-0098",
            "error.business.job-name-must-not-exceed-128-characters",
            "Job name must not exceed 128 characters"),
    JOB_RETIREMENT_TRANSITION_FAILED(
            "IJOB-BIZ-0107",
            "error.business.job-retirement-transition-failed",
            "Job retirement transition failed"),
    JOB_ID_IS_REQUIRED("IJOB-BIZ-0090", "error.business.job-id-is-required", "Job ID is required"),
    JOB_ID_MUST_BE_DISTINCT_AND_LOCKED_IN_ASCENDING_ORDER(
            "IJOB-BIZ-0091",
            "error.business.job-id-must-be-distinct-and-locked-in-ascending-order",
            "Job ID must be distinct and locked in ascending order"),
    BATCH_DELETION_IDS_IS_REQUIRED(
            "IJOB-BIZ-0010",
            "error.business.batch-deletion-ids-is-required",
            "Batch deletion IDs is required"),
    A_BATCH_DELETION_MUST_NOT_EXCEED_100_ITEMS(
            "IJOB-BIZ-0005",
            "error.business.a-batch-deletion-must-not-exceed-100-items",
            "A batch deletion must not exceed 100 items"),
    BATCH_DELETION_IDS_MUST_BE_GREATER_THAN_ZERO(
            "IJOB-BIZ-0011",
            "error.business.batch-deletion-ids-must-be-greater-than-zero",
            "Batch deletion IDs must be greater than zero"),
    BATCH_DELETION_IDS_MUST_BE_UNIQUE(
            "IJOB-BIZ-0012",
            "error.business.batch-deletion-ids-must-be-unique",
            "Batch deletion IDs must be unique"),
    FTP_RESOURCE_CONNECTION_FAILED(
            "IJOB-BIZ-0060",
            "error.business.ftp-resource-connection-failed",
            "FTP resource connection failed"),
    SFTP_RESOURCE_CONNECTION_FAILED(
            "IJOB-BIZ-0252",
            "error.business.sftp-resource-connection-failed",
            "SFTP resource connection failed"),
    SSH_RESOURCE_CONNECTION_FAILED(
            "IJOB-BIZ-0267",
            "error.business.ssh-resource-connection-failed",
            "SSH resource connection failed"),
    SSH_COMMAND_STARTUP_FAILED(
            "IJOB-BIZ-0257",
            "error.business.ssh-command-startup-failed",
            "SSH command startup failed"),
    RESOURCE_CONFIGURATION_IS_UNAVAILABLE(
            "IJOB-BIZ-0205",
            "error.business.resource-configuration-is-unavailable",
            "Resource configuration is unavailable"),
    DATABASE_TYPE_IS_REQUIRED_AND_MUST_BE_SUPPORTED(
            "IJOB-BIZ-0038",
            "error.business.database-type-is-required-and-must-be-supported",
            "Database type is required and must be supported"),
    JDBC_URL_IS_REQUIRED(
            "IJOB-BIZ-0072", "error.business.jdbc-url-is-required", "JDBC URL is required"),
    JDBC_URL_DOES_NOT_MATCH_THE_DATABASE_TYPE_OR_CONTAINS_SENSITIVE_PARAMETERS(
            "IJOB-BIZ-0071",
            "error.business.jdbc-url-does-not-match-the-database-type-or-contains-sensitive-parameters",
            "JDBC URL does not match the database type or contains sensitive parameters"),
    DATABASE_USERNAME_IS_REQUIRED(
            "IJOB-BIZ-0039",
            "error.business.database-username-is-required",
            "Database username is required"),
    DATABASE_USERNAME_MUST_NOT_EXCEED_128_CHARACTERS(
            "IJOB-BIZ-0040",
            "error.business.database-username-must-not-exceed-128-characters",
            "Database username must not exceed 128 characters"),
    DATABASE_PASSWORD_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0036",
            "error.business.database-password-exceeds-the-length-limit",
            "Database password exceeds the length limit"),
    DATABASE_SCHEMA_MUST_NOT_EXCEED_128_CHARACTERS(
            "IJOB-BIZ-0037",
            "error.business.database-schema-must-not-exceed-128-characters",
            "Database schema must not exceed 128 characters"),
    FILE_PROTOCOL_IS_REQUIRED_AND_MUST_BE_SUPPORTED(
            "IJOB-BIZ-0055",
            "error.business.file-protocol-is-required-and-must-be-supported",
            "File protocol is required and must be supported"),
    FILE_RESOURCE_TIMEOUT_MUST_BE_BETWEEN_100_AND_300000_MILLISECONDS(
            "IJOB-BIZ-0058",
            "error.business.file-resource-timeout-must-be-between-100-and-300000-milliseconds",
            "File resource timeout must be between 100 and 300000 milliseconds"),
    FILE_RESOURCE_BASEPATH_IS_REQUIRED(
            "IJOB-BIZ-0057",
            "error.business.file-resource-basepath-is-required",
            "File resource basePath is required"),
    FILE_RESOURCE_BASEPATH_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0056",
            "error.business.file-resource-basepath-exceeds-the-length-limit",
            "File resource basePath exceeds the length limit"),
    S3_ENDPOINT_IS_INVALID(
            "IJOB-BIZ-0228", "error.business.s3-endpoint-is-invalid", "S3 endpoint is invalid"),
    S3_REGION_IS_REQUIRED(
            "IJOB-BIZ-0231", "error.business.s3-region-is-required", "S3 region is required"),
    S3_REGION_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0230",
            "error.business.s3-region-exceeds-the-length-limit",
            "S3 region exceeds the length limit"),
    S3_BUCKET_IS_REQUIRED(
            "IJOB-BIZ-0226", "error.business.s3-bucket-is-required", "S3 bucket is required"),
    S3_BUCKET_NAME_IS_INVALID(
            "IJOB-BIZ-0227",
            "error.business.s3-bucket-name-is-invalid",
            "S3 bucket name is invalid"),
    S3_ACCESSKEY_IS_REQUIRED(
            "IJOB-BIZ-0225", "error.business.s3-accesskey-is-required", "S3 accessKey is required"),
    S3_ACCESSKEY_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0224",
            "error.business.s3-accesskey-exceeds-the-length-limit",
            "S3 accessKey exceeds the length limit"),
    S3_SECRETKEY_IS_REQUIRED(
            "IJOB-BIZ-0233", "error.business.s3-secretkey-is-required", "S3 secretKey is required"),
    S3_SECRETKEY_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0232",
            "error.business.s3-secretkey-exceeds-the-length-limit",
            "S3 secretKey exceeds the length limit"),
    S3_SESSIONTOKEN_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0234",
            "error.business.s3-sessiontoken-exceeds-the-length-limit",
            "S3 sessionToken exceeds the length limit"),
    REMOTE_FILE_HOST_IS_REQUIRED(
            "IJOB-BIZ-0186",
            "error.business.remote-file-host-is-required",
            "Remote file host is required"),
    REMOTE_FILE_HOST_MUST_NOT_EXCEED_255_CHARACTERS(
            "IJOB-BIZ-0187",
            "error.business.remote-file-host-must-not-exceed-255-characters",
            "Remote file host must not exceed 255 characters"),
    REMOTE_FILE_PORT_IS_INVALID(
            "IJOB-BIZ-0189",
            "error.business.remote-file-port-is-invalid",
            "Remote file port is invalid"),
    REMOTE_FILE_USERNAME_IS_REQUIRED(
            "IJOB-BIZ-0190",
            "error.business.remote-file-username-is-required",
            "Remote file username is required"),
    REMOTE_FILE_USERNAME_MUST_NOT_EXCEED_128_CHARACTERS(
            "IJOB-BIZ-0191",
            "error.business.remote-file-username-must-not-exceed-128-characters",
            "Remote file username must not exceed 128 characters"),
    REMOTE_FILE_PASSWORD_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0188",
            "error.business.remote-file-password-exceeds-the-length-limit",
            "Remote file password exceeds the length limit"),
    FTP_RESOURCE_PASSWORD_IS_REQUIRED(
            "IJOB-BIZ-0061",
            "error.business.ftp-resource-password-is-required",
            "FTP resource password is required"),
    FTP_RESOURCES_DO_NOT_SUPPORT_SFTP_SECURITY_SETTINGS(
            "IJOB-BIZ-0062",
            "error.business.ftp-resources-do-not-support-sftp-security-settings",
            "FTP resources do not support SFTP security settings"),
    SFTP_PRIVATE_KEY_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0251",
            "error.business.sftp-private-key-exceeds-the-length-limit",
            "SFTP private key exceeds the length limit"),
    SFTP_PASSPHRASE_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0249",
            "error.business.sftp-passphrase-exceeds-the-length-limit",
            "SFTP passphrase exceeds the length limit"),
    SFTP_KNOWNHOSTS_PATH_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0248",
            "error.business.sftp-knownhosts-path-exceeds-the-length-limit",
            "SFTP knownHosts path exceeds the length limit"),
    SFTP_RESOURCES_REQUIRE_A_PASSWORD_OR_PRIVATE_KEY(
            "IJOB-BIZ-0254",
            "error.business.sftp-resources-require-a-password-or-private-key",
            "SFTP resources require a password or private key"),
    SFTP_PASSPHRASE_REQUIRES_A_PRIVATE_KEY(
            "IJOB-BIZ-0250",
            "error.business.sftp-passphrase-requires-a-private-key",
            "SFTP passphrase requires a private key"),
    SFTP_RESOURCES_REQUIRE_A_KNOWNHOSTS_FILE(
            "IJOB-BIZ-0253",
            "error.business.sftp-resources-require-a-knownhosts-file",
            "SFTP resources require a knownHosts file"),
    SERVICE_PROTOCOL_IS_REQUIRED_AND_MUST_BE_SUPPORTED(
            "IJOB-BIZ-0243",
            "error.business.service-protocol-is-required-and-must-be-supported",
            "Service protocol is required and must be supported"),
    SERVICE_ENDPOINT_IS_INVALID(
            "IJOB-BIZ-0242",
            "error.business.service-endpoint-is-invalid",
            "Service endpoint is invalid"),
    SERVICE_CONNECT_TIMEOUT_MUST_BE_BETWEEN_1_AND_300_SECONDS(
            "IJOB-BIZ-0241",
            "error.business.service-connect-timeout-must-be-between-1-and-300-seconds",
            "Service connect timeout must be between 1 and 300 seconds"),
    SERVICE_READ_TIMEOUT_MUST_BE_BETWEEN_1_AND_300_SECONDS(
            "IJOB-BIZ-0244",
            "error.business.service-read-timeout-must-be-between-1-and-300-seconds",
            "Service read timeout must be between 1 and 300 seconds"),
    SERVICE_WRITE_TIMEOUT_MUST_BE_BETWEEN_1_AND_300_SECONDS(
            "IJOB-BIZ-0245",
            "error.business.service-write-timeout-must-be-between-1-and-300-seconds",
            "Service write timeout must be between 1 and 300 seconds"),
    SERVICE_CALL_TIMEOUT_MUST_BE_BETWEEN_1_AND_900_SECONDS(
            "IJOB-BIZ-0240",
            "error.business.service-call-timeout-must-be-between-1-and-900-seconds",
            "Service call timeout must be between 1 and 900 seconds"),
    SERVICE_AUTHENTICATION_TYPE_IS_NOT_SUPPORTED(
            "IJOB-BIZ-0239",
            "error.business.service-authentication-type-is-not-supported",
            "Service authentication type is not supported"),
    WEBSERVICE_DOES_NOT_SUPPORT_OAUTH2_CLIENT_CREDENTIALS_AUTHENTICATION(
            "IJOB-BIZ-0306",
            "error.business.webservice-does-not-support-oauth2-client-credentials-authentication",
            "WebService does not support OAuth2 client credentials authentication"),
    HTTP_SERVICES_MUST_NOT_INCLUDE_WEBSERVICE_SETTINGS(
            "IJOB-BIZ-0066",
            "error.business.http-services-must-not-include-webservice-settings",
            "HTTP services must not include WebService settings"),
    OAUTH2_SERVICE_RESOURCES_MUST_NOT_CONFIGURE_AN_AUTHORIZATION_HEADER(
            "IJOB-BIZ-0155",
            "error.business.oauth2-service-resources-must-not-configure-an-authorization-header",
            "OAuth2 service resources must not configure an Authorization header"),
    NONE_AUTHENTICATION_MUST_NOT_INCLUDE_OAUTH2_SETTINGS(
            "IJOB-BIZ-0140",
            "error.business.none-authentication-must-not-include-oauth2-settings",
            "NONE authentication must not include OAuth2 settings"),
    OAUTH2_CLIENT_CREDENTIALS_CONFIGURATION_IS_REQUIRED(
            "IJOB-BIZ-0149",
            "error.business.oauth2-client-credentials-configuration-is-required",
            "OAuth2 client credentials configuration is required"),
    OAUTH2_TOKENENDPOINT_IS_INVALID(
            "IJOB-BIZ-0157",
            "error.business.oauth2-tokenendpoint-is-invalid",
            "OAuth2 tokenEndpoint is invalid"),
    OAUTH2_CLIENTID_IS_REQUIRED(
            "IJOB-BIZ-0151",
            "error.business.oauth2-clientid-is-required",
            "OAuth2 clientId is required"),
    OAUTH2_CLIENTID_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0150",
            "error.business.oauth2-clientid-exceeds-the-length-limit",
            "OAuth2 clientId exceeds the length limit"),
    OAUTH2_CLIENTSECRET_IS_REQUIRED(
            "IJOB-BIZ-0153",
            "error.business.oauth2-clientsecret-is-required",
            "OAuth2 clientSecret is required"),
    OAUTH2_CLIENTSECRET_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0152",
            "error.business.oauth2-clientsecret-exceeds-the-length-limit",
            "OAuth2 clientSecret exceeds the length limit"),
    OAUTH2_SCOPES_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0154",
            "error.business.oauth2-scopes-exceeds-the-length-limit",
            "OAuth2 scopes exceeds the length limit"),
    OAUTH2_AUDIENCE_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0147",
            "error.business.oauth2-audience-exceeds-the-length-limit",
            "OAuth2 audience exceeds the length limit"),
    OAUTH2_CLIENT_AUTHENTICATION_METHOD_IS_NOT_SUPPORTED(
            "IJOB-BIZ-0148",
            "error.business.oauth2-client-authentication-method-is-not-supported",
            "OAuth2 client authentication method is not supported"),
    OAUTH2_TOKEN_REFRESH_SKEW_MUST_BE_BETWEEN_0_AND_300_SECONDS(
            "IJOB-BIZ-0156",
            "error.business.oauth2-token-refresh-skew-must-be-between-0-and-300-seconds",
            "OAuth2 token refresh skew must be between 0 and 300 seconds"),
    WEBSERVICE_CONFIGURATION_IS_REQUIRED(
            "IJOB-BIZ-0305",
            "error.business.webservice-configuration-is-required",
            "WebService configuration is required"),
    WSDL_URL_IS_INVALID(
            "IJOB-BIZ-0307", "error.business.wsdl-url-is-invalid", "WSDL URL is invalid"),
    NAMESPACE_IS_REQUIRED(
            "IJOB-BIZ-0126", "error.business.namespace-is-required", "namespace is required"),
    SERVICENAME_IS_REQUIRED(
            "IJOB-BIZ-0247", "error.business.servicename-is-required", "serviceName is required"),
    PORTNAME_IS_REQUIRED(
            "IJOB-BIZ-0168", "error.business.portname-is-required", "portName is required"),
    NAMESPACE_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0125",
            "error.business.namespace-exceeds-the-length-limit",
            "namespace exceeds the length limit"),
    SERVICENAME_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0246",
            "error.business.servicename-exceeds-the-length-limit",
            "serviceName exceeds the length limit"),
    PORTNAME_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0167",
            "error.business.portname-exceeds-the-length-limit",
            "portName exceeds the length limit"),
    RABBITMQ_HOST_IS_REQUIRED(
            "IJOB-BIZ-0173",
            "error.business.rabbitmq-host-is-required",
            "RabbitMQ host is required"),
    RABBITMQ_HOST_MUST_NOT_EXCEED_255_CHARACTERS(
            "IJOB-BIZ-0174",
            "error.business.rabbitmq-host-must-not-exceed-255-characters",
            "RabbitMQ host must not exceed 255 characters"),
    RABBITMQ_PORT_IS_INVALID(
            "IJOB-BIZ-0177", "error.business.rabbitmq-port-is-invalid", "RabbitMQ port is invalid"),
    RABBITMQ_USERNAME_IS_REQUIRED(
            "IJOB-BIZ-0179",
            "error.business.rabbitmq-username-is-required",
            "RabbitMQ username is required"),
    RABBITMQ_USERNAME_MUST_NOT_EXCEED_128_CHARACTERS(
            "IJOB-BIZ-0180",
            "error.business.rabbitmq-username-must-not-exceed-128-characters",
            "RabbitMQ username must not exceed 128 characters"),
    RABBITMQ_PASSWORD_IS_REQUIRED(
            "IJOB-BIZ-0176",
            "error.business.rabbitmq-password-is-required",
            "RabbitMQ password is required"),
    RABBITMQ_PASSWORD_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0175",
            "error.business.rabbitmq-password-exceeds-the-length-limit",
            "RabbitMQ password exceeds the length limit"),
    RABBITMQ_VHOST_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0181",
            "error.business.rabbitmq-vhost-exceeds-the-length-limit",
            "RabbitMQ vhost exceeds the length limit"),
    RABBITMQ_EXCHANGE_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0172",
            "error.business.rabbitmq-exchange-exceeds-the-length-limit",
            "RabbitMQ exchange exceeds the length limit"),
    RABBITMQ_ROUTINGKEY_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0178",
            "error.business.rabbitmq-routingkey-exceeds-the-length-limit",
            "RabbitMQ routingKey exceeds the length limit"),
    KAFKA_BOOTSTRAPSERVERS_IS_REQUIRED(
            "IJOB-BIZ-0113",
            "error.business.kafka-bootstrapservers-is-required",
            "Kafka bootstrapServers is required"),
    KAFKA_BOOTSTRAPSERVERS_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0112",
            "error.business.kafka-bootstrapservers-exceeds-the-length-limit",
            "Kafka bootstrapServers exceeds the length limit"),
    KAFKA_ACKS_MUST_BE_ALL_1_OR_0(
            "IJOB-BIZ-0111",
            "error.business.kafka-acks-must-be-all-1-or-0",
            "Kafka acks must be all, 1 or 0"),
    MONITOR_PROTOCOL_IS_REQUIRED_AND_MUST_BE_SUPPORTED(
            "IJOB-BIZ-0121",
            "error.business.monitor-protocol-is-required-and-must-be-supported",
            "Monitor protocol is required and must be supported"),
    MONITOR_ENDPOINT_IS_INVALID(
            "IJOB-BIZ-0119",
            "error.business.monitor-endpoint-is-invalid",
            "Monitor endpoint is invalid"),
    MONITOR_ENDPOINT_DOES_NOT_MATCH_THE_PROTOCOL(
            "IJOB-BIZ-0118",
            "error.business.monitor-endpoint-does-not-match-the-protocol",
            "Monitor endpoint does not match the protocol"),
    MONITOR_CONNECT_TIMEOUT_MUST_BE_BETWEEN_100_AND_300000_MILLISECONDS(
            "IJOB-BIZ-0117",
            "error.business.monitor-connect-timeout-must-be-between-100-and-300000-milliseconds",
            "Monitor connect timeout must be between 100 and 300000 milliseconds"),
    MONITOR_READ_TIMEOUT_MUST_BE_BETWEEN_100_AND_300000_MILLISECONDS(
            "IJOB-BIZ-0122",
            "error.business.monitor-read-timeout-must-be-between-100-and-300000-milliseconds",
            "Monitor read timeout must be between 100 and 300000 milliseconds"),
    MONITOR_TOKEN_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0123",
            "error.business.monitor-token-exceeds-the-length-limit",
            "Monitor Token exceeds the length limit"),
    MONITOR_OPERATING_SYSTEM_TYPE_IS_NOT_SUPPORTED(
            "IJOB-BIZ-0120",
            "error.business.monitor-operating-system-type-is-not-supported",
            "Monitor operating system type is not supported"),
    MONITOR_AGENT_VERSION_MUST_NOT_EXCEED_64_CHARACTERS(
            "IJOB-BIZ-0116",
            "error.business.monitor-agent-version-must-not-exceed-64-characters",
            "Monitor agent version must not exceed 64 characters"),
    SSH_HOST_IS_REQUIRED(
            "IJOB-BIZ-0259", "error.business.ssh-host-is-required", "SSH host is required"),
    SSH_HOST_MUST_NOT_EXCEED_255_CHARACTERS(
            "IJOB-BIZ-0260",
            "error.business.ssh-host-must-not-exceed-255-characters",
            "SSH host must not exceed 255 characters"),
    SSH_PORT_IS_INVALID(
            "IJOB-BIZ-0265", "error.business.ssh-port-is-invalid", "SSH port is invalid"),
    SSH_USERNAME_IS_REQUIRED(
            "IJOB-BIZ-0270", "error.business.ssh-username-is-required", "SSH username is required"),
    SSH_USERNAME_MUST_NOT_EXCEED_128_CHARACTERS(
            "IJOB-BIZ-0271",
            "error.business.ssh-username-must-not-exceed-128-characters",
            "SSH username must not exceed 128 characters"),
    SSH_CONNECT_TIMEOUT_MUST_BE_BETWEEN_100_AND_300000_MILLISECONDS(
            "IJOB-BIZ-0258",
            "error.business.ssh-connect-timeout-must-be-between-100-and-300000-milliseconds",
            "SSH connect timeout must be between 100 and 300000 milliseconds"),
    SSH_PASSWORD_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0264",
            "error.business.ssh-password-exceeds-the-length-limit",
            "SSH password exceeds the length limit"),
    SSH_PRIVATE_KEY_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0266",
            "error.business.ssh-private-key-exceeds-the-length-limit",
            "SSH private key exceeds the length limit"),
    SSH_PASSPHRASE_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0262",
            "error.business.ssh-passphrase-exceeds-the-length-limit",
            "SSH passphrase exceeds the length limit"),
    SSH_KNOWNHOSTS_PATH_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0261",
            "error.business.ssh-knownhosts-path-exceeds-the-length-limit",
            "SSH knownHosts path exceeds the length limit"),
    SSH_RESOURCES_REQUIRE_A_PASSWORD_OR_PRIVATE_KEY(
            "IJOB-BIZ-0269",
            "error.business.ssh-resources-require-a-password-or-private-key",
            "SSH resources require a password or private key"),
    SSH_PASSPHRASE_REQUIRES_A_PRIVATE_KEY(
            "IJOB-BIZ-0263",
            "error.business.ssh-passphrase-requires-a-private-key",
            "SSH passphrase requires a private key"),
    SSH_RESOURCES_REQUIRE_A_KNOWNHOSTS_FILE(
            "IJOB-BIZ-0268",
            "error.business.ssh-resources-require-a-knownhosts-file",
            "SSH resources require a knownHosts file"),
    RESOURCE_TYPE_IS_REQUIRED(
            "IJOB-BIZ-0221",
            "error.business.resource-type-is-required",
            "Resource type is required"),
    EXACTLY_ONE_CONFIGURATION_MATCHING_THE_RESOURCE_TYPE_IS_REQUIRED(
            "IJOB-BIZ-0048",
            "error.business.exactly-one-configuration-matching-the-resource-type-is-required",
            "Exactly one configuration matching the resource type is required"),
    LOCAL_FILE_RESOURCES_MUST_NOT_INCLUDE_REMOTE_CONNECTION_OR_CREDENTIAL_FIELDS(
            "IJOB-BIZ-0114",
            "error.business.local-file-resources-must-not-include-remote-connection-or-credential-fields",
            "LOCAL file resources must not include remote connection or credential fields"),
    S3_FILE_RESOURCES_MUST_NOT_INCLUDE_FTP_OR_SFTP_CONNECTION_FIELDS(
            "IJOB-BIZ-0229",
            "error.business.s3-file-resources-must-not-include-ftp-or-sftp-connection-fields",
            "S3 file resources must not include FTP or SFTP connection fields"),
    FTP_AND_SFTP_FILE_RESOURCES_MUST_NOT_INCLUDE_OBJECT_STORAGE_FIELDS(
            "IJOB-BIZ-0059",
            "error.business.ftp-and-sftp-file-resources-must-not-include-object-storage-fields",
            "FTP and SFTP file resources must not include object storage fields"),
    HEADER_COUNT_MUST_NOT_EXCEED_50(
            "IJOB-BIZ-0063",
            "error.business.header-count-must-not-exceed-50",
            "Header count must not exceed 50"),
    HEADER_NAME_IS_INVALID(
            "IJOB-BIZ-0064", "error.business.header-name-is-invalid", "Header name is invalid"),
    HEADER_VALUE_IS_INVALID_OR_EXCEEDS_THE_LENGTH_LIMIT(
            "IJOB-BIZ-0065",
            "error.business.header-value-is-invalid-or-exceeds-the-length-limit",
            "Header value is invalid or exceeds the length limit"),
    RESOURCE_CLIENT_CREATION_FAILED(
            "IJOB-BIZ-0194",
            "error.business.resource-client-creation-failed",
            "Resource client creation failed"),
    RESOURCE_TYPE_AND_CLIENT_TYPE_ARE_REQUIRED(
            "IJOB-BIZ-0219",
            "error.business.resource-type-and-client-type-are-required",
            "Resource type and client type are required"),
    THIS_RESOURCE_TYPE_REQUIRES_AN_EXCLUSIVE_CLIENT_LEASE(
            "IJOB-BIZ-0303",
            "error.business.this-resource-type-requires-an-exclusive-client-lease",
            "This resource type requires an exclusive client lease"),
    RESOURCE_CLIENT_TYPE_IS_UNAVAILABLE(
            "IJOB-BIZ-0195",
            "error.business.resource-client-type-is-unavailable",
            "Resource client type is unavailable"),
    RESOURCE_CONNECTION_TEST_FAILED(
            "IJOB-BIZ-0208",
            "error.business.resource-connection-test-failed",
            "Resource connection test failed"),
    RESOURCE_IS_DISABLED(
            "IJOB-BIZ-0214", "error.business.resource-is-disabled", "Resource is disabled"),
    RESOURCE_TYPE_IS_UNAVAILABLE(
            "IJOB-BIZ-0222",
            "error.business.resource-type-is-unavailable",
            "Resource type is unavailable"),
    RESOURCE_TYPE_IS_NOT_SUPPORTED_FOR_EXECUTION(
            "IJOB-BIZ-0220",
            "error.business.resource-type-is-not-supported-for-execution",
            "Resource type is not supported for execution"),
    RESOURCE_ID_MUST_BE_GREATER_THAN_ZERO(
            "IJOB-BIZ-0213",
            "error.business.resource-id-must-be-greater-than-zero",
            "Resource ID must be greater than zero"),
    RESOURCE_CREATION_FAILED(
            "IJOB-BIZ-0209", "error.business.resource-creation-failed", "Resource creation failed"),
    RESOURCE_UPDATE_FAILED(
            "IJOB-BIZ-0223", "error.business.resource-update-failed", "Resource update failed"),
    RESOURCE_BATCH_LOCKING_FAILED(
            "IJOB-BIZ-0193",
            "error.business.resource-batch-locking-failed",
            "Resource batch locking failed"),
    SOME_RESOURCES_DO_NOT_EXIST(
            "IJOB-BIZ-0256",
            "error.business.some-resources-do-not-exist",
            "Some resources do not exist"),
    RESOURCE_DELETION_CONFLICTS_WITH_A_CONCURRENT_UPDATE(
            "IJOB-BIZ-0210",
            "error.business.resource-deletion-conflicts-with-a-concurrent-update",
            "Resource deletion conflicts with a concurrent update"),
    RESOURCE_REFERENCE_CHECK_FAILED(
            "IJOB-BIZ-0218",
            "error.business.resource-reference-check-failed",
            "Resource reference check failed"),
    TASK_DEFINITION_RESOURCE_REFERENCES_DATA_IS_INCOMPLETE(
            "IJOB-BIZ-0284",
            "error.business.task-definition-resource-references-data-is-incomplete",
            "Task definition resource references data is incomplete"),
    RESOURCE_IS_STILL_REFERENCED_BY_TASK_DEFINITIONS(
            "IJOB-BIZ-0215",
            "error.business.resource-is-still-referenced-by-task-definitions",
            "Resource is still referenced by task definitions"),
    RESOURCE_DOES_NOT_EXIST(
            "IJOB-BIZ-0212", "error.business.resource-does-not-exist", "Resource does not exist"),
    RESOURCE_CODE_ALREADY_EXISTS(
            "IJOB-BIZ-0196",
            "error.business.resource-code-already-exists",
            "Resource code already exists"),
    RESOURCE_CODE_IS_REQUIRED(
            "IJOB-BIZ-0197",
            "error.business.resource-code-is-required",
            "Resource code is required"),
    RESOURCE_CODE_MUST_CONTAIN_ONLY_LETTERS_DIGITS_DOTS_UNDERSCORES_OR_HYPHENS_AND_AT_MOST_128_CHARACTERS(
            "IJOB-BIZ-0198",
            "error.business.resource-code-must-contain-only-letters-digits-dots-underscores-or-hyphens-and-at-most-128-characters",
            "Resource code must contain only letters, digits, dots, underscores or hyphens and at"
                    + " most 128 characters"),
    RESOURCE_NAME_IS_REQUIRED(
            "IJOB-BIZ-0216",
            "error.business.resource-name-is-required",
            "Resource name is required"),
    RESOURCE_NAME_MUST_NOT_EXCEED_128_CHARACTERS(
            "IJOB-BIZ-0217",
            "error.business.resource-name-must-not-exceed-128-characters",
            "Resource name must not exceed 128 characters"),
    RESOURCE_DESCRIPTION_MUST_NOT_EXCEED_500_CHARACTERS(
            "IJOB-BIZ-0211",
            "error.business.resource-description-must-not-exceed-500-characters",
            "Resource description must not exceed 500 characters"),
    ENABLED_STATE_IS_REQUIRED(
            "IJOB-BIZ-0047",
            "error.business.enabled-state-is-required",
            "Enabled state is required"),
    PREVIEW_REQUEST_IS_REQUIRED(
            "IJOB-BIZ-0171",
            "error.business.preview-request-is-required",
            "Preview request is required"),
    PREVIEW_COUNT_MUST_BE_BETWEEN_1_AND_50(
            "IJOB-BIZ-0169",
            "error.business.preview-count-must-be-between-1-and-50",
            "Preview count must be between 1 and 50"),
    SCAN_TIME_IS_REQUIRED(
            "IJOB-BIZ-0236", "error.business.scan-time-is-required", "Scan time is required"),
    SCAN_BATCH_SIZE_MUST_BE_BETWEEN_1_AND_1000(
            "IJOB-BIZ-0235",
            "error.business.scan-batch-size-must-be-between-1-and-1000",
            "Scan batch size must be between 1 and 1000"),
    SCHEDULE_ID_MUST_BE_GREATER_THAN_ZERO(
            "IJOB-BIZ-0237",
            "error.business.schedule-id-must-be-greater-than-zero",
            "Schedule ID must be greater than zero"),
    TARGET_TYPE_IS_REQUIRED(
            "IJOB-BIZ-0275", "error.business.target-type-is-required", "Target type is required"),
    TARGET_ID_MUST_BE_GREATER_THAN_ZERO(
            "IJOB-BIZ-0273",
            "error.business.target-id-must-be-greater-than-zero",
            "Target ID must be greater than zero"),
    CALENDAR_ID_MUST_BE_GREATER_THAN_ZERO_20(
            "IJOB-BIZ-0020",
            "error.business.calendar-id-must-be-greater-than-zero-20",
            "Calendar ID must be greater than zero"),
    SCHEDULE_TYPE_IS_REQUIRED(
            "IJOB-BIZ-0238",
            "error.business.schedule-type-is-required",
            "Schedule type is required"),
    START_TIME_MUST_NOT_BE_LATER_THAN_END_TIME(
            "IJOB-BIZ-0272",
            "error.business.start-time-must-not-be-later-than-end-time",
            "Start time must not be later than end time"),
    CRON_EXPRESSION_IS_REQUIRED(
            "IJOB-BIZ-0029",
            "error.business.cron-expression-is-required",
            "Cron expression is required"),
    CRON_SCHEDULES_MUST_NOT_SPECIFY_INTERVAL_SECONDS(
            "IJOB-BIZ-0030",
            "error.business.cron-schedules-must-not-specify-interval-seconds",
            "Cron schedules must not specify interval seconds"),
    INTERVAL_SECONDS_MUST_BE_BETWEEN_1_AND_31536000(
            "IJOB-BIZ-0070",
            "error.business.interval-seconds-must-be-between-1-and-31536000",
            "Interval seconds must be between 1 and 31536000"),
    INTERVAL_SCHEDULES_MUST_NOT_SPECIFY_A_CRON_EXPRESSION(
            "IJOB-BIZ-0069",
            "error.business.interval-schedules-must-not-specify-a-cron-expression",
            "Interval schedules must not specify a Cron expression"),
    ONE_TIME_SCHEDULES_REQUIRE_A_START_TIME(
            "IJOB-BIZ-0159",
            "error.business.one-time-schedules-require-a-start-time",
            "One-time schedules require a start time"),
    ONE_TIME_SCHEDULES_MUST_NOT_SPECIFY_CRON_OR_INTERVAL_SECONDS(
            "IJOB-BIZ-0158",
            "error.business.one-time-schedules-must-not-specify-cron-or-interval-seconds",
            "One-time schedules must not specify Cron or interval seconds"),
    CRON_EXPRESSION_IS_INVALID(
            "IJOB-BIZ-0028",
            "error.business.cron-expression-is-invalid",
            "Cron expression is invalid"),
    TASK_IS_DISABLED("IJOB-BIZ-0294", "error.business.task-is-disabled", "Task is disabled"),
    TASK_DOES_NOT_EXIST(
            "IJOB-BIZ-0286", "error.business.task-does-not-exist", "Task does not exist"),
    TASK_IDENTITY_LOCK_RESULT_IS_INCONSISTENT(
            "IJOB-BIZ-0293",
            "error.business.task-identity-lock-result-is-inconsistent",
            "Task identity lock result is inconsistent"),
    TASK_RETIRED_STATE_DATA_IS_INCOMPLETE(
            "IJOB-BIZ-0298",
            "error.business.task-retired-state-data-is-incomplete",
            "Task retired state data is incomplete"),
    TASK_IS_RETIRED("IJOB-BIZ-0295", "error.business.task-is-retired", "Task is retired"),
    TASK_HAS_NO_PUBLISHED_DEFINITION(
            "IJOB-BIZ-0288",
            "error.business.task-has-no-published-definition",
            "Task has no published definition"),
    TASK_CURRENT_POINTER_DATA_IS_INCOMPLETE(
            "IJOB-BIZ-0278",
            "error.business.task-current-pointer-data-is-incomplete",
            "Task current pointer data is incomplete"),
    TASK_CURRENT_DEFINITION_OWNERSHIP_IS_INCONSISTENT(
            "IJOB-BIZ-0277",
            "error.business.task-current-definition-ownership-is-inconsistent",
            "Task current definition ownership is inconsistent"),
    TASK_DEFINITION_EXECUTION_FIELDS_IS_INCOMPLETE(
            "IJOB-BIZ-0281",
            "error.business.task-definition-execution-fields-is-incomplete",
            "Task definition execution fields is incomplete"),
    TASK_ID_MUST_BE_GREATER_THAN_ZERO(
            "IJOB-BIZ-0291",
            "error.business.task-id-must-be-greater-than-zero",
            "Task ID must be greater than zero"),
    TASK_IDENTITY_CREATION_FAILED(
            "IJOB-BIZ-0292",
            "error.business.task-identity-creation-failed",
            "Task identity creation failed"),
    INITIAL_TASK_DEFINITION_ACTIVATION_FAILED(
            "IJOB-BIZ-0068",
            "error.business.initial-task-definition-activation-failed",
            "Initial task definition activation failed"),
    TASK_CURRENT_DEFINITION_ACTIVATION_FAILED(
            "IJOB-BIZ-0276",
            "error.business.task-current-definition-activation-failed",
            "Task current definition activation failed"),
    TASK_HAS_NO_BACKFILLED_CURRENT_DEFINITION(
            "IJOB-BIZ-0287",
            "error.business.task-has-no-backfilled-current-definition",
            "Task has no backfilled current definition"),
    TASK_REVISION_HAS_CHANGED(
            "IJOB-BIZ-0300",
            "error.business.task-revision-has-changed",
            "Task revision has changed"),
    TASK_REVISION_HAS_REACHED_ITS_LIMIT(
            "IJOB-BIZ-0301",
            "error.business.task-revision-has-reached-its-limit",
            "Task revision has reached its limit"),
    TASK_DEFINITION_CREATION_FAILED(
            "IJOB-BIZ-0280",
            "error.business.task-definition-creation-failed",
            "Task definition creation failed"),
    TASK_NAME_IS_REQUIRED(
            "IJOB-BIZ-0296", "error.business.task-name-is-required", "Task name is required"),
    TASK_NAME_MUST_NOT_EXCEED_128_CHARACTERS(
            "IJOB-BIZ-0297",
            "error.business.task-name-must-not-exceed-128-characters",
            "Task name must not exceed 128 characters"),
    PLUGIN_TYPE_IS_REQUIRED(
            "IJOB-BIZ-0166", "error.business.plugin-type-is-required", "Plugin type is required"),
    PLUGIN_CONFIGURATION_IS_REQUIRED(
            "IJOB-BIZ-0161",
            "error.business.plugin-configuration-is-required",
            "Plugin configuration is required"),
    PLUGIN_CONFIGURATION_MUST_NOT_EXCEED_8_MIB(
            "IJOB-BIZ-0162",
            "error.business.plugin-configuration-must-not-exceed-8-mib",
            "Plugin configuration must not exceed 8 MiB"),
    TIMEOUT_MUST_BE_GREATER_THAN_ZERO(
            "IJOB-BIZ-0304",
            "error.business.timeout-must-be-greater-than-zero",
            "Timeout must be greater than zero"),
    TASK_DESCRIPTION_MUST_NOT_EXCEED_500_CHARACTERS(
            "IJOB-BIZ-0285",
            "error.business.task-description-must-not-exceed-500-characters",
            "Task description must not exceed 500 characters"),
    VALUE_MUST_NOT_BE_NEGATIVE(
            "IJOB-BIZ-0002",
            "error.business.value-must-not-be-negative",
            "{0} must not be negative"),
    PLUGIN_IS_DISABLED_VALUE(
            "IJOB-BIZ-0163", "error.business.plugin-is-disabled-value", "Plugin is disabled: {0}"),
    PLUGIN_CONFIGURATION_IS_INVALID(
            "IJOB-BIZ-0160",
            "error.business.plugin-configuration-is-invalid",
            "Plugin configuration is invalid"),
    PLUGIN_RESOURCE_REFERENCE_RESOLUTION_FAILED(
            "IJOB-BIZ-0164",
            "error.business.plugin-resource-reference-resolution-failed",
            "Plugin resource reference resolution failed"),
    PLUGIN_RESOURCE_REFERENCE_VALIDATION_FAILED(
            "IJOB-BIZ-0165",
            "error.business.plugin-resource-reference-validation-failed",
            "Plugin resource reference validation failed"),
    A_RESOURCE_REFERENCED_BY_THE_PLUGIN_DOES_NOT_EXIST(
            "IJOB-BIZ-0008",
            "error.business.a-resource-referenced-by-the-plugin-does-not-exist",
            "A resource referenced by the plugin does not exist"),
    TASK_RETIREMENT_TRANSITION_FAILED(
            "IJOB-BIZ-0299",
            "error.business.task-retirement-transition-failed",
            "Task retirement transition failed"),
    TASK_ID_IS_REQUIRED(
            "IJOB-BIZ-0289", "error.business.task-id-is-required", "Task ID is required"),
    TASK_ID_MUST_BE_DISTINCT_AND_LOCKED_IN_ASCENDING_ORDER(
            "IJOB-BIZ-0290",
            "error.business.task-id-must-be-distinct-and-locked-in-ascending-order",
            "Task ID must be distinct and locked in ascending order"),
    RETRY_POLICY_INVALID(
            "IJOB-BIZ-0309",
            "error.business.retry-policy-invalid",
            "Retries must be 0–{0}, interval 0–{1} seconds, and total retry delay at most {2}"
                    + " seconds"),
    SSH_HOST_KEY_INVALID(
            "IJOB-BIZ-0310",
            "error.business.ssh-host-key-invalid",
            "SSH host key verification configuration is invalid"),
    SSH_HOST_KEY_REQUIRED(
            "IJOB-BIZ-0311",
            "error.business.ssh-host-key-required",
            "Confirm the SSH host identity before saving"),
    SSH_HOST_KEY_MISMATCH(
            "IJOB-BIZ-0312",
            "error.business.ssh-host-key-mismatch",
            "SSH host identity does not match the trusted key. Open the resource and verify its"
                    + " fingerprint before trusting a replacement."),
    SSH_HOST_KEY_PROBE_FAILED(
            "IJOB-BIZ-0313",
            "error.business.ssh-host-key-probe-failed",
            "Unable to obtain the SSH host fingerprint. Check the host, port and network"
                    + " connection."),
    EXECUTION_GROUPS_ARE_INVALID(
            "IJOB-BIZ-0308",
            "error.business.execution-groups-are-invalid",
            "Execution groups are invalid: {0}"),
    TASK_TIMEOUT("IJOB-RUNTIME-1001", "runtime.reason.task-timeout", "Task timeout"),
    JOB_TIMEOUT("IJOB-RUNTIME-1002", "runtime.reason.job-timeout", "Job timeout"),
    TASK_RESULT_MISSING(
            "IJOB-RUNTIME-1003",
            "runtime.reason.task-result-missing",
            "Task returned no valid result"),
    TASK_RESULT_INVALID(
            "IJOB-RUNTIME-1004",
            "runtime.reason.task-result-invalid",
            "Task returned an invalid terminal status"),
    DAG_FAILED(
            "IJOB-RUNTIME-1005",
            "runtime.reason.dag-failed",
            "Job DAG contains failed or timed out node"),
    TASK_FAILED("IJOB-RUNTIME-1006", "runtime.reason.task-failed", "Task execution failed"),
    RUNNER_UNAVAILABLE(
            "IJOB-RUNTIME-1007", "runtime.reason.runner-unavailable", "Task runner is unavailable"),
    NODE_INCOMPLETE(
            "IJOB-RUNTIME-1008",
            "runtime.reason.node-incomplete",
            "Node execution did not reach a terminal state"),
    NODE_FAILED("IJOB-RUNTIME-1009", "runtime.reason.node-failed", "Node execution failed"),
    JOB_RETRY_FAILED(
            "IJOB-RUNTIME-1010", "runtime.reason.job-retry-failed", "Job retry execution failed"),
    JOB_RETRY_CAPACITY(
            "IJOB-RUNTIME-1011",
            "runtime.reason.job-retry-capacity",
            "Job retry rejected by capacity limit"),
    EVENT_CONTINUATION_FAILED(
            "IJOB-RUNTIME-1012",
            "runtime.reason.event-continuation-failed",
            "Event continuation failed"),
    APPROVAL_REJECTED("IJOB-RUNTIME-1013", "runtime.reason.approval-rejected", "Approval rejected"),
    EVENT_TIMEOUT("IJOB-RUNTIME-1014", "runtime.reason.event-timeout", "Event wait timed out"),
    EVENT_TARGET_UNAVAILABLE(
            "IJOB-RUNTIME-1015",
            "runtime.reason.event-target-unavailable",
            "Event target is retired, disabled, unpublished or missing"),
    EXECUTION_FAILED("IJOB-RUNTIME-1016", "runtime.reason.execution-failed", "Execution failed"),
    INSTANCE_CREATION_FAILED(
            "IJOB-RUNTIME-1017",
            "runtime.reason.instance-creation-failed",
            "Execution failed before instance creation"),
    INSTANCE_MISSING(
            "IJOB-RUNTIME-1018",
            "runtime.reason.instance-missing",
            "Execution instance is missing"),
    EXECUTION_INCOMPLETE(
            "IJOB-RUNTIME-1019",
            "runtime.reason.execution-incomplete",
            "Execution did not reach a terminal state"),
    TRIGGER_CAPACITY(
            "IJOB-RUNTIME-1020",
            "runtime.reason.trigger-capacity",
            "Trigger execution rejected by capacity limit"),
    RUNTIME_DEADLINE(
            "IJOB-RUNTIME-1021", "runtime.reason.runtime-deadline", "Runtime deadline exceeded"),
    RUNTIME_ORPHANED(
            "IJOB-RUNTIME-1022",
            "runtime.reason.runtime-orphaned",
            "Execution abandoned after work item exhaustion"),
    QUEUE_DELAY("IJOB-RUNTIME-1023", "runtime.reason.queue-delay", "Queue delay limit exceeded");

    private final String errorCode;
    private final String messageKey;
    private final String defaultMessage;

    CoreMessage(String errorCode, String messageKey, String defaultMessage) {
        this.errorCode = errorCode;
        this.messageKey = messageKey;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String errorCode() {
        return errorCode;
    }

    @Override
    public String messageKey() {
        return messageKey;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
