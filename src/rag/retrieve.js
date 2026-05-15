/**
 * RAG Retrieve — embed câu hỏi user → tìm top-k chunks liên quan từ Chroma
 */
const { BedrockRuntimeClient, InvokeModelCommand } = require('@aws-sdk/client-bedrock-runtime');
const { ChromaClient } = require('chromadb');

const bedrockClient = new BedrockRuntimeClient({ region: process.env.AWS_REGION || 'us-east-1' });
const chroma = new ChromaClient({ path: process.env.CHROMA_URL || 'http://localhost:8000' });

const COLLECTION_NAME = 'hi_health_docs';
const TOP_K = 3;

/**
 * Tạo embedding cho text bằng Titan Embeddings
 */
async function embedText(text) {
  const command = new InvokeModelCommand({
    modelId: process.env.BEDROCK_EMBED_MODEL_ID || 'amazon.titan-embed-text-v1',
    contentType: 'application/json',
    accept: 'application/json',
    body: JSON.stringify({ inputText: text }),
  });
  const response = await bedrockClient.send(command);
  const result = JSON.parse(Buffer.from(response.body).toString('utf8'));
  return result.embedding;
}

/**
 * Tìm top-k đoạn tài liệu liên quan đến query
 * @param {string} query - Câu hỏi của user
 * @returns {string|null} - Chuỗi context ghép lại, hoặc null nếu không có
 */
async function retrieve(query) {
  try {
    const collection = await chroma.getCollection({ name: COLLECTION_NAME });
    const queryEmbedding = await embedText(query);

    const results = await collection.query({
      queryEmbeddings: [queryEmbedding],
      nResults: TOP_K,
    });

    if (!results.documents || results.documents[0].length === 0) return null;

    return results.documents[0]
      .map((doc, i) => `[${i + 1}] ${doc}`)
      .join('\n\n');
  } catch (_err) {
    // Chroma chưa chạy hoặc collection chưa có → bỏ qua RAG
    return null;
  }
}

module.exports = { retrieve, embedText };
