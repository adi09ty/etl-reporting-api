package com.adityakumar.etlreporting.etl;

import com.adityakumar.etlreporting.dto.Dtos.EtlRunResponse;
import com.adityakumar.etlreporting.dto.Dtos.PageResponse;
import com.adityakumar.etlreporting.dto.Dtos.RejectedRowResponse;
import com.adityakumar.etlreporting.exception.RunNotFoundException;
import com.adityakumar.etlreporting.repository.EtlRunRepository;
import com.adityakumar.etlreporting.repository.RejectedRowRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the ETL: run history and the rows that were rejected. */
@Service
@Transactional(readOnly = true)
public class EtlQueryService {

    private final EtlRunRepository runs;
    private final RejectedRowRepository rejectedRows;

    public EtlQueryService(EtlRunRepository runs, RejectedRowRepository rejectedRows) {
        this.runs = runs;
        this.rejectedRows = rejectedRows;
    }

    public PageResponse<EtlRunResponse> listRuns(Pageable pageable) {
        return PageResponse.from(runs.findAll(pageable).map(EtlRunResponse::from));
    }

    public EtlRunResponse getRun(Long id) {
        return EtlRunResponse.from(runs.findById(id).orElseThrow(() -> new RunNotFoundException(id)));
    }

    public PageResponse<RejectedRowResponse> rejectedRows(Long runId, Pageable pageable) {
        if (!runs.existsById(runId)) {
            throw new RunNotFoundException(runId);
        }
        return PageResponse.from(rejectedRows.findByRunIdOrderByIdAsc(runId, pageable)
                .map(RejectedRowResponse::from));
    }
}
