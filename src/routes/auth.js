const express = require('express');
const { register, login, getMe, refresh, googleAuth, facebookAuth } = require('../controllers/authController');
const { protect } = require('../middleware/auth');
const { loginLimiter, registerLimiter } = require('../middleware/rateLimit');

const router = express.Router();

router.post('/register', registerLimiter, register);
router.post('/login', loginLimiter, login);
router.get('/me', protect, getMe);
router.post('/refresh', protect, refresh);
router.post('/google', googleAuth);
router.post('/facebook', facebookAuth);

module.exports = router;
