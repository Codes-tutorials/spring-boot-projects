package org.codeart.cqrs.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Commands for the write side (CQRS Command).
 * Commands are imperative: "Create Product", "Update Price", etc.
 */
public sealed interface ProductCommand {

    /**
     * Command: Create a new product.
     */
    record CreateProduct(
            @NotBlank String name,
            String description,
            @Positive double price,
            @PositiveOrZero int quantity,
            String category) implements ProductCommand {
    }

    /**
     * Command: Update product price.
     */
    record UpdatePrice(
            @NotBlank String productId,
            @Positive double newPrice) implements ProductCommand {
    }

    /**
     * Command: Update product quantity.
     */
    record UpdateQuantity(
            @NotBlank String productId,
            int quantityChange, // positive = add, negative = remove
            String reason) implements ProductCommand {
    }

    /**
     * Command: Delete a product.
     */
    record DeleteProduct(
            @NotBlank String productId) implements ProductCommand {
    }
}
