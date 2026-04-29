# Code Walkthrough Report

## 1. Project overview

### What the project does
- This repository is a multi-surface call-center / telesales system with:
  - a **Web admin/agent console** in `client/`
  - a **Node.js backend API** in `server/`
  - an **Android calling app** in `android/`
- The README describes it as an "智能自动拨打电话客服销售系统" supporting administrators and agents, phone dialing, recording, voicemail, and SMS notifications in `README.md:12`, `README.md:24-62`.

### Business domain
- Core business objects are **users (admins/agents)**, **customers/leads**, **tasks**, **calls**, and communication follow-up artifacts such as **SMS**, **voicemail**, and **unanswered records**.
- The backend schema defines these tables in `server/src/config/database.ts:16-181`.
- The Android app is not just a viewer; it is a device-integrated dialer with auto-dial, overlay, call-state detection, recording upload, and update delivery, as shown by `android/app/src/main/AndroidManifest.xml:12-165` and `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt`.

### Tech stack
- **Web**: React 18 + TypeScript + Vite + Ant Design + Zustand + Axios in `client/package.json:11-20`.
- **Backend**: Node.js + Express + TypeScript + sql.js SQLite + JWT + Swagger + Twilio in `server/package.json:20-35`.
- **Android**: Kotlin + Jetpack Compose + Hilt + Retrofit + OkHttp + Room + DataStore + Coroutines in `android/app/build.gradle.kts:77-139`.
- **Build/deploy**:
  - npm workspaces for `server` and `client` in `package.json:6-20`
  - Docker / docker-compose in `docker-compose.yml:1-49`, `server/Dockerfile:1-52`, `client/Dockerfile:1-38`
  - Android Gradle build in `android/build.gradle.kts:2-7` and `android/app/build.gradle.kts:19-139`

### How to run / build
- **Docker**: `docker-compose up -d` per `README.md:68-82`.
- **Local web/backend dev**:
  - backend: `cd server && pnpm install && pnpm dev` in `README.md:95-99`
  - frontend: `cd client && pnpm install && pnpm dev` in `README.md:100-104`
- **Android**: open `android/` in Android Studio and run, per `README.md:106-127` and `android/README.md:86-115`.
- **Ports**:
  - frontend dev server `8080` in `client/vite.config.ts:9-11`
  - backend `8081` in `server/src/app.ts:29`
- **Proxy**:
  - Vite proxies `/api` to `http://localhost:8081` in `client/vite.config.ts:12-18`.

---

## 2. Top-level directory structure

```text
call-center-system/
├── client/                 Web frontend (admin + agent console)
├── server/                 Backend API service and SQLite-backed business logic
├── android/                Native Android calling app
├── docs/                   Project/process docs
├── docker-compose.yml      Local deployment topology for frontend + backend
├── README.md               Main project overview and quick start
├── SETUP.md                Setup instructions
├── DEPLOY.md               Deployment notes
├── DEPLOYMENT.md           Additional deployment notes
├── QUICK_DEPLOY.md         Fast deployment guide
├── nginx-callcenter.conf   Nginx reverse proxy config
├── deploy.sh               Deployment helper script
├── start.sh                Startup helper script
└── railway.toml            Railway deployment config for backend
```

### Notes on notable top-level contents
- `client/` contains the React app entry at `client/src/main.tsx:8-12` and route tree at `client/src/App.tsx:31-115`.
- `server/` contains the Express app entry at `server/src/app.ts:28-141`.
- `android/` contains the Android app entry points at `android/app/src/main/java/com/callcenter/app/CallCenterApp.kt:20` and `android/app/src/main/java/com/callcenter/app/MainActivity.kt:40-109`.
- The repo also contains checked-in runtime artifacts such as `dist/`, `node_modules/`, logs, APKs, and SQLite DB files, which suggests the repository is used partly as a deployment workspace, not only as clean source.

---

## 3. Core modules / packages

## 3.1 Backend (`server/src`)

### App bootstrap and routing
- `server/src/app.ts:28-141` — main Express bootstrap.
  - Registers CORS, JSON parsing, request logging, static uploads, Swagger, health check, and all route groups.
  - Mounts route modules at `server/src/app.ts:107-120`.
  - Starts the server and initializes the DB at `server/src/app.ts:129-138`.

### Database and persistence layer
- `server/src/config/database.ts:13-181` — schema definition.
- `server/src/config/database.ts:247-329` — in-code migrations.
- `server/src/config/database.ts:331-446` — DB initialization and `query()` wrapper.
- There is **no strong repository/DAO layer** on the server; most controllers call `query()` directly.

### Auth and user management
- `server/src/routes/auth.routes.ts:8-16` wires auth endpoints.
- `server/src/controllers/auth.controller.ts:77-130` — `login()`.
- `server/src/controllers/auth.controller.ts:132-178` — `getCurrentUser()`.
- `server/src/controllers/auth.controller.ts:180-230` — `changePassword()`.
- `server/src/controllers/user.controller.ts` handles admin-side user CRUD.
- `server/src/middleware/auth.ts:14-39` provides `generateToken()`, `authMiddleware()`, and `adminMiddleware()`.

### Customer management
- `server/src/controllers/customer.controller.ts` is the main customer domain controller.
- Responsibilities include:
  - customer CRUD
  - assignment to agents
  - filtering by tag / source / status
  - agent-visible customer queries
  - call-status updates tied to customer records

### Task management
- `server/src/controllers/task.controller.ts` manages tasks and task-customer relationships.
- Important functions:
  - `createTask()` in `server/src/controllers/task.controller.ts:95-139`
  - `getTasks()` in `server/src/controllers/task.controller.ts:8-93`
  - `getTaskById()` in `server/src/controllers/task.controller.ts:208-287`
- This module is central because tasks connect agents to batches of customers through `task_customers`.

### Call management
- `server/src/controllers/call.controller.ts` handles call records and recording upload.
- Important functions include:
  - `getCallRecords()`
  - `createCallRecord()` in `server/src/controllers/call.controller.ts:130-158`
  - `updateCallRecord()` in `server/src/controllers/call.controller.ts:160-197`
- This module is used by both web and Android clients.

### Twilio / communication
- `server/src/controllers/twilio.controller.ts` is the telephony integration controller.
- `makeCall()` in `server/src/controllers/twilio.controller.ts:21-88` creates a local call record and then calls Twilio.
- `handleStatusWebhook()` in `server/src/controllers/twilio.controller.ts:115-190` updates local call state from Twilio callbacks.
- Supporting services:
  - `server/src/services/config.service.ts:3-62`
  - `server/src/services/sms.service.ts:20-99`
  - `server/src/services/voicemail.service.ts:82-107`

### Import / reporting / config / version / logs
- `server/src/controllers/data-import.controller.ts` — CSV preview, mapping, import.
- `server/src/controllers/stats.controller.ts` and `server/src/controllers/report.controller.ts` — dashboard/report aggregation.
- `server/src/controllers/config.controller.ts` — system and agent config.
- `server/src/controllers/version.controller.ts` — Android APK/version management.
- `server/src/controllers/logs.controller.ts` — device log upload/list/download/delete.

## 3.2 Web frontend (`client/src`)

### App shell and routing
- `client/src/App.tsx:31-115` — top-level route tree.
- Public routes: `/login`, `/help`.
- Admin routes under `/admin/*`.
- Agent routes under `/agent/*`.
- `client/src/components/PrivateRoute.tsx:11-38` enforces auth and role checks.

### API layer
- `client/src/services/api.ts:11-235` is the central API wrapper.
- It defines modules for auth, users, customers, calls, tasks, stats, Twilio, config, communication, import, version, and logs.
- It also injects the JWT token and handles `401` redirects in `client/src/services/api.ts:43-75`.

### Admin UI modules
- `client/src/pages/admin/Layout.tsx:24-101` — admin navigation shell.
- Main admin pages:
  - dashboard
  - user management
  - customer management
  - task management
  - stats
  - system config
  - Twilio test
  - data permission
  - version management
  - logs management

### Agent UI modules
- `client/src/pages/agent/Layout.tsx` — agent shell.
- Main agent pages registered in `client/src/App.tsx:95-102`:
  - dashboard
  - task list
  - task execution
  - call list
  - communication records
  - my stats
  - app download
  - settings
- `client/src/pages/agent/AgentTaskExecution.tsx:73-245` is one of the most important pages because it drives the call workflow.

### State and types
- `client/src/stores/index.ts:14-37` — auth store.
- `client/src/stores/index.ts:44-54` — agent config store.
- `client/src/stores/index.ts:66-74` — auto-dial UI state.
- `client/src/types/index.ts:1-157` defines shared frontend models.

## 3.3 Android app (`android/app/src/main/java/com/callcenter/app`)

### App/bootstrap
- `android/app/src/main/java/com/callcenter/app/CallCenterApp.kt:20-21` — Hilt application class.
- `android/app/src/main/java/com/callcenter/app/MainActivity.kt:63-109` — startup flow.
  - requests permissions
  - checks overlay permission
  - retries pending recording uploads
  - checks app updates
  - launches Compose navigation

### Navigation and screens
- `android/app/src/main/java/com/callcenter/app/ui/navigation/AppNavigation.kt:52-96` defines routes.
- `android/app/src/main/java/com/callcenter/app/ui/navigation/AppNavigation.kt:101-467` builds the NavHost.
- `android/app/src/main/java/com/callcenter/app/ui/screens/main/MainScreen.kt:64-474` is the role-aware main shell.

### Data layer
- `android/app/src/main/java/com/callcenter/app/data/api/ApiService.kt:13-406` — Retrofit API contract.
- `android/app/src/main/java/com/callcenter/app/di/NetworkModule.kt:32-217` — dynamic base URL, auth interceptor, Retrofit/OkHttp wiring.
- `android/app/src/main/java/com/callcenter/app/data/model/Models.kt` — network/domain models.

### Local persistence
- `android/app/src/main/java/com/callcenter/app/data/local/AppDatabase.kt:24` — Room DB root.
- `android/app/src/main/java/com/callcenter/app/data/local/entity/Entities.kt:9-105` — Room entities.
- `android/app/src/main/java/com/callcenter/app/data/local/preferences/` — DataStore-based settings and auth persistence.

### Repositories
- `android/app/src/main/java/com/callcenter/app/data/repository/AuthRepository.kt:17-151`
- `android/app/src/main/java/com/callcenter/app/data/repository/TaskRepository.kt:15-355`
- `android/app/src/main/java/com/callcenter/app/data/repository/CallRecordRepository.kt:26-248`
- These repositories bridge local storage and backend APIs.

### Device/service integration
- `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt` — core auto-dial orchestration.
- `android/app/src/main/java/com/callcenter/app/service/FloatingCustomerService.kt` — overlay customer info.
- `android/app/src/main/java/com/callcenter/app/service/LogCollectorService.kt` — device log collection.
- `android/app/src/main/java/com/callcenter/app/service/AutoSpeakerInCallService.kt` and `AutoSpeakerAccessibilityService.kt` — call/speaker automation.
- Utility call analysis classes live under `android/app/src/main/java/com/callcenter/app/util/call/`.

---

## 4. Key data models / domain entities

## 4.1 Backend tables

### `users`
- Defined in `server/src/config/database.ts:16-32`.
- Represents admins and agents.
- Important fields:
  - `username`, `password`, `role`, `status`
  - `real_name`, `phone`, `email`
  - `data_access_type`
- Related to:
  - `customers.assigned_to`
  - `tasks.assigned_to`
  - `calls.agent_id`
  - `agent_configs.agent_id`

### `customers`
- Defined in `server/src/config/database.ts:37-56`.
- Represents leads/customers to be called.
- Important fields:
  - `name`, `phone`, `company`
  - `status`, `call_status`, `call_result`
  - `assigned_to`, `data_source`, `tag`, `source`
- Related to:
  - `calls.customer_id`
  - `tasks.customer_id` (legacy/simple link)
  - `task_customers.customer_id`

### `calls`
- Defined in `server/src/config/database.ts:59-77`.
- Represents call attempts and outcomes.
- Important fields:
  - `customer_id`, `agent_id`
  - `twilio_call_sid`
  - `status`, `call_result`, `call_notes`
  - `recording_url`, `recording_duration`, `call_duration`
  - `started_at`, `ended_at`

### `tasks`
- Defined in `server/src/config/database.ts:80-93`.
- Represents work assignments for agents.
- Important fields:
  - `title`, `description`
  - `assigned_to`
  - `priority`, `status`, `due_date`
  - `created_by`

### `task_customers`
- Created in migration code at `server/src/config/database.ts:306-326`.
- This is the real bridge between tasks and customers.
- Important fields:
  - `task_id`, `customer_id`
  - `status`
  - `call_id`, `call_result`, `called_at`
- This table is key to task execution progress.

### `system_configs`
- Defined in `server/src/config/database.ts:96-103`.
- Stores operational config such as Twilio and registration settings.

### `agent_configs`
- Defined in `server/src/config/database.ts:106-121`.
- Stores per-agent dialing preferences and SIP-related fields.

### Communication-related tables
- `voicemail_records` in `server/src/config/database.ts:124-134`
- `sms_records` in `server/src/config/database.ts:137-148`
- `unanswered_records` in `server/src/config/database.ts:151-160`

### App version table
- `app_versions` in `server/src/config/database.ts:163-181`
- Stores Android APK release metadata.

## 4.2 Client/Android models
- Web TS models are in `client/src/types/index.ts:1-157`.
- Android network/domain models are in `android/app/src/main/java/com/callcenter/app/data/model/Models.kt:26-251`.
- Android local Room entities are in `android/app/src/main/java/com/callcenter/app/data/local/entity/Entities.kt:9-105`.

### Relationship summary
- **User** owns/receives tasks and handles calls.
- **Customer** can be assigned directly to an agent and also linked to tasks.
- **Task** groups many customers through `task_customers`.
- **Call** belongs to a customer and an agent, and may be linked back into `task_customers.call_id`.
- **Missed/unanswered calls** can generate SMS and unanswered records.
- **App versions** support Android update delivery.

---

## 5. Main workflows / call chains

## 5.1 Login flow

### Backend path
- Route: `server/src/routes/auth.routes.ts:8-16`
- Controller: `login()` in `server/src/controllers/auth.controller.ts:77-130`
- Token generation: `generateToken()` in `server/src/middleware/auth.ts:14-16`
- DB access: `query()` in `server/src/config/database.ts:387-442`

### Web path
- UI: `handleLogin()` in `client/src/pages/login/index.tsx:164-191`
- API call: `authApi.login()` in `client/src/services/api.ts:78-86`
- Route redirect: `client/src/App.tsx:35-39`

### Android path
- ViewModel: `login()` in `android/app/src/main/java/com/callcenter/app/ui/viewmodel/AuthViewModel.kt:78-111`
- Repository: `login()` in `android/app/src/main/java/com/callcenter/app/data/repository/AuthRepository.kt:27-58`
- API: `login()` in `android/app/src/main/java/com/callcenter/app/data/api/ApiService.kt`
- Dynamic server URL rewrite: `DynamicBaseUrlInterceptor` in `android/app/src/main/java/com/callcenter/app/di/NetworkModule.kt:32-67`

### Call chain
- Web: `LoginPage.handleLogin()` → `authApi.login()` → backend `POST /api/auth/login` → `auth.controller.login()` → `query(users)` → JWT returned.
- Android: `AuthViewModel.login()` → `AuthRepository.login()` → `ApiService.login()` → backend `POST /api/auth/login`.

## 5.2 Admin creates a task with customers

### Backend path
- Route: `POST /api/tasks` in `server/src/routes/task.routes.ts:23-29`
- Controller: `createTask()` in `server/src/controllers/task.controller.ts:95-139`
- DB writes:
  - insert into `tasks` at `server/src/controllers/task.controller.ts:107-112`
  - insert into `task_customers` at `server/src/controllers/task.controller.ts:118-125`

### Web path
- Admin task UI is under `client/src/pages/admin/TaskManagement.tsx`.
- API wrapper: `taskApi.createTask()` in `client/src/services/api.ts`.

### Call chain
- Admin page submits task form → `taskApi.createTask()` → backend `task.controller.createTask()` → `tasks` + `task_customers` persisted.

## 5.3 Agent task execution and outbound call

### Web agent flow
- Task detail load: `fetchTaskDetail()` in `client/src/pages/agent/AgentTaskExecution.tsx:86-107`
- Mark customer as called: `handleCallCustomer()` in `client/src/pages/agent/AgentTaskExecution.tsx:114-143`
- Submit result: `handleSubmitCallResult()` in `client/src/pages/agent/AgentTaskExecution.tsx:166-213`

### Backend path
- Task detail: `getTaskById()` in `server/src/controllers/task.controller.ts:208-287`
- Task-customer status update: task controller endpoint under `server/src/routes/task.routes.ts`
- Twilio call: `makeCall()` in `server/src/controllers/twilio.controller.ts:21-88`
- Twilio status callback: `handleStatusWebhook()` in `server/src/controllers/twilio.controller.ts:115-190`
- SMS fallback: `SmsService.sendSms()` in `server/src/services/sms.service.ts:20-99`
- Unanswered persistence: `saveUnansweredRecord()` in `server/src/services/voicemail.service.ts:82-107`

### Android flow
- Main shell prepares dialing scope in `android/app/src/main/java/com/callcenter/app/ui/screens/main/MainScreen.kt:109-148`
- Auto-dial start is triggered from `android/app/src/main/java/com/callcenter/app/ui/screens/main/MainScreen.kt:306-415`
- Service orchestration lives in `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt`
- Server call record creation: `createServerCallRecord()` in `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt:1436-1447`
- Repository bridge: `createCallRecord()` in `android/app/src/main/java/com/callcenter/app/data/repository/CallRecordRepository.kt:72-112`
- Task-customer status update: `updateTaskCustomerStatus()` in `android/app/src/main/java/com/callcenter/app/data/repository/TaskRepository.kt:231-247`

### End-to-end call chain
1. Agent opens task.
2. UI fetches task + customer progress.
3. Agent initiates call.
4. Backend creates local `calls` row.
5. Twilio call is placed.
6. Twilio webhook updates call status.
7. If no answer/busy, backend may create `unanswered_records` and send SMS.
8. Agent or Android service updates `task_customers.status` and `call_result`.

## 5.4 Android recording upload / retry
- Startup trigger: `tryUploadPendingRecordings()` in `android/app/src/main/java/com/callcenter/app/MainActivity.kt:170-186`
- Repository: `uploadPendingRecordings()` in `android/app/src/main/java/com/callcenter/app/data/repository/CallRecordRepository.kt:134-162`
- Multipart upload API: `uploadCallRecording()` in `android/app/src/main/java/com/callcenter/app/data/repository/CallRecordRepository.kt:114-132`
- Backend endpoint is in the calls module (`server/src/routes/call.routes.ts`).

## 5.5 Android log upload and admin log management
- Android upload API: `uploadDeviceLogs()` in `android/app/src/main/java/com/callcenter/app/data/api/ApiService.kt:397-405`
- Admin web UI: `LogsManagement()` in `client/src/pages/admin/LogsManagement.tsx:35-306`
- Backend controller: `server/src/controllers/logs.controller.ts`
- This is a clear cross-surface workflow: Android produces logs, backend stores them, web admin reviews/downloads/deletes them.

---

## 6. External integrations

### Twilio
- Main telephony integration.
- Backend controller: `server/src/controllers/twilio.controller.ts:21-88`, `server/src/controllers/twilio.controller.ts:115-190`
- SMS service: `server/src/services/sms.service.ts:20-99`
- Voicemail service: `server/src/services/voicemail.service.ts:5-125`
- Config is stored in DB-backed `system_configs`, seeded in `server/src/config/database.ts:185-197`.

### SQLite / sql.js
- Backend persistence uses `sql.js` in `server/src/config/database.ts:1-446`.
- Default DB file path comes from `SQLITE_PATH` or `server/data/database.sqlite` in `server/src/config/database.ts:335`.

### Swagger / API docs
- Swagger UI is mounted in `server/src/app.ts:80-90`.
- Spec source is `server/src/config/swagger.ts`.

### File upload / storage
- Backend serves `/uploads` statically in `server/src/app.ts:61-78`.
- APK files are served from upload directories in `server/src/app.ts:31-39` and `server/src/app.ts:63-77`.
- Upload-related controllers include `server/src/controllers/upload.controller.ts` and version/logs/call upload endpoints.

### Android device integrations
- Telephony/call permissions in `android/app/src/main/AndroidManifest.xml:12-50`.
- Overlay/floating window support in `android/app/src/main/java/com/callcenter/app/MainActivity.kt:188-209` and `FloatingCustomerService`.
- Call-state / audio analysis helpers under `android/app/src/main/java/com/callcenter/app/util/call/`.
- App update flow via backend version API and Android update UI in `android/app/src/main/java/com/callcenter/app/ui/components/UpdateDialog.kt` and `UpdateViewModel`.

### Web runtime backend selection
- `client/src/services/api.ts:11-41` supports `VITE_API_URL`, `localStorage.current_server`, or relative `/api`.
- Android also supports runtime server switching via `TokenManager` + `DynamicBaseUrlInterceptor` in `android/app/src/main/java/com/callcenter/app/di/NetworkModule.kt:32-67`.

---

## 7. Config & entry points

### Backend entry/config
- Entry: `server/src/app.ts:28-141`
- Main env vars actually used in code:
  - `PORT` in `server/src/app.ts:29`
  - `NODE_ENV` in `server/src/app.ts:129`
  - `SQLITE_PATH` in `server/src/config/database.ts:335`
  - `JWT_SECRET` in `server/src/middleware/auth.ts:4`
- Env templates:
  - root `.env.example`
  - `server/.env.example`

### Web entry/config
- Entry: `client/src/main.tsx:8-12`
- App routes: `client/src/App.tsx:31-115`
- Vite config: `client/vite.config.ts:5-19`
- Runtime env:
  - `VITE_API_URL` in `client/.env.development:1`
  - `VITE_BASE_PATH` in `client/vite.config.ts:7-8` and `client/src/App.tsx:27-29`

### Android entry/config
- Application: `android/app/src/main/java/com/callcenter/app/CallCenterApp.kt:20`
- Launcher activity: `android/app/src/main/AndroidManifest.xml:65-73`
- Main activity: `android/app/src/main/java/com/callcenter/app/MainActivity.kt:63-109`
- Build config and default server URL: `android/app/build.gradle.kts:12-17`, `android/app/build.gradle.kts:28-42`
- Manifest permissions/services: `android/app/src/main/AndroidManifest.xml:12-165`

---

## 8. Notable observations

### Architecture / code organization
- **Backend is controller-centric**: most business logic and SQL live directly in controllers rather than a layered service/repository architecture.
- **Android is the most layered client**: DI, repositories, Room, DataStore, ViewModels, and services are clearly separated.
- **Web is simpler but more state-inconsistent**: auth is split between Zustand and direct `localStorage` access.

### Security-sensitive findings
- **Plaintext passwords on backend**:
  - `auth.controller.ts` compares plaintext passwords directly, e.g. `server/src/controllers/auth.controller.ts:106-107`.
  - default users are seeded with plaintext passwords in `server/src/config/database.ts:363-374`.
- **README claims password encryption, but code evidence contradicts it**:
  - README says "密码加密存储" in `README.md:296`, but the server code does not support that claim.
- **JWT secret fallback is weak**:
  - `server/src/middleware/auth.ts:4` falls back to `'your-secret-key'`.
- **Twilio webhook validation is not obvious**:
  - routes exist in `server/src/routes/twilio.routes.ts`, but no clear signature validation was found in the explored code.
- **Android stores saved credentials**:
  - `android/app/src/main/java/com/callcenter/app/data/local/preferences/TokenManager.kt:96-100` and nearby code store username/password history in DataStore.
- **Android allows cleartext traffic**:
  - `android/app/src/main/AndroidManifest.xml:62` sets `android:usesCleartextTraffic="true"`.

### Code smells / drift
- **Schema/controller mismatch risk**:
  - `call.controller.ts` references `connected_at`, but `calls` table in `server/src/config/database.ts:59-77` does not define that column.
- **Validation middleware exists but appears underused**:
  - `server/src/middleware/validator.ts` exists, but tests note validation is not fully wired.
- **Repo contains build/runtime artifacts**:
  - checked-in `dist/`, `node_modules/`, logs, APKs, DB files, uploads.
  - This makes the repo noisier for newcomers.

### Testing coverage feeling
- **Backend** has actual tests under `server/src/tests/`.
- **Web**: no obvious dedicated test suite was found in `client/`.
- **Android**: test dependencies exist in Gradle, but actual test source presence appears limited from the explored tree.
- Overall impression: **backend testing is materially stronger than client-side testing**.

### Documentation drift
- `README.md:296` says passwords are encrypted, but code suggests otherwise.
- `android/README.md:169-171` says passwords are not stored in plaintext, but `TokenManager.kt` evidence suggests saved credentials/history are stored.
- Some docs are helpful for onboarding, but parts are outdated relative to the code.

---

## 9. Suggested reading order for a newcomer (1-2 hours)

### First 15 minutes: high-level orientation
1. `README.md:12-127` — understand product scope and how the three surfaces fit together.
2. `server/src/app.ts:28-141` — see backend entry, route map, and deployment assumptions.
3. `client/src/App.tsx:31-115` — see web route structure and role split.
4. `android/app/src/main/java/com/callcenter/app/MainActivity.kt:63-109` — see Android startup responsibilities.

### Next 20-30 minutes: data model and backend core
5. `server/src/config/database.ts:13-181` — read the schema first.
6. `server/src/config/database.ts:247-446` — understand migrations, initialization, and `query()`.
7. `server/src/controllers/auth.controller.ts` — auth basics.
8. `server/src/controllers/task.controller.ts` — task/customer execution model.
9. `server/src/controllers/twilio.controller.ts` — telephony integration.
10. `server/src/services/sms.service.ts` and `server/src/services/voicemail.service.ts` — missed-call follow-up.

### Next 20-30 minutes: web workflows
11. `client/src/services/api.ts:11-235` — map frontend to backend endpoints.
12. `client/src/pages/login/index.tsx:164-191` — login flow.
13. `client/src/pages/agent/AgentTaskExecution.tsx:86-213` — most representative agent workflow.
14. `client/src/pages/admin/Layout.tsx:24-101` — admin feature map.
15. `client/src/pages/admin/LogsManagement.tsx:35-306` — example of a newer admin feature.

### Final 20-30 minutes: Android operational flow
16. `android/app/src/main/java/com/callcenter/app/ui/navigation/AppNavigation.kt:101-467` — screen map.
17. `android/app/src/main/java/com/callcenter/app/ui/screens/main/MainScreen.kt:64-474` — role-aware workbench.
18. `android/app/src/main/java/com/callcenter/app/data/api/ApiService.kt:13-406` — backend contract.
19. `android/app/src/main/java/com/callcenter/app/data/repository/AuthRepository.kt:27-58` and `TaskRepository.kt:231-355` — repository patterns.
20. `android/app/src/main/java/com/callcenter/app/service/AutoDialService.kt` — only after the above, because it is large and operationally dense.

---

## 10. Short conclusion
- The repo is best understood as a **three-part system**:
  1. **Express backend** for auth, customers, tasks, calls, config, versioning, and logs.
  2. **React web console** for admin and agent workflows.
  3. **Android dialer app** for real-world calling operations and device-integrated automation.
- The most important business flow is **task-driven outbound calling**, backed by `tasks`, `task_customers`, `calls`, and Twilio callbacks.
- The most important caveats for maintainers are **security/documentation drift**, **controller-heavy backend design**, and **limited visible client-side test coverage**.
