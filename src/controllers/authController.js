const jwt = require('jsonwebtoken');
const User = require('../models/User');

const generateToken = (id) => jwt.sign({ id }, process.env.JWT_SECRET, { expiresIn: process.env.JWT_EXPIRES_IN });
const getAdminEmails = () =>
  (process.env.ADMIN_EMAILS || '')
    .split(',')
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean);

// POST /api/auth/register
const register = async (req, res, next) => {
  try {
    const { name, email, password, gender } = req.body;
    if (!name || !email || !password || !gender)
      return res.status(400).json({ success: false, message: 'Vui lòng điền đầy đủ thông tin' });

    const existing = await User.findOne({ email });
    if (existing) return res.status(400).json({ success: false, message: 'Email đã được sử dụng' });

    const adminEmails = getAdminEmails();
    const role = adminEmails.includes(email.toLowerCase()) ? 'admin' : 'user';

    const user = await User.create({ name, email, password, gender, role });
    const token = generateToken(user._id);
    res.status(201).json({ success: true, token, user });
  } catch (err) { next(err); }
};

// POST /api/auth/login
const login = async (req, res, next) => {
  try {
    const { email, password } = req.body;
    if (!email || !password)
      return res.status(400).json({ success: false, message: 'Vui lòng nhập email và mật khẩu' });

    const user = await User.findOne({ email }).select('+password');
    if (!user || !(await user.comparePassword(password)))
      return res.status(401).json({ success: false, message: 'Email hoặc mật khẩu không đúng' });

    if (!user.role) {
      const adminEmails = getAdminEmails();
      user.role = adminEmails.includes(user.email.toLowerCase()) ? 'admin' : 'user';
      await user.save();
    }

    const token = generateToken(user._id);
    const userObj = user.toJSON();
    res.json({ success: true, token, user: userObj });
  } catch (err) { next(err); }
};

// GET /api/auth/me
const getMe = async (req, res) => {
  res.json({ success: true, user: req.user });
};

module.exports = { register, login, getMe };
