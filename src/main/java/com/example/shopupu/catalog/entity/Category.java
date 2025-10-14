package com.example.shopupu.catalog.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity                     // говорим JPA: это сущность (будет храниться в БД)
@Table(name = "categories") // явно указываем имя таблицы
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                // PK, соответствует bigserial

    @Column(nullable = false, length = 255)
    private String name;            // "Электроника"

    @Column(nullable = false, unique = true, length = 255)
    private String slug;            // "electronics" — для URL и идемпотентных запросов

    @Column(columnDefinition = "text")
    private String description;     // длинный текст

    // Самоссылка — родительская категория (для дерева)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")    // колонка в таблице categories
    private Category parent;

    // Обратная сторона: список "детей"
    @OneToMany(mappedBy = "parent")
    private List<Category> children = new ArrayList<>();

    // Геттеры/сеттеры/конструкторы — Lombok можно, но распишем явно для учебы

    public Category() {}

    public Category(String name, String slug, String description, Category parent) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.parent = parent;
    }

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Category getParent() { return parent; }
    public void setParent(Category parent) { this.parent = parent; }

    public List<Category> getChildren() { return children; }
    public void setChildren(List<Category> children) { this.children = children; }
}