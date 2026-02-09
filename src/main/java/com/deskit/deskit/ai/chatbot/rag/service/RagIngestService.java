package com.deskit.deskit.ai.chatbot.rag.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Log4j2
@Service
public class RagIngestService {

    private final RedisVectorStore vectorStore;
    private final RedisVectorStore evalVectorStore;
    private final int chunkSize;
    private final int minChunkSizeChars;

    public RagIngestService(
            RedisVectorStore vectorStore,
            @Qualifier("evalVectorStore") RedisVectorStore evalVectorStore,
            @Value("${rag.chunk.size:500}") int chunkSize,
            @Value("${rag.chunk.min-chars:100}") int minChunkSizeChars
    ) {
        this.vectorStore = vectorStore;
        this.evalVectorStore = evalVectorStore;
        this.chunkSize = chunkSize;
        this.minChunkSizeChars = minChunkSizeChars;
    }

    public void ingest(MultipartFile file) {
        try {
            Resource resource = new InputStreamResource(file.getInputStream());
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            List<Document> documents = reader.get();
            String source = file.getOriginalFilename();
            List<Document> enrichedDocs = documents.stream()
                    .map(doc -> new Document(
                            doc.getText(),
                            Map.of(
                                    "source", source
                            )
                    ))
                    .toList();
            List<Document> indexedChunks = splitAndIndex(enrichedDocs, source);
            vectorStore.add(indexedChunks);
            log.info("📄 RAG 문서 업로드: {}", file.getOriginalFilename());
            log.info("📄 생성된 Document 수: {}", documents.size());
            log.info("📄 생성된 청크 수: {} (chunk_index: 0 ~ {})", indexedChunks.size(), Math.max(0, indexedChunks.size() - 1));

        } catch (IOException e) {
            throw new RuntimeException("문서 업로드 실패", e);
        }
    }

    public void ingest(Resource resource) {
        DocumentReader reader = new TikaDocumentReader(resource);
        List<Document> documents = reader.get();

        String source = resource.getFilename();
        List<Document> enrichedDocs = documents.stream()
                .map(doc -> new Document(
                        doc.getText(),
                        Map.of(
                                "source", source
                        )
                ))
                .toList();
        List<Document> indexedChunks = splitAndIndex(enrichedDocs, source);
        vectorStore.add(indexedChunks);
        log.info("📄 RAG 문서 업로드: {}", resource.getFilename());
        log.info("📄 생성된 Document 수: {}", documents.size());
        log.info("📄 생성된 청크 수: {} (chunk_index: 0 ~ {})", indexedChunks.size(), Math.max(0, indexedChunks.size() - 1));
    }

    public void ingestToEvaluationStore(Resource resource) {
        DocumentReader reader = new TikaDocumentReader(resource);
        List<Document> documents = reader.get();

        String source = resource.getFilename();
        List<Document> enrichedDocs = documents.stream()
                .map(doc -> new Document(
                        doc.getText(),
                        Map.of(
                                "source", source
                        )
                ))
                .toList();
        List<Document> indexedChunks = splitAndIndex(enrichedDocs, source);
        evalVectorStore.add(indexedChunks);
        log.info("📄 RAG 평가 문서 생성 수: {}", documents.size());
        log.info("📄 RAG 평가 문서 청크 수: {} (chunk_index: 0 ~ {})", indexedChunks.size(), Math.max(0, indexedChunks.size() - 1));
        log.info("📄 RAG 평가 문서 업로드: {}", resource.getFilename());
    }

    private List<Document> splitAndIndex(List<Document> enrichedDocs, String source) {
        TokenTextSplitter splitter = new TokenTextSplitter(chunkSize, minChunkSizeChars, 5, 10000, true);
        List<Document> chunks = splitter.split(enrichedDocs);
        return IntStream.range(0, chunks.size())
                .mapToObj(i -> {
                    Document chunk = chunks.get(i);
                    Map<String, Object> metadata = new HashMap<>(chunk.getMetadata());
                    metadata.put("source", source);
                    metadata.put("chunk_index", i);
                    return new Document(chunk.getText(), metadata);
                })
                .toList();
    }

}
