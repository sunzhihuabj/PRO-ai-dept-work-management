package com.dept.mgmt.aiworkspace.ops;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 版块技能卡（P0-1 渐进披露）
 *
 * <p>对齐 Anthropic Agent Skills 规范的渐进披露模式：把原 93 行整块「系统版块知识」
 * 按业务版块拆成 13 张技能卡。常驻注入 LLM 的只有「版块名 + 一句话能力 + 工具名清单」
 * （每版块约 100 tokens）；本轮消息命中某版块触发词后，才注入该版块的完整业务口径
 * 与工具参数详情。token 成本下降约 50%，判定聚焦度显著提升，LLM 调用次数不变（1 次/轮）。
 *
 * <p>触发词预筛为本地正则（零成本）；未命中版块时注入全部工具的一句话目录兜底，
 * 模型仍可选对工具，缺参由后端声明式追问补齐。
 *
 * @author dept-mgmt
 */
@Component
@RequiredArgsConstructor
public class OpsSkillCards {

    /** 全局口径（常驻注入，约束红线，防编造单号/越权操作） */
    public static final String GLOBAL_RULES = """
            【全局口径】业务单号自动生成不可手填；删除类与资金审批类操作不开放对话执行；所有查询默认返回最近记录。
            """;

    private final OpsToolRegistry registry;

    /** 版块技能卡：code / 中文名 / 一句话能力 / 触发词（正则片段）/ 完整业务口径 */
    public record OpsSkillCard(String code, String name, String summary, String triggers, String knowledge) {
    }

    /** 静态卡定义（与 OpsToolRegistry 工具注册的 module 名严格一致） */
    private static final List<OpsSkillCard> CARDS = List.of(
            new OpsSkillCard("task", "任务管理", "任务查询/创建/状态流转/工时录入",
                    "任务|待办|工时|排期|干活|进度",
                    "■ 任务：状态 0待办/1进行中/2待审核/3已完成/4已关闭；优先级 1高/2中/3低；进度0-100；工时0.5-24小时。任务ID可从 task.list 结果获取。"),
            new OpsSkillCard("project", "项目管理", "项目查询/详情/创建",
                    "项目|整机厂|技改|研发|立项",
                    "■ 项目：类型 1整机厂/2技改/3研发；状态 0待启动/1进行中/2已完成/3已关闭/4暂停；创建必填 名称/类型/客户/计划起止日期，编号 PRJ 自动生成。"),
            new OpsSkillCard("contract", "合同管理", "合同查询/详情/绑定项目",
                    "合同|会签|用印|收款|签约|HT",
                    "■ 合同：状态 DRAFT草稿→COUNTERSIGN会签→APPROVING审批→SEALING用印→EFFECTIVE生效→CLOSED关闭/TERMINATED终止；会签门 G1=四项收款比例合计必须100%；审批/会签/用印仅部门领导；联系方式为敏感字段按权限脱敏；编号 HT 自动生成。"),
            new OpsSkillCard("tender", "投标管理", "标讯查询/状态推进",
                    "投标|标讯|中标|招标|竞标|定标|TN",
                    "■ 投标：标讯状态 NEW新标讯/FOLLOWED跟踪中/APPLIED已投标/PREPARED备标中/DECIDED已定标/WON已中标/LOST未中标；编号 TN 自动生成；AI 可做资格比对/报价建议/竞品分析（经 Agent 任务）。"),
            new OpsSkillCard("design", "设计管理", "设计方案查询",
                    "设计|方案|BOM|出图",
                    "■ 设计：方案状态 DRAFT草稿/RELEASED已发布/ARCHIVED已归档；发布后不可修改；BOM 仅草稿可录入，13 条校验规则。"),
            new OpsSkillCard("production", "生产管理", "生产工单查询/齐套率计算",
                    "生产|工单|齐套|试制|批量|开工|首件|FAI",
                    "■ 生产：工单类型 TRIAL试制/BATCH批量；状态 DRAFT/STARTED/CLOSED；开工卡点=齐套率≥95%且交底签字完成；FAI 首件检验 FAIL 会派生整改单；生产完工验收 FAIL 派生整改单。"),
            new OpsSkillCard("delivery", "交付管理", "发货单/交付进度/验收单查询",
                    "交付|发货|发运|验收|监理|业主",
                    "■ 交付：发货单 DRAFT草稿/SUBMITTED已发运，整机厂首发须 FAI 通过；验收分 FIRST初/MID中/LATE晚/FINAL终验，进度 30%/85% 触发阶段验收，三方（业主/监理/客户）签字齐备生效。"),
            new OpsSkillCard("ecn", "变更管理", "变更单查询/审批",
                    "变更|ECN|设变",
                    "■ 变更 ECN：DRAFT→PENDING_APPROVAL→APPROVED/REJECTED→IMPLEMENTED→CLOSED；实施时旧方案自动归档并回收任务；审批仅部门领导。"),
            new OpsSkillCard("cost", "成本管理", "成本台账/完工核算",
                    "成本|台账|毛利|核算",
                    "■ 成本：类型 MATERIAL物料/LABOR人工/EXPENSE费用；完工核算输出毛利三口径；物料成本由扫码过账自动归集。"),
            new OpsSkillCard("aftermarket", "售后服务", "报修单查询/报修受理",
                    "报修|售后|故障|维修|返修|RP",
                    "■ 售后：报修级别 EMERGENCY紧急/GENERAL一般/INQUIRY咨询（AI 自动分级）；闭环必须填根因与解决方案；编号 RP 自动生成。"),
            new OpsSkillCard("archive", "档案管理", "档案查询/借阅/归还/逾期查询",
                    "档案|借阅|归还|图纸|销毁|DA",
                    "■ 档案：类型 CONTRACT合同/SCHEME方案/DRAWING图纸/RECORD记录/ACCEPTANCE验收；介质 ELECTRONIC电子/PAPER纸质/DUAL双轨（纸质/双轨必填存放位置）；状态 NORMAL/BORROWED借出/DESTROYED已销毁；电子档案不可实物借出；编号 DA 自动生成。"),
            new OpsSkillCard("event", "事件审批", "待审批事件查询/事件处理",
                    "事件|审批|协同",
                    "■ 协同：事件审批处理类型 受理/驳回/转派/处理/完成/关闭。"),
            new OpsSkillCard("kb", "知识库", "知识库混合检索与溯源",
                    "知识库|资料|文档|规范|检索|搜一下|搜下",
                    "■ 知识库：支持混合检索与溯源；知识库检索一律走 kb.search 工具。"));

    /** 触发词正则（懒加载编译一次） */
    private final Map<String, Pattern> triggerPatterns = compileTriggers();

    private Map<String, Pattern> compileTriggers() {
        Map<String, Pattern> map = new LinkedHashMap<>();
        for (OpsSkillCard card : CARDS) {
            map.put(card.code(), Pattern.compile(card.triggers()));
        }
        return map;
    }

    /** 全部技能卡（定义顺序） */
    public List<OpsSkillCard> all() {
        return CARDS;
    }

    /**
     * 常驻摘要目录（渐进披露第一层，注入 LLM 约 400 tokens）：
     * 每版块一行 = 版块名 + 一句话能力 + 该版块全部工具名（自解释：版块.动作）。
     */
    public String briefCatalog() {
        Map<String, List<String>> byModule = registry.all().stream()
                .collect(Collectors.groupingBy(OpsTool::getModule,
                        LinkedHashMap::new,
                        Collectors.mapping(OpsTool::getName, Collectors.toList())));
        StringBuilder sb = new StringBuilder();
        for (OpsSkillCard card : CARDS) {
            sb.append("■ ").append(card.name()).append("（").append(card.summary()).append("）：");
            List<String> names = byModule.get(card.name());
            sb.append(names == null ? "无工具" : String.join(" / ", names)).append('\n');
        }
        return sb.toString();
    }

    /**
     * 触发词预筛：返回本轮消息命中的技能卡（0..n 张）。
     * 本地正则匹配，零 LLM 成本；命中后由调用方注入完整口径与参数详情。
     */
    public List<OpsSkillCard> match(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        List<OpsSkillCard> hits = new ArrayList<>();
        for (OpsSkillCard card : CARDS) {
            if (triggerPatterns.get(card.code()).matcher(content).find()) {
                hits.add(card);
            }
        }
        return hits;
    }
}
