package com.example.aicodemother.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.example.aicodemother.constant.AppConstant;
import com.example.aicodemother.core.AiCodeGeneratorFacade;
import com.example.aicodemother.core.builder.VueProjectBuilder;
import com.example.aicodemother.core.handler.StreamHandlerExecutor;
import com.example.aicodemother.exception.BusinessException;
import com.example.aicodemother.exception.ErrorCode;
import com.example.aicodemother.exception.ThrowUtils;
import com.example.aicodemother.mapper.AppMapper;
import com.example.aicodemother.model.dto.app.AppDeployRequest;
import com.example.aicodemother.model.dto.app.AppQueryRequest;
import com.example.aicodemother.model.entity.App;
import com.example.aicodemother.model.entity.User;
import com.example.aicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.example.aicodemother.model.enums.CodeGenTypeEnum;
import com.example.aicodemother.model.vo.AppVO;
import com.example.aicodemother.service.AppService;
import com.example.aicodemother.service.ChatHistoryService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author <a href="https://github.com/HHHH-LK">程序员LK</a>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    private final AiCodeGeneratorFacade aiCodeGeneratorFacade;
    private final ChatHistoryService chatHistoryService;
    private final StreamHandlerExecutor streamHandlerExecutor;
    private final VueProjectBuilder vueProjectBuilder;

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        return appList.stream()
                .map(this::getAppVO)
                .collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();

        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    @Override
    public void validApp(App app, boolean add) {
        ThrowUtils.throwIf(app == null, ErrorCode.PARAMS_ERROR);
        String appName = app.getAppName();
        String initPrompt = app.getInitPrompt();

        // 创建时，必填参数校验
        if (add) {
            ThrowUtils.throwIf(StrUtil.isBlank(appName), ErrorCode.PARAMS_ERROR, "应用名称不能为空");
            ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "应用初始化 prompt 不能为空");
        }
        // 更新时，校验参数
        if (StrUtil.isNotBlank(appName) && appName.length() > 100) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用名称过长");
        }
    }

    /**
     * 处理用户生成代码的请求
     * 该方法会进行参数校验、权限校验，并调用相应的代码生成器生成代码
     *
     * @param appId       应用的ID
     * @param userMessage 用户输入的消息
     * @param loginUser   当前登录用户信息
     * @return 返回一个Flux<String>，表示生成的代码流
     */
    @Override
    public Flux<String> chatToGenCode(Long appId, String userMessage, User loginUser) {
        //参数校验：检查appId、userMessage和loginUser是否为空
        ThrowUtils.throwIf(appId == null || userMessage == null || loginUser == null, ErrorCode.PARAMS_ERROR);
        //权限校验(只有自己才能够生成自己的代码)
        App searchResult = this.getById(appId);
        ThrowUtils.throwIf(searchResult == null, ErrorCode.PARAMS_ERROR, "应用不存在");
        Long createUserId = searchResult.getUserId();
        Long currentLoginUserId = loginUser.getId();
        ThrowUtils.throwIf(!currentLoginUserId.equals(createUserId), ErrorCode.NO_AUTH_ERROR, "没有权限生成代码");
        //获取代码生成器
        String codeGenType = searchResult.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        ThrowUtils.throwIf(codeGenTypeEnum == null, ErrorCode.PARAMS_ERROR, "代码生成器类型错误");
        //保存用户消息
        chatHistoryService.addChatMessage(appId, userMessage, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser);
        //调用代码生成器
        Flux<String> stringFlux = aiCodeGeneratorFacade.generateAndSaveCodeStream(userMessage, codeGenTypeEnum, appId);
        //收集对话消息，并保存记录
        return streamHandlerExecutor.doExecutor(stringFlux, codeGenTypeEnum, chatHistoryService, appId, loginUser);

    }

    @Override
    public String deployApp(AppDeployRequest appDeployRequest, User userLogin) {
        ThrowUtils.throwIf(userLogin == null, ErrorCode.PARAMS_ERROR, "用户未登录");
        //首先查询appId是否存在
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        Long appId = appDeployRequest.getAppId();
        ThrowUtils.throwIf(appId == null || appId < 0, ErrorCode.PARAMS_ERROR, "appId不能为空");
        //查询出对应的app
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.PARAMS_ERROR, "应用不存在");
        //查询权限校验
        Long userLoginId = userLogin.getId();
        ThrowUtils.throwIf(!app.getUserId().equals(userLoginId), ErrorCode.NO_AUTH_ERROR, "没有权限部署应用");
        //获取出对应deployId
        String deployKey = app.getDeployKey();
        if (deployKey == null || deployKey.isEmpty()) {
            //如果deployKey为空，则表示没有部署过，需要生成一个部署key
            deployKey = reDoCreateDeployKey(deployKey);
        }
        //获取源代码地址
        String codeGenType = app.getCodeGenType();
        Long mianAppId = app.getId();
        String mainAppFileName = codeGenType + "_" + mianAppId;
        String mainPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + mainAppFileName;
        File mainAppFilePath = new File(mainPath);
        File deployFilePath = new File(AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey);
        //检查是否存在
        ThrowUtils.throwIf(!mainAppFilePath.exists() || !mainAppFilePath.isDirectory(), ErrorCode.SYSTEM_ERROR, "应用源代码不存在");
        //vue项目特殊处理
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            boolean isBuildSuccess = vueProjectBuilder.buildProject(mainPath);
            ThrowUtils.throwIf(!isBuildSuccess, ErrorCode.SYSTEM_ERROR, "构建项目失败");
            File distDir = new File(mainPath, "dist");
            if (!distDir.exists() || !distDir.isDirectory()) {
                log.error("dist 目录不存在: {}", distDir.getAbsolutePath());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "构建项目失败");
            }
            mainAppFilePath = distDir;
        }
        //如果存在则进行拷贝
        deployKey = IsSuccessCopyMainAppFile(mainAppFilePath, deployFilePath, deployKey);
        //修改app部署信息
        app.setDeployKey(deployKey);
        app.setEditTime(LocalDateTime.now());
        app.setDeployedTime(LocalDateTime.now());
        app.setUpdateTime(LocalDateTime.now());
        boolean isSuccess = this.updateById(app);
        ThrowUtils.throwIf(!isSuccess, ErrorCode.SYSTEM_ERROR, "部署失败");
        //拼接对应的部署地址返回给前端
        return AppConstant.CODE_DEPLOY_HOST + "/" + deployKey;
    }

    /**
     * 此方法重写了父类的removeById方法，用于在删除应用时同时删除相关的历史对话记录
     *
     * @param appId 应用ID，用于标识要删除的应用
     * @return 删除操作是否成功执行，返回true表示成功，false表示失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable appId) {
        if (appId == null) {
            return false;
        }
        //删除应用的同时，删除相关的历史对话记录
        long appleId = Long.parseLong(appId.toString());
        if (appleId < 0) {
            return false;
        }
        try {
            chatHistoryService.deleteChatHistory(appleId);
        } catch (Exception ex) {
            log.error("删除历史对话记录失败:{}", ex.getMessage());
        }
        return super.removeById(appId);

    }


    private String reDoCreateDeployKey(String deployKey) {
        String testDeployKey = RandomUtil.randomString(6);
        if (checkCreateDeployKeyIsOK(testDeployKey)) {
            return testDeployKey;
        }
        return reDoCreateDeployKey(testDeployKey);

    }

    private Boolean checkCreateDeployKeyIsOK(String deployKey) {
        QueryWrapper queryWrapper = new QueryWrapper().eq(App::getDeployKey, deployKey);
        return deployKey != null || this.mapper.selectCountByQuery(queryWrapper) <= 0;
    }

    //文件拷贝原子操作实现
    //TODO 将文件读写实现成NIO操作。
    private String IsSuccessCopyMainAppFile(File mainAppFilePath, File deployFilePath, String deployKey) {
        //首先创建一个随机的临时文件名
        String rootPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator;
        String tmpDeployKey = reDoCreateDeployKey(deployKey);
        File tmpDeployFilePath = new File(rootPath + tmpDeployKey);
        try {
            //进行文件的拷贝
            FileUtil.copyContent(mainAppFilePath, tmpDeployFilePath, true);
            //拷贝成功后，将临时文件重命名
            boolean isSuccess = tmpDeployFilePath.renameTo(deployFilePath);
            return isSuccess ? deployKey : tmpDeployKey;
        } catch (Exception ex) {
            log.error("拷贝文件内容过程中出现异常", ex);
            boolean del = FileUtil.del(tmpDeployFilePath);
            if (!del) {
                log.error("删除临时文件失败");
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "拷贝文件内容失败");
        }

    }

}
