package com.mendel.transactions.exception;

public class ParentNotFoundException extends RuntimeException {

    private final long parentId;

    public ParentNotFoundException(long parentId) {
        super("Parent transaction with id " + parentId + " not found");
        this.parentId = parentId;
    }

    public long getParentId() {
        return parentId;
    }
}
