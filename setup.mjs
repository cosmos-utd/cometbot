import { readFileSync, writeFileSync, existsSync, unlinkSync } from "node:fs";
import {
  encryptEnv,
  promptHidden,
  promptVisible,
} from "./secrets.js";

const ENV_FILE = ".env";
const ENC_FILE = ".env.enc";

async function confirm(prompt) {
  const ans = await promptVisible(prompt);
  return /^y(es)?$/i.test(ans);
}

async function choosePasscode() {
  while (true) {
    const pass = await promptHidden("🔐 Choose a passcode: ");
    if (pass.length < 6) {
      console.log("⚠️  Passcode must be at least 6 characters.");
      continue;
    }
    const again = await promptHidden("🔐 Repeat passcode: ");
    if (pass === again) return pass;
    console.log("⚠️  Passcodes did not match.");
  }
}

function writeEncrypted(envText, passcode) {
  const payload = encryptEnv(envText, passcode);
  writeFileSync(ENC_FILE, payload, "utf8");
}

function removePlainEnv() {
  if (existsSync(ENV_FILE)) {
    unlinkSync(ENV_FILE);
  }
}

async function encryptExisting() {
  const envText = readFileSync(ENV_FILE, "utf8");
  console.log(`\n📄 Found ${ENV_FILE} with ${envText.split(/\r?\n/).filter((l) => l.trim() && !l.trim().startsWith("#")).length} entries.`);

  const passcode = await choosePasscode();
  writeEncrypted(envText, passcode);
  console.log(`✅ Wrote ${ENC_FILE}`);

  if (await confirm("🗑️  Delete plaintext .env now? (recommended) [y/N]: ")) {
    removePlainEnv();
    console.log("✅ Deleted .env");
  } else {
    console.log("ℹ️  Kept .env (bot will prefer .env.enc).");
  }
}

async function freshSetup() {
  console.log("\nEnter values. Secret fields are masked — safe to type on stream.");
  const values = {
    DISCORD_TOKEN: await promptHidden("  DISCORD_TOKEN (secret): "),
    AWS_ACCESS_KEY_ID: await promptHidden("  AWS_ACCESS_KEY_ID (secret): "),
    AWS_SECRET_ACCESS_KEY: await promptHidden("  AWS_SECRET_ACCESS_KEY (secret): "),
    AWS_REGION: (await promptVisible(`  AWS_REGION [us-east-2]: `)) || "us-east-2",
    S3_BUCKET: await promptVisible("  S3_BUCKET: "),
    DB_HOST: await promptVisible("  DB_HOST: "),
    DB_PORT: (await promptVisible("  DB_PORT [3306]: ")) || "3306",
    DB_USER: await promptVisible("  DB_USER: "),
    DB_PASSWORD: await promptHidden("  DB_PASSWORD (secret): "),
    DB_NAME: await promptVisible("  DB_NAME: "),
    BOT_OWNER_ID: await promptVisible("  BOT_OWNER_ID: "),
  };

  const envText =
    Object.entries(values)
      .map(([k, v]) => `${k}=${v}`)
      .join("\n") + "\n";

  if (await confirm("\n🔒 Encrypt into .env.enc with a passcode? [Y/n]: ")) {
    const passcode = await choosePasscode();
    writeEncrypted(envText, passcode);
    console.log(`✅ Wrote ${ENC_FILE}`);
    removePlainEnv();
    console.log("✅ Removed plaintext .env");
  } else {
    writeFileSync(ENV_FILE, envText, "utf8");
    console.log(`✅ Wrote plaintext ${ENV_FILE} (not recommended for streaming).`);
  }
}

async function main() {
  console.log("=== CometBot secret setup ===\n");

  if (existsSync(ENC_FILE)) {
    console.log("ℹ️  .env.enc already exists. Run `node index.js` — it will ask for the passcode.");
    return;
  }

  if (existsSync(ENV_FILE)) {
    if (await confirm("Found existing .env. Encrypt it with a passcode? [Y/n]: ")) {
      await encryptExisting();
      return;
    }
    console.log("ℹ️  Keeping .env as-is. Secrets are NOT passcode-protected.");
    return;
  }

  await freshSetup();
}

main();