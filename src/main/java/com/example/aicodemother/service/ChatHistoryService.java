package com.example.aicodemother.service;

import com.example.aicodemother.model.dto.chathistory.ChatHistoryQueryRequest;
import com.example.aicodemother.model.entity.User;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.example.aicodemother.model.entity.ChatHistory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author <a href="https://github.com/HHHH-LK">程序员LK</a>
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 添加聊天消息的方法
     *
     * @param appId       应用ID，用于标识消息所属的应用
     * @param message     具体的聊天消息内容
     * @param messageType 消息类型，用于区分不同类型的消息
     * @param loginUser   当前登录用户信息，用于记录消息发送者
     * @return 添加消息成功返回true，失败返回false
     */
    boolean addChatMessage(Long appId, String message, String messageType, User loginUser);


    /**
     * 删除聊天历史记录的方法
     *
     * @param appId 应用程序ID，用于标识特定的应用程序
     * @return 返回一个布尔值，表示删除操作是否成功执行
     * true表示删除成功
     * false表示删除失败
     */
    boolean deleteChatHistory(Long appId);

    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                               LocalDateTime lastCreateTime,
                                               User loginUser);

    int LoadChatHistoryToMemory(Long appId, MessageWindowChatMemory messageWindowChatMemory, int maxCount);
}
