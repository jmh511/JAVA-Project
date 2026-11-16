# Desktop Planner

일정관리 플래너를 스마트폰 위젯처럼 Windows 바탕화면에 띄워 두는 Java Swing 프로그램입니다. (자바 기말 프로젝트)

## 실행 방법

`run.bat`을 더블클릭하면 컴파일 후 실행됩니다.

- 본 프로그램: **JDK 17 이상**
- 바탕화면 모드(보드를 바탕화면 층에 붙이기, 아이콘 피하기 등): **Windows + JDK 22 이상**
  - JDK 22 미만이면 자동으로 일반 창으로 실행됩니다.
- 외부 라이브러리 없음 (Windows 기능은 JDK 표준 Foreign Function API 사용)
- 데이터는 `data/planner.txt`에 저장됩니다 (메모장으로 열어 볼 수 있음).

## 주요 기능

- **위젯 5종**: 캘린더, 오늘 타임테이블, 주요 일정(Highlights), 체크리스트, 투두리스트
- **격자 보드**: 위젯을 칸 단위로 배치. 편집 모드에서 끌어서 이동/크기 조절, 놓으면 가까운 칸으로 부드럽게 정렬
- **보드 크기 조절**: 편집 모드에서 보드 테두리를 끌어 조절
- **위젯 간 데이터 연동**: 일정 하나를 입력하면 관련 위젯에 동시에 반영 (옵저버 패턴)
- **크기에 따른 표시 조절**: 체크리스트 1/3/7일, 투두 마감일 표시 등
- **바탕화면 모드** (Windows)
  - 클릭해도 다른 창 위로 올라오지 않고 항상 바탕화면 층에 머무름
  - 배경은 투명, 편집할 때만 30% 배경과 격자 표시
  - 바탕화면 아이콘(파일)을 가리지 않음: 빈자리로 이동하거나 아이콘 부분을 뚫어서 보이게 함
  - 설정에서 **바탕화면 아이콘 격자에 맞추기** (칸 크기 = 아이콘 간격, 보드 = 바탕화면 전체)
- **설정**: 위젯 투명도(30~100%), 글씨는 100% 유지 옵션, 보드 크기

## 사용법

- 위젯 위에서 **오른쪽 클릭**: 일정/할 일 추가, Edit layout, Settings, Exit
- 보드 크기: Edit layout에서 보드 테두리를 끌어 조절 (Settings에서 바탕화면 격자 맞춤 / 아이콘 사이 빈 공간에 맞춤)
- 위젯 투명도, 글씨 선명도: Settings
- 작업 표시줄 알림 영역 아이콘 메뉴로도 같은 기능 사용 가능
- 편집 모드 종료: **Done** 버튼

## 구조 (`src/planner`)

| 구분 | 클래스 |
|---|---|
| 시작 | `Main` |
| 데이터 | `DataStore`, `Event`, `Todo`, `ChecklistItem`, `WidgetLayout` |
| 보드/창 | `BoardFrame`, `BoardPanel`, `EditOverlay`, `SnapAnimator` |
| 위젯 | `WidgetPanel`(공통 부모), `CalendarWidget`, `TimetableWidget`, `HighlightsWidget`, `ChecklistWidget`, `TodoWidget` |
| 입력 창 | `EventDialog`, `TodoDialog`, `DayDialog`, `TimetableSettingsDialog`, `SettingsDialog` |
| 바탕화면 | `DesktopEmbedder`, `DesktopHost`, `win/Win32Desktop` |
| 기타 | `Theme`, `Times`, `DateField`, `FormDialog`, `WidthTrackingPanel`, `WidgetRepaintManager` |

## AI 활용

이 프로젝트의 코드는 기획 보고서를 바탕으로 Claude(Anthropic)의 도움을 받아 작성했습니다.
