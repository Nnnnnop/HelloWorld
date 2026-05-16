package com.example.polyusigwebsite.config;

import com.example.polyusigwebsite.entity.ResourceFile;
import com.example.polyusigwebsite.repository.ResourceFileRepository;
import com.example.polyusigwebsite.search.ResourceSearchService;
import com.example.polyusigwebsite.service.FileContentExtractor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Configuration
@ConditionalOnProperty(name = "sig.search.enabled", havingValue = "true")
public class SearchReindexBootstrapConfig {
    private static final Logger log = LoggerFactory.getLogger(SearchReindexBootstrapConfig.class);

    @Bean
    CommandLineRunner backfillSearchContent(
            ResourceFileRepository resourceFileRepository,
            ResourceSearchService resourceSearchService,
            FileContentExtractor fileContentExtractor
    ) {
        return args -> {
            List<ResourceFile> files = resourceFileRepository.findAllWithUploader();
            for (ResourceFile file : files) {
                String extractedContent = "";
                try {
                    Path path = Paths.get(file.getFilePath());
                    extractedContent = fileContentExtractor.extract(path, file.getFileName());
                } catch (Exception ignored) {
                    // keep metadata-only index for unreadable files
                }
                file.setContentText(extractedContent);
                resourceFileRepository.save(file);
                try {
                    resourceSearchService.index(file, extractedContent);
                } catch (Exception ex) {
                    log.warn("Skip reindex for resource {} due to ES error: {}", file.getId(), ex.getMessage());
                }
            }
        };
    }
}
