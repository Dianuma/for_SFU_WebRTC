package com.example.for_sfu_project.signaling;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

// 웹소켓 설정
@RequiredArgsConstructor
@Configuration
@EnableWebSocketMessageBroker
@EnableWebSocket // 웹 소켓 활성화
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer, WebSocketConfigurer  {

    private final SessionHandler sessionHandler;

    // WebRTC 를 위한 시그널링 서버 부분으로 요청타입에 따라 분기 처리
    // signal 로 요청이 왔을 때 아래의 signalingSocketHandler 가 동작하도록 registry 에 설정
    // 요청은 클라이언트 접속, close, 메시지 발송 등에 대해 특정 메서드를 호출한다

    // STOMP 프로토콜을 위한 엔드포인트 등록
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-stomp");
//                .withSockJS();
    }

    // WebSocket 엔드포인트 등록
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(sessionHandler, "/signal")  // signal 엔드포인트 등록
                .setAllowedOriginPatterns("*"); // CORS 설정 (모든 도메인 허용)
//                .setAllowedOrigins("http://localhost:63342/") // CORS 설정
//                .withSockJS(); // SockJS 설정
    }

    // SignalHandler Bean 등록
//    @Bean
//    public WebSocketHandler signalHandler() {
//        return new WebSocketHandler() {
//            @Override
//            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
//                // 웹소켓이 연결되면 실행되는 메소드
//            }
//
//            @Override
//            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
//                // 웹소켓으로 메시지가 수신되었을 때 처리
//            }
//
//            @Override
//            public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
//                // 전송 에러 발생시 처리
//            }
//
//            @Override
//            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
//                // 웹소켓 연결이 닫히면 실행되는 메소드
//            }
//
//            @Override
//            public boolean supportsPartialMessages() {
//                return false; // 메시지의 부분 전송을 지원할지 여부
//            }
//        };
//    }
}
