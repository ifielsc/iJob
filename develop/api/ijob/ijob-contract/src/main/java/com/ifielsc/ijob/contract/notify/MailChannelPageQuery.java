package com.ifielsc.ijob.contract.notify;

import com.ifielsc.ijob.contract.common.BasePageQuery;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MailChannelPageQuery extends BasePageQuery {
    private String name;

    public String getNamePattern() {
        return "%" + (name == null ? "" : name.trim()) + "%";
    }

    private Boolean enabled;
}
