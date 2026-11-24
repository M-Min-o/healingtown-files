# 힐링타운 채팅창 (Fabric 1.21.4 클라이언트 모드)

바닐라 채팅 대신 쓰는 채팅창입니다.

- 채팅을 열면(T): 위 탭 줄을 끌어 **옮기기**, 오른쪽 아래 모서리로 **크기 조절**, 휠로 스크롤
- 탭: 전체 · 공지 · 일반 · 귓속말 · 파티 · 시스템 · 자랑 · 접속·퇴장 (골드·경험치는 탭 없이 전체에만, 보상 · 획득 메시지는 시스템) (안 읽은 채팅이 있으면 노란 점)
- 자물쇠: 위치 잠금 / 톱니바퀴: 채팅 설정
  - 전체 탭에 보일 채팅 (종류마다 켜기 · 끄기)
  - 배경 불투명도 · 글자 크기 · 표시 시간 · 종류별 기록 개수 · 항상 표시 · 시간 표시 · 위치 잠금 · 위치·크기 처음대로
- 채팅 글자에 마우스 → 아이템 정보 등, 클릭 → 명령어 실행 (예: /자랑, /레벨보상 안내)
- 설정 파일: `.minecraft/config/healingtown-chat.json` (`enabled: false` 면 바닐라 채팅으로)
  - `rules`: 시스템 메시지를 종류로 나누는 규칙 (서버 TotalSystemPlugin `chat-channels.rules` 와 같은 기본값)

## 빌드 (GitHub Actions)

`healingtown-files` 저장소에 이 폴더를 `chat-mod/` 로 올리면
`.github/workflows/build-chat-mod.yml` 이 자동으로 jar 를 만들어 `mods/` 에 넣고 모드 목록(distribution.json)까지 갱신합니다.

직접 빌드: JDK 21 + Gradle 8.14 → `gradle build` → `build/libs/healingtown-chat-1.0.0.jar`
