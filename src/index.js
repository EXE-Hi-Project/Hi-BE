require('dotenv').config();
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
const connectDB = require('./config/db');
const errorHandler = require('./middleware/error');

const app = express();

// Connect MongoDB
connectDB();

// Middleware
app.use(helmet({ crossOriginOpenerPolicy: { policy: 'unsafe-none' }, crossOriginEmbedderPolicy: false }));
const allowedOrigins = [
  'http://localhost:3000',
  'http://localhost:5173',
  'https://hilover.space',
  'https://www.hilover.space',
  process.env.FRONTEND_URL,
].filter(Boolean);
app.use(cors({
  origin: (origin, callback) => {
    if (!origin || allowedOrigins.includes(origin)) return callback(null, true);
    callback(new Error('Not allowed by CORS'));
  },
  credentials: true,
}));
app.use(express.json());
app.use(morgan('dev'));

// Routes
app.use('/api/auth', require('./routes/auth'));
app.use('/api/users', require('./routes/users'));
app.use('/api/cycles', require('./routes/cycles'));
app.use('/api/symptoms', require('./routes/symptoms'));
app.use('/api/notifications', require('./routes/notifications'));
app.use('/api/chat', require('./routes/chat'));
app.use('/api/admin', require('./routes/admin'));

// Health check
app.get('/health', (req, res) => res.json({ status: 'ok', message: 'Hi API is running' }));

// 404
app.use((req, res) => res.status(404).json({ success: false, message: 'Route không tồn tại' }));

// Error handler
app.use(errorHandler);

const PORT = process.env.PORT || 5000;
app.listen(PORT, () => console.log(`🚀 Server chạy tại http://localhost:${PORT}`));
