package com.larrydevincarter.optionscanner.controllers;

import com.larrydevincarter.optionscanner.models.dtos.UpdateStatusDto;
import com.larrydevincarter.optionscanner.services.UpdateStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UpdateStatusController {

    private final UpdateStatusService updateStatusService;

    @GetMapping("/update-status")
    public UpdateStatusDto getUpdateStatus() {
        return new UpdateStatusDto(updateStatusService.isUpdating());
    }
}