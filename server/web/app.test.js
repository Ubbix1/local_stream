const test = require('node:test');
const assert = require('node:assert/strict');
const { validateFeedback } = require('./app');

const validPayload = {
  device_id: 'device-123',
  device: { manufacturer: 'Samsung', model: 'SM-S918B', android_version: '16', android_sdk: 36, app_version: '1.4.0' },
  battery: { percentage: 78, is_charging: true, charging_source: 'USB' },
  timestamp: '2026-09-23T03:00:00.000Z',
};

test('accepts the documented internal payload', () => assert.equal(validateFeedback(validPayload), true));
test('rejects malformed or out-of-range diagnostics', () => {
  assert.equal(validateFeedback({ ...validPayload, battery: { ...validPayload.battery, percentage: 101 } }), false);
  assert.equal(validateFeedback({ ...validPayload, device_id: '' }), false);
});