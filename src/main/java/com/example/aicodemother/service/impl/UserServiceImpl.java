package com.example.aicodemother.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.example.aicodemother.exception.BusinessException;
import com.example.aicodemother.exception.ErrorCode;
import com.example.aicodemother.model.dto.user.UserQueryRequest;
import com.example.aicodemother.model.entity.User;
import com.example.aicodemother.mapper.UserMapper;
import com.example.aicodemother.model.enums.UserRoleEnum;
import com.example.aicodemother.model.vo.LoginUserVO;
import com.example.aicodemother.model.vo.UserVO;
import com.example.aicodemother.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.example.aicodemother.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户 服务层实现。
 *
 * @author <a href="https://github.com/HHHH-LK">程序员LK</a>
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        //1、校验参数
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userPassword.length() < 8 || userPassword.length() > 16) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度不合法");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码不一致");
        }
        //2、校验账号是否已存在
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        long selectCount = this.mapper.selectCountByQuery(queryWrapper);
        if (selectCount > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号已存在");
        }
        //3、加密
        String encryptPassword = getEncryptPassword(userPassword);
        //4、保存用户信息
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("默认用户名");
        user.setUserRole(UserRoleEnum.USER.getValue());
        int insert = this.mapper.insert(user, true);
        if (insert <= 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "注册失败,数据库异常");
        }
        return user.getId();
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }


    @Override
    public String getEncryptPassword(String password) {
        if (StrUtil.isBlank(password)) {
            return null;
        }
        //盐值加密
        final String SALT = "lk_HHH";

        return DigestUtils.md5DigestAsHex((password + SALT).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        //1、校验账号密码是否合法
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userPassword.length() < 8 || userPassword.length() > 16) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度不合法");
        }
        //2、查询用户是否存在
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "账号不存在");
        }
        //3、校验密码是否正确
        String db_userPassword = user.getUserPassword();
        String encryptPassword = getEncryptPassword(userPassword);
        if (!db_userPassword.equals(encryptPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        //4、脱敏
        LoginUserVO loginUserVO = this.getLoginUserVO(user);
        //5、保存用户状态到session
        HttpSession session = request.getSession();
        session.setAttribute(USER_LOGIN_STATE, loginUserVO);
        return loginUserVO;

    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        //1、获取session
        LoginUserVO loginUserVOInfo = (LoginUserVO) request.getSession().getAttribute(USER_LOGIN_STATE);
        //2、判断用户是否登陆
        if (loginUserVOInfo == null || loginUserVOInfo.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        }
        //3、查询用户信息
        Long userId = loginUserVOInfo.getId();
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        return user;
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {

        //1、查询是否登陆
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        }
        //2、移除session
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;

    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream()
                .map(this::getUserVO)
                .collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .eq("userRole", userRole)
                .like("userAccount", userAccount)
                .like("userName", userName)
                .like("userProfile", userProfile)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }


}
