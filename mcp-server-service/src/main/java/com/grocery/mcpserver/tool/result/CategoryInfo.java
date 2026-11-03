package com.grocery.mcpserver.tool.result;

/**
 * A category of the shop (listCategories). The name can be given to searchProducts as "category".
 */
public record CategoryInfo(String name, String description) {
}
