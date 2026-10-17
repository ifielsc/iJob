package com.ifelio.ijob.core.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.github.pagehelper.Page;

import org.junit.jupiter.api.Test;

import java.util.List;

class PageResultsTest {
    @Test
    void retainsDatabaseTotalInsteadOfCountingTheCurrentPage() {
        try (Page<String> page = new Page<>(3, 2)) {
            page.setTotal(11);
            page.addAll(List.of("fifth", "sixth"));
            var result = PageResults.from(page);
            assertSame(page, result.getList());
            assertEquals(11, result.getTotal());
            assertEquals(3, result.getPageNum());
            assertEquals(2, result.getPageSize());
            assertEquals(6, result.getPages());
        }
    }

    @Test
    void preservesPlainListAndEmptyPageSemantics() {
        var result = PageResults.from(List.of("one"));
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getPages());
        assertEquals(List.of("one"), result.getList());
        try (Page<String> page = new Page<>(1, 20)) {
            page.setTotal(0);
            var empty = PageResults.from(page);
            assertEquals(0, empty.getTotal());
            assertEquals(0, empty.getPages());
            assertEquals(20, empty.getPageSize());
        }
    }
}
