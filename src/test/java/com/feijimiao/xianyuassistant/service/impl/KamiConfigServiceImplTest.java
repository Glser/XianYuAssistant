package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.entity.XianyuKamiConfig;
import com.feijimiao.xianyuassistant.entity.XianyuKamiItem;
import com.feijimiao.xianyuassistant.mapper.XianyuKamiConfigMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuKamiItemMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KamiConfigServiceImplTest {

    @Test
    void importGeneratedKamiItemsStoresRemoteIdAndSkipsDatabaseDuplicates() {
        XianyuKamiConfigMapper configMapper = mock(XianyuKamiConfigMapper.class);
        XianyuKamiItemMapper itemMapper = mock(XianyuKamiItemMapper.class);
        XianyuKamiConfig config = new XianyuKamiConfig();
        config.setId(9L);
        when(configMapper.selectById(9L)).thenReturn(config);
        when(itemMapper.countByConfigId(9L)).thenReturn(3);
        when(itemMapper.countByConfigIdAndContent(9L, "new-code")).thenReturn(0);
        when(itemMapper.countByConfigIdAndContent(9L, "existing-code")).thenReturn(1);

        KamiConfigServiceImpl service = new KamiConfigServiceImpl();
        ReflectionTestUtils.setField(service, "kamiConfigMapper", configMapper);
        ReflectionTestUtils.setField(service, "kamiItemMapper", itemMapper);

        Map<String, Long> generatedCodes = new LinkedHashMap<>();
        generatedCodes.put("new-code", 101L);
        generatedCodes.put("existing-code", 102L);
        generatedCodes.put(" ", null);
        int imported = service.importGeneratedKamiItems(9L, generatedCodes);

        assertEquals(1, imported);
        ArgumentCaptor<XianyuKamiItem> itemCaptor = ArgumentCaptor.forClass(XianyuKamiItem.class);
        verify(itemMapper, times(1)).insert(itemCaptor.capture());
        assertEquals("new-code", itemCaptor.getValue().getKamiContent());
        assertEquals(101L, itemCaptor.getValue().getNewApiRedemptionId());
        assertEquals(1, itemCaptor.getValue().getNewApiManaged());
        verify(itemMapper).bindNewApiRedemptionId(9L, "existing-code", 102L);
        verify(configMapper).updateById(config);
    }
}
