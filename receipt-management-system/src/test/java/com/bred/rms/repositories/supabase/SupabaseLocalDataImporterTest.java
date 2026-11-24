package com.bred.rms.repositories.supabase;

import com.bred.rms.support.TestEnv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupabaseLocalDataImporterTest {
    private static final String RECEIPT_ID = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String SUPPLIER_ID = "bbbbbbbb-0000-0000-0000-000000000001";
    private static final String PROJECT_ID = "cccccccc-0000-0000-0000-000000000001";
    private static final String LOCAL_USER_ID = "dddddddd-0000-0000-0000-000000000001";
    private static final String CLOUD_USER_ID = "eeeeeeee-0000-0000-0000-000000000001";
    private static final String ATTACHMENT_ID = "ffffffff-0000-0000-0000-000000000001";

    @TempDir
    Path tempDirectory;

    @Test
    void importsLocalRecordsAndCanBeRetried() throws Exception {
        String storagePath = RECEIPT_ID + "/" + ATTACHMENT_ID + "-scan.pdf";
        Path attachmentPath = tempDirectory.resolve("attachments").resolve(storagePath);
        Files.createDirectories(attachmentPath.getParent());
        Files.writeString(attachmentPath, "pdf-bytes");
        Files.writeString(tempDirectory.resolve("data.json"), """
                {
                  "version": 1,
                  "users": [{"id":"%s","email":"local@example.com"}],
                  "suppliers": [{"id":"%s","businessName":"Acme","tin":"123","address":"A","contactNumber":"9"}],
                  "projects": [{"id":"%s","name":"Bridge","code":"BR-1","fundSource":"General"}],
                  "receipts": [{
                    "id":"%s","receiptNo":"R-1","date":"2025-01-02","type":"official_receipt",
                    "supplierId":"%s","projectId":"%s","items":[{"description":"Part","quantity":"1","unitPrice":"5"}],
                    "discount":"0","vat":"0","additionalCharges":"0",
                    "attachments":[{"fileName":"scan.pdf","storagePath":"%s"}],
                    "encodedBy":"%s","createdAt":"2025-01-02T00:00:00Z","modifiedAt":"2025-01-02T00:00:00Z"
                  }],
                  "activity": [{
                    "at":"2025-01-02T00:00:00Z","userId":"%s","userName":"Local User",
                    "action":"receipt.create","detail":"R-1"
                  }]
                }
                """.formatted(LOCAL_USER_ID, SUPPLIER_ID, PROJECT_ID, RECEIPT_ID, SUPPLIER_ID, PROJECT_ID,
                storagePath, LOCAL_USER_ID, LOCAL_USER_ID));

        try (TestEnv env = new TestEnv()) {
            env.fake.on("GET", "/rest/v1/profiles", 200,
                    "[{\"id\":\"" + CLOUD_USER_ID + "\",\"email\":\"LOCAL@example.com\"}]");
            env.fake.on("POST", "/rest/v1/suppliers", 201, "[]");
            env.fake.on("POST", "/rest/v1/projects", 201, "[]");
            env.fake.on("POST", "/storage/v1/object/receipts/", 200, "{}");
            env.fake.on("POST", "/rest/v1/rpc/save_receipt", 200, "\"" + RECEIPT_ID + "\"");
            env.fake.on("GET", "/rest/v1/receipts", 200, """
                    [{"id":"%s","receipt_no":"R-1","receipt_date":"2025-01-02","receipt_type":"official_receipt",
                      "supplier_id":"%s","project_id":"%s","receipt_items":[],
                      "receipt_attachments":[{"file_name":"scan.pdf","storage_path":"%s"}]}]
                    """.formatted(RECEIPT_ID, SUPPLIER_ID, PROJECT_ID, storagePath));
            env.fake.on("POST", "/functions/v1/admin-import-activity", 200, "{\"processed\":1}");

            var importer = new SupabaseLocalDataImporter(env.rest, new SupabaseReceiptRepository(env.rest),
                    new SupabaseAttachmentStore(env.rest));
            var first = importer.importDirectory(tempDirectory);
            var second = importer.importDirectory(tempDirectory);

            assertEquals(1, first.suppliers());
            assertEquals(1, first.projects());
            assertEquals(1, first.receipts());
            assertEquals(1, first.attachments());
            assertEquals(1, first.activityEntries());
            assertEquals(1, first.localUsers());
            assertEquals(first, second);

            var upsert = env.fake.requests("POST", "/rest/v1/suppliers").get(0);
            assertTrue(upsert.pathAndQuery().contains("on_conflict=id"));
            var upload = env.fake.requests("POST", "/storage/v1/object/receipts/").get(0);
            assertEquals("true", upload.headers().get("x-upsert"));
            var activity = env.fake.requests("POST", "/functions/v1/admin-import-activity").get(0).bodyString();
            assertTrue(activity.contains(CLOUD_USER_ID), "local activity is linked to the matching cloud account");
        }
    }
}
