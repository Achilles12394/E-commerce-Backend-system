package com.example.retailflow.product.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCategoryRequest {
    @NotBlank
    private String name;
    private Long parentId = 0L;
    private Integer sortNo = 0;
}