package com.campus.config;

import com.campus.websocket.WebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Collections;
import java.util.Map;

/**
 * WebSocket 配置：注册 /ws/{cid} 处理器，与打包版管理端前端的连接协议保持一致。
 *
 * 设计取舍（面试话术）：打包前端以随机 cid 连接、不带 token，因此握手阶段不校验 JWT。
 * 来单提醒消息只含订单号等低敏信息且属于内网管理端，风险可控；生产化会在连接参数中携带
 * token 并在 beforeHandshake 里校验。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {

    @Autowired
    private WebSocketServer webSocketServer;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketServer, "/ws/{cid}")
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
                        // 从 URI 模板变量中取出 cid，作为会话 key 存入 attributes
                        Map<String, String> uriVars = (Map<String, String>) attributes.getOrDefault(
                                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Collections.emptyMap());
                        attributes.put("cid", uriVars.get("cid"));
                        return true;
                    }

                    @Override
                    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                               WebSocketHandler wsHandler, Exception exception) {
                    }
                })
                .setAllowedOrigins("*");
    }
}
