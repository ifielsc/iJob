package com.ifielsc.ijob.contract.auth;

/** Safe display projection; no internal user IDs or authentication material. */
public record ApproverResolutionDTO(
        String loginId, String username, String displayName, String status) {}
