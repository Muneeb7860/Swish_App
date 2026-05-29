package ch.swissqcommerce.backend.domain.transaction.port.out;

public interface SystemConfigPort {
    String getSystemConfig(String key, String defaultValue);
}
