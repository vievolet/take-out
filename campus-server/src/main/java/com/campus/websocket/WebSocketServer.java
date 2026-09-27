package com.campus.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理端 WebSocket 服务：向浏览器推送来单提醒/催单等实时消息。
 *
 * 会话以连接时携带的随机 cid 为 key 存储（打包前端以 ws://host/ws/{cid} 连接，不带 token），
 * 当前店铺为单店场景，采用广播推送；ConcurrentHashMap 按 cid 管理会话，为将来"按指定客户端定向推送"预留扩展点。
 */
@Component
@Slf4j
public class WebSocketServer extends TextWebSocketHandler {

    /**
     * 已连接的管理端会话，key 为前端生成的随机 cid
     */
    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String cid = (String) session.getAttributes().get("cid");
        if (cid != null) {
            sessionMap.put(cid, session);
        }
        log.info("WebSocket 连接建立：cid={}, 当前连接数={}", cid, sessionMap.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String cid = (String) session.getAttributes().get("cid");
        if (cid != null) {
            sessionMap.remove(cid);
        }
        log.info("WebSocket 连接关闭：cid={}, 当前连接数={}", cid, sessionMap.size());
    }

    /**
     * 向所有已连接的管理端广播消息
     * @param message JSON 文本，格式：{"type":1,"orderId":x,"content":"订单号：xxx"}（type 1 来单提醒 / 2 催单）
     */
    public void sendToAllClient(String message) {
        for (WebSocketSession session : sessionMap.values()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                log.error("WebSocket 推送失败：session={}", session.getId(), e);
            }
        }
    }
}
