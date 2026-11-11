package com.dept.mgmt.agent.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.mdm.customer.entity.Customer;
import com.dept.mgmt.mdm.material.entity.Material;
import com.dept.mgmt.mapper.CustomerMapper;
import com.dept.mgmt.mapper.MaterialMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 主数据模糊匹配处理器（AGT-12，T-308 补全，Ask 级）
 * T-104 治理 Agent：主数据查重聚类建议，验收标准"TOP3 命中率 ≥90%"
 * 确定性相似度引擎（透明可复核）：
 * 1. 归一化（全角→半角/去空格/大写/去括号注记）
 * 2. 命中通道：别名/名称精确包含（1.0）、编码前缀（0.9）、Dice bigram 字符相似度（0~1）
 * 3. TOP3 候选 ≥0.4，附建议动作（疑似重复→建议归并目标）
 *
 * @author dept-mgmt
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MdmMatchHandler implements CapabilityHandler {

    private static final Pattern KEYWORD = Pattern.compile("keyword\\s*=\\s*([^&]+)");
    private static final Pattern TYPE = Pattern.compile("type\\s*=\\s*(MATERIAL|CUSTOMER)");
    private static final double THRESHOLD = 0.4;

    private final MaterialMapper materialMapper;
    private final CustomerMapper customerMapper;
    private final ObjectMapper objectMapper;

    @Override
    public String capabilityCode() {
        return "AGT-12";
    }

    /** Ask 级：仅输出查重聚类建议，归并动作由人工确认后执行 */
    @Override
    public boolean requireConfirm() {
        return true;
    }

    @Override
    public String execute(String instruction, Long userId) throws Exception {
        String keyword = null;
        String type = "MATERIAL";
        if (instruction != null) {
            Matcher k = KEYWORD.matcher(instruction);
            if (k.find()) {
                keyword = k.group(1).trim();
            }
            Matcher t = TYPE.matcher(instruction);
            if (t.find()) {
                type = t.group(1);
            }
        }
        if (!StringUtils.hasText(keyword)) {
            throw new BusinessException(400, "指令中未含 keyword=，无法进行主数据模糊匹配");
        }
        List<Map<String, Object>> candidates = switch (type) {
            case "CUSTOMER" -> matchCustomers(keyword);
            default -> matchMaterials(keyword);
        };
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("capability", "AGT-12 主数据模糊匹配");
        out.put("type", type);
        out.put("keyword", keyword);
        out.put("topN", candidates.size());
        out.put("candidates", candidates);
        out.put("suggestion", candidates.isEmpty()
                ? "未发现疑似重复主数据，可新建建档"
                : "发现 " + candidates.size() + " 条疑似相似主数据（TOP" + candidates.size() + "），建议优先复用最高分记录，归并动作需人工确认");
        log.info("AGT-12 模糊匹配: type={} keyword={} top={}", type, keyword, candidates.size());
        return objectMapper.writeValueAsString(out);
    }

    // ===== 物料匹配 =====

    private List<Map<String, Object>> matchMaterials(String keyword) {
        String norm = normalize(keyword);
        List<Map<String, Object>> top = new ArrayList<>();
        for (Material m : materialMapper.selectList(new LambdaQueryWrapper<Material>().orderByAsc(Material::getId))) {
            double score = bestScore(norm, m.getMaterialName(), m.getAlias1(), m.getAlias2(),
                    m.getMaterialCode(), m.getOldCode(), m.getSpecModel());
            if (score >= THRESHOLD) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("id", m.getId());
                c.put("code", m.getMaterialCode());
                c.put("name", m.getMaterialName());
                c.put("spec", m.getSpecModel());
                c.put("dedupStatus", m.getDedupStatus());
                c.put("score", round(score));
                c.put("action", score >= 0.9 ? "疑似重复：建议复用该编码，不新建" : "相似候选：人工判断是否归并");
                top.add(c);
            }
        }
        top.sort((a, b) -> Double.compare((double) b.get("score"), (double) a.get("score")));
        return top.size() > 3 ? new ArrayList<>(top.subList(0, 3)) : top;
    }

    // ===== 客户匹配 =====

    private List<Map<String, Object>> matchCustomers(String keyword) {
        String norm = normalize(keyword);
        List<Map<String, Object>> top = new ArrayList<>();
        for (Customer c : customerMapper.selectList(new LambdaQueryWrapper<Customer>().orderByAsc(Customer::getId))) {
            double score = bestScore(norm, c.getCustomerName(), c.getAlias1(), c.getAlias2(),
                    c.getSocialCreditCode(), null, null);
            if (score >= THRESHOLD) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("id", c.getId());
                r.put("name", c.getCustomerName());
                r.put("creditCode", c.getSocialCreditCode());
                r.put("level", c.getLevel());
                r.put("score", round(score));
                r.put("action", score >= 0.9 ? "疑似重复：建议复用该客户主体（能选不填）" : "相似候选：人工判断是否归并");
                top.add(r);
            }
        }
        top.sort((a, b) -> Double.compare((double) b.get("score"), (double) a.get("score")));
        return top.size() > 3 ? new ArrayList<>(top.subList(0, 3)) : top;
    }

    // ===== 确定性相似度引擎 =====

    /** 多通道取最高分：别名/名称包含 > 编码前缀 > Dice bigram */
    private double bestScore(String normQuery, String name, String alias1, String alias2,
                             String code, String oldCode, String spec) {
        double best = 0;
        for (String text : new String[]{name, alias1, alias2}) {
            if (!StringUtils.hasText(text)) {
                continue;
            }
            String normText = normalize(text);
            if (normText.equals(normQuery)) {
                best = Math.max(best, 1.0);
            } else if (normText.contains(normQuery) || normQuery.contains(normText)) {
                // 包含关系=强信号：下限 0.45 保证短查询词（如"螺栓"）不被阈值过滤；
                // 长度比例加权避免 2 字别名碰瓷满分
                double ratio = Math.min((double) Math.min(normText.length(), normQuery.length())
                        / Math.max(normText.length(), normQuery.length()), 1.0);
                best = Math.max(best, Math.max(0.45, 0.85 * ratio + 0.15));
            } else {
                best = Math.max(best, dice(normText, normQuery));
            }
        }
        for (String codeText : new String[]{code, oldCode}) {
            if (StringUtils.hasText(codeText) && normalize(codeText).startsWith(normQuery)) {
                best = Math.max(best, 0.9);
            }
        }
        if (StringUtils.hasText(spec) && normalize(spec).contains(normQuery)) {
            best = Math.max(best, 0.8);
        }
        return best;
    }

    /** 归一化：全角→半角、去空白、大写、去括号注记 */
    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char ch : text.toCharArray()) {
            if (ch >= 0xFF01 && ch <= 0xFF5E) {
                ch = (char) (ch - 0xFEE0);
            }
            if (ch == 0x3000) {
                ch = ' ';
            }
            if (!Character.isWhitespace(ch) && ch != '（' && ch != '）' && ch != '(' && ch != ')') {
                sb.append(Character.toUpperCase(ch));
            }
        }
        return sb.toString();
    }

    /** Dice bigram 字符相似度（0~1） */
    private double dice(String a, String b) {
        if (a.length() < 2 || b.length() < 2) {
            return a.equals(b) ? 1.0 : 0.0;
        }
        Set<String> bigramsA = bigrams(a);
        Set<String> bigramsB = bigrams(b);
        int overlap = 0;
        Map<String, Integer> count = new HashMap<>();
        for (String g : bigramsA) {
            count.merge(g, 1, Integer::sum);
        }
        for (String g : bigramsB) {
            Integer n = count.get(g);
            if (n != null && n > 0) {
                overlap++;
                count.put(g, n - 1);
            }
        }
        return 2.0 * overlap / (bigramsA.size() + bigramsB.size());
    }

    private Set<String> bigrams(String s) {
        Set<String> set = new HashSet<>();
        for (int i = 0; i < s.length() - 1; i++) {
            set.add(s.substring(i, i + 2));
        }
        return set;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
