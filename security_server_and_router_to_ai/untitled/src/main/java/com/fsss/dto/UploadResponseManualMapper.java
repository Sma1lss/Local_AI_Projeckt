package com.fsss.dto;

import com.fsss.domain.FileMetadata;
import com.fsss.domain.ScanFinding;
import com.fsss.domain.ScanVerdict;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Primary
public class UploadResponseManualMapper implements UploadResponseMapper {
    @Override
    public UploadResponse toResponse(String scanId,
                                     ScanVerdict verdict,
                                     FileMetadata metadata,
                                     String detectedMime,
                                     List<ScanFinding> findings) {
        return new UploadResponse(
                scanId,
                verdict,
                metadata.sha256(),
                metadata.sizeBytes(),
                detectedMime,
                List.copyOf(findings)
        );
    }
}
