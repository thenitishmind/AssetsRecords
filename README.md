# 📦 Asset Management System

> **Laptop Asset, Repair & Stock Management** — A full-stack solution consisting of a **Node.js REST API** backend and a native **Android (Jetpack Compose)** mobile client.

---

## 📁 Project Structure (Full Tree)

```
AssetsRecords/
├── .gitignore
├── atlas-credentials.env          # MongoDB Atlas credentials (do NOT commit)
├── README.md
│
├── backend/                       # Node.js REST API
│   ├── .env                       # Local environment variables (do NOT commit)
│   ├── .env.example               # Template for environment variables
│   ├── package.json
│   ├── package-lock.json
│   ├── node_modules/
│   ├── tests/                     # Integration & unit tests
│   └── src/
│       ├── app.js                 # Express app factory
│       ├── server.js              # Entry point (connect DB → seed → listen)
│       ├── config/
│       │   ├── db.js              # MongoDB connection & index management
│       │   └── env.js             # Validated environment config
│       ├── controllers/
│       │   ├── index.js           # All controllers (auth, assets, repairs …)
│       │   └── common.js          # Shared helpers (paging, parseFilters)
│       ├── middleware/
│       │   └── index.js           # authenticate, requireRole, errorHandler
│       ├── models/
│       │   └── constants.js       # Enums: ASSET_STATUS, REPAIR_STATUS, ROLES …
│       ├── repositories/          # Raw MongoDB query layer
│       ├── routes/
│       │   └── index.js           # All API routes wired to controllers
│       ├── services/              # Business logic layer
│       │   ├── authService.js
│       │   ├── assetService.js
│       │   ├── assignmentService.js
│       │   ├── branchService.js
│       │   ├── employeeService.js
│       │   ├── repairService.js
│       │   ├── replacementService.js
│       │   ├── reportService.js
│       │   ├── searchService.js
│       │   ├── stockService.js
│       │   └── auditService.js
│       ├── utils/
│       │   └── errors.js          # AppError, forbidden, unauthorized helpers
│       └── validators/            # Request body validators
│
└── android/                       # Native Android app (Kotlin + Compose)
    ├── build.gradle.kts
    ├── settings.gradle.kts
    ├── gradle.properties
    ├── gradlew / gradlew.bat
    └── app/
        ├── build.gradle.kts       # Dependencies, SDK config, buildConfig
        └── src/main/
            ├── AndroidManifest.xml
            └── java/com/assetmgmt/laptops/
                ├── AssetApp.kt            # Application class
                ├── MainActivity.kt        # Single-activity entry point
                ├── data/
                │   ├── local/             # Local/shared preferences storage
                │   ├── remote/
                │   │   ├── ApiService.kt  # Retrofit interface (all API calls)
                │   │   ├── Dtos.kt        # Request/response data classes
                │   │   └── NetworkModule.kt # OkHttp + Retrofit setup
                │   └── repository/        # Repository pattern (data bridge)
                ├── navigation/
                │   └── AppNav.kt          # NavHost + route definitions
                ├── ui/
                │   ├── components/        # Reusable Composable widgets
                │   ├── theme/             # Material3 theme, colors, typography
                │   └── screens/
                │       ├── LoginScreen.kt
                │       ├── DashboardScreen.kt
                │       ├── AssetListScreens.kt
                │       ├── AssetDetailScreen.kt
                │       ├── AssignScreen.kt
                │       ├── RepairScreens.kt
                │       ├── ReportsScreen.kt
                │       ├── MasterScreens.kt
                │       ├── SettingsScreens.kt
                │       └── ScreenKit.kt   # Shared screen utilities
                ├── util/                  # Extension functions & helpers
                └── viewmodel/             # ViewModels for each screen
```

---

## 🛠️ Technology Stack

### Backend

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **Runtime** | Node.js ≥ 20 (ESM) | JavaScript server runtime |
| **Framework** | Express 5 | HTTP routing & middleware |
| **Database** | MongoDB Atlas (via `mongodb` driver 6.x) | Primary data store |
| **Auth** | JSON Web Tokens (`jsonwebtoken`) | Stateless authentication |
| **Password Hashing** | `bcryptjs` | Secure password storage |
| **Rate Limiting** | `express-rate-limit` | Brute-force protection on login |
| **Security Headers** | `helmet` | HTTP security headers |
| **CORS** | `cors` | Cross-Origin Resource Sharing |
| **PDF Export** | `pdfkit` | Generate downloadable PDF reports |
| **Config** | `dotenv` | Environment variable management |
| **Testing** | Node.js built-in test runner | Integration & unit tests |

### Android

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **Language** | Kotlin | Modern Android development |
| **UI Toolkit** | Jetpack Compose + Material 3 | Declarative UI |
| **Architecture** | MVVM (ViewModel + StateFlow) | Separation of concerns |
| **Navigation** | Navigation Compose | Screen routing |
| **Networking** | Retrofit 2 + OkHttp 4 | HTTP client for API calls |
| **Serialization** | `kotlinx.serialization` (JSON) | Request/response parsing |
| **Concurrency** | Kotlin Coroutines | Async operations |
| **Min SDK** | API 26 (Android 8.0 Oreo) | Minimum supported Android version |
| **Target SDK** | API 37 | Latest Android target |

---

## 🔐 Authentication & Login Flow

### How It Works

The system uses **JWT-based stateless authentication** with **role-based access control (RBAC)**.

#### Roles

| Role | Permissions |
|------|------------|
| `ADMIN` | Full access — create/edit/delete assets, users, branches, employees; export reports |
| `OPERATOR` | Read access + can create assignments and repairs |

---

### Login Flow — Step by Step

```
┌──────────────────────────────────────────────────────────────┐
│                     ANDROID APP                              │
│                                                              │
│  1. User enters username + password on LoginScreen.kt        │
│  2. ViewModel calls ApiService.login(LoginRequest)           │
│  3. Retrofit POST → /api/auth/login                          │
└───────────────────────────┬──────────────────────────────────┘
                            │  HTTP POST /api/auth/login
                            │  { "username": "...", "password": "..." }
                            ▼
┌──────────────────────────────────────────────────────────────┐
│                     BACKEND API                              │
│                                                              │
│  4. Rate limiter: max 20 attempts per 15 min per IP          │
│  5. authController.login → authService.login()               │
│  6. Look up user by username in MongoDB                       │
│  7. Compare password with bcryptjs hash                      │
│  8. On success: sign JWT (HS256) with user id, role, name    │
│  9. Return { token, user } to client                         │
└───────────────────────────┬──────────────────────────────────┘
                            │  { token: "eyJ...", user: { ... } }
                            ▼
┌──────────────────────────────────────────────────────────────┐
│                     ANDROID APP                              │
│                                                              │
│  10. App stores JWT in local/SharedPreferences               │
│  11. OkHttp Interceptor attaches token to every request:     │
│      Authorization: Bearer <token>                           │
│  12. Navigate to DashboardScreen                             │
└──────────────────────────────────────────────────────────────┘
```

#### Subsequent Requests (Protected Endpoints)

```
Android → POST/GET /api/<endpoint>
          Header: Authorization: Bearer <JWT>
                   ↓
          Backend middleware: authenticate()
          • Strips "Bearer " prefix
          • Calls verifyToken() → decodes JWT
          • Attaches user object to req.user
                   ↓
          requireRole('ADMIN') or requireRole('ADMIN','OPERATOR')
          • Checks req.user.role against allowed roles
          • 403 Forbidden if not allowed
                   ↓
          Controller → Service → Repository → MongoDB
```

#### Key Auth Endpoints

| Method | Endpoint | Auth Required | Description |
|--------|----------|--------------|-------------|
| `POST` | `/api/auth/login` | ❌ Public | Log in with username & password |
| `GET` | `/api/auth/me` | ✅ Any role | Get current logged-in user info |
| `POST` | `/api/auth/change-password` | ✅ Any role | Change own password |
| `GET` | `/api/health` | ❌ Public | Server health check |

#### First-Time Admin Seed

When the database is **empty** (no users), the server automatically seeds an admin account using values from `.env`:

```env
ADMIN_USERNAME=admin
ADMIN_NAME=System Administrator
ADMIN_PASSWORD=change-me
```

> ⚠️ **Change the default admin password immediately after first login.**

---

## 🚀 Getting Started

### Backend

#### Prerequisites
- Node.js ≥ 20
- MongoDB Atlas cluster (or local MongoDB with replica set for transactions)

#### Setup

```bash
cd backend

# 1. Copy environment template
cp .env.example .env

# 2. Fill in your values in .env
#    MONGODB_URI, JWT_SECRET, ADMIN_PASSWORD (at minimum)

# 3. Install dependencies
npm install

# 4. Start development server (with file watching)
npm run dev

# 5. Run tests
npm test
```

The API will be available at: `http://localhost:4000/api`

#### Environment Variables (`.env`)

```env
MONGODB_URI="mongodb+srv://<user>:<password>@<cluster>.mongodb.net"
MONGODB_DB="asset_management"
MONGODB_TEST_DB="asset_management_test"
PORT=4000
JWT_SECRET="your-long-random-secret"
JWT_EXPIRES_IN="12h"
CORS_ORIGINS="*"
ADMIN_USERNAME="admin"
ADMIN_NAME="System Administrator"
ADMIN_PASSWORD="change-me"
```

---

### Android

#### Prerequisites
- Android Studio (Hedgehog or later)
- JDK 17
- Android SDK (API 26+)

#### Setup

1. Open the `android/` folder in Android Studio
2. Let Gradle sync complete
3. Configure the API base URL:
   - For emulator (default): `http://10.0.2.2:4000/api/` is pre-configured
   - For a real device/server: add `apiBaseUrl=http://<server-ip>:4000/api/` to `local.properties`
4. Run on emulator or physical device

---

## 📊 API Endpoints Summary

| Resource | GET (list) | POST (create) | PUT (update) | DELETE |
|---------|-----------|--------------|-------------|--------|
| `/api/branches` | ALL | ADMIN | ADMIN | ADMIN |
| `/api/employees` | ALL | ADMIN | ADMIN | ADMIN |
| `/api/assets` | ALL | ADMIN | ADMIN | ADMIN |
| `/api/assignments` | ALL | ANY | ADMIN | — |
| `/api/repairs` | ALL | ANY | ANY | — |
| `/api/replacements` | ALL | ADMIN | — | — |
| `/api/stock` | ALL | — | — | — |
| `/api/reports/:name` | ALL (JSON) / ADMIN (CSV/PDF) | — | — | — |
| `/api/users` | ADMIN | ADMIN | ADMIN | — |
| `/api/audit-logs` | ADMIN | — | — | — |
| `/api/dashboard` | ALL | — | — | — |
| `/api/search` | ALL | — | — | — |

> **ALL** = authenticated users (ADMIN + OPERATOR); **ADMIN** = ADMIN role only.

---

## 🗄️ Data Models (Key Collections)

| Collection | Description |
|-----------|-------------|
| `assets` | Laptop records — serial number, asset tag, status, branch |
| `employees` | Employee records linked to branches |
| `branches` | Office/branch locations |
| `assignments` | Tracks which laptop is assigned to which employee |
| `repairs` | Repair tickets with vendor and status tracking |
| `replacements` | Laptop swap records |
| `assetHistory` | Immutable event log for every asset lifecycle change |
| `users` | System users with roles (ADMIN / OPERATOR) |
| `auditLogs` | Admin action audit trail |

### Asset Status State Machine

```
AVAILABLE ──► ASSIGNED ──► AVAILABLE (on return)
AVAILABLE ──► REPAIR   ──► AVAILABLE (on repair complete)
AVAILABLE ──► DEAD
AVAILABLE ──► SOLD
AVAILABLE ──► RETIRED
```

### Repair Status State Machine

```
SENT_FOR_REPAIR ──► UNDER_REPAIR ──► REPAIRED ──► RETURNED
                               ├──► NOT_REPAIRABLE ──► DEAD
                               └──► DEAD
```

---

## 🔒 Security Notes

- JWT tokens are **HS256-signed** and expire after 12 hours (configurable)
- Passwords are hashed using **bcrypt** (never stored in plain text)
- Login endpoint is **rate-limited** (20 requests / 15 min per IP)
- All HTTP security headers are set via **Helmet**
- Raw database errors are **never leaked** to API consumers
- `.env` and `atlas-credentials.env` are in `.gitignore` — **never commit them**

---

## 📄 License

Private & Unlicensed — Internal use only.
