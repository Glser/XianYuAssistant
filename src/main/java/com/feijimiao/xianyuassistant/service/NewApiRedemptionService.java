package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.controller.dto.NewApiRedemptionGenerateReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.NewApiRedemptionImportRespDTO;

public interface NewApiRedemptionService {

    ResultObject<NewApiRedemptionImportRespDTO> generateAndImport(NewApiRedemptionGenerateReqDTO reqDTO);

    ResultObject<Void> deleteRedemptionAndKami(Long kamiItemId);

    ResultObject<Void> deleteConfigAndRedemptions(Long kamiConfigId);
}
