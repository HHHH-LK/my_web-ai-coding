package com.example.aicodemother.service.impl;

import cn.hutool.core.util.StrUtil;
import com.example.aicodemother.constant.UserConstant;
import com.example.aicodemother.exception.ErrorCode;
import com.example.aicodemother.exception.ThrowUtils;
import com.example.aicodemother.model.dto.chathistory.ChatHistoryQueryRequest;
import com.example.aicodemother.model.entity.App;
import com.example.aicodemother.model.entity.User;
import com.example.aicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.example.aicodemother.service.AppService;
import com.github.xiaoymin.knife4j.core.util.CollectionUtils;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.example.aicodemother.model.entity.ChatHistory;
import com.example.aicodemother.mapper.ChatHistoryMapper;
import com.example.aicodemother.service.ChatHistoryService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 对话历史 服务层实现。
 *
 * @author <a href="https://github.com/HHHH-LK">程序员LK</a>
 */
@Service
@Slf4j
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory> implements ChatHistoryService {

    @Resource
    @Lazy
    private AppService appService;

    @Override
    public boolean addChatMessage(Long appId, String message, String messageType, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId < 0, ErrorCode.PARAMS_ERROR, "appId不能为空");
        ThrowUtils.throwIf(message == null || message.isEmpty(), ErrorCode.PARAMS_ERROR, "消息不能为空");
        ThrowUtils.throwIf(messageType == null || messageType.isEmpty(), ErrorCode.PARAMS_ERROR, "消息类型不能为空");
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null || loginUser.getId() < 0, ErrorCode.PARAMS_ERROR, "用户异常");
        ChatHistoryMessageTypeEnum enumByValue = ChatHistoryMessageTypeEnum.getEnumByValue(messageType);
        ThrowUtils.throwIf(enumByValue == null, ErrorCode.PARAMS_ERROR, "消息类型异常");
        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setAppId(appId);
        chatHistory.setMessage(message);
        chatHistory.setMessageType(messageType);
        chatHistory.setUserId(loginUser.getId());
        return this.save(chatHistory);
    }

    @Override
    public boolean deleteChatHistory(Long appId) {
        ThrowUtils.throwIf(appId == null || appId < 0, ErrorCode.PARAMS_ERROR, "appId不能为空");
        QueryWrapper removeWrapper = new QueryWrapper();
        removeWrapper.eq(ChatHistory::getAppId, appId);
        return this.remove(removeWrapper);
    }

    /**
     * 获取查询包装类
     *
     * @param chatHistoryQueryRequest
     * @return
     */
    @Override
    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) {
        QueryWrapper queryWrapper = QueryWrapper.create();
        if (chatHistoryQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chatHistoryQueryRequest.getId();
        String message = chatHistoryQueryRequest.getMessage();
        String messageType = chatHistoryQueryRequest.getMessageType();
        Long appId = chatHistoryQueryRequest.getAppId();
        Long userId = chatHistoryQueryRequest.getUserId();
        LocalDateTime lastCreateTime = chatHistoryQueryRequest.getLastCreateTime();
        String sortField = chatHistoryQueryRequest.getSortField();
        String sortOrder = chatHistoryQueryRequest.getSortOrder();
        // 拼接查询条件
        queryWrapper.eq("id", id)
                .like("message", message)
                .eq("messageType", messageType)
                .eq("appId", appId)
                .eq("userId", userId);
        // 游标查询逻辑 - 只使用 createTime 作为游标
        if (lastCreateTime != null) {
            queryWrapper.lt("createTime", lastCreateTime);
        }
        // 排序
        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        } else {
            // 默认按创建时间降序排列
            queryWrapper.orderBy("createTime", false);
        }
        return queryWrapper;
    }

    /**
     * 分页查询应用的对话历史记录
     *
     * @param appId          应用ID，用于指定要查询的应用
     * @param pageSize       每页显示的记录数，必须在1-50之间
     * @param lastCreateTime 最后一次创建时间，用于分页查询
     * @param loginUser      当前登录用户，用于权限验证
     * @return 返回分页后的对话历史记录
     */
    @Override
    public Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize, LocalDateTime lastCreateTime, User loginUser) {
        // 验证应用ID参数是否有效
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        // 验证分页大小参数是否在有效范围内
        ThrowUtils.throwIf(pageSize <= 0 || pageSize > 50, ErrorCode.PARAMS_ERROR, "页面大小必须在1-50之间");
        // 验证用户是否已登录
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        // 验证权限：只有应用创建者和管理员可以查看
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        boolean isCreator = app.getUserId().equals(loginUser.getId());
        ThrowUtils.throwIf(!isAdmin && !isCreator, ErrorCode.NO_AUTH_ERROR, "无权查看该应用的对话历史");
        // 构建查询条件
        ChatHistoryQueryRequest queryRequest = new ChatHistoryQueryRequest();
        queryRequest.setAppId(appId);
        queryRequest.setLastCreateTime(lastCreateTime);
        QueryWrapper queryWrapper = this.getQueryWrapper(queryRequest);
        // 查询数据
        return this.page(Page.of(1, pageSize), queryWrapper);
    }

    @Override
    public int LoadChatHistoryToMemory(Long appId, MessageWindowChatMemory messageWindowChatMemory, int maxCount) {
        ThrowUtils.throwIf(appId == null || appId < 0, ErrorCode.PARAMS_ERROR, "appId不能为空");
        ThrowUtils.throwIf(messageWindowChatMemory == null, ErrorCode.PARAMS_ERROR, "messageWindowChatMemory不能为空");
        ThrowUtils.throwIf(maxCount <= 0, ErrorCode.PARAMS_ERROR, "最大加载数量不能小于等于0");
        // 构建查询条件
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq(ChatHistory::getAppId, appId);
        queryWrapper.orderBy(ChatHistory::getCreateTime, false);
        queryWrapper.limit(1, maxCount);
        try {
            List<ChatHistory> chatHistoryList = this.list(queryWrapper);
            if (CollectionUtils.isEmpty(chatHistoryList)) return 0;
            // 将数据加载到内存中
            AtomicInteger loadCount = new AtomicInteger();
            //清理历史缓存，防止重复加载
            messageWindowChatMemory.clear();
            chatHistoryList.reversed().stream().map(chatMemory -> {
                ChatHistoryMessageTypeEnum chatHistoryMessageTypeEnum = ChatHistoryMessageTypeEnum.valueOf(chatMemory.getMessageType());
                return switch (chatHistoryMessageTypeEnum) {
                    case USER -> UserMessage.from(chatMemory.getMessage());
                    case AI -> AiMessage.from(chatMemory.getMessage());
                    default -> throw new RuntimeException("未知消息类型");
                };
            }).forEach(message -> {
                messageWindowChatMemory.add(message);
                loadCount.incrementAndGet();
            });
            log.info("成功为appId {} 加载 {} 条历史记录到内存中", appId, loadCount.get());
            return loadCount.get();
        } catch (Exception e) {
            log.error("加载appId {} 历史记录到内存中失败", appId, e);
            return 0;
        }
    }


}
