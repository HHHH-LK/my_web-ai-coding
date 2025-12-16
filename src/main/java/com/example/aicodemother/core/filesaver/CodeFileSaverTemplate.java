package com.example.aicodemother.core.filesaver;

import cn.hutool.core.io.FileUtil;
import com.example.aicodemother.constant.AppConstant;
import com.example.aicodemother.exception.BusinessException;
import com.example.aicodemother.exception.ErrorCode;
import com.example.aicodemother.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * @program: ai-code-mother
 * @description: 文件保存器模板类
 * @author: lk_hhh
 * @create: 2025-10-09 19:52
 **/
public abstract class CodeFileSaverTemplate<T> {

    public static final String FILE_SAVE_ROOT_PATH = AppConstant.CODE_OUTPUT_ROOT_DIR;

    //流程模版
    public final File saveCode(T result,Long appId) {
        //校验参数
        checkParamIsTure(result);
        //构建唯一路径
        String createFilePath = buildUniquePath(result,appId);
        //写入文件
        String finallyCodingPath = writeToFile(result, createFilePath);

        return new File(finallyCodingPath);
    }

    protected abstract String writeToFile(T result, String createFilePath);

    private String buildUniquePath(T result, Long appId) {
        CodeGenTypeEnum resultType = getResultType(result);
        String codeTypeValue = resultType.getValue();
        String uniqueFileDirName = FILE_SAVE_ROOT_PATH + File.separator + codeTypeValue + "_" + appId;
        FileUtil.mkdir(uniqueFileDirName);
        return uniqueFileDirName;
    }

    protected abstract CodeGenTypeEnum getResultType(T result);

    protected void checkParamIsTure(T result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "参数为空");
        }

    }

    /**
     * 保存文件的方法
     *
     * @param dirPath     文件所在的目录路径
     * @param fileName    文件名
     * @param fileContent 文件内容
     */
    protected final void writeToFileUtil(String dirPath, String fileName, String fileContent) {
        if (dirPath != null) { //编写文件路径，将目录路径和文件名通过系统分隔符连接
            String filePath = dirPath + File.separator + fileName;
            // 使用FileUtil工具类将文件内容以UTF-8编码写入指定路径
            FileUtil.writeString(fileContent, filePath, StandardCharsets.UTF_8);
        }

    }


}