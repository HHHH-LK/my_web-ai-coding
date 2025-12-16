#!/bin/bash

# API 连接测试脚本

echo "=========================================="
echo "DashScope API 连接测试"
echo "=========================================="
echo ""

# 1. 检查环境变量
echo "1. 检查 DASHSCOPE_API_KEY 环境变量..."
if [ -z "$DASHSCOPE_API_KEY" ]; then
    echo "❌ 错误：DASHSCOPE_API_KEY 未设置"
    echo "   请运行: export DASHSCOPE_API_KEY='your-api-key'"
    exit 1
else
    echo "✅ DASHSCOPE_API_KEY 已设置"
    # 只显示前 8 位，保护隐私
    masked_key="${DASHSCOPE_API_KEY:0:8}..."
    echo "   Key: $masked_key"
fi
echo ""

# 2. 检查网络连接
echo "2. 检查网络连接..."
if curl -s --max-time 10 https://dashscope.aliyuncs.com > /dev/null 2>&1; then
    echo "✅ 网络连接正常"
else
    echo "❌ 无法连接到 DashScope API"
    echo "   请检查网络连接和防火墙设置"
    exit 1
fi
echo ""

# 3. 测试 API 调用
echo "3. 测试 API 调用..."
response=$(curl -s -w "\nHTTP_STATUS:%{http_code}" --max-time 30 \
  -X POST "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation" \
  -H "Authorization: Bearer $DASHSCOPE_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "deepseek-v3",
    "input": {
      "messages": [
        {
          "role": "user",
          "content": "你好"
        }
      ]
    },
    "parameters": {
      "max_tokens": 100
    }
  }')

# 提取 HTTP 状态码
http_status=$(echo "$response" | grep "HTTP_STATUS" | cut -d: -f2)
response_body=$(echo "$response" | sed '/HTTP_STATUS/d')

if [ "$http_status" = "200" ]; then
    echo "✅ API 调用成功 (HTTP $http_status)"
    echo "   响应: ${response_body:0:100}..."
else
    echo "❌ API 调用失败 (HTTP $http_status)"
    echo "   响应: $response_body"
    
    # 检查常见错误
    if echo "$response_body" | grep -q "InvalidApiKey"; then
        echo ""
        echo "💡 可能的原因："
        echo "   - API Key 无效或已过期"
        echo "   - API Key 权限不足"
    elif echo "$response_body" | grep -q "QuotaExceeded"; then
        echo ""
        echo "💡 可能的原因："
        echo "   - API 调用配额已用完"
        echo "   - 需要充值或等待配额重置"
    fi
    exit 1
fi
echo ""

# 4. 检查 Java 版本
echo "4. 检查 Java 版本..."
if command -v java &> /dev/null; then
    java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
    echo "✅ Java 版本: $java_version"
    
    # 检查是否是 Java 21+
    major_version=$(echo "$java_version" | cut -d'.' -f1)
    if [ "$major_version" -ge 21 ]; then
        echo "   ⚠️  使用 Java 21+，建议配置 -XX:+EnableDynamicAgentLoading"
    fi
else
    echo "❌ Java 未安装"
    exit 1
fi
echo ""

# 5. 检查 Maven
echo "5. 检查 Maven..."
if command -v mvn &> /dev/null; then
    mvn_version=$(mvn -version | head -n 1 | cut -d' ' -f3)
    echo "✅ Maven 版本: $mvn_version"
else
    echo "⚠️  Maven 未安装（可选）"
fi
echo ""

echo "=========================================="
echo "✅ 所有检查完成！"
echo "=========================================="
echo ""
echo "💡 建议："
echo "1. 如果仍然遇到 EOF 异常，请检查："
echo "   - API 配额是否充足"
echo "   - 网络连接是否稳定"
echo "   - 请求内容是否触发了安全策略"
echo ""
echo "2. 运行测试："
echo "   mvn test -Dtest=AiCodeGeneratorFacadeTest#generateVueProjectCodeStream"
echo ""

