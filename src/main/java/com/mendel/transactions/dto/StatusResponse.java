package com.mendel.transactions.dto;

public class StatusResponse {

    private final String status;

    public StatusResponse(String status) {
        this.status = status;
    }

    public static StatusResponse ok() {
        return new StatusResponse("ok");
    }

    public String getStatus() {
        return status;
    }
}
