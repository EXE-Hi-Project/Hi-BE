const jwt = require('jsonwebtoken');
const User = require('../models/User');

const getAdminEmails = () =>
  (process.env.ADMIN_EMAILS || '')
    .split(',')
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean);

const protect = async (req, res, next) => {
  let token;
  if (req.headers.authorization && req.headers.authorization.startsWith('Bearer')) {
    token = req.headers.authorization.split(' ')[1];
  }
  if (!token) return res.status(401).json({ success: false, message: 'Không có quyền truy cập' });

  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    req.user = await User.findById(decoded.id).select('-password');
    if (!req.user) return res.status(401).json({ success: false, message: 'Người dùng không tồn tại' });

    if (!req.user.role) {
      const adminEmails = getAdminEmails();
      const role = adminEmails.includes(req.user.email.toLowerCase()) ? 'admin' : 'user';
      await User.findByIdAndUpdate(req.user._id, { role });
      req.user.role = role;
    }

    next();
  } catch {
    res.status(401).json({ success: false, message: 'Token không hợp lệ' });
  }
};

const authorize = (...roles) => (req, res, next) => {
  if (!req.user || !roles.includes(req.user.role)) {
    return res.status(403).json({ success: false, message: 'Bạn không có quyền thực hiện thao tác này' });
  }
  next();
};

module.exports = { protect, authorize };
