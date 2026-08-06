package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.controller.dto.NewApiRedemptionGenerateReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.NewApiRedemptionImportRespDTO;
import com.feijimiao.xianyuassistant.service.NewApiRedemptionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/new-api/redemption")
public class NewApiRedemptionController {

    private final NewApiRedemptionService newApiRedemptionService;

    public NewApiRedemptionController(NewApiRedemptionService newApiRedemptionService) {
        this.newApiRedemptionService = newApiRedemptionService;
    }

    @PostMapping("/generate")
    public ResultObject<NewApiRedemptionImportRespDTO> generateRedemptions(
            @Valid @RequestBody NewApiRedemptionGenerateReqDTO reqDTO) {
        return newApiRedemptionService.generateAndImport(reqDTO);
    }
}
