/**
 * RAG Ingest — đọc tài liệu từ S3, chia chunk, embed bằng Titan, lưu vào Chroma
 *
 * Chạy 1 lần (hoặc khi cập nhật tài liệu):
 *   node src/rag/ingest.js
 *
 * Yêu cầu env:
 *   AWS_REGION, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY
 *   BEDROCK_EMBED_MODEL_ID, S3_RAG_BUCKET, CHROMA_URL
 */
require('dotenv').config();
const { S3Client, ListObjectsV2Command, GetObjectCommand } = require('@aws-sdk/client-s3');
const { BedrockRuntimeClient, InvokeModelCommand } = require('@aws-sdk/client-bedrock-runtime');
const { ChromaClient } = require('chromadb');

const s3 = new S3Client({ region: process.env.AWS_REGION || 'us-east-1' });
const bedrock = new BedrockRuntimeClient({ region: process.env.AWS_REGION || 'us-east-1' });
const chroma = new ChromaClient({ path: process.env.CHROMA_URL || 'http://localhost:8000' });

const BUCKET = process.env.S3_RAG_BUCKET || 'hi-rag-docs';
const COLLECTION_NAME = 'hi_health_docs';
const CHUNK_SIZE = 500;       // ký tự mỗi chunk
const CHUNK_OVERLAP = 50;     // ký tự overlap giữa các chunk

/** Chia text thành chunks có overlap */
function chunkText(text, size = CHUNK_SIZE, overlap = CHUNK_OVERLAP) {
  const chunks = [];
  let i = 0;
  while (i < text.length) {
    chunks.push(text.slice(i, i + size));
    i += size - overlap;
  }
  return chunks;
}

/** Đọc stream S3 thành string */
async function streamToString(stream) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    stream.on('data', chunk => chunks.push(chunk));
    stream.on('end', () => resolve(Buffer.concat(chunks).toString('utf-8')));
    stream.on('error', reject);
  });
}

/** Tạo embedding từ Bedrock Titan */
async function embedText(text) {
  const command = new InvokeModelCommand({
    modelId: process.env.BEDROCK_EMBED_MODEL_ID || 'amazon.titan-embed-text-v1',
    contentType: 'application/json',
    accept: 'application/json',
    body: JSON.stringify({ inputText: text }),
  });
  const response = await bedrock.send(command);
  const result = JSON.parse(Buffer.from(response.body).toString('utf8'));
  return result.embedding;
}

async function main() {
  console.log('=== RAG Ingest bắt đầu ===');

  // Lấy hoặc tạo collection Chroma
  const collection = await chroma.getOrCreateCollection({ name: COLLECTION_NAME });
  console.log(`Collection: ${COLLECTION_NAME}`);

  // List tất cả file trong S3 bucket
  const listCmd = new ListObjectsV2Command({ Bucket: BUCKET });
  const listRes = await s3.send(listCmd);
  const objects = listRes.Contents || [];
  console.log(`Tìm thấy ${objects.length} file trong s3://${BUCKET}`);

  let totalChunks = 0;
  for (const obj of objects) {
    if (!obj.Key) continue;
    console.log(`\nXử lý: ${obj.Key}`);

    // Đọc file từ S3
    const getCmd = new GetObjectCommand({ Bucket: BUCKET, Key: obj.Key });
    const getRes = await s3.send(getCmd);
    const text = await streamToString(getRes.Body);

    // Chia chunk
    const chunks = chunkText(text);
    console.log(`  → ${chunks.length} chunks`);

    // Embed và lưu vào Chroma (batch 10 cùng lúc)
    for (let i = 0; i < chunks.length; i += 10) {
      const batch = chunks.slice(i, i + 10);
      const embeddings = await Promise.all(batch.map(embedText));
      const ids = batch.map((_, j) => `${obj.Key}-chunk-${i + j}`);
      const metadatas = batch.map((_, j) => ({ source: obj.Key, chunkIndex: i + j }));

      await collection.upsert({ ids, embeddings, documents: batch, metadatas });
      process.stdout.write('.');
    }
    totalChunks += chunks.length;
  }

  console.log(`\n\n=== Ingest hoàn tất: ${totalChunks} chunks từ ${objects.length} file ===`);
}

main().catch(err => {
  console.error('Ingest lỗi:', err.message);
  process.exit(1);
});
