package org.example.service;

import org.example.dto.MobileProductsResponse;
import org.example.dto.PatchProductRequest;
import org.example.dto.ProductWithBrandAndCategoryRow;
import org.example.dto.ProductWriteRequest;
import org.example.exception.BrandNotFoundException;
import org.example.exception.CategoryNotFoundException;
import org.example.exception.InvalidPaginationException;
import org.example.exception.ProductNotFoundException;
import org.example.model.Product;
import org.example.repository.BrandRepository;
import org.example.repository.CategoryRepository;
import org.example.repository.ProductRepository;

import java.util.*;

public class ProductService {
    private static final int MAX_LIMIT = 1000;

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, BrandRepository brandRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<Product> getProduct(int page, int size){
        long offset = (long) (page - 1) * size;
        return productRepository.readProduct(size, offset);
    }

    public Product getProductById(int productId){
        Optional<Product> optionalProduct = productRepository.readById(productId);

        if(optionalProduct.isEmpty()){
            throw new ProductNotFoundException(productId);
        }

        return optionalProduct.get();
    }

    public ProductPage getProducts(int offset, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidPaginationException("limit must be between 1 and " + MAX_LIMIT);
        }

        if (offset < 0) {
            throw new InvalidPaginationException("offset must be greater than or equal to 0");
        }

        List<ProductWithBrandAndCategoryRow> rows = productRepository.getProductsWithBrandsAndCategories(offset, limit);

        List<Product> products = new ArrayList<>();
        Map<Integer, String> brands = new HashMap<>();
        Map<Integer, String> categories = new HashMap<>();

        for (ProductWithBrandAndCategoryRow row : rows) {
            products.add(new Product(
                    row.productId(),
                    row.productName(),
                    row.brandId(),
                    row.categoryId(),
                    row.price()
            ));

            brands.put(row.brandId(), row.brandName());
            categories.put(row.categoryId(), row.categoryName());
        }

        long total = productRepository.countProducts();

        return new ProductPage(products, brands, categories, total, limit, offset);
    }

    public Product createProduct(ProductWriteRequest request){

        ensureBrandExists(request.brandId());
        ensureCategoryExists(request.categoryId());

        return productRepository.createProduct(request);
    }

    public Product updateProductById(int productId, ProductWriteRequest request){
        ensureBrandExists(request.brandId());
        ensureCategoryExists(request.categoryId());
        Optional<Product> optionalProduct = productRepository.updateById(productId, request);

        if(optionalProduct.isEmpty()){
            throw new ProductNotFoundException(productId);
        }

        return optionalProduct.get();
    }

    public Product updateProductFieldsById(int productId, PatchProductRequest request){
        if(request.brandId() != null){
            ensureBrandExists(request.brandId());
        }
        if(request.categoryId() != null){
            ensureCategoryExists(request.categoryId());
        }

        Optional<Product> optionalProduct = productRepository.updateFieldsById(productId, request);

        if(optionalProduct.isEmpty()){
            throw new ProductNotFoundException(productId);
        }

        return optionalProduct.get();
    }

    public Product deleteProductById(int productId){
        Optional<Product> optionalProduct = productRepository.deleteById(productId);

        if(optionalProduct.isEmpty()){
            throw new ProductNotFoundException(productId);
        }

        return optionalProduct.get();
    }

    private void ensureBrandExists(int brandId){
        if(!brandRepository.existsById(brandId)){
            throw new BrandNotFoundException(brandId);
        }
    }

    private void ensureCategoryExists(int categoryId){
        if(!categoryRepository.existsById(categoryId)){
            throw new CategoryNotFoundException(categoryId);
        }
    }
}