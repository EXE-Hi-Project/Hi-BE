const jwt = require('jsonwebtoken');
const { OAuth2Client } = require('google-auth-library');
const User = require('../models/User');

const generateToken = (id) => jwt.sign({ id }, process.env.JWT_SECRET, { expiresIn: process.env.JWT_EXPIRES_IN });
const getAdminEmails = () =>
  (process.env.ADMIN_EMAILS || '')
    .split(',')
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean);

const googleClient = new OAuth2Client(process.env.VITE_GOOGLE_CLIENT_ID || process.env.GOOGLE_CLIENT_ID);

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

// POST /api/auth/google
const googleAuth = async (req, res, next) => {
  try {
    const { credential, accessToken } = req.body;
    if (!credential && !accessToken) return res.status(400).json({ success: false, message: 'Thiếu Google credential' });

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
      if (!userInfoRes.ok) return res.status(401).json({ success: false, message: 'Google token không hợp lệ' });
      const userInfo = await userInfoRes.json();
      googleId = userInfo.sub;
      email = userInfo.email;
      name = userInfo.name;
      picture = userInfo.picture;
    }

    let user = await User.findOne({ $or: [{ googleId }, { email }] });
    const adminEmails = getAdminEmails();

    if (!user) {
      user = await User.create({
        name,
        email,
        googleId,
        authProvider: 'google',
        avatar: picture || '',
        role: adminEmails.includes(email.toLowerCase()) ? 'admin' : 'user',
      });
    } else if (!user.googleId) {
      user.googleId = googleId;
      user.authProvider = 'google';
      if (picture && !user.avatar) user.avatar = picture;
      await user.save();
    }

    const token = generateToken(user._id);
    res.json({ success: true, token, user: user.toJSON() });
  } catch (err) { next(err); }
};

// POST /api/auth/facebook
const facebookAuth = async (req, res, next) => {
  try {
    const { accessToken, userID } = req.body;
    if (!accessToken || !userID) return res.status(400).json({ success: false, message: 'Thiếu Facebook token' });

    const fbRes = await fetch(
      `https://graph.facebook.com/${userID}?fields=id,name,email,picture&access_token=${accessToken}`
    );
    const fbData = await fbRes.json();
    if (fbData.error) return res.status(401).json({ success: false, message: 'Facebook token không hợp lệ' });

    const { id: facebookId, name, email, picture } = fbData;
    const avatar = picture?.data?.url || '';

    let user = await User.findOne({ $or: [{ facebookId }, ...(email ? [{ email }] : [])] });
    const adminEmails = getAdminEmails();

    if (!user) {
      user = await User.create({
        name,
        email: email || `fb_${facebookId}@noemail.com`,
        facebookId,
        authProvider: 'facebook',
        avatar,
        role: email && adminEmails.includes(email.toLowerCase()) ? 'admin' : 'user',
      });
    } else if (!user.facebookId) {
      user.facebookId = facebookId;
      user.authProvider = 'facebook';
      if (avatar && !user.avatar) user.avatar = avatar;
      await user.save();
    }

    const token = generateToken(user._id);
    res.json({ success: true, token, user: user.toJSON() });
  } catch (err) { next(err); }
};

module.exports = { register, login, getMe, googleAuth, facebookAuth };
