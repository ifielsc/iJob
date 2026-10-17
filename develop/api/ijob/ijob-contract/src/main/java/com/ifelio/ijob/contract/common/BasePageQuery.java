package com.ifelio.ijob.contract.common;

import lombok.Data;

@Data
public class BasePageQuery {
    private static final int MAX_PAGE_SIZE = 500;

    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String orderBy;
    private Boolean isAsc = false;

    public int getOffset() {
        return (getPageNo() - 1) * getPageSize();
    }

    public Integer getPageNo() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public Integer getPageSize() {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    public String getSafeOrderBy() {
        if (orderBy == null || orderBy.isEmpty()) {
            return null;
        }
        return orderBy.replaceAll("[^a-zA-Z0-9_]", "");
    }
}
