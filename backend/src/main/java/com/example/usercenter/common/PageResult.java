package com.example.usercenter.common;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 统一分页返回。
 */
@Data
@Accessors(chain = true)
public class PageResult<T> {

    private List<T> list;
    private long total;
    private int page;
    private int size;

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        return new PageResult<T>()
                .setList(list)
                .setTotal(total)
                .setPage(page)
                .setSize(size);
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return of(List.of(), 0L, page, size);
    }
}
