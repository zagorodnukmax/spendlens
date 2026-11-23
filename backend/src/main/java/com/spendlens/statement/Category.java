package com.spendlens.statement;

/**
 * Fixed set of spending categories. A closed list keeps the LLM output easy to validate
 * (it is used as an enum in the structured-output schema) and makes statistics comparable.
 */
public enum Category {
    GROCERIES("Groceries", "#16a34a"),
    RESTAURANTS("Cafe & Restaurants", "#ea580c"),
    TRANSPORT("Transport", "#2563eb"),
    SUBSCRIPTIONS("Subscriptions", "#9333ea"),
    SHOPPING("Shopping", "#db2777"),
    HEALTH("Health", "#dc2626"),
    UTILITIES("Utilities & Telecom", "#0891b2"),
    ENTERTAINMENT("Entertainment", "#ca8a04"),
    TRAVEL("Travel", "#0d9488"),
    TRANSFERS("Transfers", "#64748b"),
    CASH("Cash", "#78716c"),
    INCOME("Income", "#059669"),
    OTHER("Other", "#94a3b8");

    private final String label;
    private final String color;

    Category(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public String label() { return label; }
    public String color() { return color; }
}
