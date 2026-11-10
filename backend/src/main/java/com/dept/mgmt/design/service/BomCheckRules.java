package com.dept.mgmt.design.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.design.entity.DesignBomItem;
import com.dept.mgmt.mdm.material.entity.Material;
import com.dept.mgmt.mapper.MaterialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AGT-11 BOM 校验规则引擎（T-116，13 条确定性规则）
 * 规划验收口径"缺陷检出≥90%"要求校验结果可复现——全部为确定性规则，不经 LLM；
 * 13 条规则为 V2 详细规则文档缺失下的自主制定版本（已在任务汇报中披露，业务方确认后可增删）：
 * R01 编码存在性 / R02 编码格式 / R03 名称与主数据一致 / R04 数量有效 / R05 单位必填
 * R06 规格必填 / R07 重复行 / R08 关键件覆盖 / R09 螺栓等级 / R10 钢板材质
 * R11 防腐要求存在 / R12 单行数量上限 / R13 备注长度
 *
 * @author dept-mgmt
 */
@Component
@RequiredArgsConstructor
public class BomCheckRules {

    /** 规则总数 */
    public static final int RULE_COUNT = 13;

    /** 单行数量上限（超出提示人工复核） */
    private static final BigDecimal MAX_QTY = new BigDecimal("500");

    /** 塔筒类方案的关键件类别（R08 关键件覆盖） */
    private static final List<String> KEY_CATEGORIES = List.of("塔筒", "法兰", "钢板", "防腐");

    /** 允许的钢板材质（R10） */
    private static final List<String> ALLOWED_STEEL = List.of("Q355B", "Q345B", "Q420B", "Q460C");

    private final MaterialMapper materialMapper;

    /** 单条违规 */
    public record Violation(String rule, int itemIndex, String materialCode, String message) {
    }

    /** 校验结果 */
    public record CheckResult(boolean passed, int totalItems, List<Violation> violations) {
    }

    /**
     * 执行 13 条规则校验
     */
    public CheckResult check(List<DesignBomItem> items) {
        List<Violation> violations = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            violations.add(new Violation("R00", 0, "", "BOM 明细为空，无法校验"));
            return new CheckResult(false, 0, violations);
        }
        // 预加载物料主数据（一次查询，key=编码）
        Map<String, Material> master = new HashMap<>();
        for (Material m : materialMapper.selectList(new LambdaQueryWrapper<Material>())) {
            master.put(m.getMaterialCode(), m);
        }
        Set<String> seenCodes = new HashSet<>();
        Set<String> nameSet = new HashSet<>();

        for (int i = 0; i < items.size(); i++) {
            DesignBomItem item = items.get(i);
            String code = item.getMaterialCode() == null ? "" : item.getMaterialCode().trim();
            String name = item.getMaterialName() == null ? "" : item.getMaterialName().trim();
            Material mdm = master.get(code);

            // R01 编码存在性
            if (mdm == null) {
                violations.add(new Violation("R01", i, code, "物料编码在主数据中不存在"));
            }
            // R02 编码格式
            if (!code.matches("WL-\\d{6}")) {
                violations.add(new Violation("R02", i, code, "编码格式非法（应为 WL-XXXXXX）"));
            }
            // R03 名称一致
            if (mdm != null && !mdm.getMaterialName().equals(name)) {
                violations.add(new Violation("R03", i, code,
                        "名称与主数据不一致（主数据: " + mdm.getMaterialName() + "）"));
            }
            // R04 数量有效
            if (item.getQty() == null || item.getQty().compareTo(BigDecimal.ZERO) <= 0) {
                violations.add(new Violation("R04", i, code, "数量必须大于 0"));
            }
            // R05 单位必填
            if (item.getUnit() == null || item.getUnit().isBlank()) {
                violations.add(new Violation("R05", i, code, "计量单位必填"));
            } else if (mdm != null && mdm.getUnit() != null && !mdm.getUnit().equals(item.getUnit())) {
                violations.add(new Violation("R05", i, code,
                        "单位与主数据不一致（主数据: " + mdm.getUnit() + "）"));
            }
            // R06 规格必填
            if (item.getSpecModel() == null || item.getSpecModel().isBlank()) {
                violations.add(new Violation("R06", i, code, "规格型号必填"));
            }
            // R07 重复行
            if (!seenCodes.add(code)) {
                violations.add(new Violation("R07", i, code, "物料编码重复出现"));
            }
            // R09 螺栓等级
            if (name.contains("螺栓") && item.getSpecModel() != null
                    && !item.getSpecModel().contains("8.8") && !item.getSpecModel().contains("10.9")) {
                violations.add(new Violation("R09", i, code, "螺栓等级缺失（规格须含 8.8 或 10.9 级）"));
            }
            // R10 钢板材质
            if (name.contains("钢板") && item.getSpecModel() != null
                    && ALLOWED_STEEL.stream().noneMatch(item.getSpecModel()::contains)) {
                violations.add(new Violation("R10", i, code, "钢板材质不在允许清单（Q355B/Q345B/Q420B/Q460C）"));
            }
            // R12 单行数量上限
            if (item.getQty() != null && item.getQty().compareTo(MAX_QTY) > 0) {
                violations.add(new Violation("R12", i, code, "单行数量超过 " + MAX_QTY.intValue() + "，请人工复核"));
            }
            // R13 备注长度
            if (item.getRemark() != null && item.getRemark().length() > 100) {
                violations.add(new Violation("R13", i, code, "备注超过 100 字"));
            }
            nameSet.add(name);
        }
        // R08 关键件覆盖（方案级：任一关键类别均无对应物料）
        for (String category : KEY_CATEGORIES) {
            boolean covered = items.stream().anyMatch(it -> it.getMaterialName() != null
                    && (it.getMaterialName().contains(category)));
            if (!covered) {
                violations.add(new Violation("R08", -1, "", "关键件类别缺失: " + category));
            }
        }
        // R11 防腐要求存在（方案级）
        boolean anticorrosion = nameSet.stream().anyMatch(n -> n.contains("喷锌") || n.contains("油漆") || n.contains("防腐"));
        if (!anticorrosion) {
            violations.add(new Violation("R11", -1, "", "BOM 缺少防腐类物料（喷锌/油漆）"));
        }
        return new CheckResult(violations.isEmpty(), items.size(), violations);
    }
}
