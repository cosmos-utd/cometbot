import { Client, GatewayIntentBits } from "discord.js";
import { uploadFromUrl } from "./upload.js";
import { getSyllabusText } from "./s3.js";
import { askBedrock } from "./bedrock.js";

const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMessages,
    GatewayIntentBits.MessageContent,
  ],
});

export function startBot() {
  client.once("ready", () => {
    console.log(`Logged in as ${client.user.tag}`);
  });

  client.on("messageCreate", async (message) => {
    if (message.author.bot) return;
    if (!message.guild) return;

    const guildId = message.guild.id;

    // ---------------- !upload ----------------
    if (message.content.startsWith("!upload")) {
      const url = message.content.replace("!upload", "").trim();

      if (!url.startsWith("http")) {
        return message.reply("❌ Invalid URL");
      }

      await message.reply("📥 Uploading syllabus...");

      try {
        await uploadFromUrl(url, guildId);
        return message.reply("✅ Uploaded to S3");
      } catch (err) {
        console.error(err);
        return message.reply("❌ Upload failed");
      }
    }

    // ---------------- !ask ----------------
    if (message.content.startsWith("!ask")) {
      const question = message.content.replace("!ask", "").trim();

      await message.reply("📄 Reading syllabus...");

      try {
        const syllabus = await getSyllabusText(guildId);

        await message.reply("🤖 Thinking...");

        const answer = await askBedrock(syllabus, question);

        return message.reply(answer);
      } catch (err) {
        console.error(err);
        return message.reply("❌ Error answering question");
      }
    }
  });

  client.login(process.env.DISCORD_TOKEN);
}