package com.example.polyusigwebsite.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ResourceSearchRepository extends ElasticsearchRepository<ResourceSearchDocument, String> {
}
