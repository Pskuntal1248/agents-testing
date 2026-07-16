package com.srelab.inventory.controller;

import com.srelab.inventory.model.Order;
import com.srelab.inventory.model.OrderItem;
import com.srelab.inventory.repository.OrderItemRepository;
import com.srelab.inventory.repository.OrderRepository;
import com.srelab.inventory.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    @Value("${srelab.fault.disable-n1-fix:false}")
    private boolean disableN1Fix;

    public OrderController(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productRepository = productRepository;
    }

    @GetMapping
    public List<Order> getAll() {
        return orderRepository.findAll();
    }

    @GetMapping("/with-items")
    public List<Order> getAllWithItems() {
        if (disableN1Fix) {
            return orderRepository.findAll();
        }
        return orderRepository.findAllWithItems();
    }

    @GetMapping("/{id}")
    public Order getById(@PathVariable Long id) {
        return orderRepository.findById(id).orElseThrow();
    }

    @PostMapping
    public Order create(@RequestBody Order order) {
        order.setCreatedAt(LocalDateTime.now());
        if (order.getStatus() == null) {
            order.setStatus("PENDING");
        }
        return orderRepository.save(order);
    }

    @PostMapping("/{orderId}/items")
    public Order addItem(@PathVariable Long orderId, @RequestBody OrderItem item) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        var product = productRepository.findById(item.getProduct().getId()).orElseThrow();

        item.setOrder(order);
        item.setProduct(product);
        if (item.getUnitPrice() == null) {
            item.setUnitPrice(product.getPrice());
        }
        orderItemRepository.save(item);
        order.getItems().add(item);
        return order;
    }

    @PutMapping("/{id}")
    public Order update(@PathVariable Long id, @RequestBody Order order) {
        order.setId(id);
        return orderRepository.save(order);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        orderRepository.deleteById(id);
    }
}
