package com.example.retailflow.product.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "product_search")
public class ProductSearchDocument {

    @Id
    private Long skuId;

    @Field(type = FieldType.Keyword)
    private String skuCode;

    @Field(type = FieldType.Long)
    private Long spuId;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Text)
    private String subtitle;

    @Field(type = FieldType.Long)
    private Long categoryId;

    @Field(type = FieldType.Keyword)
    private String categoryName;

    @Field(type = FieldType.Long)
    private Long brandId;

    @Field(type = FieldType.Keyword)
    private String brandName;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Integer)
    private Integer publishStatus;
}
