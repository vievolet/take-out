package com.campus.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WebSocket 会话管理与广播单测
 */
class WebSocketServerTest {

    @Test
    void sessionShouldBeAddedOnConnectAndRemovedOnClose() throws Exception {
        WebSocketServer server = new WebSocketServer();

        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("cid", "test-cid");
        when(session.getAttributes()).thenReturn(attributes);

        // 连接建立后广播应送达
        server.afterConnectionEstablished(session);
        server.sendToAllClient("{\"type\":1,\"orderId\":1}");
        verify(session, times(1)).sendMessage(any(TextMessage.class));

        // 连接关闭后不再收到广播
        server.afterConnectionClosed(session, CloseStatus.NORMAL);
        server.sendToAllClient("{\"type\":1,\"orderId\":2}");
        verify(session, times(1)).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendShouldNotThrowWhenSessionSendFails() throws Exception {
        WebSocketServer server = new WebSocketServer();

        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("cid", "bad-session");
        when(session.getAttributes()).thenReturn(attributes);
        doThrow(new java.io.IOException("模拟发送失败")).when(session).sendMessage(any(TextMessage.class));

        server.afterConnectionEstablished(session);
        // 单会话失败不影响其他会话，广播不抛异常
        server.sendToAllClient("{\"type\":1,\"orderId\":1}");
    }
}
