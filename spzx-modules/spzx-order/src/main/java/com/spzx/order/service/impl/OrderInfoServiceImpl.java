package com.spzx.order.service.impl;

import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spzx.cart.api.RemoteCartService;
import com.spzx.cart.api.domain.CartInfo;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.context.SecurityContextHolder;
import com.spzx.common.core.domain.R;
import com.spzx.common.rabbit.service.RabbitService;
import com.spzx.order.api.domain.OrderInfo;
import com.spzx.order.api.domain.OrderItem;
import com.spzx.order.domain.OrderLog;
import com.spzx.order.domain.vo.OrderForm;
import com.spzx.order.domain.vo.TradeVo;
import com.spzx.order.mapper.OrderInfoMapper;
import com.spzx.order.mapper.OrderItemMapper;
import com.spzx.order.mapper.OrderLogMapper;
import com.spzx.order.service.IOrderInfoService;
import com.spzx.order.service.IOrderItemService;
import com.spzx.product.api.RemoteProductService;
import com.spzx.product.api.domain.ProductSku;
import com.spzx.user.api.RemoteUserAddressService;
import com.spzx.user.domain.UserAddress;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements IOrderInfoService {
    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private OrderLogMapper orderLogMapper;

    @Autowired
    private RemoteCartService remoteCartService;

    @Autowired
    private IOrderItemService orderItemService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RemoteProductService remoteProductService;

    @Autowired
    private RemoteUserAddressService remoteUserAddressService;

    @Autowired
    RabbitService rabbitService; //来自于公共模块：spzx-common-rabbit

    /**
     * 查询订单列表
     *
     * @param orderInfo 订单
     * @return 订单
     */
    @Override
    public List<OrderInfo> selectOrderInfoList(OrderInfo orderInfo) {
        return orderInfoMapper.selectOrderInfoList(orderInfo);
    }

    /**
     * 查询订单
     *
     * @param id 订单主键
     * @return 订单
     */
    @Override
    public OrderInfo selectOrderInfoById(Long id) {
        OrderInfo orderInfo = orderInfoMapper.selectById(id);
        List<OrderItem> orderItemList = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, id));
        orderInfo.setOrderItemList(orderItemList);
        return orderInfo;
    }

    @Override
    public TradeVo trade() {
        TradeVo tradeVo = new TradeVo();
        R<List<CartInfo>> checkedCartInfo = remoteCartService.getCheckedCartInfo(SecurityConstants.INNER);
        if (R.FAIL == checkedCartInfo.getCode()) {
            throw new RuntimeException("远程调用失败"+checkedCartInfo.getMsg());
        }
        List<CartInfo> cartInfoList = checkedCartInfo.getData();

        List<OrderItem> orderItemList = new ArrayList<>();

        if (!CollectionUtils.isEmpty(cartInfoList)) {
            orderItemList = cartInfoList.stream().map(cartInfo -> {
                OrderItem orderItem = new OrderItem();
                BeanUtils.copyProperties(cartInfo, orderItem);
                return orderItem;
            }).collect(Collectors.toList());
        }
        BigDecimal totalPrice = BigDecimal.ZERO;

        for (CartInfo cartInfo : cartInfoList) {
            BigDecimal price = cartInfo.getSkuPrice();
            Integer num = cartInfo.getSkuNum();
            BigDecimal totalPriceSku = price.multiply(BigDecimal.valueOf(num));
            totalPrice = totalPrice.add(totalPriceSku);

        }
        tradeVo.setOrderItemList(orderItemList);
        tradeVo.setTotalAmount(totalPrice);
        String tradeNo = UUID.randomUUID().toString().replaceAll("-", "");
        redisTemplate.opsForValue().set("user:tradeNo"+tradeNo, tradeNo,5, TimeUnit.MINUTES);
        tradeVo.setTradeNo(tradeNo);
        return tradeVo;
    }

    @Override
    public TradeVo buy(Long skuId) {
        TradeVo tradeVo = new TradeVo();
        R<ProductSku> productSkuR = remoteProductService.getProductSku(skuId, SecurityConstants.INNER);
        if (R.FAIL == productSkuR.getCode()) {
            throw new RuntimeException("远程调用失败"+productSkuR.getMsg());
        }
        ProductSku productSku = productSkuR.getData();
        OrderItem orderItem = new OrderItem();
        BeanUtils.copyProperties(productSku, orderItem);
        orderItem.setId(null);
        orderItem.setSkuPrice(productSku.getSalePrice());
        orderItem.setSkuNum(1);
        List<OrderItem> orderItemList = Arrays.asList(orderItem);
        tradeVo.setOrderItemList(orderItemList);
        tradeVo.setTotalAmount(orderItem.getSkuPrice());
        String tradeNo = UUID.randomUUID().toString().replaceAll("-", "");
        redisTemplate.opsForValue().set("user:tradeNo"+tradeNo, tradeNo,5, TimeUnit.MINUTES);
        tradeVo.setTradeNo(tradeNo);
        return tradeVo;
    }

    @Override
    public Long submitOrder(OrderForm orderForm) {
        String tradeNo = orderForm.getTradeNo();
        String userTradeNo = (String) redisTemplate.opsForValue().get("user:tradeNo" + tradeNo);
        if (!tradeNo.equals(userTradeNo)) {
            throw new RuntimeException("订单过期");
        }
        redisTemplate.delete("user:tradeNo" + tradeNo);

        Long userAddressId = orderForm.getUserAddressId();
        String remark = orderForm.getRemark();
        BigDecimal feightFee = orderForm.getFeightFee();
        Long userId = SecurityContextHolder.getUserId();
        String userName = SecurityContextHolder.getUserName();
        String orderNo = UUID.randomUUID().toString().replaceAll("-", "").toString();


        //插入订单
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setUserId(userId);
        orderInfo.setFeightFee(feightFee);
        orderInfo.setRemark(remark);
        orderInfo.setOrderStatus(0);
        orderInfo.setCreateBy(userName);
        orderInfo.setOrderNo(orderNo);

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> orderItemList = orderForm.getOrderItemList();
        for (OrderItem orderItem : orderItemList) {
            BigDecimal skuPrice = orderItem.getSkuPrice();
            Integer skuNum = orderItem.getSkuNum();
            BigDecimal skuPriceSku = skuPrice.multiply(BigDecimal.valueOf(skuNum));
            totalAmount = totalAmount.add(skuPriceSku);
        }
        orderInfo.setTotalAmount(totalAmount);
        orderInfo.setOriginalTotalAmount(totalAmount);
        R<UserAddress> userAddress = remoteUserAddressService.getUserAddress(userAddressId, SecurityConstants.INNER);
        if (R.FAIL == userAddress.getCode()) {
            throw new RuntimeException("远程调用用户地址失败"+userAddress.getMsg());
        }
        UserAddress userAddress1 = userAddress.getData();
        orderInfo.setReceiverAddress(userAddress1.getAddress());
        orderInfo.setReceiverName(userAddress1.getName());
        orderInfo.setReceiverPhone(userAddress1.getPhone());
        orderInfo.setReceiverTagName(userAddress1.getTagName());
        orderInfo.setReceiverProvince(userAddress1.getProvinceCode());
        orderInfo.setReceiverDistrict(userAddress1.getDistrictCode());
        orderInfo.setNickName(userName);
        //插入
        baseMapper.insert(orderInfo);

        Long orderId = orderInfo.getId();
        orderItemList.forEach(orderItem -> {
            orderItem.setOrderId(orderId);
        });

        orderItemService.saveBatch(orderItemList);

        //日志
        OrderLog orderLog = new OrderLog();
        orderLog.setOrderId(orderId);
        orderLog.setRemark(remark);
        orderLog.setCreateBy(userName);
        orderLog.setOperateUser("用户");
        orderLog.setNote("提交订单");
        orderLog.setProcessStatus(0);

        orderLogMapper.insert(orderLog);


        return orderId;
    }
}