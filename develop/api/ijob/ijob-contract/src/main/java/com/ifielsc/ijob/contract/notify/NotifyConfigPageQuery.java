package com.ifielsc.ijob.contract.notify;

import com.ifielsc.ijob.contract.common.BasePageQuery;
import com.ifielsc.ijob.contract.enums.NotifyType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class NotifyConfigPageQuery extends BasePageQuery {
    private String name;
    private NotifyType type;
    private Boolean enabled;
}
