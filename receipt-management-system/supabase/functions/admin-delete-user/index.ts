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
    if (body.user_id === callerData.user.id) {
      throw new HttpError(400, "The Creator cannot delete their own account.");
    }

    const { data: target, error: targetError } = await service.from("profiles")
      .select("role,email,full_name,company_id")
      .eq("id", body.user_id).maybeSingle();
    if (targetError) {
      console.error("Failed to load account for deletion:", targetError);
      throw new HttpError(500, "Could not load the account.");
    }
    if (!target) throw new HttpError(404, "That account no longer exists.");

    if (target.role === "creator") {
      const { count, error: countError } = await service.from("profiles")
        .select("id", { count: "exact", head: true })
        .eq("role", "creator").eq("is_active", true).eq("account_status", "APPROVED");
      if (countError) {
        console.error("Failed to verify remaining Creator accounts:", countError);
        throw new HttpError(500, "Could not verify Creator account protection.");
      }
      if ((count ?? 0) <= 1) throw new HttpError(400, "Cannot delete the last active Creator account.");
    }

    const { error: activityError } = await service.from("activity_log").insert({
      user_id: callerData.user.id,
      user_name: actor.full_name,
      company_id: actor.company_id,
      action: "user.delete",
      detail: `${target.email} (${body.user_id})`,
    });
    if (activityError) {
      console.error("Failed to record account deletion activity:", activityError);
      throw new HttpError(500, "Could not record account deletion; no account was removed.");
    }

    const { error: deleteError } = await service.auth.admin.deleteUser(body.user_id);
    if (deleteError) {
      console.error("Failed to delete account:", deleteError);
      throw new HttpError(500, "The account could not be deleted.");
    }
    return json({ deleted: true });
  } catch (error) {
    if (error instanceof HttpError) return json({ error: error.message }, error.status);
    console.error("Unhandled admin-delete-user error:", error);
    return json({ error: "The account could not be deleted due to a server error." }, 500);
  }
});
