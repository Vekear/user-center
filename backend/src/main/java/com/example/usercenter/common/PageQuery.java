package com.example.usercenter.common;

import lombok.Data;

/**
 * 分页入参基类：页码与每页条数在这里统一收敛，任何接口都不会拿到越界值。
 */
@Data
public class PageQuery {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 100;

    private Integer page;
    private Integer size;

    public int normalizedPage() {
        if (page == null || page < 1) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    public int normalizedSize() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /** MyBatis-Plus 的 offset，避免业务层重复算。 */
    public long offset() {
        return (long) (normalizedPage() - 1) * normalizedSize();
    }
}
