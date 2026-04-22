package com.fsss.dto;

import com.fsss.domain.FileMetadata;
import com.fsss.domain.ScanFinding;
import com.fsss.domain.ScanVerdict;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UploadResponseMapper {
    @Mapping(target = "scanId", source = "scanId")
    @Mapping(target = "verdict", source = "verdict")
    @Mapping(target = "sha256", source = "metadata.sha256")
    @Mapping(target = "sizeBytes", source = "metadata.sizeBytes")
    @Mapping(target = "detectedMime", source = "detectedMime")
    @Mapping(target = "findings", source = "findings")
    UploadResponse toResponse(String scanId,
                              ScanVerdict verdict,
                              FileMetadata metadata,
                              String detectedMime,
                              List<ScanFinding> findings);
}
