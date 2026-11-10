package com.dept.mgmt.platform.code;

import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.platform.code.service.CodeEngineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编码规则引擎测试（依赖本地 MySQL dept_mgmt 库）
 * 覆盖：并发取号不重号 / 格式正确 / 参数缺失 / 规则不存在
 *
 * @author dept-mgmt
 */
@SpringBootTest
class CodeEngineServiceTest {

    @Autowired
    private CodeEngineService codeEngineService;

    /**
     * 并发取号：100 线程同规则并发，编码必须全部唯一（发号正确性的硬指标）
     */
    @Test
    void nextCode_concurrent_noDuplicate() throws Exception {
        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        Set<String> codes = ConcurrentHashMap.newKeySet();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                try {
                    codes.add(codeEngineService.nextCode("WL", null));
                } finally {
                    done.countDown();
                }
            }));
        }
        done.await();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        assertEquals(threads, codes.size(), "并发取号出现重复编码");
        assertTrue(codes.stream().allMatch(c -> c.matches("WL-\\d{6}")), "编码格式不符: " + codes);
    }

    /**
     * 项目编码格式：PRJ-年份-类型缩写-流水（与存量格式一致）
     */
    @Test
    void nextCode_prj_format() {
        String code = codeEngineService.nextCode("PRJ", Map.of("type", "ZJC"));
        assertTrue(code.matches("PRJ-\\d{4}-ZJC-\\d{3,}"), "项目编码格式不符: " + code);
    }

    /**
     * 模板自定义变量缺失 → 明确报错
     */
    @Test
    void nextCode_missingParam() {
        assertThrows(BusinessException.class, () -> codeEngineService.nextCode("PRJ", null));
    }

    /**
     * 规则不存在 → 拒绝发号
     */
    @Test
    void nextCode_ruleNotExists() {
        assertThrows(BusinessException.class, () -> codeEngineService.nextCode("NOT_EXIST_RULE", null));
    }
}
