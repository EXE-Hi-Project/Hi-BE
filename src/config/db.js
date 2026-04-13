const mongoose = require('mongoose');

const connectDB = async () => {
  try {
    if (!process.env.MONGODB_URI) {
      console.error('❌ Missing MONGODB_URI in BE/.env');
      process.exit(1);
    }

    const dbName = process.env.MONGODB_DB_NAME || 'HiProject';
    const conn = await mongoose.connect(process.env.MONGODB_URI, { dbName });
    console.log(`✅ MongoDB Connected: ${conn.connection.host}/${conn.connection.name}`);
  } catch (error) {
    console.error('❌ MongoDB connection error:', error.message);
    process.exit(1);
  }
};

module.exports = connectDB;
