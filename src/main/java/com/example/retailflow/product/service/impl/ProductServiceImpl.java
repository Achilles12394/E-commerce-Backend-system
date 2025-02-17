package com.example.retailflow.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.retailflow.common.enums.ErrorCode;
import com.example.retailflow.common.exception.BizException;
import com.example.retailflow.product.dto.CreateBrandRequest;
import com.example.retailflow.product.dto.CreateCategoryRequest;
import com.example.retailflow.product.dto.CreateProductRequest;
import com.example.retailflow.product.dto.ProductResponse;
import com.example.retailflow.product.dto.ProductSearchRequest;
import com.example.retailflow.product.entity.BrandEntity;
import com.example.retailflow.product.entity.CategoryEntity;
import com.example.retailflow.product.entity.FileRecordEntity;
import com.example.retailflow.product.entity.SkuEntity;
import com.example.retailflow.product.entity.SpuEntity;
import com.example.retailflow.product.mapper.BrandMapper;
import com.example.retailflow.product.mapper.CategoryMapper;
import com.example.retailflow.product.mapper.FileRecordMapper;
import com.example.retailflow.product.mapper.SkuMapper;
import com.example.retailflow.product.mapper.SpuMapper;
import com.example.retailflow.product.search.ProductSearchIndexService;
import com.example.retailflow.product.storage.ProductMediaStorageService;
import com.example.retailflow.product.storage.StoredFileResult;
import com.example.retailflow.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final CategoryMapper categoryMapper;
    private final BrandMapper brandMapper;
    private final SpuMapper spuMapper;
    private final SkuMapper skuMapper;
    private final FileRecordMapper fileRecordMapper;
    private final ObjectProvider<ProductSearchIndexService> productSearchIndexServiceProvider;
    private final ProductMediaStorageService productMediaStorageService;

    @Override
    @Transactional
    @CacheEvict(cacheNames = "category:list", allEntries = true)
    public Long createCategory(CreateCategoryRequest request) {
        CategoryEntity entity = new CategoryEntity();
        entity.setId(System.nanoTime());
        entity.setName(request.getName());
        entity.setParentId(request.getParentId());
        entity.setSortNo(request.getSortNo());
        entity.setStatus(1);
        categoryMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional
    public Long createBrand(CreateBrandRequest request) {
        BrandEntity entity = new BrandEntity();
        entity.setId(System.nanoTime());
        entity.setName(request.getName());
        entity.setLogoUrl(request.getLogoUrl());
        entity.setStatus(1);
        brandMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional
    public Long createProduct(CreateProductRequest request) {
        SpuEntity spu = new SpuEntity();
        spu.setId(System.nanoTime());
        spu.setSpuCode(request.getSpuCode());
        spu.setTitle(request.getTitle());
        spu.setSubtitle(request.getSubtitle());
        spu.setCategoryId(request.getCategoryId());
        spu.setBrandId(request.getBrandId());
        spu.setPublishStatus(0);
        spuMapper.insert(spu);

        SkuEntity sku = new SkuEntity();
        sku.setId(System.currentTimeMillis());
        sku.setSkuCode(request.getSkuCode());
        sku.setSpuId(spu.getId());
        sku.setTitle(request.getTitle());
        sku.setPrice(request.getPrice());
        sku.setSalesCount(0L);
        sku.setStockStatus(1);
        skuMapper.insert(sku);
        syncSearchIndexQuietly(sku.getId());
        return sku.getId();
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "product:detail", key = "#skuId")
    public Long updateProduct(Long skuId, CreateProductRequest request) {
        SkuEntity sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        sku.setTitle(request.getTitle());
        sku.setPrice(request.getPrice());
        skuMapper.updateById(sku);

        SpuEntity spu = spuMapper.selectById(sku.getSpuId());
        if (spu != null) {
            spu.setSubtitle(request.getSubtitle());
            spu.setCategoryId(request.getCategoryId());
            spu.setBrandId(request.getBrandId());
            spuMapper.updateById(spu);
        }
        syncSearchIndexQuietly(skuId);
        return skuId;
    }

    @Override
    @Transactional
    public void publish(Long skuId) {
        SkuEntity sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        SpuEntity spu = spuMapper.selectById(sku.getSpuId());
        spu.setPublishStatus(1);
        spuMapper.updateById(spu);
        syncSearchIndexQuietly(skuId);
    }

    @Override
    @Transactional
    public void unpublish(Long skuId) {
        SkuEntity sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        SpuEntity spu = spuMapper.selectById(sku.getSpuId());
        spu.setPublishStatus(0);
        spuMapper.updateById(spu);
        removeSearchIndexQuietly(skuId);
    }

    @Override
    @Transactional
    public String uploadSkuImage(Long skuId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "上传文件不能为空");
        }
        StoredFileResult storedFile = productMediaStorageService.storeSkuImage(skuId, file);

        FileRecordEntity record = new FileRecordEntity();
        record.setId(System.nanoTime());
        record.setBizType("SKU_IMAGE");
        record.setBizId(skuId);
        record.setFileName(file.getOriginalFilename());
        record.setStoragePlatform(storedFile.getStoragePlatform());
        record.setBucketName(storedFile.getBucketName());
        record.setObjectKey(storedFile.getObjectKey());
        record.setFileUrl(storedFile.getFileUrl());
        record.setContentType(storedFile.getContentType());
        record.setFileSize(storedFile.getFileSize());
        fileRecordMapper.insert(record);
        return storedFile.getFileUrl();
    }

    @Override
    @Cacheable(cacheNames = "product:detail", key = "#skuId")
    public ProductResponse getProduct(Long skuId) {
        SkuEntity sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        SpuEntity spu = spuMapper.selectById(sku.getSpuId());
        if (spu == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return toResponse(sku, spu);
    }

    @Override
    public Page<ProductResponse> page(Integer pageNum, Integer pageSize) {
        Page<SkuEntity> page = skuMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<>());
        Page<ProductResponse> result = new Page<>(pageNum, pageSize, page.getTotal());
        result.setRecords(page.getRecords().stream().map(item -> getProduct(item.getId())).toList());
        return result;
    }

    @Override
    public List<ProductResponse> search(ProductSearchRequest request) {
        ProductSearchIndexService searchIndexService = productSearchIndexServiceProvider.getIfAvailable();
        if (searchIndexService != null) {
            try {
                return searchIndexService.search(request);
            } catch (Exception ex) {
                log.warn("search from elasticsearch failed, fallback to mysql, keyword={}", request.getKeyword(), ex);
            }
        }

        return searchFromDatabase(request);
    }

    @Override
    public long rebuildSearchIndex() {
        ProductSearchIndexService searchIndexService = productSearchIndexServiceProvider.getIfAvailable();
        if (searchIndexService == null) {
            return 0L;
        }
        return searchIndexService.rebuildIndex();
    }

    @Override
    @Cacheable(cacheNames = "category:list", key = "'all'")
    public List<CreateCategoryRequest> categoryCacheList() {
        return categoryMapper.selectList(new LambdaQueryWrapper<CategoryEntity>().eq(CategoryEntity::getStatus, 1))
                .stream()
                .map(c -> {
                    CreateCategoryRequest dto = new CreateCategoryRequest();
                    dto.setName(c.getName());
                    dto.setParentId(c.getParentId());
                    dto.setSortNo(c.getSortNo());
                    return dto;
                }).toList();
    }

    private List<ProductResponse> searchFromDatabase(ProductSearchRequest request) {
        LambdaQueryWrapper<SkuEntity> skuQuery = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(request.getKeyword())) {
            skuQuery.like(SkuEntity::getTitle, request.getKeyword());
        }
        if (request.getMinPrice() != null) {
            skuQuery.ge(SkuEntity::getPrice, request.getMinPrice());
        }
        if (request.getMaxPrice() != null) {
            skuQuery.le(SkuEntity::getPrice, request.getMaxPrice());
        }

        if (request.getCategoryId() != null || request.getBrandId() != null) {
            LambdaQueryWrapper<SpuEntity> spuQuery = new LambdaQueryWrapper<>();
            if (request.getCategoryId() != null) {
                spuQuery.eq(SpuEntity::getCategoryId, request.getCategoryId());
            }
            if (request.getBrandId() != null) {
                spuQuery.eq(SpuEntity::getBrandId, request.getBrandId());
            }
            List<Long> spuIds = spuMapper.selectList(spuQuery).stream().map(SpuEntity::getId).toList();
            if (spuIds.isEmpty()) {
                return List.of();
            }
            skuQuery.in(SkuEntity::getSpuId, spuIds);
        }

        Page<SkuEntity> page = skuMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()), skuQuery);
        return page.getRecords().stream().map(item -> {
            SpuEntity spu = spuMapper.selectById(item.getSpuId());
            return toResponse(item, spu);
        }).toList();
    }

    private void syncSearchIndexQuietly(Long skuId) {
        ProductSearchIndexService searchIndexService = productSearchIndexServiceProvider.getIfAvailable();
        if (searchIndexService == null) {
            return;
        }
        try {
            searchIndexService.syncProduct(skuId);
        } catch (Exception ex) {
            log.warn("sync elasticsearch index failed, skuId={}", skuId, ex);
        }
    }

    private void removeSearchIndexQuietly(Long skuId) {
        ProductSearchIndexService searchIndexService = productSearchIndexServiceProvider.getIfAvailable();
        if (searchIndexService == null) {
            return;
        }
        try {
            searchIndexService.removeProduct(skuId);
        } catch (Exception ex) {
            log.warn("remove elasticsearch index failed, skuId={}", skuId, ex);
        }
    }

    private ProductResponse toResponse(SkuEntity sku, SpuEntity spu) {
        CategoryEntity category = categoryMapper.selectById(spu.getCategoryId());
        BrandEntity brand = brandMapper.selectById(spu.getBrandId());
        return ProductResponse.builder()
                .skuId(sku.getId())
                .skuCode(sku.getSkuCode())
                .spuId(spu.getId())
                .title(sku.getTitle())
                .subtitle(spu.getSubtitle())
                .price(sku.getPrice())
                .publishStatus(spu.getPublishStatus())
                .categoryName(category == null ? null : category.getName())
                .brandName(brand == null ? null : brand.getName())
                .build();
    }

}
