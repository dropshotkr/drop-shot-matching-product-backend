# DROP SHOT Matching Backend

Kotlin Spring Boot + JPA 백엔드입니다.

## 기본 실행

```bash
./gradlew bootRun
```

기본 DB는 H2 file DB입니다. 최초 실행 시 `src/main/resources/drop-shot-players.csv`를 사람 명단으로 seed 합니다.

## 테스트

```bash
./gradlew test
```

API 통합 테스트는 멤버 관리, 이벤트 조회 실패, 참가자 등록, 파트너 연결, 조 생성, 완료 체크를 검증합니다.

## MySQL 로컬 실행

```bash
docker compose up -d mysql

SPRING_PROFILES_ACTIVE=mysql \
DB_URL='jdbc:mysql://localhost:3306/dropshot?serverTimezone=Asia/Seoul&characterEncoding=UTF-8' \
DB_USERNAME=dropshot \
DB_PASSWORD=dropshot \
./gradlew bootRun
```

## 주요 API

```text
GET    /api/members?q=
POST   /api/members
PUT    /api/members/{id}
DELETE /api/members/{id}

POST   /api/events
GET    /api/events/{eventId}
POST   /api/events/{eventId}/participants
DELETE /api/events/{eventId}/participants/{participantId}
POST   /api/events/{eventId}/partners
DELETE /api/events/{eventId}/partners/{partnerId}
POST   /api/events/{eventId}/groups/generate
POST   /api/events/{eventId}/groups/replan
PATCH  /api/events/{eventId}/groups/{groupId}/complete
```
