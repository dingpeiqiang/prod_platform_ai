package com.sitech.prodai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sitech.prodai.domain.entity.ApiSavedRequest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 接口管理 · 已保存请求 Mapper。
 */
@Mapper
public interface ApiSavedRequestMapper extends BaseMapper<ApiSavedRequest> {
}
