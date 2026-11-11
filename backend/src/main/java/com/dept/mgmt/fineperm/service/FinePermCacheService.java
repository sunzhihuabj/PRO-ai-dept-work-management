package com.dept.mgmt.fineperm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 精细权限缓存服务（T-404 实时生效核心）
 *
 * 机制：进程内缓存 + 全局版本号
 * - 每次请求通过 get(userId) 取用户权限集合：版本一致用缓存，不一致重载
 * - 任何权限配置变更（分配/移除/职位调整/模板应用）调用 bump() 递增全局版本并清缓存
 *   → 全部用户的下一次请求立即按新配置重载，无需重新登录（单机部署场景下实时生效）
 * - 多实例部署可后续升级为 DB 版本表 + 定时比对（文档披露）
 *
 * @author dept-mgmt
 */
@Service
@RequiredArgsConstructor
public class FinePermCacheService {

    private final FinePermissionService finePermissionService;

    /** 全局权限版本号（初始 1） */
    private final AtomicLong version = new AtomicLong(1);

    /** userId -> 缓存条目 */
    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * 缓存条目：版本 + 是否启用精细控制 + 最终生效权限点
     */
    public record CacheEntry(long version, boolean finePermEnabled, Set<String> grantedPerms) {
    }

    /**
     * 获取用户权限缓存条目（版本不一致自动重载）
     */
    public CacheEntry get(Long userId) {
        long v = version.get();
        CacheEntry entry = cache.get(userId);
        if (entry == null || entry.version() != v) {
            FinePermissionService.Resolution r = finePermissionService.resolve(userId);
            entry = new CacheEntry(v, r.isFinePermEnabled(), r.getGranted());
            cache.put(userId, entry);
        }
        return entry;
    }

    /**
     * 判断用户是否持有权限点（未启用精细控制的用户一律 false，由调用方决定灰度逻辑）
     */
    public boolean hasPerm(Long userId, String permCode) {
        CacheEntry entry = get(userId);
        return entry.finePermEnabled() && entry.grantedPerms().contains(permCode);
    }

    /**
     * 权限配置变更后调用：递增全局版本并清空缓存，下一次请求全部实时重载
     */
    public void bump() {
        version.incrementAndGet();
        cache.clear();
    }

    /**
     * 当前全局版本号（前端轮询比对用）
     */
    public long currentVersion() {
        return version.get();
    }
}
