package com.bred.rms.repositories;

import com.bred.rms.models.Attachment;
import com.bred.rms.repositories.supabase.SupabaseAttachmentStore;
import com.bred.rms.support.TestEnv;
import org.junit.jupiter.api.Test;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentRulesTest {
    private static final UUID RID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    private static Path tempFile(String name, byte[] content) throws Exception {
        Path dir = Files.createTempDirectory("rms-att");
        Path f = dir.resolve(name);
        Files.write(f, content);
        return f;
    }

    @Test
    void uploadRejectsExeAndOver10MBBeforeAnyRequest() throws Exception {
        try (TestEnv env = new TestEnv()) {
            var store = new SupabaseAttachmentStore(env.rest);
            Path exe = tempFile("virus.exe", new byte[] {1, 2, 3});
            AttachmentException e1 = assertThrows(AttachmentException.class, () -> store.upload(RID, exe));
            assertTrue(e1.getMessage().contains("png, jpg or pdf"), e1.getMessage());

            Path big = tempFile("big.pdf", new byte[0]);
            try (RandomAccessFile raf = new RandomAccessFile(big.toFile(), "rw")) { raf.setLength(10L * 1024 * 1024 + 1); }
            AttachmentException e2 = assertThrows(AttachmentException.class, () -> store.upload(RID, big));
            assertTrue(e2.getMessage().contains("10 MB"), e2.getMessage());

            AttachmentException e3 = assertThrows(AttachmentException.class, () -> store.upload(RID, Path.of("/no/such/file.pdf")));
            assertTrue(e3.getMessage().contains("read"), e3.getMessage());
            assertEquals(0, env.fake.requests().size());
        }
    }

    @Test
    void uploadSendsFileToStorageAndReturnsAttachment() throws Exception {
        try (TestEnv env = new TestEnv()) {
            env.fake.on("POST", "/storage/v1/object/receipts/", 200, "{\"Key\":\"receipts/x\"}");
            byte[] content = "%PDF-1.4 hello".getBytes(StandardCharsets.UTF_8);
            Attachment a = new SupabaseAttachmentStore(env.rest).upload(RID, tempFile("scan.pdf", content));
            assertEquals("scan.pdf", a.fileName());
            assertTrue(a.storagePath().startsWith(RID + "/"), a.storagePath());
            var req = env.fake.requests("POST", "/storage/v1/object/receipts/").get(0);
            assertEquals("/storage/v1/object/receipts/" + a.storagePath(), req.path());
            assertEquals("application/pdf", req.headers().get("content-type"));
            assertEquals("Bearer tok1", req.headers().get("authorization"));
            assertEquals("%PDF-1.4 hello", req.bodyString());
        }
    }

    @Test
    void uploadSanitizesStoragePathButKeepsDisplayName() throws Exception {
        try (TestEnv env = new TestEnv()) {
            env.fake.on("POST", "/storage/v1/object/receipts/", 200, "{}");
            Attachment a = new SupabaseAttachmentStore(env.rest).upload(RID, tempFile("my receipt (1).JPG", new byte[] {9}));
            assertEquals("my receipt (1).JPG", a.fileName());
            assertTrue(a.storagePath().matches("[A-Za-z0-9._/-]+"), a.storagePath());
            assertEquals("image/jpeg", env.fake.requests("POST", "/storage/v1/object/receipts/").get(0).headers().get("content-type"));
        }
    }

    @Test
    void downloadReturnsBytes() throws Exception {
        try (TestEnv env = new TestEnv()) {
            env.fake.onBytes("GET", "/storage/v1/object/authenticated/receipts/", 200, new byte[] {1, 2, 3}, "application/pdf");
            byte[] data = new SupabaseAttachmentStore(env.rest).download(new Attachment("scan.pdf", RID + "/1-scan.pdf"));
            assertEquals(3, data.length);
        }
    }

    @Test
    void downloadMissingObjectGivesFriendlyMessage() {
        try (TestEnv env = new TestEnv()) {
            env.fake.on("GET", "/storage/v1/object/authenticated/receipts/", 404,
                    "{\"statusCode\":\"404\",\"error\":\"not_found\",\"message\":\"Object not found\"}");
            AttachmentException e = assertThrows(AttachmentException.class,
                    () -> new SupabaseAttachmentStore(env.rest).download(new Attachment("scan.pdf", RID + "/1-scan.pdf")));
            assertTrue(e.getMessage().contains("could not be found"), e.getMessage());
        }
    }

    @Test
    void removeSendsDeleteForTheStoragePath() throws Exception {
        try (TestEnv env = new TestEnv()) {
            env.fake.on("DELETE", "/storage/v1/object/receipts/", 200, "{}");
            new SupabaseAttachmentStore(env.rest).remove(new Attachment("scan.pdf", RID + "/1-scan.pdf"));
            assertEquals("/storage/v1/object/receipts/" + RID + "/1-scan.pdf",
                    env.fake.requests("DELETE", "/storage/v1/object/receipts/").get(0).path());
        }
    }
}
