// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT
package com.ifielsc.ijob.contract.resource;

import java.util.List;

public final class FileBrowseDTO {
    private FileBrowseDTO() {}

    public record Root(String id, String path) {}

    public record Request(Long resourceId, ResourceUpsertRequest draft, String localRootId) {}

    public record Session(
            String id, String protocol, String name, String root, String node, int expiresIn) {}

    public record Entry(String name, boolean directory, long size, String modified) {}

    public record Page(String path, List<Entry> entries, int total, int page, int pageSize) {}
}
