package com.ifelio.ijob.contract.runtime;

import java.util.List;

/** A bounded, replaceable view of the latest visible logs, ordered by log time and ID. */
public record InstanceLogSnapshotDTO(List<InstanceLogDTO> rows, boolean truncated) {}
