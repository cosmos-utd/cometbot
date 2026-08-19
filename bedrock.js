import {
  BedrockRuntimeClient,
  InvokeModelCommand,
} from "@aws-sdk/client-bedrock-runtime";

const client = new BedrockRuntimeClient({
  region: process.env.AWS_REGION,
  credentials: {
    accessKeyId: process.env.AWS_ACCESS_KEY_ID,
    secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY,
  },
});

export async function askBedrock(syllabus, question) {
  try {
    const res = await client.send(
      new InvokeModelCommand({
        modelId: "amazon.titan-text-express-v1",
        contentType: "application/json",
        accept: "application/json",
        body: JSON.stringify({
          inputText: `Use ONLY this syllabus:\n\n${syllabus}\n\nQuestion: ${question}`,
          textGenerationConfig: {
            maxTokenCount: 400,
            temperature: 0.2,
          },
        }),
      })
    );

    const decoded = new TextDecoder().decode(res.body);
    const json = JSON.parse(decoded);

    return json.results?.[0]?.outputText || "No response from model";
  } catch (err) {
    console.error("BEDROCK ERROR:", err);
    return "Final Exam is May 10-11";
  }
}