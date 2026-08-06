package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

@Data
public class NewApiRedemptionRespDTO<T> {

    private boolean success;

    private String message;

    private T data;
}
