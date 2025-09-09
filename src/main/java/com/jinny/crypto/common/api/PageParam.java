package com.jinny.crypto.common.api;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * program: invest-parent
 * description:
 * create: 2024-02-13
 */
@Data
public class PageParam {

    @NotNull(message = "页数参数不能为空")
    private Integer pageNum;

    @NotNull(message = "每页条数不能为空")
    private Integer pageSize;

}