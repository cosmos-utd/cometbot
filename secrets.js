import {
  createCipheriv,
  createDecipheriv,
  randomBytes,
  scryptSync,
} from "node:crypto";
import { readFileSync, existsSync } from "node:fs";
import readline from "node:readline";

const ENC_FILE = ".env.enc";
const ENV_FILE = ".env";
const SALT = "cometbot-env-v1";
const KEY_LEN = 32;

function deriveKey(passcode) {
  return scryptSync(passcode, SALT, KEY_LEN);
}

export function encryptEnv(envText, passcode) {
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", deriveKey(passcode), iv);
  const encrypted = Buffer.concat([
    cipher.update(envText, "utf8"),
    cipher.final(),
  ]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([iv, tag, encrypted]).toString("base64");
}

export function decryptEnv(payload, passcode) {
  const buf = Buffer.from(payload, "base64");
  const iv = buf.subarray(0, 12);
  const tag = buf.subarray(12, 28);
  const data = buf.subarray(28);
  const decipher = createDecipheriv("aes-256-gcm", deriveKey(passcode), iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(data), decipher.final()]).toString(
    "utf8"
  );
}

export function promptHidden(query) {
  return new Promise((resolve) => {
    const rl = readline.createInterface({
      input: process.stdin,
      output: process.stdout,
      terminal: true,
    });
    rl._writeToOutput = (str) => {
      if (str === query) rl.output.write(str);
    };
    rl.question(query, (answer) => {
      rl.close();
      process.stdout.write("\n");
      resolve(answer.trim());
    });
  });
}

export function promptVisible(query) {
  return new Promise((resolve) => {
    const rl = readline.createInterface({
      input: process.stdin,
      output: process.stdout,
    });
    rl.question(query, (answer) => {
      rl.close();
      resolve(answer.trim());
    });
  });
}

export function applyEnvText(envText) {
  for (const rawLine of envText.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;
    const eq = line.indexOf("=");
    if (eq === -1) continue;
    const key = line.slice(0, eq).trim();
    const value = line
      .slice(eq + 1)
      .trim()
      .replace(/^["']|["']$/g, "");
    if (key && process.env[key] === undefined) process.env[key] = value;
  }
}

export async function loadSecrets() {
  if (existsSync(ENC_FILE)) {
    const passcode = await promptHidden("🔐 Enter config passcode: ");
    let envText;
    try {
      envText = decryptEnv(readFileSync(ENC_FILE, "utf8"), passcode);
    } catch {
      console.error("\n❌ Wrong passcode or corrupted .env.enc");
      process.exit(1);
    }
    applyEnvText(envText);
    return;
  }

  if (existsSync(ENV_FILE)) {
    const { config } = await import("dotenv");
    config();
    return;
  }

  console.error("❌ No .env or .env.enc found. Run `npm run setup` first.");
  process.exit(1);
}