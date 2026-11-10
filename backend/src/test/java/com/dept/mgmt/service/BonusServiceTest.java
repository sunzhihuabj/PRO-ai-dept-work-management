package com.dept.mgmt.service;

import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.dto.BonusSaveDTO;
import com.dept.mgmt.entity.ProjectBonus;
import com.dept.mgmt.mapper.ProjectBonusMapper;
import com.dept.mgmt.mapper.ProjectBonusRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 奖金金额计算单元测试
 * 规则：monthly = base * coef；yearly = monthly * 3
 */
@ExtendWith(MockitoExtension.class)
class BonusServiceTest {

    @Mock
    private ProjectBonusMapper projectBonusMapper;
    @Mock
    private ProjectBonusRecordMapper projectBonusRecordMapper;
    @Mock
    private KpiService kpiService;
    @InjectMocks
    private BonusService bonusService;

    private BonusSaveDTO dto(BigDecimal base, BigDecimal coef) {
        BonusSaveDTO dto = new BonusSaveDTO();
        dto.setProjectId(1L);
        dto.setUserId(1L);
        dto.setBonusType(1);
        dto.setProjectPeriod(2);
        dto.setBaseAmount(base);
        dto.setCoefficient(coef);
        return dto;
    }

    @Test
    void amountsCalculatedOnCreate() {
        when(projectBonusMapper.selectCount(any())).thenReturn(0L);

        bonusService.create(dto(new BigDecimal("5000"), BigDecimal.ONE));

        ArgumentCaptor<ProjectBonus> captor = ArgumentCaptor.forClass(ProjectBonus.class);
        verify(projectBonusMapper).insert(captor.capture());
        assertEquals(new BigDecimal("5000.00"), captor.getValue().getMonthlyAmount());
        assertEquals(new BigDecimal("15000.00"), captor.getValue().getYearlyAmount());
    }

    @Test
    void kpiCoefficientAffectsAmounts() {
        when(projectBonusMapper.selectCount(any())).thenReturn(0L);

        bonusService.create(dto(new BigDecimal("5000"), new BigDecimal("1.2")));

        ArgumentCaptor<ProjectBonus> captor = ArgumentCaptor.forClass(ProjectBonus.class);
        verify(projectBonusMapper).insert(captor.capture());
        assertEquals(new BigDecimal("6000.00"), captor.getValue().getMonthlyAmount());
        assertEquals(new BigDecimal("18000.00"), captor.getValue().getYearlyAmount());
    }

    @Test
    void duplicateConfigRejected() {
        when(projectBonusMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> bonusService.create(dto(BigDecimal.ONE, BigDecimal.ONE)));
        assertEquals(409, ex.getCode());
    }
}
