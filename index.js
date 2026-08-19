import { loadSecrets } from "./secrets.js";

await loadSecrets();

const { startBot } = await import("./bot.js");
startBot();