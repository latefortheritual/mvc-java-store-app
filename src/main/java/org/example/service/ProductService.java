package org.example.service;

import org.example.dto.MobileProductsResponse;
import org.example.dto.PatchProductRequest;
import org.example.dto.ProductWriteRequest;
import org.example.exception.BrandNotFoundException;
import org.example.exception.CategoryNotFoundException;
import org.example.exception.ProductNotFoundException;
import org.example.model.Product;
import org.example.repository.BrandRepository;
import org.example.repository.CategoryRepository;
import org.example.repository.ProductRepository;

import java.util.List;
import java.util.Optional;

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
        return productRepository.readProduct(size, offset);
    }

    public Product getProductById(int productId){
        Optional<Product> optionalProduct = productRepository.readById(productId);

        if(optionalProduct.isEmpty()){
            throw new ProductNotFoundException(productId);
        }

        return optionalProduct.get();
    }

    public MobileProductsResponse getMobileProducts(){
        return productRepository.readMobileProducts();
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