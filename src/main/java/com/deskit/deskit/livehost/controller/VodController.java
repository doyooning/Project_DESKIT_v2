package com.deskit.deskit.livehost.controller;

import com.deskit.deskit.livehost.service.VodService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vods")
@RequiredArgsConstructor
public class VodController {

    private final VodService vodService;

    @GetMapping("/{vodId}/stream")
    public ResponseEntity<InputStreamResource> streamVod(
            @PathVariable Long vodId,
            @RequestHeader(value = "Range", required = false) String rangeHeader
    ) {
        return vodService.streamVod(vodId, rangeHeader);
    }
}
