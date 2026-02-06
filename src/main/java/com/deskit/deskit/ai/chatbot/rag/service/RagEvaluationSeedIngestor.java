package com.deskit.deskit.ai.chatbot.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class RagEvaluationSeedIngestor implements ApplicationRunner {

    private final RagIngestService ragIngestService;
    private final ResourceLoader resourceLoader;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Resource[] resources =
                new PathMatchingResourcePatternResolver(resourceLoader)
                        .getResources("classpath:rag/eval-seed/*");

        for (Resource resource : resources) {
            ragIngestService.ingestToEvaluationStore(resource);
            log.info("Ingested evaluation seed: {}", resource.getFilename());
        }
    }
}
