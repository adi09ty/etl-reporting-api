package com.adityakumar.etlreporting.web;

import com.adityakumar.etlreporting.dto.Dtos.CategoryReport;
import com.adityakumar.etlreporting.dto.Dtos.DailySalesReport;
import com.adityakumar.etlreporting.report.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    /** Dates look like 2026-03-01. Without parameters: the last 30 days up to today (UTC). */
    @GetMapping("/daily-sales")
    public DailySalesReport dailySales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.dailySales(from, to);
    }

    @GetMapping("/by-category")
    public CategoryReport byCategory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.byCategory(from, to);
    }
}
