package com.example.aicodemother.service;

import com.example.aicodemother.model.dto.user.UserQueryRequest;
import com.example.aicodemother.model.entity.User;
import com.example.aicodemother.model.vo.LoginUserVO;
import com.example.aicodemother.model.vo.UserVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 用户 服务层。
 *
 * @author <a href="https://github.com/HHHH-LK">程序员LK</a>
 */
public interface UserService extends IService<User> {


    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 获取脱敏的已登录用户信息
     * 该方法用于从用户实体中提取并返回脱敏后的登录用户信息视图对象(VO)
     * 通常用于前端展示，避免敏感信息泄
     *
     * @return LoginUserVO 返回一个包含脱敏后用户信息的视图对象，该对象不包含用户的敏感信息如密码等
     */
    LoginUserVO getLoginUserVO(User user); // 方法接收User对象作为参数，返回脱敏后的LoginUserVO对象

    /**
     * 加密用户密码的方法
     *
     * @param password 原始密码字符串
     * @return 返回加密后的密码字符串
     */
    String getEncryptPassword(String password);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);


    /**
     * 根据User对象获取对应的UserVO对象
     * UserVO通常用于前端展示，是对User对象的数据封装和转换
     *
     * @param user 用户实体对象，包含用户的基本信息
     * @return UserVO 视图对象，用于前端展示，可能包含经过处理或筛选后的用户数据
     */
    UserVO getUserVO(User user);

    /**
     * 根据用户列表获取用户视图对象列表
     * 该方法用于将用户实体列表转换为用户视图对象列表，通常用于数据展示层
     *
     * @param userList 用户实体列表，包含用户的基本信息
     * @return 返回用户视图对象列表，用于前端展示，可能包含脱敏或格式化后的数据
     */
    List<UserVO> getUserVOList(List<User> userList);


    QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);
}
