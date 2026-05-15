const express = require('express');
const { register, login, getMe, googleAuth, facebookAuth } = require('../controllers/authController');
const { protect } = require('../middleware/auth');

const router = express.Router();

router.post('/register', register);
router.post('/login', login);
router.get('/me', protect, getMe);
router.post('/google', googleAuth);
router.post('/facebook', facebookAuth);

module.exports = router;
