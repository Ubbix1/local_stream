const http = require('node:http');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const port = Number(process.env.PORT || 3000);
const webRoot = __dirname;
const deviceTokens = new Map(
	(process.env.DEVICE_TOKENS || '')
		.split(',')
		.map((entry) => entry.trim().split(':'))
		.filter(([deviceId, token]) => deviceId && token)
		.map(([deviceId, token]) => [token, deviceId])
);

function writeJson(response, code, body) {
	response.writeHead(code, { 'content-type': 'application/json; charset=utf-8' });
	response.end(JSON.stringify(body));
}

function isValidString(value, maxLength) {
	return typeof value === 'string' && value.length > 0 && value.length <= maxLength;
}

function validateFeedback(payload) {
	if (!payload || typeof payload !== 'object') return false;
	const device = payload.device;
	const battery = payload.battery;
	return isValidString(payload.device_id, 128)
		&& device && isValidString(device.manufacturer, 128)
		&& isValidString(device.model, 128)
		&& isValidString(device.android_version, 64)
		&& Number.isInteger(device.android_sdk) && device.android_sdk > 0
		&& isValidString(device.app_version, 64)
		&& battery && Number.isInteger(battery.percentage)
		&& battery.percentage >= 0 && battery.percentage <= 100
		&& typeof battery.is_charging === 'boolean'
		&& isValidString(battery.charging_source, 32)
		&& isValidString(payload.timestamp, 64);
}

function authenticate(request) {
	const header = request.headers.authorization || '';
	if (!header.startsWith('Bearer ')) return null;
	const token = header.slice('Bearer '.length);
	for (const [knownToken, deviceId] of deviceTokens) {
		const left = Buffer.from(token);
		const right = Buffer.from(knownToken);
		if (left.length === right.length && crypto.timingSafeEqual(left, right)) return deviceId;
	}
	return null;
}

async function readJson(request) {
	let body = '';
	for await (const chunk of request) {
		body += chunk;
		if (body.length > 16 * 1024) throw new Error('payload too large');
	}
	return JSON.parse(body);
}

async function handleFeedback(request, response) {
	const authenticatedDeviceId = authenticate(request);
	if (!authenticatedDeviceId) return writeJson(response, 401, { success: false, error: 'Unauthorized' });

	try {
		const payload = await readJson(request);
		if (payload.device_id !== authenticatedDeviceId || !validateFeedback(payload)) {
			return writeJson(response, 400, { success: false, error: 'Invalid request' });
		}

		return writeJson(response, 202, { success: true });
	} catch {
		return writeJson(response, 400, { success: false, error: 'Unable to process request' });
	}
}

const server = http.createServer(async (request, response) => {
	if (request.method === 'POST' && request.url === '/api/internal/device-feedback') {
		return handleFeedback(request, response);
	}
	if (request.method === 'GET' && request.url === '/api/health') {
		return writeJson(response, 200, { success: true, status: 'ok' });
	}
	if (request.method === 'GET') {
		if (request.url === '/' || request.url === '/index.html') {
			return serveFile(response, 'index.html', 'text/html; charset=utf-8');
		}
		if (request.url === '/logo.png') {
			return serveFile(response, 'logo.png', 'image/png');
		}
	}
	return writeJson(response, 404, { success: false, error: 'Not found' });
});

function serveFile(response, name, contentType) {
	const filePath = path.join(webRoot, name);
	fs.readFile(filePath, (error, data) => {
		if (error) return writeJson(response, 404, { success: false, error: 'Not found' });
		response.writeHead(200, { 'content-type': contentType });
		response.end(data);
	});
}

if (require.main === module) server.listen(port, () => {});

module.exports = { server, validateFeedback };
