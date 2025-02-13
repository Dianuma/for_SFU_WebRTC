package com.example.for_sfu_project.signaling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.io.IOException;
import java.util.*;

// 기능 : WebRTC를 위한 시그널링 서버 부분으로 요청타입에 따라 분기 처리
@Slf4j
@Component
public class SessionHandler extends TextWebSocketHandler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final SessionRepository sessionRepositoryRepo = SessionRepository.getInstance();  // 세션 데이터 저장소
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 시그널링에 사용되는 메시지 타입
    // join_room : 방 입장
    private static final String MSG_TYPE_JOIN_ROOM = "join_room";
    // offer : 제안
    private static final String MSG_TYPE_OFFER = "offer";
    // answer : 응답
    private static final String MSG_TYPE_ANSWER = "answer";
    // candidate : 새로운 ICE 후보자
    private static final String MSG_TYPE_CANDIDATE = "candidate";
    // room_list : 방 목록
    private static final String MSG_TYPE_ROOM_LIST = "room_list";

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        // 웹소켓이 연결되면 실행되는 메소드
        log.info(">>> [ws] 클라이언트 접속 : 세션 - {}", session);
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, TextMessage textMessage) {
        // 웹소켓으로 메시지가 수신되었을 때 처리
        log.info(">>> [ws] 메시지 수신 : 세션 - {}, 메시지 - {}", session, textMessage.getPayload());
        try {
            // 받은 메세지를 WebSocketMessage 객체로 변환
            WebSocketMessage message = objectMapper.readValue(textMessage.getPayload(), WebSocketMessage.class);
            // 메세지의 정보를 추출
            String userName = message.getSender();
            String data = message.getData();
            Long roomId = message.getRoomId();

            log.info("==========session.Id : {}, getType : {}, getData : {}, getRoomId : {}", session.getId(), message.getType(), data, roomId.toString());

            switch (message.getType()) {
                // 처음 입장
                case MSG_TYPE_JOIN_ROOM:
                    if (sessionRepositoryRepo.hasRoom(roomId)) {
                        // 해당 챗룸이 존재하면

                        // 로그를 통해 방이 있음을 알리고 해당 방의 클라이언트 리스트를 출력
                        log.info("==========join 0 : 방 있음 : {}", roomId);
                        log.info("==========join 1 : (join 전) Client List - \n {} \n", Optional.ofNullable(sessionRepositoryRepo.getClientList(roomId)));

                        // 세션 저장 1) : 게임방 안의 session List에 새로운 Client session정보를 저장
                        sessionRepositoryRepo.addClient(roomId, session);

                    } else {
                        // 해당 챗룸이 존재하지 않으면
                        log.info("==========join 0 : 방 없음 : {}", roomId);

                        // 세션 저장 1) : 새로운 게임방 정보와 새로운 Client session정보를 저장
                        sessionRepositoryRepo.addClientInNewRoom(roomId, session);
                    }

                    // 방안 참가자들의 세션 정보 출력
                    log.info("==========join 2 : (join 후) Client List - \n {} \n", Optional.ofNullable(sessionRepositoryRepo.getClientList(roomId)));

                    // 세션 저장 2) : 이 세션이 어느 방에 들어가 있는지 저장
                    sessionRepositoryRepo.saveRoomIdToSession(session, roomId);

                    log.info("==========join 3 : 지금 세션이 들어간 방 : {}", Optional.ofNullable(sessionRepositoryRepo.getRoomId(session)));

                    // 방안 참가자 중 자신을 제외한 나머지 사람들의 Session ID를 List로 저장
                    List<String> exportClientList = new ArrayList<>();
                    for (Map.Entry<String, WebSocketSession> entry : sessionRepositoryRepo.getClientList(roomId).entrySet()) {
                        if (entry.getValue() != session) {
                            exportClientList.add(entry.getKey());
                        }
                    }

                    log.info("==========join 4 : allUsers로 Client List : {}", exportClientList);

                    log.info(">> [ws] room_id : {}", sessionRepositoryRepo.hasRoom(roomId));

                    // 접속한 본인에게 방안 참가자들 정보를 전송
                    sendMessage(session,
                            new WebSocketMessage().builder()
                                    .type("all_users")
                                    .sender(userName)
                                    .data(message.getData())
                                    .allUsers(exportClientList)
                                    .candidate(message.getCandidate())
                                    .sdp(message.getSdp())
                                    .build());

                    break;
                case MSG_TYPE_ROOM_LIST:
                    // 방 목록 요청
                    log.info("==========0 : 방 목록 요청 : {}", message.getType());
                    log.info("==========1 : 방 목록 : {}", sessionRepositoryRepo.getAllRoomId());

                    // 방 목록을 요청한 클라이언트에게 방 목록을 전송
                    sendMessage(session,
                            new WebSocketMessage().builder()
                                    .type("room_list")
                                    .sender(userName)
                                    .data(sessionRepositoryRepo.getAllRoomId().toString())
                                    .allUsers(null)
                                    .candidate(null)
                                    .sdp(null)
                                    .roomId(null)
                                    .build());
                    break;
                case MSG_TYPE_OFFER:
                case MSG_TYPE_ANSWER:
                case MSG_TYPE_CANDIDATE:

                    if (sessionRepositoryRepo.hasRoom(roomId)) {
                        Map<String, WebSocketSession> clientList = sessionRepositoryRepo.getClientList(roomId);

                        log.info("=========={} 5 : 보내는 사람 - {}, 받는 사람 - {}", message.getType(), session.getId(), message.getReceiver());

                        if (clientList.containsKey(message.getReceiver())) {
                            WebSocketSession ws = clientList.get(message.getReceiver());
                            sendMessage(ws,
                                    new WebSocketMessage().builder()
                                            .type(message.getType())
                                            .sender(session.getId())            // 보낸사람 session Id
                                            .receiver(message.getReceiver())    // 받을사람 session Id
                                            .data(message.getData())
                                            .offer(message.getOffer())
                                            .answer(message.getAnswer())
                                            .candidate(message.getCandidate())
                                            .sdp(message.getSdp())
                                            .build());
                        }
                    }
                    break;

                default:
                    log.info("======================================== DEFAULT");
                    log.info("============== 들어온 타입 : " + message.getType());

            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void afterConnectionClosed(final WebSocketSession session, @NonNull final CloseStatus status) {
        // 웹소켓 연결이 끊어지면 실행되는 메소드
        log.info("======================================== 웹소켓 연결 해제 : {}", session.getId());
        // 끊어진 세션이 어느방에 있었는지 조회
        Long roomId = Optional.ofNullable(sessionRepositoryRepo.getRoomId(session)).orElseThrow(
                () -> new IllegalArgumentException("해당 세션이 있는 방정보가 없음!")
        );

        // 1) 방 참가자들 세션 정보들 사이에서 삭제
        log.info("==========leave 1 : (삭제 전) Client List - \n {} \n", Optional.ofNullable(sessionRepositoryRepo.getClientList(roomId)));
        sessionRepositoryRepo.deleteClient(roomId, session);
        log.info("==========leave 2 : (삭제 후) Client List - \n {} \n", Optional.ofNullable(sessionRepositoryRepo.getClientList(roomId)));


        // 2) 별도 해당 참가자 세션 정보도 삭제
        log.info("==========leave 3 : (삭제 전) roomId to Session - \n {} \n", Optional.ofNullable(sessionRepositoryRepo.searchRoomIdToSession(roomId)));
        sessionRepositoryRepo.deleteRoomIdToSession(session);
        log.info("==========leave 4 : (삭제 후) roomId to Session - \n {} \n", Optional.ofNullable(sessionRepositoryRepo.searchRoomIdToSession(roomId)));


        // 본인 제외 모두에게 전달
        Map<String, WebSocketSession> clientList = Optional.ofNullable(sessionRepositoryRepo.getClientList(roomId))
                .orElseThrow(() -> new IllegalArgumentException("clientList 없음"));
        for(Map.Entry<String, WebSocketSession> oneClient : clientList.entrySet()){
            sendMessage(oneClient.getValue(),
                    new WebSocketMessage().builder()
                            .type("leave")
                            .sender(session.getId())
                            .receiver(oneClient.getKey())
                            .build());
        }

    }

    // 메세지 발송
    private void sendMessage(WebSocketSession session, WebSocketMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            log.info("========== 발송 to : {}", session.getId());
            log.info("========== 발송 내용 : {}", json);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.info("============== 발생한 에러 메세지: {}", e.getMessage());
        }
    }
}
