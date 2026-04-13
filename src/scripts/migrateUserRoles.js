require('dotenv').config();
const mongoose = require('mongoose');
const User = require('../models/User');

const getAdminEmails = () =>
  (process.env.ADMIN_EMAILS || '')
    .split(',')
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean);

async function run() {
  if (!process.env.MONGODB_URI) {
    throw new Error('Thiếu MONGODB_URI trong BE/.env');
  }

  const adminEmails = getAdminEmails();

  await mongoose.connect(process.env.MONGODB_URI);
  console.log('✅ Đã kết nối MongoDB');
  console.log(`ℹ️ ADMIN_EMAILS: ${adminEmails.length > 0 ? adminEmails.join(', ') : '(trống)'}`);

  const setUserResult = await User.updateMany(
    {
      $or: [
        { role: { $exists: false } },
        { role: null },
        { role: '' },
      ],
    },
    { $set: { role: 'user' } }
  );

  let setAdminResult = { modifiedCount: 0 };
  if (adminEmails.length > 0) {
    setAdminResult = await User.updateMany(
      { email: { $in: adminEmails } },
      { $set: { role: 'admin' } }
    );
  }

  const [totalUsers, totalAdmins, usersWithoutRole] = await Promise.all([
    User.countDocuments(),
    User.countDocuments({ role: 'admin' }),
    User.countDocuments({
      $or: [{ role: { $exists: false } }, { role: null }, { role: '' }],
    }),
  ]);

  console.log(`✅ Cập nhật role=user (thiếu role): ${setUserResult.modifiedCount}`);
  console.log(`✅ Cập nhật role=admin theo ADMIN_EMAILS: ${setAdminResult.modifiedCount}`);
  console.log(`📊 Tổng users: ${totalUsers} | Admins: ${totalAdmins} | Users thiếu role còn lại: ${usersWithoutRole}`);
}

run()
  .catch((error) => {
    console.error('❌ Migration thất bại:', error.message);
    process.exitCode = 1;
  })
  .finally(async () => {
    await mongoose.disconnect();
    console.log('🔌 Đã đóng kết nối MongoDB');
  });
