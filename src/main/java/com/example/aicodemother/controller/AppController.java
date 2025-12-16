package com.example.aicodemother.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.example.aicodemother.annotation.AuthCheck;
import com.example.aicodemother.common.BaseResponse;
import com.example.aicodemother.common.DeleteRequest;
import com.example.aicodemother.common.ResultUtils;
import com.example.aicodemother.constant.UserConstant;
import com.example.aicodemother.exception.BusinessException;
import com.example.aicodemother.exception.ErrorCode;
import com.example.aicodemother.exception.ThrowUtils;
import com.example.aicodemother.model.dto.app.*;
import com.example.aicodemother.model.entity.App;
import com.example.aicodemother.model.entity.User;
import com.example.aicodemother.model.vo.AppVO;
import com.example.aicodemother.service.AppService;
import com.example.aicodemother.service.UserService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 应用 控制层。
 *
 * @author <a href="https://github.com/HHHH-LK">程序员LK</a>
 */
@RestController
@RequestMapping("/app")
@RequiredArgsConstructor
public class AppController {

    private final AppService appService;
    private final UserService userService;

    /**
     * 应用部署
     *
     * @param appDeployRequest 部署请求
     * @param request          请求
     * @return 部署 URL
     */
    @PostMapping("/deploy")
    public BaseResponse<String> deployApp(@RequestBody AppDeployRequest appDeployRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR, "部署请求不能为空");
        ThrowUtils.throwIf(appDeployRequest.getAppId() == null || appDeployRequest.getAppId() < 0, ErrorCode.PARAMS_ERROR, "appId不能为空");
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.PARAMS_ERROR, "用户未登录");
        String deployUrl = appService.deployApp(appDeployRequest, loginUser);
        return ResultUtils.success(deployUrl);

    }


    @GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam Long appId, @RequestParam String userMessage, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.PARAMS_ERROR, "用户未登录");
        ThrowUtils.throwIf(appId == null || appId < 0, ErrorCode.PARAMS_ERROR, "appId不能为空");
        ThrowUtils.throwIf(userMessage == null || userMessage.isEmpty(), ErrorCode.PARAMS_ERROR, "用户信息不能为空");
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR, "请求不能为空");
        Flux<String> contentFlux = appService.chatToGenCode(appId, userMessage, loginUser);
        //对流进行处理(Json封装/加密解密)避免空格和换行符号的丢失
        return contentFlux.map(content -> {
            Map<String, String> map = Map.of("d", content);
            String contentToJson = JSONUtil.toJsonStr(map);
            //将转Json后的内容封装成ServerSentEvents
            return ServerSentEvent.<String>builder().data(contentToJson).build();
        }).concatWith(Mono.just(
                ServerSentEvent.<String>builder().event("done").data("").build()
        ));
    }


    /**
     * 创建应用（用户）
     *
     * @param appAddRequest 创建应用请求
     * @param request       请求对象
     * @return 应用 id
     */
    @PostMapping("/add")
    public BaseResponse<Long> addApp(@RequestBody AppAddRequest appAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取登录用户
        User loginUser = userService.getLoginUser(request);
        // 参数校验
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        appService.validApp(app, true);
        // 设置创建用户
        app.setUserId(loginUser.getId());
        // 保存应用
        boolean result = appService.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(app.getId());
    }

    /**
     * 删除应用（用户）
     *
     * @param deleteRequest 删除请求
     * @param request       请求对象
     * @return 是否删除成功
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteApp(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断应用是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人可删除
        if (!oldApp.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 删除应用
        boolean result = appService.removeById(id);
        return ResultUtils.success(result);
    }

    /**
     * 更新应用（用户）
     *
     * @param appUpdateRequest 更新应用请求
     * @param request          请求对象
     * @return 是否更新成功
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateApp(@RequestBody AppUpdateRequest appUpdateRequest, HttpServletRequest request) {
        if (appUpdateRequest == null || appUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = appUpdateRequest.getId();
        // 判断应用是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人可修改
        if (!oldApp.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 参数校验
        App app = new App();
        BeanUtil.copyProperties(appUpdateRequest, app);
        appService.validApp(app, false);
        // 更新应用
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取应用详情（用户）
     *
     * @param id 应用 id
     * @return 应用详情
     */
    @GetMapping("/get/vo")
    public BaseResponse<AppVO> getAppVOById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(appService.getAppVO(app));
    }

    /**
     * 分页获取自己的应用列表（用户）
     *
     * @param appQueryRequest 查询请求参数
     * @param request         请求对象
     * @return 分页应用列表
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<AppVO>> listMyAppVOByPage(@RequestBody AppQueryRequest appQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        // 限制爬虫
        long pageNum = appQueryRequest.getPageNum();
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多 20 个");
        // 设置查询条件
        appQueryRequest.setUserId(loginUser.getId());
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 转换为 VO
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 分页获取精选的应用列表（用户）
     *
     * @param appQueryRequest 查询请求参数
     * @return 分页应用列表
     */
    @PostMapping("/list/page/vo/featured")
    public BaseResponse<Page<AppVO>> listFeaturedAppVOByPage(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 限制爬虫
        long pageNum = appQueryRequest.getPageNum();
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多 20 个");
        // 查询优先级大于 0 的应用（精选应用）
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        queryWrapper.gt("priority", 0);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 转换为 VO
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 根据 id 获取应用（管理员）
     *
     * @param id 应用 id
     * @return 应用详情
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<App> getAppById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(app);
    }

    /**
     * 删除应用（管理员）
     *
     * @param deleteRequest 删除请求
     * @return 是否删除成功
     */
    @PostMapping("/delete/admin")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteAppByAdmin(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = appService.removeById(deleteRequest.getId());
        return ResultUtils.success(result);
    }

    /**
     * 更新应用（管理员）
     *
     * @param appAdminUpdateRequest 更新应用请求
     * @return 是否更新成功
     */
    @PostMapping("/update/admin")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateAppByAdmin(@RequestBody AppAdminUpdateRequest appAdminUpdateRequest) {
        if (appAdminUpdateRequest == null || appAdminUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 参数校验
        App app = new App();
        BeanUtil.copyProperties(appAdminUpdateRequest, app);
        appService.validApp(app, false);
        // 更新应用
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 分页获取应用列表（管理员）
     *
     * @param appQueryRequest 查询请求参数
     * @return 分页应用列表
     */
    @PostMapping("/list/page/vo/admin")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AppVO>> listAppVOByPageAdmin(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = appQueryRequest.getPageNum();
        long pageSize = appQueryRequest.getPageSize();
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 转换为 VO
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }



}
