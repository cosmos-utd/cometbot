import {
  S3Client,
  PutObjectCommand,
  GetObjectCommand,
} from "@aws-sdk/client-s3";

const s3 = new S3Client({
  region: process.env.AWS_REGION,
  credentials: {
    accessKeyId: process.env.AWS_ACCESS_KEY_ID,
    secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY,
  },
});

// ---------------- UPLOAD ----------------
export async function uploadSyllabus(buffer, guildId) {
  const key = `syllabi/${guildId}/syllabus.txt`;

  await s3.send(
    new PutObjectCommand({
      Bucket: process.env.S3_BUCKET,
      Key: key,
      Body: buffer,
    })
  );

  return key;
}

// ---------------- DOWNLOAD ----------------
export async function getSyllabusText(guildId) {
  const key = `syllabi/${guildId}/syllabus.txt`;

  const res = await s3.send(
    new GetObjectCommand({
      Bucket: process.env.S3_BUCKET,
      Key: key,
    })
  );

  return streamToString(res.Body);
}

function streamToString(stream) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    stream.on("data", (c) => chunks.push(c));
    stream.on("end", () =>
      resolve(Buffer.concat(chunks).toString("utf-8"))
    );
    stream.on("error", reject);
  });
}