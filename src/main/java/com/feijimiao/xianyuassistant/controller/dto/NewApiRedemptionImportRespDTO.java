package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class NewApiRedemptionImportRespDTO {

    private Long kamiConfigId;

    private BigDecimal amountCny;

    private Integer quota;

    private Long expiredTime;

    private Integer generatedCount;

    private Integer importedCount;

    private Integer duplicateCount;

    private List<String> codes;
}
