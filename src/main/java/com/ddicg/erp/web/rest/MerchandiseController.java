
package com.ddicg.erp.web.rest;

import com.ddicg.erp.service.dto.AttributesDto;
import com.ddicg.erp.service.dto.CategoryDto;
import com.ddicg.erp.service.dto.ProductDto;
import com.ddicg.erp.service.dto.request.*;
import com.ddicg.erp.service.dto.response.CategoryExitingResponse;
import com.ddicg.erp.service.dto.response.ProductIsExiting;
import com.ddicg.erp.service.dto.response.ResponseConfig.PagingResponse;
import com.ddicg.erp.service.dto.response.ResponseConfig.Response;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RequestMapping("/api/merchandise")
public interface MerchandiseController {

        /************* Product CRUD *****************/

        @PostMapping(value = "/add-Product")
        @ResponseStatus(HttpStatus.CREATED)
        Response<?> addProduct(@RequestBody CreateProductRequest request);

        @PutMapping("/update-Product")
        @ResponseStatus(HttpStatus.OK)
        Response<?> updateProduct(@Valid @RequestBody UpdateProductRequest request);

        @DeleteMapping("/delete-Product")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        Response<?> deleteProduct(@RequestParam List<String> ids);

        @PostMapping("/search-Product")
        @ResponseStatus(HttpStatus.OK)
        Response<PagingResponse<ProductDto>> searchProduct(@Valid @RequestBody GetProductRequest request);

        @GetMapping("/products")
        @ResponseStatus(HttpStatus.OK)
        Response<List<ProductDto>> getProductsByIds(@RequestParam List<Long> ids);

        @GetMapping("/products/by-skus")
        @ResponseStatus(HttpStatus.OK)
        Response<List<ProductDto>> getProductsBySkus(@RequestParam List<String> skus);

        @GetMapping("/products/by-category-skus")
        @ResponseStatus(HttpStatus.OK)
        Response<List<ProductDto>> getProductsByCategorySkus(@RequestParam List<String> categorySkus);

        /************* Product Images Management *****************/

        @PostMapping(value = "/add-Product-Images/{productId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @ResponseStatus(HttpStatus.OK)
        Response<?> addProductImages(
                        @PathVariable String productId,
                        @RequestParam("images") List<MultipartFile> images);

        @DeleteMapping("/delete-Product-Image/{productId}")
        @ResponseStatus(HttpStatus.OK)
        Response<?> deleteProductImage(
                        @PathVariable String productId,
                        @RequestParam String imageKey);

        @PutMapping(value = "/replace-Product-Images/{productId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @ResponseStatus(HttpStatus.OK)
        Response<?> replaceProductImages(
                        @PathVariable String productId,
                        @RequestParam("images") List<MultipartFile> images);

        @GetMapping(value = "/view-image/{imageName}", produces = MediaType.IMAGE_JPEG_VALUE)
        @ResponseStatus(HttpStatus.OK)
        byte[] viewProductImage(@PathVariable String imageName);

        @GetMapping("/checkProduct/{name}")
        ProductIsExiting checkProduct(@RequestParam String name);

        @PostMapping("/view-Product/{productId}")
        @ResponseStatus(HttpStatus.OK)
        Response<?> incrementViewCount(@PathVariable String productId);

        /************* Category CRUD *****************/

        @PostMapping("/add-Category")
        @ResponseStatus(HttpStatus.CREATED)
        Response<?> addCategory(@Valid @RequestParam String name);

        @PutMapping("/update-Category")
        @ResponseStatus(HttpStatus.OK)
        Response<?> updateCategory(@Valid @RequestBody UpdateCategoryRequest categoryDto);

        @DeleteMapping("/delete-Category")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        Response<?> deleteCategory(@RequestParam @Valid List<String> ids);

        @PostMapping("/search-Category")
        @ResponseStatus(HttpStatus.OK)
        Response<PagingResponse<CategoryDto>> searchCategory(@Valid @RequestBody CategorySearchRequest request);

        @GetMapping("/categories")
        @ResponseStatus(HttpStatus.OK)
        Response<List<CategoryDto>> getCategoriesByIds(@RequestParam List<Long> ids);

        @GetMapping("/categories/by-skus")
        @ResponseStatus(HttpStatus.OK)
        Response<List<CategoryDto>> getCategoriesBySkus(@RequestParam List<String> skus);



        @GetMapping("/checkCategory/{name}")
        CategoryExitingResponse check(@RequestParam String name);

        /************* Attributes Management *****************/

        @PostMapping("/add-Attributes")
        @ResponseStatus(HttpStatus.CREATED)
        Response<List<AttributesDto>> addAttributes(@Valid @RequestBody CreateAttributesRequest request);

        @PutMapping("/update-Attributes")
        @ResponseStatus(HttpStatus.OK)
        Response<?> updateAttributes(@Valid @RequestBody UpdateAttributesRequest request);

        @DeleteMapping("/delete-Attributes")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        Response<?> deleteAttributes(@RequestParam @Valid List<String> ids);

        @DeleteMapping("/delete-Attributes-by-Product/{productId}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        Response<?> deleteAttributesByProduct(@PathVariable String productId);

        @PostMapping("/search-Attributes")
        @ResponseStatus(HttpStatus.OK)
        Response<PagingResponse<AttributesDto>> searchAttributes(@Valid @RequestBody AttributesSearchRequest request);

        @GetMapping("/attributes")
        @ResponseStatus(HttpStatus.OK)
        Response<List<AttributesDto>> getAttributesByIds(@RequestParam List<Long> ids);

        @GetMapping("/attributes/by-skus")
        @ResponseStatus(HttpStatus.OK)
        Response<List<AttributesDto>> getAttributesBySkus(@RequestParam List<String> skus);

        @GetMapping("/attributes/by-product-skus")
        @ResponseStatus(HttpStatus.OK)
        Response<List<AttributesDto>> getAttributesByProductSkus(@RequestParam List<String> productSkus);
}
