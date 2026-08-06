package com.feijimiao.xianyuassistant.controller.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class NewApiRedemptionGenerateReqDTO {

    @NotNull(message = "卡密配置ID不能为空")
    private Long kamiConfigId;

    @NotBlank(message = "兑换码名称不能为空")
    @Size(max = 20, message = "兑换码名称长度不能超过20个字符")
    private String name;

    @NotNull(message = "人民币额度不能为空")
    @DecimalMin(value = "0.01", message = "人民币额度不能小于0.01元")
    @DecimalMax(value = "4000", message = "人民币额度不能超过4000元")
    @Digits(integer = 4, fraction = 2, message = "人民币额度最多支持两位小数")
    private BigDecimal amountCny;

    @NotNull(message = "生成数量不能为空")
    @Min(value = 1, message = "生成数量不能小于1")
    @Max(value = 100, message = "生成数量不能超过100")
    private Integer count;
}
