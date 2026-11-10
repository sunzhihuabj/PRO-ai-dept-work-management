package com.dept.mgmt.platform.code.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.mapper.CodeSeqMapper;
import com.dept.mgmt.platform.code.entity.CodeSeq;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 发号事务服务（独立 Bean，保证 REQUIRES_NEW 事务经代理生效）
 *
 * <p>只在独立短事务内做一件事：指定 (rule_code, date_part) 维度原子自增并返回本次流水号。
 * 行级 X 锁由 UPDATE 语句持有至短事务提交，调用方主业务事务回滚不影响已发号码（跳号容忍）。
 *
 * @author dept-mgmt
 */
@Service
@RequiredArgsConstructor
public class CodeSeqTxService {

    private final CodeSeqMapper codeSeqMapper;

    /**
     * 原子取号：返回该维度下本次流水号（从 1 开始）
     * 首次发号该维度无记录：插入初始值 1；并发冲突改走自增路径
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public long nextSeq(String ruleCode, String datePart) {
        int updated = codeSeqMapper.increment(ruleCode, datePart);
        if (updated == 1) {
            // 本事务持有行锁，读到的即本事务自增后的值
            return readCurrentValue(ruleCode, datePart);
        }
        // 首次发号：初始化 current_value=1；并发下唯一键冲突则回退自增路径
        CodeSeq init = new CodeSeq();
        init.setRuleCode(ruleCode);
        init.setDatePart(datePart);
        init.setCurrentValue(1L);
        try {
            codeSeqMapper.insert(init);
            return 1L;
        } catch (DuplicateKeyException e) {
            codeSeqMapper.increment(ruleCode, datePart);
            return readCurrentValue(ruleCode, datePart);
        }
    }

    private long readCurrentValue(String ruleCode, String datePart) {
        CodeSeq row = codeSeqMapper.selectOne(new LambdaQueryWrapper<CodeSeq>()
                .eq(CodeSeq::getRuleCode, ruleCode)
                .eq(CodeSeq::getDatePart, datePart));
        return row.getCurrentValue();
    }
}
