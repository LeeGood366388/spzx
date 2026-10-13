package com.spzx.cart.controller;

import com.spzx.cart.api.domain.CartInfo;
import com.spzx.cart.service.ICartService;
import com.spzx.common.core.domain.R;
import com.spzx.common.core.web.controller.BaseController;
import com.spzx.common.core.web.domain.AjaxResult;
import com.spzx.common.security.annotation.InnerAuth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "购物车接口")
@RestController
@RequestMapping
public class CartController extends BaseController {

    @Autowired
    private ICartService cartService;

    @Operation(summary = "添加商品到购物车")
    @GetMapping("/addToCart/{skuId}/{num}")
    public AjaxResult addTOCart(@PathVariable("skuId") Long skuId, @PathVariable("num") Integer num) {
        cartService.addToCart(skuId, num);
        return AjaxResult.success();
    }

    @Operation(summary = "展示购物车")
    @GetMapping("/cartList")
    public AjaxResult getCart() {
        List<CartInfo> cartInfoList = cartService.cartList();
        return success(cartInfoList);
    }

    @Operation(summary = "更新购物车选中状态")
    @GetMapping("/checkCart/{skuId}/{Status}")
    public AjaxResult checkCart(@PathVariable("skuId") Long skuId, @PathVariable("Status") Integer status) {
        cartService.checkCart(skuId, status);
        return AjaxResult.success();
    }

    @Operation(summary = "更新购物车全选中状态")
    @GetMapping("/allCheckCart/{Status}")
    public AjaxResult checkCart(@PathVariable("Status") Integer status) {
        cartService.allCheckCart(status);
        return AjaxResult.success();
    }

    @Operation(summary = "删除购物车的商品")
    @DeleteMapping("/deleteCart/{skuId}")
    public AjaxResult deleteCart(@PathVariable("skuId") Long skuId) {
        cartService.deleteCart(skuId);
        return AjaxResult.success();
    }

    @Operation(summary = "清空购物车的商品")
    @GetMapping("/clearCart")
    public AjaxResult clearCart() {
        cartService.clearCart();
        return AjaxResult.success();
    }

    @InnerAuth
    @Operation(summary = "内部远程调用的购物车方法")
    @GetMapping("/getCheckedCartInfo")
    public R<List<CartInfo>> getCheckedCartInfo() {
        List<CartInfo> cartInfoList = cartService.getCheckedCartInfo();
        return R.ok(cartInfoList);
    }

    @Operation(summary = "远程调用清空购物车的方法")
    @InnerAuth
    @GetMapping("/clearCheckedCartInfo")
    public R<Void> clearCheckedCartInfo() {
        cartService.clearCheckedCartInfo();
        return R.ok();
    }
}