# Backend - Hi API

Node.js backend API cho ứng dụng Hi.

## 🚀 Quick Start

```bash
# Install dependencies
npm install

# Setup database
npm run prisma:generate
npm run prisma:migrate

# Run development server
npm run dev

# One-time: backfill user roles (user/admin)
npm run migrate:roles

# Build for production
npm run build
npm start
```

## 📁 Cấu trúc

```
Backend/
├── src/
│   ├── routes/        # API routes
│   ├── controllers/   # Business logic
│   ├── services/      # Service layer
│   ├── middleware/    # Custom middleware
│   └── utils/         # Utilities
├── prisma/
│   └── schema.prisma  # Database schema
└── package.json
```

## 🛠️ Tech Stack

- Node.js 18+
- Express
- TypeScript
- Prisma ORM
- PostgreSQL
- AWS Bedrock (AI)
- JWT Authentication

## 🌐 Development

Backend chạy tại: http://localhost:3001
