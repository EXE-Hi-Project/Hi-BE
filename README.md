# Backend - Hi API

Node.js backend API cho ứng dụng Hi.

## 🚀 Quick Start

```bash
# Install dependencies
npm install

# Run development server
npm run dev

# One-time: backfill user roles (user/admin)
npm run migrate:roles

# Run production
npm start
```

## 📁 Cấu trúc

```
Backend/
├── src/
│   ├── routes/        # API routes
│   ├── controllers/   # Business logic
│   ├── middleware/    # Custom middleware
│   ├── models/        # MongoDB models
│   ├── config/        # DB config
│   └── scripts/       # Utility scripts
└── package.json
```

## 🛠️ Tech Stack

- Node.js 18+
- Express
- JavaScript (CommonJS)
- Mongoose
- MongoDB
- AWS Bedrock (AI)
- JWT Authentication

## 🌐 Development

Backend chạy tại: http://localhost:5000
