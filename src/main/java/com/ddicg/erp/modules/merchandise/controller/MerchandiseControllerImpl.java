package com.ddicg.erp.modules.merchandise.controller;

import com.ddicg.erp.modules.merchandise.service.AttributesService;
import com.ddicg.erp.modules.merchandise.service.CategoryService;
import com.ddicg.erp.modules.merchandise.service.ProductService;
import com.ddicg.erp.modules.merchandise.service.iProductImage;
import com.ddicg.erp.core.common.service.MinioService;
import com.ddicg.erp.modules.merchandise.dto.AttributesDto;
import com.ddicg.erp.modules.merchandise.dto.CategoryDto;
import com.ddicg.erp.modules.merchandise.dto.ProductDto;
import com.ddicg.erp.modules.iam.dto.request.*;
import com.ddicg.erp.modules.merchandise.dto.request.*;
import com.ddicg.erp.modules.order.dto.request.*;
import com.ddicg.erp.core.common.dto.request.*;
import com.ddicg.erp.modules.merchandise.dto.response.CategoryExitingResponse;
import com.ddicg.erp.modules.merchandise.dto.response.ProductIsExiting;
import com.ddicg.erp.core.common.dto.response.PageableData;
import com.ddicg.erp.core.common.dto.response.PagingResponse;
import com.ddicg.erp.core.common.dto.response.Response;
import com.ddicg.erp.modules.merchandise.controller.MerchandiseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Tag(name = "Merchandise Management", description = "API quản lý sản phẩm, danh mục và thuộc tính")
public class MerchandiseControllerImpl implements MerchandiseController {
    private final ProductService productService;
    private final iProductImage productImageService;
    private final CategoryService categoryService;
    private final AttributesService attributesService;
    private final MinioService minioService;
    private final com.ddicg.erp.core.security.SecurityUtil securityUtil;

    /************* Product CRUD *****************/

    @Override
    @Operation(summary = "Tạo sản phẩm mới", description = "Tạo một sản phẩm mới trong hệ thống")
    public Response<?> addProduct(@RequestBody CreateProductRequest request) {
        return productService.addProduct(request);
    }

    @Override
    @Operation(summary = "Cập nhật sản phẩm", description = "Cập nhật thông tin của một sản phẩm đã tồn tại")
    public Response<?> updateProduct(@Valid @RequestBody UpdateProductRequest request) {
        return productService.updateProduct(request);
    }

    @Override
    @Operation(summary = "Xóa sản phẩm", description = "Đánh dấu xóa (soft delete) một hoặc nhiều sản phẩm dựa trên danh sách SKUs")
    public Response<?> deleteProduct(@RequestBody DeleteBySkusRequest request) {
        return productService.delete(request.getSkus());
    }

      @Override
      @Operation(summary = "Tìm kiếm sản phẩm", description = "Tìm kiếm sản phẩm theo các tiêu chí như tên, danh mục, giá, v.v.")
      public Response<PagingResponse<ProductDto>> searchProduct(@RequestBody GetProductRequest request) {
          final Page<ProductDto> products = productService.searchProducts(request);
          return Response.ok(PagingResponse.from(products));
      }



      @Override
    @Operation(summary = "Lấy danh sách sản phẩm theo SKUs", description = "Lấy chi tiết danh sách sản phẩm dựa trên danh sách mã SKUs")
    public Response<List<ProductDto>> getProductsBySkus(@RequestBody GetBySkusRequest request) {
        return productService.getProductsBySkus(request.getSkus());
    }

    @Override
    @Operation(summary = "Lấy danh sách sản phẩm theo SKU danh mục", description = "Resolve SKU danh mục sang ID danh mục trước khi lấy sản phẩm")
    public Response<List<ProductDto>> getProductsByCategorySkus(@RequestBody GetBySkusRequest request) {
        return productService.getProductsByCategorySkus(request.getSkus());
    }

    /************* Product Images Management *****************/

    @Override
    @Operation(summary = "Thêm hình ảnh sản phẩm", description = "Thêm một hoặc nhiều hình ảnh cho sản phẩm")
    public Response<?> addProductImages(
            @PathVariable String sku,
            @RequestParam("images") List<MultipartFile> images) {
        return productImageService.addProductImages(sku, images);
    }

    @Override
    @Operation(summary = "Xóa hình ảnh sản phẩm", description = "Xóa một hình ảnh cụ thể của sản phẩm")
    public Response<?> deleteProductImage(@RequestBody DeleteProductImageRequest request) {
        return productImageService.deleteProductImage(request.getSku(), request.getImageKey());
    }

    @Override
    @Operation(summary = "Thay thế hình ảnh sản phẩm", description = "Thay thế toàn bộ hình ảnh hiện tại của sản phẩm bằng hình ảnh mới")
    public Response<?> replaceProductImages(
            @PathVariable String sku,
            @RequestParam("images") List<MultipartFile> images) {
        return productImageService.replaceProductImages(sku, images);
    }

    @Override
    @Operation(summary = "Xem ảnh", description = "Lấy và xem trực tiếp ảnh sản phẩm dựa trên tên file ảnh")
    public byte[] viewProductImage(@PathVariable String imageName) {
        return productImageService.getProductImage(imageName);
    }

    @Override
    public ProductIsExiting checkProduct(@RequestBody CheckNameRequest request) {
        return productService.isExiting(request.getName());
    }

    @Override
    @Operation(summary = "Tăng lượt xem sản phẩm", description = "Tăng lượt xem khi người dùng truy cập chi tiết sản phẩm")
    public Response<?> incrementViewCount(@PathVariable String sku) {
        productService.viewCount(sku);
        return Response.ok("Đã tăng lượt xem");
    }

    /************* Category CRUD *****************/

    @Override
    @Operation(summary = "Tạo danh mục mới", description = "Tạo một danh mục sản phẩm mới")
    public Response<?> addCategory(@RequestBody CreateCategoryRequest request) {
        return categoryService.create(request.getName());
    }

    @Override
    @Operation(summary = "Cập nhật danh mục", description = "Cập nhật thông tin của một danh mục đã tồn tại")
    public Response<?> updateCategory(@RequestBody UpdateCategoryRequest request) {
        return categoryService.update(request);
    }

    @Override
    @Operation(summary = "Xóa danh mục", description = "Xóa một hoặc nhiều danh mục theo danh sách SKUs")
    public Response<?> deleteCategory(@RequestBody DeleteBySkusRequest request) {
        return categoryService.delete(request.getSkus());
    }

    @Override
    @Operation(summary = "Tìm kiếm danh mục", description = "Tìm kiếm danh mục theo các tiêu chí với phân trang")
    public Response<PagingResponse<CategoryDto>> searchCategory(@RequestBody CategorySearchRequest request) {
        final Page<CategoryDto> categories = categoryService.search(request);
        return Response.ok(
                PagingResponse.<CategoryDto>builder()
                        .contents(categories.getContent())
                        .paging(PageableData.from(categories))
                        .build());
    }

    @Override
    @Operation(summary = "Lấy danh sách danh mục theo SKUs", description = "Lấy chi tiết danh sách danh mục dựa trên danh sách mã SKUs")
    public Response<List<CategoryDto>> getCategoriesBySkus(@RequestBody GetBySkusRequest request) {
        return categoryService.getCategoriesBySkus(request.getSkus());
    }



    @Override
    public CategoryExitingResponse check(@RequestBody CheckNameRequest request) {
        return categoryService.isExiting(request.getName());
    }

    /************* Attributes Management *****************/

    @Override
    @Operation(summary = "Tạo thuộc tính sản phẩm", description = "Tạo thuộc tính/biến thể mới cho sản phẩm (ví dụ: màu sắc, kích thước)")
    public Response<List<AttributesDto>> addAttributes(@RequestBody CreateAttributesRequest request) {
        return attributesService.create(request);
    }

    @Override
    @Operation(summary = "Cập nhật thuộc tính sản phẩm", description = "Cập nhật thông tin của thuộc tính/biến thể sản phẩm")
    public Response<?> updateAttributes(@RequestBody UpdateAttributesRequest request) {
        return attributesService.update(request);
    }

    @Override
    @Operation(summary = "Xóa thuộc tính theo SKU", description = "Xóa một hoặc nhiều thuộc tính sản phẩm theo danh sách ids")
    public Response<?> deleteAttributes(@RequestParam List<String> ids) {
        return attributesService.delete(ids);
    }

    @Override
    @Operation(summary = "Xóa tất cả thuộc tính của sản phẩm", description = "Xóa tất cả các thuộc tính/biến thể của một sản phẩm cụ thể")
    public Response<?> deleteAttributesByProduct(@PathVariable String productId) {
        return attributesService.deleteByProduct(productId);
    }

    @Override
    @Operation(summary = "Tìm kiếm thuộc tính sản phẩm", description = "Tìm kiếm thuộc tính/biến thể theo các tiêu chí với phân trang")
    public Response<PagingResponse<AttributesDto>> searchAttributes(@RequestBody AttributesSearchRequest request) {
        final Page<AttributesDto> attributes = attributesService.search(request);
        return Response.ok(
                PagingResponse.<AttributesDto>builder()
                        .contents(attributes.getContent())
                        .paging(PageableData.from(attributes))
                        .build());
    }



    @Override
    @Operation(summary = "Lấy danh sách thuộc tính theo IDs", description = "Lấy chi tiết các thuộc tính từ cache hoặc DB dựa trên danh sách IDs")
    public Response<List<AttributesDto>> getAttributesByIds(@RequestParam List<Long> ids) {
        return attributesService.getAttributesByIds(ids);
    }

    @Override
    @Operation(summary = "Lấy danh sách thuộc tính theo SKUs", description = "Lấy chi tiết danh sách thuộc tính dựa trên danh sách mã SKUs")
    public Response<List<AttributesDto>> getAttributesBySkus(@RequestParam List<String> skus) {
        return attributesService.getAttributesBySkus(skus);
    }

    @Override
    @Operation(summary = "Lấy danh sách thuộc tính theo SKU sản phẩm", description = "Resolve SKU sản phẩm sang ID sản phẩm trước khi lấy thuộc tính")
    public Response<List<AttributesDto>> getAttributesByProductSkus(@RequestParam List<String> productSkus) {
        return attributesService.getAttributesByProductSkus(productSkus);
    }



    @Operation(summary = "Upload file", description = "Upload file lên MinIO storage")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(minioService.uploadFile(file));
    }
}
