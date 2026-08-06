package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.controller.dto.NewApiRedemptionGenerateReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.NewApiRedemptionImportRespDTO;
import com.feijimiao.xianyuassistant.controller.dto.NewApiPageDTO;
import com.feijimiao.xianyuassistant.controller.dto.NewApiRedemptionDTO;
import com.feijimiao.xianyuassistant.controller.dto.NewApiRedemptionReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.NewApiRedemptionRespDTO;
import com.feijimiao.xianyuassistant.entity.XianyuKamiItem;
import com.feijimiao.xianyuassistant.service.KamiConfigService;
import com.feijimiao.xianyuassistant.service.NewApiRedemptionService;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class NewApiRedemptionServiceImpl implements NewApiRedemptionService {

    private static final String BASE_URL = "https://newapi.3-24-221-206.sslip.io";
    private static final String ACCESS_TOKEN = "s+aFTfLSTlGt1YfJirQLvCQ4p4u6";
    private static final BigDecimal QUOTA_PER_CNY = BigDecimal.valueOf(500_000L);
    private static final long NEVER_EXPIRES = 0L;
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final Type RESPONSE_TYPE = new TypeToken<NewApiRedemptionRespDTO<List<String>>>() {
    }.getType();
    private static final Type PAGE_RESPONSE_TYPE = new TypeToken<NewApiRedemptionRespDTO<NewApiPageDTO<NewApiRedemptionDTO>>>() {
    }.getType();
    private static final Type ACTION_RESPONSE_TYPE = new TypeToken<NewApiRedemptionRespDTO<Object>>() {
    }.getType();

    private final String redemptionUrl;
    private final OkHttpClient httpClient;
    private final KamiConfigService kamiConfigService;
    private final Gson gson = new Gson();

    @Autowired
    public NewApiRedemptionServiceImpl(KamiConfigService kamiConfigService) {
        this(BASE_URL, new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(10))
                .build(), kamiConfigService);
    }

    NewApiRedemptionServiceImpl(String baseUrl, OkHttpClient httpClient, KamiConfigService kamiConfigService) {
        String normalizedBaseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        this.redemptionUrl = normalizedBaseUrl + "/api/redemption/";
        this.httpClient = httpClient;
        this.kamiConfigService = kamiConfigService;
    }

    @Override
    public ResultObject<NewApiRedemptionImportRespDTO> generateAndImport(NewApiRedemptionGenerateReqDTO reqDTO) {
        if (kamiConfigService.getConfig(reqDTO.getKamiConfigId()) == null) {
            return ResultObject.validateFailed("卡密配置不存在");
        }

        int quota;
        try {
            quota = reqDTO.getAmountCny()
                    .multiply(QUOTA_PER_CNY)
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValueExact();
        } catch (ArithmeticException e) {
            return ResultObject.validateFailed("人民币额度超出支持范围");
        }

        NewApiRedemptionReqDTO newApiReqDTO = new NewApiRedemptionReqDTO();
        newApiReqDTO.setName(reqDTO.getName().trim());
        newApiReqDTO.setQuota(quota);
        newApiReqDTO.setCount(reqDTO.getCount());
        newApiReqDTO.setExpiredTime(NEVER_EXPIRES);

        RequestBody requestBody = RequestBody.create(gson.toJson(newApiReqDTO), JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(redemptionUrl)
                .header("Authorization", "Bearer " + ACCESS_TOKEN)
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            String responseJson = body == null ? "" : body.string();

            if (!response.isSuccessful()) {
                log.warn("调用new-api生成兑换码失败: status={}", response.code());
                return ResultObject.failed(502, "调用new-api失败，HTTP状态码: " + response.code());
            }

            NewApiRedemptionRespDTO<List<String>> newApiResponse = gson.fromJson(responseJson, RESPONSE_TYPE);
            if (newApiResponse == null) {
                log.warn("new-api生成兑换码响应为空");
                return ResultObject.failed(502, "new-api响应为空");
            }

            List<String> generatedCodes = newApiResponse.getData() == null
                    ? Collections.emptyList()
                    : newApiResponse.getData();

            Map<String, Long> generatedCodeIds = new LinkedHashMap<>();
            for (String generatedCode : generatedCodes) {
                generatedCodeIds.put(generatedCode, null);
            }
            try {
                generatedCodeIds.putAll(resolveRedemptionIds(reqDTO.getName().trim(), generatedCodes));
            } catch (IOException | JsonSyntaxException e) {
                log.warn("查询new-api兑换码ID失败，删除时将通过key重新查询: {}", e.getMessage());
            }

            NewApiRedemptionImportRespDTO importRespDTO = new NewApiRedemptionImportRespDTO();
            importRespDTO.setKamiConfigId(reqDTO.getKamiConfigId());
            importRespDTO.setAmountCny(reqDTO.getAmountCny());
            importRespDTO.setQuota(quota);
            importRespDTO.setExpiredTime(NEVER_EXPIRES);
            importRespDTO.setGeneratedCount(generatedCodes.size());
            importRespDTO.setImportedCount(0);
            importRespDTO.setDuplicateCount(0);
            importRespDTO.setCodes(generatedCodes);

            try {
                int importedCount = kamiConfigService.importGeneratedKamiItems(
                        reqDTO.getKamiConfigId(), generatedCodeIds);
                importRespDTO.setImportedCount(importedCount);
                importRespDTO.setDuplicateCount(generatedCodes.size() - importedCount);
            } catch (RuntimeException e) {
                log.error("new-api兑换码已生成但写入本地卡密仓库失败: kamiConfigId={}",
                        reqDTO.getKamiConfigId(), e);
                return new ResultObject<>(500, "兑换码已生成，但写入卡密仓库失败", importRespDTO);
            }

            if (!newApiResponse.isSuccess()) {
                String message = newApiResponse.getMessage() == null || newApiResponse.getMessage().isBlank()
                        ? "new-api生成兑换码失败"
                        : newApiResponse.getMessage();
                return new ResultObject<>(502, message, importRespDTO);
            }

            return ResultObject.success(importRespDTO,
                    "成功生成" + generatedCodes.size() + "个兑换码，入库" + importRespDTO.getImportedCount() + "个");
        } catch (JsonSyntaxException e) {
            log.error("解析new-api生成兑换码响应失败", e);
            return ResultObject.failed(502, "new-api响应格式错误");
        } catch (IOException e) {
            log.error("调用new-api生成兑换码接口失败: url={}", redemptionUrl, e);
            return ResultObject.failed(502, "无法连接new-api服务");
        }
    }

    @Override
    public ResultObject<Void> deleteRedemptionAndKami(Long kamiItemId) {
        XianyuKamiItem kamiItem = kamiConfigService.getKamiItem(kamiItemId);
        if (kamiItem == null) {
            return ResultObject.failed("卡密不存在");
        }

        boolean newApiManaged = Integer.valueOf(1).equals(kamiItem.getNewApiManaged()) ||
                kamiItem.getNewApiRedemptionId() != null;
        if (!newApiManaged) {
            return kamiConfigService.deleteKamiItem(kamiItemId);
        }

        try {
            Long redemptionId = kamiItem.getNewApiRedemptionId();
            if (redemptionId == null) {
                redemptionId = findRedemptionIdByKey(kamiItem.getKamiContent());
            }
            if (redemptionId != null) {
                deleteRedemption(redemptionId);
            }
        } catch (IOException | JsonSyntaxException e) {
            log.error("联动删除new-api兑换码失败: kamiItemId={}", kamiItemId, e);
            return ResultObject.failed(502, "new-api兑换码删除失败，本地卡密未删除");
        }

        return kamiConfigService.deleteKamiItem(kamiItemId);
    }

    @Override
    public ResultObject<Void> deleteConfigAndRedemptions(Long kamiConfigId) {
        if (kamiConfigService.getConfig(kamiConfigId) == null) {
            return ResultObject.failed("卡密配置不存在");
        }

        List<XianyuKamiItem> kamiItems = kamiConfigService.getKamiItems(kamiConfigId);
        List<XianyuKamiItem> managedItems = kamiItems.stream()
                .filter(item -> Integer.valueOf(1).equals(item.getNewApiManaged()) ||
                        item.getNewApiRedemptionId() != null)
                .toList();
        List<String> missingIdCodes = managedItems.stream()
                .filter(item -> item.getNewApiRedemptionId() == null)
                .map(XianyuKamiItem::getKamiContent)
                .toList();
        try {
            Map<String, Long> resolvedIds = resolveRedemptionIds(null, missingIdCodes);
            for (XianyuKamiItem kamiItem : managedItems) {
                Long redemptionId = kamiItem.getNewApiRedemptionId();
                if (redemptionId == null) {
                    redemptionId = resolvedIds.get(kamiItem.getKamiContent());
                }
                if (redemptionId != null) {
                    deleteRedemption(redemptionId);
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            log.error("联动删除new-api兑换码失败，保留本地卡密仓库: kamiConfigId={}", kamiConfigId, e);
            return ResultObject.failed(502, "new-api兑换码删除失败，本地卡密仓库未删除");
        }

        return kamiConfigService.deleteConfig(kamiConfigId);
    }

    private Map<String, Long> resolveRedemptionIds(String name, List<String> codes) throws IOException {
        Map<String, Long> resolvedIds = new LinkedHashMap<>();
        if (codes.isEmpty()) {
            return resolvedIds;
        }

        Set<String> pendingCodes = new HashSet<>(codes);
        int page = 1;
        while (!pendingCodes.isEmpty()) {
            NewApiPageDTO<NewApiRedemptionDTO> pageData = getRedemptionPage(name, page);
            List<NewApiRedemptionDTO> items = pageData.getItems() == null
                    ? Collections.emptyList()
                    : pageData.getItems();
            for (NewApiRedemptionDTO redemption : items) {
                if (pendingCodes.remove(redemption.getKey())) {
                    resolvedIds.put(redemption.getKey(), redemption.getId());
                }
            }

            int total = pageData.getTotal() == null ? items.size() : pageData.getTotal();
            if (page * 100 >= total || items.isEmpty()) {
                break;
            }
            page++;
        }
        return resolvedIds;
    }

    private Long findRedemptionIdByKey(String key) throws IOException {
        int page = 1;
        while (true) {
            NewApiPageDTO<NewApiRedemptionDTO> pageData = getRedemptionPage(null, page);
            List<NewApiRedemptionDTO> items = pageData.getItems() == null
                    ? Collections.emptyList()
                    : pageData.getItems();
            for (NewApiRedemptionDTO redemption : items) {
                if (key.equals(redemption.getKey())) {
                    return redemption.getId();
                }
            }

            int total = pageData.getTotal() == null ? items.size() : pageData.getTotal();
            if (page * 100 >= total || items.isEmpty()) {
                return null;
            }
            page++;
        }
    }

    private NewApiPageDTO<NewApiRedemptionDTO> getRedemptionPage(String keyword, int page) throws IOException {
        HttpUrl baseUrl = HttpUrl.get(keyword == null ? redemptionUrl : redemptionUrl + "search");
        HttpUrl.Builder urlBuilder = baseUrl.newBuilder()
                .addQueryParameter("p", String.valueOf(page))
                .addQueryParameter("page_size", "100");
        if (keyword != null) {
            urlBuilder.addQueryParameter("keyword", keyword);
        }

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .header("Authorization", "Bearer " + ACCESS_TOKEN)
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String responseJson = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("new-api查询兑换码失败，HTTP状态码: " + response.code());
            }
            NewApiRedemptionRespDTO<NewApiPageDTO<NewApiRedemptionDTO>> result =
                    gson.fromJson(responseJson, PAGE_RESPONSE_TYPE);
            if (result == null || !result.isSuccess() || result.getData() == null) {
                throw new IOException(result == null ? "new-api查询兑换码响应为空" : result.getMessage());
            }
            return result.getData();
        }
    }

    private void deleteRedemption(Long redemptionId) throws IOException {
        Request request = new Request.Builder()
                .url(redemptionUrl + redemptionId)
                .header("Authorization", "Bearer " + ACCESS_TOKEN)
                .delete()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String responseJson = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("new-api删除兑换码失败，HTTP状态码: " + response.code());
            }
            NewApiRedemptionRespDTO<Object> result = gson.fromJson(responseJson, ACTION_RESPONSE_TYPE);
            if (result == null || !result.isSuccess()) {
                if (result != null && result.getMessage() != null &&
                        result.getMessage().toLowerCase().contains("record not found")) {
                    return;
                }
                throw new IOException(result == null ? "new-api删除兑换码响应为空" : result.getMessage());
            }
        }
    }
}
