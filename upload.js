import fetch from "node-fetch";
import { uploadSyllabus } from "./s3.js";

export async function uploadFromUrl(url, guildId) {
  const res = await fetch(url);
  if (!res.ok) throw new Error("Download failed");

  const buffer = Buffer.from(await res.arrayBuffer());

  // store raw PDF bytes as text file in S3
  return await uploadSyllabus(buffer, guildId);
}