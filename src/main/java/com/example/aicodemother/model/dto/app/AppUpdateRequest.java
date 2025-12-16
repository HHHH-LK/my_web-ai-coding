package com.example.aicodemother.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户更新应用请求（只支持修改应用名称）
 *
 * @author <a href="https://github.com/HHHH-LK">程序员LK</a>
 */
@Data
public class AppUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    private static final long serialVersionUID = 1L;
}

