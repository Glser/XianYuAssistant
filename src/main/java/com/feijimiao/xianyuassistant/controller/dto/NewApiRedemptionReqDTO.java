package com.feijimiao.xianyuassistant.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NewApiRedemptionReqDTO {

    @NotBlank(message = "兑换码名称不能为空")
    @Size(max = 20, message = "兑换码名称长度不能超过20个字符")
    private String name;

    @NotNull(message = "兑换额度不能为空")
    @Min(value = 0, message = "兑换额度不能小于0")
    private Integer quota;

    @NotNull(message = "生成数量不能为空")
    @Min(value = 1, message = "生成数量不能小于1")
    @Max(value = 100, message = "生成数量不能超过100")
    private Integer count;

    @NotNull(message = "过期时间不能为空")
    @Min(value = 0, message = "过期时间不能小于0")
    @JsonProperty("expired_time")
    @SerializedName("expired_time")
    private Long expiredTime;
}
