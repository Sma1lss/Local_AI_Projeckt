package com.fsss.config;

import com.fsss.security.ApiKeySecret;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Validated
@ConfigurationProperties(prefix = "fsss")
public final class FsssProperties {
    private static final List<String> DEFAULT_SIGNATURE_PATTERNS = List.of(
            "MZ",
            "<script",
            "powershell",
            "cmd.exe",
            "vbaProject.bin"
    );

    @Min(1)
    private final long maxFileSizeBytes;

    @Min(1024)
    private final int maxHeaderBytes;

    private final boolean earlyAbortOnMalware;

    @NotNull
    private final SpoolProperties spool;

    @NotNull
    private final SecurityProperties security;

    @NotNull
    private final DownstreamProperties downstream;

    @NotNull
    private final ClamAvProperties clamav;

    @NotNull
    private final ScanProperties scan;

    @NotNull
    private final SandboxProperties sandbox;

    @NotNull
    private final LoggingProperties logging;

    public FsssProperties(
            @DefaultValue("524288000") long maxFileSizeBytes,
            @DefaultValue("65536") int maxHeaderBytes,
            @DefaultValue("true") boolean earlyAbortOnMalware,
            SpoolProperties spool,
            SecurityProperties security,
            DownstreamProperties downstream,
            ClamAvProperties clamav,
            ScanProperties scan,
            SandboxProperties sandbox,
            LoggingProperties logging
    ) {
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxHeaderBytes = maxHeaderBytes;
        this.earlyAbortOnMalware = earlyAbortOnMalware;
        this.spool = spool == null ? new SpoolProperties(null, 64 * 1024, 10L * 1024 * 1024, "/fsss-spool") : spool;
        this.security = security == null ? new SecurityProperties("change-me", 50, null) : security;
        this.downstream = downstream == null ? new DownstreamProperties("http://127.0.0.1:8090/files", Duration.ofSeconds(10)) : downstream;
        this.clamav = clamav == null ? new ClamAvProperties(true, "clamav", 3310, Duration.ofSeconds(10)) : clamav;
        this.scan = scan == null ? new ScanProperties(null, null, null, null, null) : scan;
        this.sandbox = sandbox == null ? new SandboxProperties(null, "http://scanner-sandbox:8081/internal/scan") : sandbox;
        this.logging = logging == null ? new LoggingProperties("") : logging;
    }

    public static FsssProperties defaults() {
        return new FsssProperties(
                500L * 1024 * 1024,
                64 * 1024,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public FsssProperties withClamAv(ClamAvProperties updatedClamav) {
        return new FsssProperties(
                maxFileSizeBytes,
                maxHeaderBytes,
                earlyAbortOnMalware,
                spool,
                security,
                downstream,
                updatedClamav,
                scan,
                sandbox,
                logging
        );
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public int getMaxHeaderBytes() {
        return maxHeaderBytes;
    }

    public boolean isEarlyAbortOnMalware() {
        return earlyAbortOnMalware;
    }

    public SpoolProperties getSpool() {
        return spool;
    }

    public SecurityProperties getSecurity() {
        return security;
    }

    public DownstreamProperties getDownstream() {
        return downstream;
    }

    public ClamAvProperties getClamav() {
        return clamav;
    }

    public ScanProperties getScan() {
        return scan;
    }

    public SandboxProperties getSandbox() {
        return sandbox;
    }

    public LoggingProperties getLogging() {
        return logging;
    }

    public static final class SpoolProperties {
        public enum Mode { MEMORY, TMPFS, HYBRID }

        @NotNull
        private final Mode mode;

        @Min(1024)
        private final int bufferSizeBytes;

        @Min(1024)
        private final long memoryThresholdBytes;

        @NotBlank
        private final String tempDir;

        public SpoolProperties(
                @DefaultValue("HYBRID") Mode mode,
                @DefaultValue("65536") int bufferSizeBytes,
                @DefaultValue("10485760") long memoryThresholdBytes,
                @DefaultValue("/fsss-spool") String tempDir
        ) {
            this.mode = mode == null ? Mode.HYBRID : mode;
            this.bufferSizeBytes = bufferSizeBytes;
            this.memoryThresholdBytes = memoryThresholdBytes;
            this.tempDir = normalizeRequiredString(tempDir, "/fsss-spool");
        }

        public Mode getMode() {
            return mode;
        }

        public int getBufferSizeBytes() {
            return bufferSizeBytes;
        }

        public long getMemoryThresholdBytes() {
            return memoryThresholdBytes;
        }

        public String getTempDir() {
            return tempDir;
        }
    }

    public static final class SecurityProperties {
        @NotNull
        private final ApiKeySecret apiKeySecret;

        @Min(1)
        private final int maxInFlight;

        @NotNull
        private final RateLimitProperties rateLimit;

        public SecurityProperties(
                @DefaultValue("change-me") String apiKey,
                @DefaultValue("50") int maxInFlight,
                RateLimitProperties rateLimit
        ) {
            this.apiKeySecret = ApiKeySecret.from(normalizeRequiredString(apiKey, "change-me"));
            this.maxInFlight = maxInFlight;
            this.rateLimit = rateLimit == null ? new RateLimitProperties(100, 50) : rateLimit;
        }

        public ApiKeySecret getApiKeySecret() {
            return apiKeySecret;
        }

        public int getMaxInFlight() {
            return maxInFlight;
        }

        public RateLimitProperties getRateLimit() {
            return rateLimit;
        }
    }

    public static final class RateLimitProperties {
        @Min(1)
        private final long capacity;

        @Min(1)
        private final long refillPerSecond;

        public RateLimitProperties(
                @DefaultValue("100") long capacity,
                @DefaultValue("50") long refillPerSecond
        ) {
            this.capacity = capacity;
            this.refillPerSecond = refillPerSecond;
        }

        public long getCapacity() {
            return capacity;
        }

        public long getRefillPerSecond() {
            return refillPerSecond;
        }
    }

    public static final class DownstreamProperties {
        @NotBlank
        private final String url;

        @NotNull
        private final Duration timeout;

        public DownstreamProperties(
                @DefaultValue("http://127.0.0.1:8090/files") String url,
                @DefaultValue("10s") Duration timeout
        ) {
            this.url = normalizeRequiredString(url, "http://127.0.0.1:8090/files");
            this.timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        }

        public String getUrl() {
            return url;
        }

        public Duration getTimeout() {
            return timeout;
        }
    }

    public static final class ClamAvProperties {
        private final boolean enabled;

        @NotBlank
        private final String host;

        @Min(1)
        @Max(65535)
        private final int port;

        @NotNull
        private final Duration timeout;

        public ClamAvProperties(
                @DefaultValue("true") boolean enabled,
                @DefaultValue("clamav") String host,
                @DefaultValue("3310") int port,
                @DefaultValue("10s") Duration timeout
        ) {
            this.enabled = enabled;
            this.host = normalizeRequiredString(host, "clamav");
            this.port = port;
            this.timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public Duration getTimeout() {
            return timeout;
        }
    }

    public static final class ScanProperties {
        @NotNull
        private final MimeProperties mime;

        @NotNull
        private final SignatureProperties signatures;

        @NotNull
        private final MacroProperties macros;

        @NotNull
        private final YaraProperties yara;

        @NotNull
        private final DynamicProperties dynamic;

        public ScanProperties(
                MimeProperties mime,
                SignatureProperties signatures,
                MacroProperties macros,
                YaraProperties yara,
                DynamicProperties dynamic
        ) {
            this.mime = mime == null ? new MimeProperties(64 * 1024) : mime;
            this.signatures = signatures == null ? new SignatureProperties(true, DEFAULT_SIGNATURE_PATTERNS) : signatures;
            this.macros = macros == null ? new MacroProperties(true, 4 * 1024 * 1024) : macros;
            this.yara = yara == null ? new YaraProperties(false, "/opt/fsss/yara/index.yar") : yara;
            this.dynamic = dynamic == null ? new DynamicProperties(false, "http://scanner-sandbox:8081/internal/dynamic", Duration.ofSeconds(20)) : dynamic;
        }

        public MimeProperties getMime() {
            return mime;
        }

        public SignatureProperties getSignatures() {
            return signatures;
        }

        public MacroProperties getMacros() {
            return macros;
        }

        public YaraProperties getYara() {
            return yara;
        }

        public DynamicProperties getDynamic() {
            return dynamic;
        }
    }

    public static final class MimeProperties {
        @Min(1024)
        private final int maxBytes;

        public MimeProperties(@DefaultValue("65536") int maxBytes) {
            this.maxBytes = maxBytes;
        }

        public int getMaxBytes() {
            return maxBytes;
        }
    }

    public static final class SignatureProperties {
        private final boolean enabled;
        private final List<String> patterns;

        public SignatureProperties(
                @DefaultValue("true") boolean enabled,
                List<String> patterns
        ) {
            this.enabled = enabled;
            this.patterns = List.copyOf(patterns == null || patterns.isEmpty() ? DEFAULT_SIGNATURE_PATTERNS : patterns);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public List<String> getPatterns() {
            return patterns;
        }
    }

    public static final class MacroProperties {
        private final boolean enabled;

        @Min(1024)
        private final int maxScanBytes;

        public MacroProperties(
                @DefaultValue("true") boolean enabled,
                @DefaultValue("4194304") int maxScanBytes
        ) {
            this.enabled = enabled;
            this.maxScanBytes = maxScanBytes;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getMaxScanBytes() {
            return maxScanBytes;
        }
    }

    public static final class YaraProperties {
        private final boolean enabled;
        private final String rulesPath;

        public YaraProperties(
                @DefaultValue("false") boolean enabled,
                @DefaultValue("/opt/fsss/yara/index.yar") String rulesPath
        ) {
            this.enabled = enabled;
            this.rulesPath = normalizeRequiredString(rulesPath, "/opt/fsss/yara/index.yar");
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getRulesPath() {
            return rulesPath;
        }
    }

    public static final class DynamicProperties {
        private final boolean enabled;
        private final String url;
        private final Duration timeout;

        public DynamicProperties(
                @DefaultValue("false") boolean enabled,
                @DefaultValue("http://scanner-sandbox:8081/internal/dynamic") String url,
                @DefaultValue("20s") Duration timeout
        ) {
            this.enabled = enabled;
            this.url = normalizeRequiredString(url, "http://scanner-sandbox:8081/internal/dynamic");
            this.timeout = timeout == null ? Duration.ofSeconds(20) : timeout;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getUrl() {
            return url;
        }

        public Duration getTimeout() {
            return timeout;
        }
    }

    public static final class SandboxProperties {
        public enum Mode { LOCAL, REMOTE }

        @NotNull
        private final Mode mode;
        private final String remoteUrl;

        public SandboxProperties(
                @DefaultValue("LOCAL") Mode mode,
                @DefaultValue("http://scanner-sandbox:8081/internal/scan") String remoteUrl
        ) {
            this.mode = mode == null ? Mode.LOCAL : mode;
            this.remoteUrl = normalizeRequiredString(remoteUrl, "http://scanner-sandbox:8081/internal/scan");
        }

        public Mode getMode() {
            return mode;
        }

        public String getRemoteUrl() {
            return remoteUrl;
        }
    }

    public static final class LoggingProperties {
        private final String securityWebhookUrl;

        public LoggingProperties(@DefaultValue("") String securityWebhookUrl) {
            this.securityWebhookUrl = Objects.requireNonNullElse(securityWebhookUrl, "");
        }

        public String getSecurityWebhookUrl() {
            return securityWebhookUrl;
        }
    }

    private static String normalizeRequiredString(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
