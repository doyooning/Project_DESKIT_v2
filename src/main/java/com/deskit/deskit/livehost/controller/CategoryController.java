package com.deskit.deskit.livehost.controller;

import com.deskit.deskit.livehost.common.exception.ApiResult;
import com.deskit.deskit.livehost.dto.response.CategoryResponse;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final DSLContext dsl;

    @GetMapping
    public ResponseEntity<ApiResult<List<CategoryResponse>>> listCategories() {
        var tagCategoryTable = table(name("tag_category")).as("tc");
        var idField = field(name("tc", "tag_category_id"), Long.class);
        var nameField = field(name("tc", "tag_category_name"), String.class);

        List<CategoryResponse> categories = dsl.select(idField, nameField)
                .from(tagCategoryTable)
                .orderBy(idField.asc())
                .fetch(record -> new CategoryResponse(
                        record.get(idField),
                        record.get(nameField)
                ));

        return ResponseEntity.ok(ApiResult.success(categories));
    }
}
