package org.example.service;

import org.example.dto.CreateProductRequest;
import org.example.exception.BrandNotFoundException;
import org.example.exception.CategoryNotFoundException;
import org.example.model.Product;
import org.example.repository.BrandRepository;
import org.example.repository.CategoryRepository;
import org.example.repository.ProductRepository;

import java.util.List;

public class ProductService {

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
        return productRepository.listProduct(size, offset);
    }

    public Product createProduct(CreateProductRequest request){

        if(!brandRepository.existsById(request.brandId())){
            throw new BrandNotFoundException(request.brandId());
        }

        if(!categoryRepository.existsById(request.categoryId())){
            throw new CategoryNotFoundException(request.categoryId());
        }

        return productRepository.createProduct(request);
    }
}