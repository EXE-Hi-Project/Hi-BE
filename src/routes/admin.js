const express = require('express');
const { protect, authorize } = require('../middleware/auth');
const { getOverview, getUsers, updateUserRole } = require('../controllers/adminController');

const router = express.Router();

router.use(protect, authorize('admin'));

router.get('/overview', getOverview);
router.get('/users', getUsers);
router.patch('/users/:id/role', updateUserRole);

module.exports = router;
