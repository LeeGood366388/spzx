package com.spzx.cart.service.impl;

import com.spzx.cart.api.domain.CartInfo;
import com.spzx.cart.service.ICartService;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.context.SecurityContextHolder;
import com.spzx.common.core.domain.R;
import com.spzx.common.core.exception.ServiceException;
import com.spzx.product.api.RemoteProductService;
import com.spzx.product.api.domain.ProductSku;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CartServiceImpl implements ICartService {

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    RemoteProductService remoteProductService;

    public String getCartKey(Long userId){
        return "user:cart:" + userId;
    }

    @Override
    public void addToCart(Long skuId, Integer num) {
        Long userId = SecurityContextHolder.getUserId();
        String cartKey = getCartKey(skuId);
        CartInfo cartInfo = (CartInfo) redisTemplate.opsForHash().get(cartKey, userId.toString());
        if (cartInfo != null) {
            cartInfo.setSkuNum(Math.min((cartInfo.getSkuNum() + num),99));
        }else {
            cartInfo = new CartInfo();
            cartInfo.setSkuNum(1);
            cartInfo.setCreateTime(new Date());
            cartInfo.setCreateBy(SecurityContextHolder.getUserName());
            cartInfo.setSkuId(skuId);
            R<ProductSku> productSku = remoteProductService.getProductSku(skuId, SecurityConstants.INNER);
            if (R.FAIL == productSku.getCode()) {
                throw new ServiceException(productSku.getMsg());
            }
            ProductSku productSku1 = productSku.getData();
            cartInfo.setSkuName(productSku1.getSkuName());
            cartInfo.setSkuPrice(productSku1.getSalePrice());
            cartInfo.setThumbImg(productSku1.getThumbImg());
            cartInfo.setCartPrice(productSku1.getSalePrice());

        }
        redisTemplate.opsForHash().put(cartKey, userId.toString(), cartInfo);
    }

    @Override
    public List<CartInfo> cartList() {
        Long userId = SecurityContextHolder.getUserId();
        String cartKey = getCartKey(userId);
        List<CartInfo> list = redisTemplate.opsForHash().values(cartKey);
        list.forEach(cartInfo -> {
            R<ProductSku> productSkuR = remoteProductService.getProductSku(cartInfo.getSkuId(), SecurityConstants.INNER);
            if (R.FAIL == productSkuR.getCode()) {
                throw new ServiceException(productSkuR.getMsg());
            }
            ProductSku productSku = productSkuR.getData();
            BigDecimal  salePrice = productSku.getSalePrice();
            cartInfo.setSkuName(String.valueOf(salePrice));
            redisTemplate.opsForHash().put(cartKey, userId.toString(), cartInfo);
        });

        return list;
    }

    @Override
    public void checkCart(Long skuId, Integer status) {
        Long userId = SecurityContextHolder.getUserId();
        String cartKey = getCartKey(skuId);
        CartInfo cartInfo = (CartInfo) redisTemplate.opsForHash().get(cartKey, userId.toString());
        cartInfo.setIsChecked(status);
        redisTemplate.opsForHash().put(cartKey, userId.toString(), cartInfo);

    }

    @Override
    public void allCheckCart(Integer status) {
        Long userId = SecurityContextHolder.getUserId();
        String cartKey = getCartKey(userId);
        List<CartInfo> list = redisTemplate.opsForHash().values(cartKey);
        list.forEach(cartInfo -> {
            cartInfo.setIsChecked(status);
            redisTemplate.opsForHash().put(cartKey, userId.toString(), cartInfo);
        });
    }

    @Override
    public void deleteCart(Long skuId) {
        Long userId = SecurityContextHolder.getUserId();
        String cartKey = getCartKey(skuId);
        redisTemplate.opsForHash().delete(cartKey, userId.toString());
    }

    @Override
    public void clearCart() {
        Long userId = SecurityContextHolder.getUserId();
        String cartKey = getCartKey(userId);
        redisTemplate.delete(cartKey);
    }

    @Override
    public List<CartInfo> getCheckedCartInfo() {
        Long userId = SecurityContextHolder.getUserId();
        String cartKey = getCartKey(userId);
        List<CartInfo> list = redisTemplate.opsForHash().values(cartKey);
        List<CartInfo> collect = list.stream().filter(cartInfo -> cartInfo.getIsChecked() == 1).collect(Collectors.toList());
        return collect;
    }

    @Override
    public void clearCheckedCartInfo() {
        Long userId = SecurityContextHolder.getUserId();
        String cartKey = getCartKey(userId);
        List<CartInfo> checkedCartInfo = getCheckedCartInfo();
        checkedCartInfo.forEach(cartInfo -> {
            redisTemplate.opsForHash().delete(cartKey,cartInfo.getSkuId().toString());
        });
    }
}