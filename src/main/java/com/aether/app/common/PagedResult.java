package com.aether.app.common;

import java.util.List;

public class PagedResult<T> {

    private static final int DEFAULT_LIMIT = 50;

    private final List<T> items;
    private final int total;
    private final int limit;
    private final int offset;
    private final boolean hasNext;

    public PagedResult(List<T> items, int total, int limit, int offset) {
        this.items = items;
        this.total = total;
        this.limit = limit;
        this.offset = offset;
        this.hasNext = offset + limit < total;
    }

    public static int effectiveLimit(PageInput page) {
        if (page == null || page.getLimit() == null || page.getLimit() <= 0) {
            return DEFAULT_LIMIT;
        }
        return page.getLimit();
    }

    public static int effectiveOffset(PageInput page) {
        if (page == null || page.getOffset() == null || page.getOffset() < 0) {
            return 0;
        }
        return page.getOffset();
    }

    public List<T> getItems() {
        return items;
    }

    public int getTotal() {
        return total;
    }

    public int getLimit() {
        return limit;
    }

    public int getOffset() {
        return offset;
    }

    public boolean isHasNext() {
        return hasNext;
    }
}
