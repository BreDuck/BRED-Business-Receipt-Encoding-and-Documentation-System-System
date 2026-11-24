import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

type ReviewAction = "APPROVE" | "REJECT";

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
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...cors, "Content-Type": "application/json" },
  });
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  try {
    let body: { account_id?: unknown; action?: unknown };
    try {
      body = await request.json() as { account_id?: unknown; action?: unknown };
    } catch {
      throw new HttpError(400, "A valid JSON request body is required.");
    }

    if (typeof body?.account_id !== "string"
      || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(body.account_id)) {
      throw new HttpError(400, "A valid account ID is required.");
    }
    if (body.action !== "APPROVE" && body.action !== "REJECT") {
      throw new HttpError(400, "Action must be APPROVE or REJECT.");
    }
    const action: ReviewAction = body.action;

    const url = Deno.env.get("SUPABASE_URL");
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
    if (!url || !serviceKey || !anonKey) throw new HttpError(500, "Server configuration is incomplete.");
    const authorization = request.headers.get("Authorization");
    if (!authorization) throw new HttpError(401, "Authentication required.");

    const service = createClient(url, serviceKey);
    const caller = createClient(url, anonKey, { global: { headers: { Authorization: authorization } } });
    const { data: callerData, error: authError } = await caller.auth.getUser();
    if (authError || !callerData.user) throw new HttpError(401, "Authentication required.");
    if (callerData.user.id === body.account_id) {
      throw new HttpError(403, "You cannot review your own registration.");
    }

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

    const { error: reviewError } = await service.rpc("review_account", {
      p_admin_id: callerData.user.id,
      p_target_id: body.account_id,
      p_action: action,
    });
    if (reviewError) {
      const message = reviewError.message.toLowerCase();
      if (message.includes("no longer pending")) throw new HttpError(409, "This registration is no longer pending.");
      if (message.includes("not found")) throw new HttpError(404, "Registration not found.");
      if (message.includes("cannot review their own")) throw new HttpError(403, "You cannot review your own registration.");
      if (message.includes("administrator")) throw new HttpError(403, "An approved, active administrator account is required.");
      console.error("Failed to review registration:", reviewError);
      throw new HttpError(500, "The registration could not be reviewed.");
    }

    return json({ account_id: body.account_id, account_status: action === "APPROVE" ? "APPROVED" : "REJECTED" });
  } catch (error) {
    if (error instanceof HttpError) return json({ error: error.message }, error.status);
    console.error("Unhandled review-account error:", error);
    return json({ error: "The registration could not be reviewed due to a server error." }, 500);
  }
});
