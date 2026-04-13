const express = require('express');
const { getProfile, updateProfile, connectPartner, disconnectPartner, getPartnerCycles } = require('../controllers/userController');
const { protect } = require('../middleware/auth');

const router = express.Router();
router.use(protect);

router.get('/profile', getProfile);
router.put('/profile', updateProfile);
router.post('/connect-partner', connectPartner);
router.delete('/disconnect-partner', disconnectPartner);
router.get('/partner-cycles', getPartnerCycles);

module.exports = router;
