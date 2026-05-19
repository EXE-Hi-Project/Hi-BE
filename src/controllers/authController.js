const jwt = require('jsonwebtoken');
const { OAuth2Client } = require('google-auth-library');
const User = require('../models/User');
const { sendSuccess, sendError } = require('../utils/apiResponse');
const { normalizeEmail, validateLoginInput, validateRegisterInput } = require('../utils/authValidation');

const generateToken = (id) => jwt.sign({ id }, process.env.JWT_SECRET, { expiresIn: process.env.JWT_EXPIRES_IN || '7d' });
const getAdminEmails = () =>
  (process.env.ADMIN_EMAILS || '')
    .split(',')
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean);

const googleClient = new OAuth2Client(process.env.VITE_GOOGLE_CLIENT_ID || process.env.GOOGLE_CLIENT_ID);

const authPayload = (user) => ({
  token: generateToken(user._id),
  user: user.toJSON ? user.toJSON() : user,
});

// POST /api/auth/register
const register = async (req, res, next) => {
  try {
    const validation = validateRegisterInput(req.body);
    if (!validation.isValid) {
      return sendError(res, { statusCode: 400, message: validation.message });
    }

    const { name, email, password, gender } = validation.values;
    const existing = await User.findOne({ email });
    if (existing) return sendError(res, { statusCode: 400, message: 'Email đã được sử dụng' });

    const adminEmails = getAdminEmails();
    const role = adminEmails.includes(email) ? 'admin' : 'user';

    const user = await User.create({ name, email, password, gender, role });
    sendSuccess(res, {
      statusCode: 201,
      message: 'Đăng ký thành công',
      data: authPayload(user),
    });
  } catch (err) { next(err); }
};

// POST /api/auth/login
const login = async (req, res, next) => {
  try {
    const validation = validateLoginInput(req.body);
    if (!validation.isValid) {
      return sendError(res, { statusCode: 400, message: validation.message });
    }

    const { email, password } = validation.values;
    const user = await User.findOne({ email }).select('+password');
    if (!user || !(await user.comparePassword(password)))
      return sendError(res, { statusCode: 401, message: 'Email hoặc mật khẩu không đúng' });

    if (!user.role) {
      const adminEmails = getAdminEmails();
      user.role = adminEmails.includes(user.email.toLowerCase()) ? 'admin' : 'user';
      await user.save();
    }

    sendSuccess(res, {
      message: 'Đăng nhập thành công',
      data: authPayload(user),
    });
  } catch (err) { next(err); }
};

// GET /api/auth/me
const getMe = async (req, res) => {
  sendSuccess(res, { message: 'Lấy phiên đăng nhập thành công', data: { user: req.user } });
};

// POST /api/auth/refresh
const refresh = async (req, res) => {
  sendSuccess(res, {
    message: 'Làm mới phiên đăng nhập thành công',
    data: authPayload(req.user),
  });
};

// POST /api/auth/google
const googleAuth = async (req, res, next) => {
  try {
    const { credential, accessToken } = req.body;
    if (!credential && !accessToken) return sendError(res, { statusCode: 400, message: 'Thiếu Google credential' });

    let googleId, email, name, picture;

    if (credential) {
      // ID token flow (from GoogleLogin component)
      const ticket = await googleClient.verifyIdToken({
        idToken: credential,
        audience: process.env.VITE_GOOGLE_CLIENT_ID || process.env.GOOGLE_CLIENT_ID,
      });
      const payload = ticket.getPayload();
      googleId = payload.sub;
      email = payload.email;
      name = payload.name;
      picture = payload.picture;
    } else {
      // Access token flow (from useGoogleLogin hook)
      const userInfoRes = await fetch(`https://www.googleapis.com/oauth2/v3/userinfo`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      if (!userInfoRes.ok) return sendError(res, { statusCode: 401, message: 'Google token không hợp lệ' });
      const userInfo = await userInfoRes.json();
      googleId = userInfo.sub;
      email = normalizeEmail(userInfo.email);
      name = userInfo.name;
      picture = userInfo.picture;
    }

    email = normalizeEmail(email);
    if (!email) return sendError(res, { statusCode: 400, message: 'Tài khoản Google chưa có email hợp lệ' });

    let user = await User.findOne({ $or: [{ googleId }, { email }] });
    const adminEmails = getAdminEmails();

    if (!user) {
      user = await User.create({
        name,
        email,
        googleId,
        authProvider: 'google',
        avatar: picture || '',
        role: adminEmails.includes(email) ? 'admin' : 'user',
      });
    } else if (!user.googleId) {
      user.googleId = googleId;
      user.authProvider = 'google';
      if (picture && !user.avatar) user.avatar = picture;
      await user.save();
    }

    sendSuccess(res, { message: 'Đăng nhập Google thành công', data: authPayload(user) });
  } catch (err) { next(err); }
};

// POST /api/auth/facebook
const facebookAuth = async (req, res, next) => {
  try {
    const { accessToken, userID } = req.body;
    if (!accessToken || !userID) return sendError(res, { statusCode: 400, message: 'Thiếu Facebook token' });

    const fbRes = await fetch(
      `https://graph.facebook.com/${userID}?fields=id,name,email,picture&access_token=${accessToken}`
    );
    const fbData = await fbRes.json();
    if (fbData.error) return sendError(res, { statusCode: 401, message: 'Facebook token không hợp lệ' });

    const { id: facebookId, name, email, picture } = fbData;
    const normalizedEmail = normalizeEmail(email);
    const avatar = picture?.data?.url || '';

    let user = await User.findOne({ $or: [{ facebookId }, ...(normalizedEmail ? [{ email: normalizedEmail }] : [])] });
    const adminEmails = getAdminEmails();

    if (!user) {
      user = await User.create({
        name,
        email: normalizedEmail || `fb_${facebookId}@noemail.com`,
        facebookId,
        authProvider: 'facebook',
        avatar,
        role: normalizedEmail && adminEmails.includes(normalizedEmail) ? 'admin' : 'user',
      });
    } else if (!user.facebookId) {
      user.facebookId = facebookId;
      user.authProvider = 'facebook';
      if (avatar && !user.avatar) user.avatar = avatar;
      await user.save();
    }

    sendSuccess(res, { message: 'Đăng nhập Facebook thành công', data: authPayload(user) });
  } catch (err) { next(err); }
};

module.exports = { register, login, getMe, refresh, googleAuth, facebookAuth };
