package com.mendel.transactions.model;

import java.time.Instant;
import java.util.Optional;

public class Transaction {

    private final long id;
    private final double amount;
    private final String type;
    private final Long parentId;
    private final String path;
    private final Instant createdAt;

    private double accumulatedSum;
    private long version;
    private boolean propagated;

    private Transaction(Builder builder) {
        this.id             = builder.id;
        this.amount         = builder.amount;
        this.type           = builder.type;
        this.parentId       = builder.parentId;
        this.path           = builder.path;
        this.createdAt      = builder.createdAt;
        this.accumulatedSum = builder.accumulatedSum;
        this.version        = builder.version;
        this.propagated     = builder.propagated;
    }

    // --- Getters ---

    public long getId() {
        return id;
    }

    public double getAmount() {
        return amount;
    }

    public String getType() {
        return type;
    }

    public Optional<Long> getParentId() {
        return Optional.ofNullable(parentId);
    }

    public String getPath() {
        return path;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public double getAccumulatedSum() {
        return accumulatedSum;
    }

    public long getVersion() {
        return version;
    }

    public boolean isPropagated() {
        return propagated;
    }

    // --- Mutators (solo los campos que cambian post-creación) ---

    public void addToAccumulatedSum(double amount) {
        this.accumulatedSum += amount;
        this.version++;
    }

    public void markAsPropagated() {
        this.propagated = true;
    }

    // --- Builder ---

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private long id;
        private double amount;
        private String type;
        private Long parentId;
        private String path;
        private Instant createdAt = Instant.now();
        private double accumulatedSum;
        private long version = 0L;
        private boolean propagated = false;

        public Builder id(long id) {
            this.id = id;
            return this;
        }

        public Builder amount(double amount) {
            this.amount = amount;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder parentId(Long parentId) {
            this.parentId = parentId;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder accumulatedSum(double accumulatedSum) {
            this.accumulatedSum = accumulatedSum;
            return this;
        }

        public Builder version(long version) {
            this.version = version;
            return this;
        }

        public Builder propagated(boolean propagated) {
            this.propagated = propagated;
            return this;
        }

        public Transaction build() {
            return new Transaction(this);
        }
    }
}
