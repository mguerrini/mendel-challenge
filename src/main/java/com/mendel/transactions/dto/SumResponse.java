package com.mendel.transactions.dto;

public class SumResponse {

    private final double sum;

    public SumResponse(double sum) {
        this.sum = sum;
    }

    public double getSum() {
        return sum;
    }
}
