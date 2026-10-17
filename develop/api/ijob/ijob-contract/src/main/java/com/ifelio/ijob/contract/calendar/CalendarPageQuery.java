package com.ifelio.ijob.contract.calendar;

import com.ifelio.ijob.contract.common.BasePageQuery;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CalendarPageQuery extends BasePageQuery {
    private String name;
}
