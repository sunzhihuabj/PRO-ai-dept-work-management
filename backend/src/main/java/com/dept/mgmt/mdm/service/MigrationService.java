package com.dept.mgmt.mdm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.excel.ExcelImportReader;
import com.dept.mgmt.common.excel.ImportErrorRow;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.entity.Project;
import com.dept.mgmt.mapper.CustomerMapper;
import com.dept.mgmt.mapper.ProjectMapper;
import com.dept.mgmt.mdm.customer.entity.Customer;
import com.dept.mgmt.mdm.dto.CustomerImportRow;
import com.dept.mgmt.mdm.dto.MigrationImportResult;
import com.dept.mgmt.mdm.dto.ProjectImportRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 数据迁移服务（T-213）：存量客户/项目档案批量导入 + 清洗校验报告
 * 清洗口径（与物料导入 T-103 一致）：先全量校验，存在任意错误行则整批拒绝不落库；
 * 校验维度：必填/长度/枚举值域/格式/文件内重复/库内查重/日期逻辑；
 * 项目导入客户联动：客户名在客户主体库不存在时自动建档（"一次录入多处引用"）
 * 物料迁移复用 MaterialService.importMaterials（T-103 已实现同一清洗口径）
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;
    private static final Map<String, Integer> PROJECT_TYPES = Map.of("整机厂", 1, "技改", 2, "研发", 3);
    /** 项目编号类型码（与 ProjectService.create 同口径：PRJ-{yyyy}-{type}-{seq:3}） */
    private static final String[] PROJECT_TYPE_CODES = {"", "ZJC", "JG", "YF"};
    private static final Set<String> CUSTOMER_LEVELS = Set.of("战略", "重点", "普通");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CustomerMapper customerMapper;
    private final ProjectMapper projectMapper;
    private final com.dept.mgmt.platform.code.service.CodeEngineService codeEngineService;

    /**
     * 存量客户主体导入（全量校验，整批拒绝）
     */
    @Transactional(rollbackFor = Exception.class)
    public MigrationImportResult importCustomers(MultipartFile file) {
        List<ExcelImportReader.RowRecord<CustomerImportRow>> rows = readAll(file, CustomerImportRow.class);
        List<ImportErrorRow> errors = new ArrayList<>();
        Set<String> seenInFile = new HashSet<>();
        for (ExcelImportReader.RowRecord<CustomerImportRow> rr : rows) {
            CustomerImportRow row = rr.getData();
            String name = StringUtils.trimWhitespace(nvl(row.getCustomerName()));
            if (!StringUtils.hasText(name)) {
                errors.add(new ImportErrorRow(rr.getRowIndex(), "客户名称", "必填"));
            } else if (name.length() > 200) {
                errors.add(new ImportErrorRow(rr.getRowIndex(), "客户名称", "长度不能超过200字符"));
            } else if (!seenInFile.add(name)) {
                errors.add(new ImportErrorRow(rr.getRowIndex(), "客户名称", "文件内重复: " + name));
            } else if (customerExists(name)) {
                errors.add(new ImportErrorRow(rr.getRowIndex(), "客户名称", "库内已存在: " + name));
            }
            String creditCode = StringUtils.trimWhitespace(nvl(row.getSocialCreditCode()));
            if (StringUtils.hasText(creditCode) && !creditCode.matches("[0-9A-HJ-NPQRTUWXY]{18}")) {
                errors.add(new ImportErrorRow(rr.getRowIndex(), "统一社会信用代码", "须为18位且不含 I/O/S/V/Z 字符: " + creditCode));
            }
            String level = StringUtils.trimWhitespace(nvl(row.getLevel()));
            if (StringUtils.hasText(level) && !CUSTOMER_LEVELS.contains(level)) {
                errors.add(new ImportErrorRow(rr.getRowIndex(), "客户分级", "仅允许 战略/重点/普通: " + level));
            }
        }
        if (!errors.isEmpty()) {
            log.info("客户迁移校验未通过，整批拒绝: 总 {} 行，错误 {} 行", rows.size(), errors.size());
            return new MigrationImportResult(rows.size(), 0, errors);
        }
        for (ExcelImportReader.RowRecord<CustomerImportRow> rr : rows) {
            CustomerImportRow row = rr.getData();
            Customer customer = new Customer();
            customer.setCustomerName(StringUtils.trimWhitespace(row.getCustomerName()));
            customer.setSocialCreditCode(blankToNull(row.getSocialCreditCode()));
            customer.setAlias1(blankToNull(row.getAlias1()));
            customer.setAlias2(blankToNull(row.getAlias2()));
            customer.setContactPerson(blankToNull(row.getContactPerson()));
            customer.setContactPhone(blankToNull(row.getContactPhone()));
            customer.setLevel(blankToNull(row.getLevel()));
            customer.setRemark(blankToNull(row.getRemark()));
            customerMapper.insert(customer);
        }
        log.info("客户主体迁移完成: 导入 {} 行", rows.size());
        return new MigrationImportResult(rows.size(), rows.size(), List.of());
    }

    /**
     * 存量项目档案导入（全量校验；客户联动建档）
     */
    @Transactional(rollbackFor = Exception.class)
    public MigrationImportResult importProjects(MultipartFile file) {
        List<ExcelImportReader.RowRecord<ProjectImportRow>> rows = readAll(file, ProjectImportRow.class);
        List<ImportErrorRow> errors = new ArrayList<>();
        Set<String> seenInFile = new HashSet<>();
        for (ExcelImportReader.RowRecord<ProjectImportRow> rr : rows) {
            ProjectImportRow row = rr.getData();
            String name = StringUtils.trimWhitespace(nvl(row.getProjectName()));
            if (!StringUtils.hasText(name)) {
                errors.add(new ImportErrorRow(rr.getRowIndex(), "项目名称", "必填"));
            } else if (name.length() > 200) {
                errors.add(new ImportErrorRow(rr.getRowIndex(), "项目名称", "长度不能超过200字符"));
            } else if (!seenInFile.add(name)) {
                errors.add(new ImportErrorRow(rr.getRowIndex(), "项目名称", "文件内重复: " + name));
            } else if (projectExists(name)) {
                errors.add(new ImportErrorRow(rr.getRowIndex(), "项目名称", "库内已存在: " + name));
            }
            String typeText = StringUtils.trimWhitespace(nvl(row.getProjectType()));
            if (!PROJECT_TYPES.containsKey(typeText)) {
                errors.add(new ImportErrorRow(rr.getRowIndex(), "项目类型", "仅允许 整机厂/技改/研发: " + typeText));
            }
            String customerName = StringUtils.trimWhitespace(nvl(row.getCustomerName()));
            if (!StringUtils.hasText(customerName)) {
                errors.add(new ImportErrorRow(rr.getRowIndex(), "客户名称", "必填（项目档案必须挂接客户主体）"));
            }
            LocalDate start = checkDate(rr, errors, row.getPlanStartDate(), "计划开始日期");
            LocalDate end = checkDate(rr, errors, row.getPlanEndDate(), "计划结束日期");
            if (start != null && end != null && end.isBefore(start)) {
                errors.add(new ImportErrorRow(rr.getRowIndex(), "计划结束日期", "不能早于计划开始日期"));
            }
            if (row.getContractAmount() != null && row.getContractAmount().signum() < 0) {
                errors.add(new ImportErrorRow(rr.getRowIndex(), "合同金额", "不能为负数"));
            }
        }
        if (!errors.isEmpty()) {
            log.info("项目迁移校验未通过，整批拒绝: 总 {} 行，错误 {} 行", rows.size(), errors.size());
            return new MigrationImportResult(rows.size(), 0, errors);
        }
        for (ExcelImportReader.RowRecord<ProjectImportRow> rr : rows) {
            ProjectImportRow row = rr.getData();
            String customerName = StringUtils.trimWhitespace(row.getCustomerName());
            // 客户联动建档（"一次录入多处引用"）：主体库不存在时自动补档
            if (!customerExists(customerName)) {
                Customer customer = new Customer();
                customer.setCustomerName(customerName);
                customer.setRemark("项目迁移自动建档");
                customerMapper.insert(customer);
            }
            Project project = new Project();
            Integer type = PROJECT_TYPES.get(StringUtils.trimWhitespace(row.getProjectType()));
            // 项目编号（与手工建档同口径：PRJ-{yyyy}-{type}-{seq:3}）
            String typeCode = type != null && type >= 1 && type <= 3 ? PROJECT_TYPE_CODES[type] : "OTH";
            project.setProjectCode(codeEngineService.nextCode("PRJ", java.util.Map.of("type", typeCode)));
            project.setProjectName(StringUtils.trimWhitespace(row.getProjectName()));
            project.setProjectType(type);
            project.setCustomerName(customerName);
            project.setProjectLeader(blankToNull(row.getProjectLeader()));
            project.setContractAmount(row.getContractAmount() == null ? BigDecimal.ZERO : row.getContractAmount());
            project.setPlanStartDate(LocalDate.parse(StringUtils.trimWhitespace(row.getPlanStartDate()), DATE_FMT));
            project.setPlanEndDate(LocalDate.parse(StringUtils.trimWhitespace(row.getPlanEndDate()), DATE_FMT));
            project.setStatus(0);
            project.setDescription(blankToNull(row.getDescription()));
            projectMapper.insert(project);
        }
        log.info("项目档案迁移完成: 导入 {} 行", rows.size());
        return new MigrationImportResult(rows.size(), rows.size(), List.of());
    }

    // ===== 内部工具 =====

    private <T> List<ExcelImportReader.RowRecord<T>> readAll(MultipartFile file, Class<T> clazz) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "迁移文件为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(400, "迁移文件超过 10MB 上限，请拆分后导入");
        }
        try {
            List<ExcelImportReader.RowRecord<T>> rows = ExcelImportReader.read(file.getInputStream(), clazz);
            if (rows.isEmpty()) {
                throw new BusinessException(400, "迁移文件无数据行");
            }
            return rows;
        } catch (IOException e) {
            throw new BusinessException(400, "文件读取失败: " + e.getMessage());
        }
    }

    private boolean customerExists(String name) {
        return customerMapper.selectCount(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getCustomerName, name)) > 0;
    }

    private boolean projectExists(String name) {
        return projectMapper.selectCount(new LambdaQueryWrapper<Project>()
                .eq(Project::getProjectName, name)) > 0;
    }

    private LocalDate checkDate(ExcelImportReader.RowRecord<?> rr, List<ImportErrorRow> errors,
                                String value, String field) {
        String text = StringUtils.trimWhitespace(nvl(value));
        if (!StringUtils.hasText(text)) {
            errors.add(new ImportErrorRow(rr.getRowIndex(), field, "必填"));
            return null;
        }
        try {
            return LocalDate.parse(text, DATE_FMT);
        } catch (DateTimeParseException e) {
            errors.add(new ImportErrorRow(rr.getRowIndex(), field, "格式须为 yyyy-MM-dd: " + text));
            return null;
        }
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? StringUtils.trimWhitespace(value) : null;
    }
}
