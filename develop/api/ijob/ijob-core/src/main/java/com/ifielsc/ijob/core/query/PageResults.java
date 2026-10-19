package com.ifielsc.ijob.core.query;

import com.github.pagehelper.PageInfo;
import com.ifielsc.ijob.common.response.PageResult;

import java.util.List;

/** Application-side pagination adapter; the wire DTO has no paging-engine dependency. */
public final class PageResults {
    private PageResults() {}

    public static <T> PageResult<T> from(List<T> rows) {
        PageInfo<T> page = new PageInfo<>(rows);
        PageResult<T> result = new PageResult<>();
        result.setList(page.getList());
        result.setTotal(page.getTotal());
        result.setPageNum(page.getPageNum());
        result.setPageSize(page.getPageSize());
        result.setPages(page.getPages());
        return result;
    }
}
