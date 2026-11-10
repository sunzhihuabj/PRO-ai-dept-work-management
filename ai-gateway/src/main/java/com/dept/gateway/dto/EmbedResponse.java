package com.dept.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 网关向量化响应
 *
 * @author dept-mgmt
 */
@Data
@AllArgsConstructor
public class EmbedResponse {

    /** 与请求顺序一致的向量列表 */
    private List<List<Double>> embeddings;

    /** 向量化模型标识（mock 或真实模型名） */
    private String model;
}
