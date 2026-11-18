-- =============================================================
-- D2: E1 眼睛 — 视觉通道路由列
-- 背景：实测纯文本通道（ModelScope Qwen3.5 等）对带 image 部件的请求
-- 返回 200 但静默丢弃图片并编造"看不到图片"回复；vision 请求必须
-- 前置过滤，仅路由到声明 supports_vision=1 的通道。
-- =============================================================

ALTER TABLE sys_ai_provider_config
    ADD COLUMN supports_vision TINYINT NOT NULL DEFAULT 0 COMMENT '支持视觉/多模态（E1：vision请求仅路由到本列=1的通道）' AFTER remark;

-- VISION 通道（LM Studio 视觉模型，id=15）：声明支持视觉；LM 主机恢复后 UPDATE enabled=1 启用
UPDATE sys_ai_provider_config SET supports_vision = 1 WHERE provider_code = 'VISION';
