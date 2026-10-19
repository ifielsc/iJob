package com.ifielsc.ijob.core.task.retirement;

import java.time.LocalDateTime;

public record TaskRetirementResult(
        Long taskId, LocalDateTime retiredTime, boolean alreadyRetired) {}
