package com.example.for_sfu_project.signaling;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.stream.Collectors;

// 웹소켓에 필요한 세션 정보를 저장, 관리 ( 싱글톤 )
@Slf4j
@Component
@NoArgsConstructor
public class SessionRepository {
    private static volatile SessionRepository sessionRepositoryRepo; // volatile 를 통해 멀티스레드 환경에서의 가시성 보장 - 캐시 메모리에 캐싱되지 않고 항상 메인 메모리에서 읽어옴

    // 세션 저장 1) clientsInRoom : 방 Id를 key 값으로 하여 방마다 가지고 있는 Client들의 session Id 와 session 객체를 저장
    private final Map<Long, Map<String, WebSocketSession>> clientsInRoom = new HashMap<>();

    // 세션 저장 2) roomIdToSession : 참가자들 각각의 데이터로 session 객체를 key 값으로 하여 해당 객체가 어느방에 속해있는지를 저장
    private final Map<WebSocketSession, Long> roomIdToSession = new HashMap<>();

    // Session 데이터를 공통으로 사용하기 위해 싱글톤으로 구현
    public static SessionRepository getInstance(){
        log.info("========== SessionRepository getInstance() 호출");
        if(sessionRepositoryRepo == null){ // 인스턴스가 없는 경우
            synchronized (SessionRepository.class){ // 멀티스레드 환경에서 동시 접근 방지를 위한 동기화 블럭
                if ( sessionRepositoryRepo == null ) // 더블 락킹 체크
                    sessionRepositoryRepo = new SessionRepository(); // SessionRepository 객체를 한 번만 생성
            }
        }
        return sessionRepositoryRepo; // 생성 이후 같은 객체 반환
    }

    // 해당 방의 ClientList 조회
    public Map<String, WebSocketSession> getClientList(Long roomId) {
        return clientsInRoom.get(roomId);
    }

    // 해당 방 존재 유무 조회
    public boolean hasRoom(Long roomId){
        return clientsInRoom.containsKey(roomId);
    }

    // 해당 session이 어느방에 있는지 조회
    public Long getRoomId(WebSocketSession session){
        return roomIdToSession.get(session);
    }

    // 모든 방 아이디 조회
    public Set<Long> getAllRoomId() { return clientsInRoom.keySet(); }

    // 새로운 Room 생성 및 첫 번째 Client session 정보 추가
    public void addClientInNewRoom(Long roomId, WebSocketSession session) {
        Map<String, WebSocketSession> newClient = new HashMap<>();
        newClient.put(session.getId(), session);
        clientsInRoom.put(roomId, newClient);
    }

    // 끊어진 Client session 하나만 지우고 다시 저장
    public void deleteClient(Long roomId, WebSocketSession session) {
        Map<String, WebSocketSession> clientList = clientsInRoom.get(roomId);
        String removeKey = "";
        for(Map.Entry<String, WebSocketSession> oneClient : clientList.entrySet()){
            if(oneClient.getKey().equals(session.getId())){
                removeKey = oneClient.getKey();
            }
        }
        log.info("========== 지워질 session id : {}", removeKey);
        clientList.remove(removeKey);

        // 끊어진 세션을 제외한 나머지 세션들을 다시 저장
        clientsInRoom.put(roomId, clientList);
    }

    // 새로운 Client session 추가
    public void addClient(Long roomId, WebSocketSession session) {
        clientsInRoom.get(roomId).put(session.getId(), session);
    }

    // 방정보 모두 삭제 (방 폭파시 연계 작동)
    public void deleteAllclientsInRoom(Long roomId){
        clientsInRoom.remove(roomId);
    }

    public Map<WebSocketSession, Long> searchRoomIdToSession(Long roomId) {
        return Optional.of(roomIdToSession.entrySet()
                        .stream()
                        .filter(entry -> Objects.equals(entry.getValue(), roomId)) // roomIdToSession 에서 roomId 와 일치하는 entry 찾기
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))) // 찾은 entry 반환
                .orElseThrow(
                        () -> new IllegalArgumentException("RoodIdToSession 정보 없음!")
                );
    }

    public void saveRoomIdToSession(WebSocketSession session, Long roomId) {
        roomIdToSession.put(session, roomId);
    }

    public void deleteRoomIdToSession(WebSocketSession session) {
        roomIdToSession.remove(session);
    }
}
