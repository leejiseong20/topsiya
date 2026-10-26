package com.topsiya.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStoreTest {

    @TempDir
    Path dir;

    record Sample(String name, int value) {
    }

    @Test
    void 저장한_값을_다시_읽는다() {
        FileStore store = new FileStore(dir, new ObjectMapper());
        store.write("test", "KR_123", new Sample("darius", 3));

        assertThat(store.read("test", "KR_123", Sample.class)).contains(new Sample("darius", 3));
        assertThat(dir.resolve("test/KR_123.json")).exists();
    }

    @Test
    void 제네릭_목록도_읽는다() {
        FileStore store = new FileStore(dir, new ObjectMapper());
        store.write("ids", "p", List.of("KR_1", "KR_2"));

        assertThat(store.read("ids", "p", new TypeReference<List<String>>() {})).contains(List.of("KR_1", "KR_2"));
    }

    @Test
    void 없는_키는_빈값() {
        FileStore store = new FileStore(dir, new ObjectMapper());

        assertThat(store.read("test", "none", Sample.class)).isEmpty();
    }

    @Test
    void 깨진_파일은_무시한다() throws Exception {
        FileStore store = new FileStore(dir, new ObjectMapper());
        Files.createDirectories(dir.resolve("test"));
        Files.writeString(dir.resolve("test/bad.json"), "{not json");

        assertThat(store.read("test", "bad", Sample.class)).isEmpty();
    }

    @Test
    void 한글이나_기호가_있는_키는_해시_파일이름으로_바꾼다() {
        assertThat(FileStore.fileName("KR_7012345678")).isEqualTo("KR_7012345678");
        assertThat(FileStore.fileName("hide on bush#kr1")).startsWith("h-").hasSize(34);
        assertThat(FileStore.fileName("../../etc/passwd")).startsWith("h-");
        assertThat(FileStore.fileName("탑시야#KR1")).isNotEqualTo(FileStore.fileName("탑시야#KR2"));
    }

    @Test
    void 분류_이름에_경로_기호는_쓸_수_없다() {
        FileStore store = new FileStore(dir, new ObjectMapper());

        assertThatThrownBy(() -> store.write("../x", "k", "v")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 바이트_파일도_저장한다() {
        FileStore store = new FileStore(dir, new ObjectMapper());
        store.writeBytes("img", "15.20.1-Darius.png", new byte[]{1, 2, 3});

        assertThat(store.readBytes("img", "15.20.1-Darius.png")).hasValueSatisfying(b -> assertThat(b).containsExactly(1, 2, 3));
    }
}
