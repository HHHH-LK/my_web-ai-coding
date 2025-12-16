package com.example.aicodemother.service;

import com.example.aicodemother.model.dto.app.AppDeployRequest;
import com.example.aicodemother.model.dto.app.AppQueryRequest;
import com.example.aicodemother.model.entity.App;
import com.example.aicodemother.model.entity.User;
import com.example.aicodemother.model.vo.AppVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author <a href="https://github.com/HHHH-LK">程序员LK</a>
 */
public interface AppService extends IService<App> {

    /**
     * 根据 App 对象获取对应的 AppVO 对象
     *
     * @param app 应用实体对象
     * @return AppVO 视图对象
     */
    AppVO getAppVO(App app);

    /**
     * 根据应用列表获取应用视图对象列表
     *
     * @param appList 应用实体列表
     * @return 应用视图对象列表
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 获取查询条件包装器
     *
     * @param appQueryRequest 查询请求参数
     * @return 查询条件包装器
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 校验应用参数
     *
     * @param app 应用对象
     * @param add 是否为创建操作
     */
    void validApp(App app, boolean add);

    /**
     * 根据应用ID和用户提示生成代码的异步方法
     *
     * @param appId     应用的唯一标识符
     * @param prompt    用户输入的提示信息，用于生成代码
     * @param loginUser 当前登录用户信息
     * @return 返回一个Flux流，包含生成的代码字符串
     */
    Flux<String> chatToGenCode(Long appId, String prompt, User loginUser);

    /**
     * 部署应用程序的方法
     * 该方法接收一个AppDeployRequest对象作为参数，用于执行应用程序的部署操作
     *
     * @param appDeployRequest 包含应用程序部署所需信息的请求对象
     * @return 返回一个String类型的结果，可能表示部署状态或部署后的相关信息
     */
    String deployApp(AppDeployRequest appDeployRequest,User userLogin);

}
