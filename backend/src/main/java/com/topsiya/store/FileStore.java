package com.topsiya.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 결과를 파일(JSON)로 저장해 두는 아주 작은 저장소.
 * 서버를 껐다 켜도 남아 있어서, 같은 경기는 Riot API·OpenAI 를 다시 부르지 않는다.
 *
 * 저장 위치: {dataDir}/{category}/{key}.json
 * 저장에 실패해도 서비스는 계속 동작한다 (저장은 "있으면 좋은 것").
 */
@Component
public class FileStore {

    private static final Logger log = LoggerFactory.getLogger(FileStore.class);
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9._-]{1,120}");

    private final Path root;
    private final ObjectMapper objectMapper;

    @Autowired
    public FileStore(AppProperties properties, ObjectMapper objectMapper) {
        this(Path.of(properties.dataDirOrDefault()), objectMapper);
        log.info("저장 폴더: {}", root.toAbsolutePath());
    }

    public FileStore(Path root, ObjectMapper objectMapper) {
        this.root = root;
        this.objectMapper = objectMapper;
    }

    public <T> Optional<T> read(String category, String key, Class<T> type) {
        return read(category, key, objectMapper.constructType(type));
    }

    public <T> Optional<T> read(String category, String key, TypeReference<T> type) {
        return read(category, key, objectMapper.constructType(type));
    }

    private <T> Optional<T> read(String category, String key, JavaType type) {
        Path file = jsonPath(category, key);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(objectMapper.readValue(file.toFile(), type));
        } catch (IOException e) {
            log.warn("저장된 파일을 읽지 못해서 무시해요: {}", file, e);
            return Optional.empty();
        }
    }

    public void write(String category, String key, Object value) {
        try {
            writeAtomically(jsonPath(category, key), objectMapper.writeValueAsBytes(value));
        } catch (IOException e) {
            log.warn("파일 저장 실패 ({}/{}): {}", category, key, e.getMessage());
        }
    }

    /** 이 분류에 저장된 키 목록 (해시로 바뀐 키는 원래 이름을 알 수 없다) */
    public java.util.List<String> keys(String category) {
        Path dir = dir(category);
        if (!Files.isDirectory(dir)) {
            return java.util.List.of();
        }
        try (var files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> name.substring(0, name.length() - ".json".length()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return java.util.List.of();
        }
    }

    public void delete(String category, String key) {
        try {
            Files.deleteIfExists(jsonPath(category, key));
        } catch (IOException e) {
            log.warn("파일 삭제 실패 ({}/{}): {}", category, key, e.getMessage());
        }
    }

    public Optional<byte[]> readBytes(String category, String fileName) {
        Path file = dir(category).resolve(fileName(fileName));
        try {
            return Files.exists(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public void writeBytes(String category, String fileName, byte[] bytes) {
        try {
            writeAtomically(dir(category).resolve(fileName(fileName)), bytes);
        } catch (IOException e) {
            log.warn("파일 저장 실패 ({}/{}): {}", category, fileName, e.getMessage());
        }
    }

    /** 임시 파일에 다 쓴 뒤 이름을 바꾼다. 쓰다가 서버가 꺼져도 반쯤 쓴 파일이 남지 않는다. */
    private void writeAtomically(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".tmp-", ".part");
        Files.write(temp, bytes);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path jsonPath(String category, String key) {
        return dir(category).resolve(fileName(key) + ".json");
    }

    private Path dir(String category) {
        if (!SAFE_KEY.matcher(category).matches()) {
            throw new IllegalArgumentException("잘못된 저장 분류: " + category);
        }
        return root.resolve(category);
    }

    /** 경기 ID 처럼 안전한 키는 그대로, 한글·공백·기호가 있으면 해시로 바꿔 파일 이름으로 쓴다 */
    static String fileName(String key) {
        if (SAFE_KEY.matcher(key).matches() && !key.startsWith(".")) {
            return key;
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return "h-" + HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
