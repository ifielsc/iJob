# HTTP, parameters and response extraction

[简体中文](../../zh-CN/examples/api-and-parameters.md) · [Manual home](../README.md)

**Verification: tested in this round’s isolated environment within the scope below.**

Fields: [API calls](../user-guide/plugins/api-call.md).

## Prepare, configure and run

Provide a dedicated read-only endpoint returning `{"total":3,"amount":600}` from `GET /orders/summary`. Set its base URL/authentication in a service resource. Choose GET/path in API_CALL, extract total→`$.total` and amount→`$.amount`, and bound response size.

Create batchDate/limit parameters (required DATE, INTEGER default 3), then verify the execution input snapshot. Use each field's supported binding syntax, not template names as values. Check response, variables and business conditions. Exercise business rejection, missing fields and timeout separately. For writes, use idempotency and query remote outcomes before retrying timeouts.

## Scope verified this round

An isolated HTTP receiver verified parameters, extraction, business failures, missing fields, HTTP 503, request timeout and repeated-write idempotency. HTTP 503 is a nonretryable result even with retryTimes configured; rerun manually after correction. SOAP, OAuth2 and remote-stop endpoints remain separate.

## Finish

Retain inputs, definition versions, trigger/instance IDs, sanitized logs and target checks. Stop only processes/schedules owned by this case; other environments and historical evidence are not cleanup targets.
