# 故障排查指南

## 1. EOF 异常（EOFException）

### 问题描述
在调用阿里云 DashScope API 时出现 `java.io.EOFException` 异常：
```
com.alibaba.dashscope.exception.ApiException: {"statusCode":200,"message":"","code":"response_error","isJson":false}
Caused by: java.io.EOFException
```

### 可能的原因

#### 1.1 API Token 配额不足
- **检查方法**：登录阿里云控制台，查看 DashScope API 的调用配额
- **解决方案**：充值或等待配额重置

#### 1.2 网络连接问题
- **检查方法**：
  ```bash
  # 测试网络连接
  curl -v https://dashscope.aliyuncs.com
  ```
- **解决方案**：
  - 检查网络代理设置
  - 检查防火墙规则
  - 切换网络环境

#### 1.3 请求超时
- **检查方法**：查看日志中的请求耗时
- **解决方案**：已在代码中增加超时配置
  - 普通模型：3 分钟
  - 推理模型（deepseek-r1）：5 分钟

#### 1.4 模型返回内容过大
- **检查方法**：检查 `max-tokens` 配置
- **解决方案**：
  ```yaml
  # application.yml
  langchain4j:
    community:
      dashscope:
        streaming-chat-model:
          chat-model:
            max-tokens: 8192  # 根据需要调整
  ```

#### 1.5 API 服务端问题
- **检查方法**：查看阿里云服务状态页面
- **解决方案**：等待服务恢复或联系阿里云技术支持

### 解决步骤

#### 步骤 1：验证 API Key
```bash
# 检查环境变量是否设置
echo $DASHSCOPE_API_KEY

# 如果未设置，请设置环境变量
export DASHSCOPE_API_KEY="your-api-key"
```

#### 步骤 2：测试 API 连接
```java
// 创建一个简单的测试类
@Test
void testApiConnection() {
    QwenStreamingChatModel model = QwenStreamingChatModel.builder()
            .apiKey(System.getenv("DASHSCOPE_API_KEY"))
            .modelName("deepseek-v3")
            .timeout(Duration.ofMinutes(3))
            .build();
    
    // 发送简单的测试请求
    // ...
}
```

#### 步骤 3：启用详细日志
```yaml
# application.yml
logging:
  level:
    dev.langchain4j: DEBUG
    com.alibaba.dashscope: DEBUG
    com.example.aicodemother: DEBUG
```

#### 步骤 4：检查请求内容
- 确保用户输入不包含敏感内容
- 确保请求长度在合理范围内
- 检查是否触发了内容安全策略

### 已实施的修复

1. **增加超时配置**
   - 普通流式模型：3 分钟
   - 推理模型：5 分钟

2. **增强错误处理**
   - 添加详细的错误日志
   - 捕获并识别 EOF 异常
   - 提供诊断建议

3. **改进异常处理**
   - 在每个回调中添加 try-catch
   - 防止部分错误导致整个流中断

## 2. Mockito 警告

### 问题描述
```
WARNING: A Java agent has been loaded dynamically
WARNING: Dynamic loading of agents will be disallowed by default in a future release
```

### 原因
- JDK 21+ 对动态加载 Java Agent 有更严格的限制
- Mockito 的 inline-mock-maker 需要动态加载 Agent

### 解决方案
已在 `pom.xml` 中配置 Maven Surefire 插件：
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <argLine>
            -XX:+EnableDynamicAgentLoading
        </argLine>
    </configuration>
</plugin>
```

## 3. 通用调试技巧

### 3.1 启用请求重试
可以考虑在应用层添加重试逻辑：
```java
@Retryable(
    value = {ApiException.class, EOFException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 2000)
)
public Flux<String> generateCodeWithRetry(...) {
    // ...
}
```

### 3.2 监控和告警
- 添加 API 调用成功率监控
- 设置错误告警阈值
- 记录每次调用的耗时

### 3.3 降级策略
当主模型不可用时，考虑切换到备用模型：
```java
try {
    return primaryModel.generate(prompt);
} catch (ApiException e) {
    log.warn("主模型调用失败，切换到备用模型", e);
    return fallbackModel.generate(prompt);
}
```

## 4. 常见问题 FAQ

### Q: 为什么只有 Vue 项目生成会出错？
A: Vue 项目使用 `deepseek-r1` 推理模型，该模型：
- 需要更长的处理时间
- 返回的内容可能更复杂
- 对网络稳定性要求更高

### Q: 如何临时禁用 Vue 项目生成？
A: 在测试类中注释掉相关测试：
```java
// @Test
void generateVueProjectCodeStream() {
    // ...
}
```

### Q: 是否可以切换到其他模型？
A: 可以，修改配置：
```java
@Bean
public QwenStreamingChatModel reasoningStreamingChatModel() {
    return QwenStreamingChatModel.builder()
            .apiKey(System.getenv("DASHSCOPE_API_KEY"))
            .modelName("deepseek-v3")  // 改为其他模型
            .maxTokens(32768)
            .timeout(Duration.ofMinutes(5))
            .build();
}
```

## 5. 联系支持

如果以上方法都无法解决问题，请：
1. 收集完整的错误日志
2. 记录问题发生的时间和频率
3. 检查阿里云 DashScope 服务状态
4. 联系阿里云技术支持或提交工单

