package com.example.retailflow.product.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.retailflow.product.dto.ProductResponse;
import com.example.retailflow.product.dto.ProductSearchRequest;
import com.example.retailflow.product.entity.BrandEntity;
import com.example.retailflow.product.entity.CategoryEntity;
import com.example.retailflow.product.entity.SkuEntity;
import com.example.retailflow.product.entity.SpuEntity;
import com.example.retailflow.product.mapper.BrandMapper;
import com.example.retailflow.product.mapper.CategoryMapper;
import com.example.retailflow.product.mapper.SkuMapper;
import com.example.retailflow.product.mapper.SpuMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.search.elasticsearch", name = "enabled", havingValue = "true")
public class ElasticsearchProductSearchService implements ProductSearchIndexService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final SkuMapper skuMapper;
    private final SpuMapper spuMapper;
    private final CategoryMapper categoryMapper;
    private final BrandMapper brandMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void syncProduct(Long skuId) {
        ensureIndex();
        ProductSearchDocument document = loadDocument(skuId);
        if (document == null) {
            removeProduct(skuId);
            return;
        }
        elasticsearchOperations.save(document);
    }

    @Override
    public void removeProduct(Long skuId) {
        elasticsearchOperations.delete(String.valueOf(skuId), ProductSearchDocument.class);
    }

    @Override
    public List<ProductResponse> search(ProductSearchRequest request) {
        ensureIndex();
        StringQuery query = new StringQuery(buildQueryJson(request));
        query.setPageable(PageRequest.of(normalizePageNum(request.getPageNum()) - 1, normalizePageSize(request.getPageSize())));
        SearchHits<ProductSearchDocument> hits = elasticsearchOperations.search(query, ProductSearchDocument.class);
        return hits.stream()
                .map(SearchHit::getContent)
                .map(this::toResponse)
                .toList();
    }

    @Override
    public long rebuildIndex() {
        IndexOperations indexOperations = elasticsearchOperations.indexOps(ProductSearchDocument.class);
        if (indexOperations.exists()) {
            indexOperations.delete();
        }
        indexOperations.create();
        indexOperations.putMapping(indexOperations.createMapping());

        long count = 0L;
        List<SkuEntity> skus = skuMapper.selectList(new LambdaQueryWrapper<SkuEntity>().orderByAsc(SkuEntity::getId));
        for (SkuEntity sku : skus) {
            ProductSearchDocument document = loadDocument(sku.getId());
            if (document == null) {
                continue;
            }
            elasticsearchOperations.save(document);
            count++;
        }
        log.info("rebuilt product search index, count={}", count);
        return count;
    }

    private void ensureIndex() {
        IndexOperations indexOperations = elasticsearchOperations.indexOps(ProductSearchDocument.class);
        if (indexOperations.exists()) {
            return;
        }
        indexOperations.create();
        indexOperations.putMapping(indexOperations.createMapping());
    }

    private ProductSearchDocument loadDocument(Long skuId) {
        SkuEntity sku = skuMapper.selectById(skuId);
        if (sku == null) {
            return null;
        }
        SpuEntity spu = spuMapper.selectById(sku.getSpuId());
        if (spu == null || !Integer.valueOf(1).equals(spu.getPublishStatus())) {
            return null;
        }

        CategoryEntity category = categoryMapper.selectById(spu.getCategoryId());
        BrandEntity brand = brandMapper.selectById(spu.getBrandId());

        return ProductSearchDocument.builder()
                .skuId(sku.getId())
                .skuCode(sku.getSkuCode())
                .spuId(spu.getId())
                .title(sku.getTitle())
                .subtitle(spu.getSubtitle())
                .categoryId(spu.getCategoryId())
                .categoryName(category == null ? null : category.getName())
                .brandId(spu.getBrandId())
                .brandName(brand == null ? null : brand.getName())
                .price(sku.getPrice())
                .publishStatus(spu.getPublishStatus())
                .build();
    }

    private ProductResponse toResponse(ProductSearchDocument document) {
        return ProductResponse.builder()
                .skuId(document.getSkuId())
                .skuCode(document.getSkuCode())
                .spuId(document.getSpuId())
                .title(document.getTitle())
                .subtitle(document.getSubtitle())
                .price(document.getPrice())
                .publishStatus(document.getPublishStatus())
                .categoryName(document.getCategoryName())
                .brandName(document.getBrandName())
                .build();
    }

    private String buildQueryJson(ProductSearchRequest request) {
        Map<String, Object> boolQuery = new LinkedHashMap<>();
        List<Object> filters = new ArrayList<>();
        filters.add(Map.of("term", Map.of("publishStatus", 1)));

        if (request.getCategoryId() != null) {
            filters.add(Map.of("term", Map.of("categoryId", request.getCategoryId())));
        }
        if (request.getBrandId() != null) {
            filters.add(Map.of("term", Map.of("brandId", request.getBrandId())));
        }
        if (request.getMinPrice() != null || request.getMaxPrice() != null) {
            Map<String, Object> priceRange = new LinkedHashMap<>();
            if (request.getMinPrice() != null) {
                priceRange.put("gte", request.getMinPrice());
            }
            if (request.getMaxPrice() != null) {
                priceRange.put("lte", request.getMaxPrice());
            }
            filters.add(Map.of("range", Map.of("price", priceRange)));
        }
        boolQuery.put("filter", filters);

        if (StringUtils.hasText(request.getKeyword())) {
            boolQuery.put("must", List.of(Map.of(
                    "multi_match", Map.of(
                            "query", request.getKeyword().trim(),
                            "fields", List.of("title^3", "subtitle^2", "brandName", "categoryName"),
                            "type", "best_fields"
                    )
            )));
        }

        try {
            return objectMapper.writeValueAsString(Map.of("bool", boolQuery));
        } catch (Exception ex) {
            throw new IllegalStateException("build es query failed", ex);
        }
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 50);
    }
}
