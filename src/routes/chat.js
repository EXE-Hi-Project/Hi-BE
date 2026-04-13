const express = require('express');
const { getHistory, sendMessage } = require('../controllers/chatController');
const { protect } = require('../middleware/auth');

const router = express.Router();
router.use(protect);

router.get('/', getHistory);
router.post('/', sendMessage);

module.exports = router;
