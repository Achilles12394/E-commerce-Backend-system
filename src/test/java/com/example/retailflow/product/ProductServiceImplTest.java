package com.example.retailflow.product;

import com.example.retailflow.product.dto.CreateProductRequest;
import com.example.retailflow.product.mapper.BrandMapper;
import com.example.retailflow.product.mapper.CategoryMapper;
import com.example.retailflow.product.mapper.FileRecordMapper;
import com.example.retailflow.product.mapper.SkuMapper;
import com.example.retailflow.product.mapper.SpuMapper;
import com.example.retailflow.product.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProductServiceImplTest {

    @Test
    void testCreateProduct() {
        ProductServiceImpl service = new ProductServiceImpl(
                Mockito.mock(CategoryMapper.class),
                Mockito.mock(BrandMapper.class),
                Mockito.mock(SpuMapper.class),
                Mockito.mock(SkuMapper.class),
                Mockito.mock(FileRecordMapper.class)
        );
        CreateProductRequest req = new CreateProductRequest();
        req.setSpuCode("SPU-T");
        req.setSkuCode("SKU-T");
        req.setTitle("test");
        req.setCategoryId(1L);
        req.setBrandId(1L);
        req.setPrice(BigDecimal.TEN);
        assertNotNull(service.createProduct(req));
    }
}