package com.feijimiao.xianyuassistant.controller.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class NewApiPageDTO<T> {

    private List<T> items;

    private Integer total;

    private Integer page;

    @SerializedName("page_size")
    private Integer pageSize;
}
