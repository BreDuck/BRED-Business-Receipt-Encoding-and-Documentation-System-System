import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

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

function requiredEnv(name: string): string {
  const value = Deno.env.get(name);
  if (!value) throw new HttpError(500, `Server configuration is missing ${name}.`);
  return value;
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  try {
    const body = await request.json().catch(() => null);
    if (!body || typeof body.user_id !== "string"
      || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(body.user_id)) {
      throw new HttpError(400, "A valid user_id is required.");
    }

    const url = requiredEnv("SUPABASE_URL");
    const serviceKey = requiredEnv("SUPABASE_SERVICE_ROLE_KEY");
    const anonKey = requiredEnv("SUPABASE_ANON_KEY");
    const authorization = request.headers.get("Authorization");
    if (!authorization) throw new HttpError(401, "Authentication required.");

    const service = createClient(url, serviceKey);
    const caller = createClient(url, anonKey, {
      global: { headers: { Authorization: authorization } },
    });
    const { data: callerData, error: authError } = await caller.auth.getUser();
    if (authError || !callerData.user) throw new HttpError(401, "Authentication required.");

    const { data: actor, error: actorError } = await service.from("profiles")
      .select("role,is_active,account_status,full_name,company_id")
      .eq("id", callerData.user.id).maybeSingle();
    if (actorError) {
      console.error("Failed to verify Creator profile:", actorError);
      throw new HttpError(500, "Could not verify Creator permissions.");
    }
    if (!actor || actor.role !== "creator" || !actor.is_active || actor.account_status !== "APPROVED") {
      throw new HttpError(403, "An approved, active Creator account is required.");
    }

    const { data: target, error: targetError } = await service.from("profiles")
      .select("email,full_name,company_id")
      .eq("id", body.user_id).maybeSingle();
    if (targetError) {
      console.error("Failed to load account for password reset:", targetError);
      throw new HttpError(500, "Could not load the account.");
    }
    if (!target) throw new HttpError(404, "That account no longer exists.");

    const recovery = createClient(url, anonKey, {
      auth: { autoRefreshToken: false, persistSession: false },
    });
    const { error: resetError } = await recovery.auth.resetPasswordForEmail(target.email);
    if (resetError) {
      console.error("Failed to send password reset email:", resetError);
      throw new HttpError(500, "The password reset email could not be sent.");
    }

    const { error: activityError } = await service.from("activity_log").insert({
      user_id: callerData.user.id,
      user_name: actor.full_name,
      company_id: actor.company_id,
      action: "user.password_reset",
      detail: target.email,
    });
    if (activityError) {
      console.error("Failed to record password reset activity:", activityError);
    }
    return json({ sent: true });
  } catch (error) {
    if (error instanceof HttpError) return json({ error: error.message }, error.status);
    console.error("Unhandled admin-send-password-reset error:", error);
    return json({ error: "The password reset email could not be sent due to a server error." }, 500);
  }
});
