package com.sitech.prodai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sitech.prodai.domain.entity.Prompt;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PromptMapper extends BaseMapper<Prompt> {
}
