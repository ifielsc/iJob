// Copyright (c) 2026 ifielsc
// SPDX-License-Identifier: MIT

package com.ifielsc.ijob.contract.parameter;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Editor metadata only. Scheduled execution never reloads or revalidates a live template. */
@Getter
@Setter
public class ParameterForm {
    private Long templateId;
    private List<ParameterField> fields;
}
