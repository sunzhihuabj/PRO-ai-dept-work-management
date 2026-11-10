package com.dept.mgmt.mdm.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.common.excel.ExcelImportReader;
import com.dept.mgmt.common.excel.ImportErrorRow;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.mapper.MaterialMapper;
import com.dept.mgmt.mdm.material.dto.MaterialImportResult;
import com.dept.mgmt.mdm.material.dto.MaterialImportRow;
import com.dept.mgmt.mdm.material.entity.Material;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 物料主数据服务（导入导出示例场景，T-103 主数据首块）
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialService {

    /** 导入文件大小上限 10MB（防超大文件拖垮内存） */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;

    /** 字段值域（对齐 T-003 模板下拉口径） */
    private static final Set<String> DEDUP_VALUES = Set.of("未查重", "疑似重复", "已归并", "唯一");
    private static final Set<String> VERIFY_VALUES = Set.of("待核验", "通过", "不通过");

    private final MaterialMapper materialMapper;

    /**
     * 物料分页查询
     */
    public PageResult<Material> list(int pageNum, int pageSize, String materialName, String category) {
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(materialName), Material::getMaterialName, materialName);
        wrapper.eq(StringUtils.hasText(category), Material::getCategory, category);
        wrapper.orderByAsc(Material::getMaterialCode);
        return PageResult.of(materialMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    /**
     * 批量导入（先全量校验，任意错误行则整批拒绝不落库）
     */
    @Transactional(rollbackFor = Exception.class)
    public MaterialImportResult importMaterials(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "导入文件为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(400, "导入文件超过 10MB 上限，请拆分后导入");
        }
        List<ExcelImportReader.RowRecord<MaterialImportRow>> rows;
        try {
            rows = ExcelImportReader.read(file.getInputStream(), MaterialImportRow.class);
        } catch (IOException e) {
            throw new BusinessException(400, "文件读取失败: " + e.getMessage());
        }
        if (rows.isEmpty()) {
            throw new BusinessException(400, "导入文件无数据行");
        }

        // ===== 全量校验（任何错误行都收集，一次返回完整清单） =====
        List<ImportErrorRow> errors = new ArrayList<>();
        Set<String> seenInFile = new HashSet<>();
        for (ExcelImportReader.RowRecord<MaterialImportRow> rr : rows) {
            MaterialImportRow row = rr.getData();
            if (!StringUtils.hasText(row.getMaterialCode())) {
                errors.add(new ImportErrorRow(rr.getRowIndex(), "物料编码", "必填"));
            } else if (row.getMaterialCode().length() > 30) {
                errors.add(new ImportErrorRow(rr.getRowIndex(), "物料编码", "长度不能超过30字符"));
            } else if (!seenInFile.add(row.getMaterialCode())) {
                errors.add(new ImportErrorRow(rr.getRowIndex(), "物料编码", "文件内重复: " + row.getMaterialCode()));
            }
            if (!StringUtils.hasText(row.getMaterialName())) {
                errors.add(new ImportErrorRow(rr.getRowIndex(), "物料名称", "必填"));
            } else if (row.getMaterialName().length() > 100) {
                errors.add(new ImportErrorRow(rr.getRowIndex(), "物料名称", "长度不能超过100字符"));
            }
            checkEnum(rr, errors, row.getDedupStatus(), "查重状态", DEDUP_VALUES);
            checkEnum(rr, errors, row.getVerifyStatus(), "一码一物核验", VERIFY_VALUES);
        }

        // 数据库查重（仅编码合法的行）
        List<String> codes = rows.stream()
                .map(r -> r.getData().getMaterialCode())
                .filter(StringUtils::hasText)
                .toList();
        if (!codes.isEmpty()) {
            Set<String> existing = materialMapper.selectList(new LambdaQueryWrapper<Material>()
                            .in(Material::getMaterialCode, codes))
                    .stream().map(Material::getMaterialCode).collect(Collectors.toSet());
            for (ExcelImportReader.RowRecord<MaterialImportRow> rr : rows) {
                String code = rr.getData().getMaterialCode();
                if (StringUtils.hasText(code) && existing.contains(code)) {
                    errors.add(new ImportErrorRow(rr.getRowIndex(), "物料编码", "系统中已存在: " + code));
                }
            }
        }

        if (!errors.isEmpty()) {
            // 整批拒绝：事务内未执行任何 insert，安全回空
            return new MaterialImportResult(rows.size(), 0, errors);
        }

        // ===== 全部通过才入库 =====
        for (ExcelImportReader.RowRecord<MaterialImportRow> rr : rows) {
            materialMapper.insert(toEntity(rr.getData()));
        }
        log.info("物料批量导入成功: {} 行", rows.size());
        return new MaterialImportResult(rows.size(), rows.size(), List.of());
    }

    /**
     * 导出查询结果
     */
    public List<MaterialImportRow> exportRows(String materialName, String category) {
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(materialName), Material::getMaterialName, materialName);
        wrapper.eq(StringUtils.hasText(category), Material::getCategory, category);
        wrapper.orderByAsc(Material::getMaterialCode);
        return materialMapper.selectList(wrapper).stream()
                .map(this::toRow)
                .collect(Collectors.toList());
    }

    /**
     * 模板示例行（供模板下载，表头由 @ExcelProperty 生成）
     */
    public List<MaterialImportRow> templateRows() {
        MaterialImportRow sample = new MaterialImportRow();
        sample.setMaterialCode("WL-000001");
        sample.setMaterialName("示例物料（填写时删除本行）");
        sample.setSpecModel("示例规格");
        sample.setUnit("台");
        sample.setCategory("示例类别");
        sample.setOldCode("T+旧码-A1001");
        sample.setDedupStatus("唯一");
        sample.setVerifyStatus("通过");
        sample.setRemark("下载本模板填写后原样导入");
        return List.of(sample);
    }

    // ===== 私有辅助 =====

    private void checkEnum(ExcelImportReader.RowRecord<MaterialImportRow> rr,
                           List<ImportErrorRow> errors, String value, String column, Set<String> allowed) {
        if (StringUtils.hasText(value) && !allowed.contains(value)) {
            errors.add(new ImportErrorRow(rr.getRowIndex(), column, "仅允许: " + String.join("/", allowed)));
        }
    }

    private Material toEntity(MaterialImportRow row) {
        Material m = new Material();
        m.setMaterialCode(row.getMaterialCode().trim());
        m.setMaterialName(row.getMaterialName().trim());
        m.setSpecModel(trimOrNull(row.getSpecModel()));
        m.setUnit(trimOrNull(row.getUnit()));
        m.setCategory(trimOrNull(row.getCategory()));
        m.setOldCode(trimOrNull(row.getOldCode()));
        m.setAlias1(trimOrNull(row.getAlias1()));
        m.setAlias2(trimOrNull(row.getAlias2()));
        m.setDedupStatus(StringUtils.hasText(row.getDedupStatus()) ? row.getDedupStatus() : "未查重");
        m.setVerifyStatus(StringUtils.hasText(row.getVerifyStatus()) ? row.getVerifyStatus() : "待核验");
        m.setRemark(trimOrNull(row.getRemark()));
        return m;
    }

    private MaterialImportRow toRow(Material m) {
        MaterialImportRow row = new MaterialImportRow();
        row.setMaterialCode(m.getMaterialCode());
        row.setMaterialName(m.getMaterialName());
        row.setSpecModel(m.getSpecModel());
        row.setUnit(m.getUnit());
        row.setCategory(m.getCategory());
        row.setOldCode(m.getOldCode());
        row.setAlias1(m.getAlias1());
        row.setAlias2(m.getAlias2());
        row.setDedupStatus(m.getDedupStatus());
        row.setVerifyStatus(m.getVerifyStatus());
        row.setRemark(m.getRemark());
        return row;
    }

    private String trimOrNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
