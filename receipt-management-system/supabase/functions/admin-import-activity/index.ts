import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

type ActivityEntry = {
  source_key: string;
  at: string;
  user_id: string | null;
  user_name: string;
  action: string;
  detail: string;
};

class HttpError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(body: Record<string, unknown>, status = 200): Response {
  return Response.json(body, { status, headers: cors });
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  try {
    let body: { entries?: ActivityEntry[] };
    try {
      body = await request.json() as { entries?: ActivityEntry[] };
    } catch {
      throw new HttpError(400, "A valid JSON request body is required.");
    }

    if (!Array.isArray(body?.entries) || body.entries.length === 0 || body.entries.length > 200) {
      throw new HttpError(400, "entries must contain between 1 and 200 activity records.");
    }
    for (const entry of body.entries) {
      if (!entry || typeof entry.source_key !== "string" || entry.source_key.length > 100
        || typeof entry.at !== "string" || Number.isNaN(Date.parse(entry.at))
        || (entry.user_id !== null && (typeof entry.user_id !== "string"
          || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(entry.user_id)))
        || typeof entry.user_name !== "string" || typeof entry.action !== "string"
        || typeof entry.detail !== "string") {
        throw new HttpError(400, "An activity record has invalid fields.");
      }
    }

    const url = Deno.env.get("SUPABASE_URL");
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
    if (!url || !serviceKey || !anonKey) {
      throw new HttpError(500, "Server configuration is incomplete.");
    }
    const authorization = request.headers.get("Authorization");
    if (!authorization) throw new HttpError(401, "Authentication required.");

    const service = createClient(url, serviceKey);
    const caller = createClient(url, anonKey, { global: { headers: { Authorization: authorization } } });
    const { data: callerData, error: authError } = await caller.auth.getUser();
    if (authError || !callerData.user) throw new HttpError(401, "Authentication required.");

    const { data: admin, error: profileError } = await service.from("profiles")
      .select("role,is_active,account_status")
      .eq("id", callerData.user.id)
      .maybeSingle();
    if (profileError) {
      console.error("Failed to verify administrator profile:", profileError);
      throw new HttpError(500, "Could not verify administrator permissions.");
    }
    if (!admin || admin.role !== "admin" || !admin.is_active || admin.account_status !== "APPROVED") {
      throw new HttpError(403, "An approved, active administrator account is required.");
    }

    const { error: importError } = await service.from("activity_log")
      .upsert(body.entries, { onConflict: "source_key", ignoreDuplicates: true });
    if (importError) {
      console.error("Failed to import activity records:", importError);
      throw new HttpError(500, "Could not import the activity history.");
    }

    return json({ processed: body.entries.length });
  } catch (error) {
    if (error instanceof HttpError) return json({ error: error.message }, error.status);
    console.error("Unhandled admin-import-activity error:", error);
    return json({ error: "The activity history could not be imported due to a server error." }, 500);
  }
});
