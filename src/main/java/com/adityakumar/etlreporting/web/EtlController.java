package com.adityakumar.etlreporting.web;

import com.adityakumar.etlreporting.domain.TriggerType;
import com.adityakumar.etlreporting.dto.Dtos.EtlRunResponse;
import com.adityakumar.etlreporting.dto.Dtos.PageResponse;
import com.adityakumar.etlreporting.dto.Dtos.RejectedRowResponse;
import com.adityakumar.etlreporting.etl.EtlQueryService;
import com.adityakumar.etlreporting.etl.OrdersImportJob;
import com.adityakumar.etlreporting.rates.RatesRefreshJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/etl")
public class EtlController {

    private final OrdersImportJob ordersImport;
    private final RatesRefreshJob ratesRefresh;
    private final EtlQueryService queries;

    public EtlController(OrdersImportJob ordersImport, RatesRefreshJob ratesRefresh, EtlQueryService queries) {
        this.ordersImport = ordersImport;
        this.ratesRefresh = ratesRefresh;
        this.queries = queries;
    }

    /** Runs the import now and returns how it went. A FAILED run is still a 200 with status FAILED in the body. */
    @PostMapping("/jobs/orders-import/run")
    public EtlRunResponse runOrdersImport() {
        return EtlRunResponse.from(ordersImport.run(TriggerType.MANUAL));
    }

    @PostMapping("/jobs/rates-refresh/run")
    public EtlRunResponse runRatesRefresh() {
        return EtlRunResponse.from(ratesRefresh.run(TriggerType.MANUAL));
    }

    /** Newest runs first. */
    @GetMapping("/runs")
    public PageResponse<EtlRunResponse> runs(
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return queries.listRuns(pageable);
    }

    @GetMapping("/runs/{id}")
    public EtlRunResponse run(@PathVariable Long id) {
        return queries.getRun(id);
    }

    @GetMapping("/runs/{id}/rejected-rows")
    public PageResponse<RejectedRowResponse> rejectedRows(@PathVariable Long id,
                                                          @PageableDefault(size = 50) Pageable pageable) {
        return queries.rejectedRows(id, pageable);
    }
}
